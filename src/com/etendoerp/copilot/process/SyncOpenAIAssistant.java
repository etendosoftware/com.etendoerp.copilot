package com.etendoerp.copilot.process;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotOpenAIModel;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.OpenAIUtils;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;

public class SyncOpenAIAssistant extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger(SyncOpenAIAssistant.class);
  public static final String ERROR = "error";

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {

    // Declare json to be returned
    JSONObject result = new JSONObject();
    try {
      OBContext.setAdminMode();
      // Get request parameters
      JSONObject request = new JSONObject(content);
      JSONArray selecterRecords = request.optJSONArray("recordIds");
      int totalRecords = (selecterRecords == null) ? 0 : selecterRecords.length();
      if (totalRecords == 0) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_NoSelectedRecords"));
      }
      int syncCount = 0;
      String openaiApiKey = OpenAIUtils.getOpenaiApiKey();

      List<CopilotApp> appList = new ArrayList<>();

      for (int i = 0; i < totalRecords; i++) {
        CopilotApp app = OBDal.getInstance().get(CopilotApp.class, selecterRecords.getString(i));
        if (app != null) { // Null check
          appList.add(app);
        }
      }
      Set<CopilotAppSource> appSourcesToRefresh = new HashSet<>();
      Set<CopilotAppSource> appSourcesToSync = new HashSet<>();
      for (CopilotApp app : appList) {
        checkWebHookAccess(app);
        List<CopilotAppSource> appSources = app.getETCOPAppSourceList();
        List<CopilotAppSource> listSourcesForKb = appSources.stream().filter(CopilotConstants::isKbBehaviour).collect(
            Collectors.toList());
        if (StringUtils.equalsIgnoreCase(app.getAppType(), CopilotConstants.APP_TYPE_OPENAI)) {
          if (!listSourcesForKb.isEmpty() && !app.isCodeInterpreter() && !app.isRetrieval()) {

            throw new OBException(
                String.format(OBMessageUtils.messageBD("ETCOP_Error_KnowledgeBaseIgnored"), app.getName()));
          }
        }
        appSourcesToSync.addAll(listSourcesForKb);
        appSourcesToRefresh.addAll(appSources);
      }
      for (CopilotAppSource as : appSourcesToRefresh) {
        log.debug("Syncing file " + as.getFile().getName());
        CopilotFile fileToSync = as.getFile();
        WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class)
            .executeHooks(fileToSync);
      }

      //for langchain apps we need to reset vector DB, so collect all langchain apps and reset the vector DBs
      List<CopilotApp> langchainApps = appList.stream().filter(
              app -> StringUtils.equalsIgnoreCase(app.getAppType(), CopilotConstants.APP_TYPE_LANGCHAIN))
          .distinct().collect(Collectors.toList());
      for (CopilotApp langApp : langchainApps) {
        CopilotUtils.resetVectorDB(langApp);
      }

      if (!appList.isEmpty()) {
        syncOpenaiModels(openaiApiKey);
      }
      for (CopilotAppSource appSource : appSourcesToSync) {

        if (StringUtils.equalsIgnoreCase(appSource.getEtcopApp().getAppType(), CopilotConstants.APP_TYPE_OPENAI)) {

          OpenAIUtils.syncAppSource(appSource, openaiApiKey);


        } else if (StringUtils.equalsIgnoreCase(appSource.getEtcopApp().getAppType(),
            CopilotConstants.APP_TYPE_LANGCHAIN)) {
          CopilotUtils.syncAppLangchainSource(appSource);
        } else {
          // For langgraph apps, nothing to do
        }

      }

      for (CopilotApp app : appList) {
        if (StringUtils.equalsIgnoreCase(app.getAppType(), CopilotConstants.APP_TYPE_OPENAI)) {
          OBDal.getInstance().refresh(app);
          OpenAIUtils.refreshVectorDb(app);
          OBDal.getInstance().refresh(app);
          syncCount = callSync(syncCount, openaiApiKey, app);
        } else {
          syncCount++;
        }
      }


      returnSuccessMsg(result, syncCount, totalRecords);
    } catch (Exception e) {
      log.error("Error in process", e);
      try {
        OBDal.getInstance().getConnection().rollback();
        result = new JSONObject();
        JSONObject errorMessage = new JSONObject();
        Throwable ex = DbUtility.getUnderlyingSQLException(e);
        String message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
        errorMessage.put("severity", ERROR);
        errorMessage.put("title", OBMessageUtils.messageBD("Error"));
        errorMessage.put("text", message);
        result.put("message", errorMessage);
      } catch (Exception ignore) {
        log.error("Error in process", ignore);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return result;
  }


  /**
   * This method checks the access of each role to the webhooks associated with the given application.
   * It queries the database for each role and associated webhook, and if a role does not have access to a webhook,
   * it calls the upsertAccess method to create a new access record.
   *
   * @param app
   *     The CopilotApp object representing the application for which to check webhook access.
   */
  private void checkWebHookAccess(CopilotApp app) {
    StringBuilder hql = new StringBuilder();
    hql.append("select ");
    hql.append("roapp.role.name, ");
    hql.append("roapp.role.id, ");
    hql.append("toolweb.webHook.name, ");
    hql.append("toolweb.webHook.id, ");
    hql.append("(  ");
    hql.append("    select count(id) ");
    hql.append("    from smfwhe_definedwebhook_role hkrole ");
    hql.append("    where hkrole.role = roapp.role ");
    hql.append("        and hkrole.smfwheDefinedwebhook = toolweb.webHook ");
    hql.append(") ");
    hql.append("from ETCOP_Role_App roapp ");
    hql.append("    left join ETCOP_App_Tool apptool on apptool.copilotApp = roapp.copilotApp ");
    hql.append("    left join etcop_tool_wbhk toolweb on apptool.copilotTool = toolweb.copilotTool ");
    hql.append("where 1 = 1 ");
    hql.append("and roapp.copilotApp.id = :appId ");
  /*
    Returns a list of arrays with the following structure:
    [0] Role Id
    [1] Role Name
    [2] Webhook Id
    [3] Webhook Name
    [4] Quantity of Record of access to the webhook by the role (if not null, the role has access)
    Basically, we are looking for the roles that don't have access to the webhook, if the quantity of records is 0, the role doesn't have access
   */
    List<Object[]> results = OBDal.getInstance().getSession().createQuery(hql.toString())
        .setParameter("appId", app.getId()).list();
    if (log.isDebugEnabled()) {
      log.debug(String.format("Results: %d", results.size()));
    }
    for (Object[] result : results) {
      String roleId = (String) result[1];
      String roleName = (String) result[0];
      String webhookId = (String) result[3];
      String webhookName = (String) result[2];
      Long accessCount = (Long) result[4];
      if (accessCount == null || accessCount == 0) {
        if (log.isDebugEnabled()) {
          log.debug(String.format("Role %s does not have access to webhook %s", roleName, webhookName));
        }
        Role role = OBDal.getInstance().get(Role.class, roleId);
        DefinedWebHook hook = OBDal.getInstance().get(DefinedWebHook.class, webhookId);
        if (role != null && hook != null) { // Null check
          upsertAccess(hook, role, false);
        }
      }
    }
  }

  /**
   * This method is used to insert or update access to a defined webhook for a specific role.
   * If the role already has access to the webhook and skipIfExist is false, the method will return without making any changes.
   * If the role does not have access to the webhook or skipIfExist is true, a new access record will be created.
   *
   * @param hook
   *     The DefinedWebHook object representing the webhook to which access is being granted.
   * @param role
   *     The Role object representing the role to which access is being granted.
   * @param skipIfExist
   *     A boolean value that determines whether to skip the operation if the role already has access to the webhook.
   */
  private void upsertAccess(DefinedWebHook hook, Role role, boolean skipIfExist) {
    // If skipIfExist is false, check if the role already has access to the webhook
    if (!skipIfExist) {
      OBCriteria<DefinedwebhookRole> critWebHookRole = OBDal.getInstance().createCriteria(DefinedwebhookRole.class);
      critWebHookRole.add(Restrictions.eq(DefinedwebhookRole.PROPERTY_SMFWHEDEFINEDWEBHOOK, hook));
      critWebHookRole.add(Restrictions.eq(DefinedwebhookRole.PROPERTY_ROLE, role));
      critWebHookRole.setMaxResults(1);
      DefinedwebhookRole wbhkRole = (DefinedwebhookRole) (critWebHookRole.uniqueResult());
      // If the role already has access, return without making any changes
      if (wbhkRole != null) {
        return;
      }
    }
    // If the role does not have access or skipIfExist is true, create a new access record
    DefinedwebhookRole newRole = OBProvider.getInstance().get(DefinedwebhookRole.class);
    newRole.setNewOBObject(true);
    newRole.setRole(role);
    newRole.setClient(role.getClient());
    newRole.setOrganization(role.getOrganization());
    newRole.setSmfwheDefinedwebhook(hook);
    OBDal.getInstance().save(newRole);
  }

  private void syncOpenaiModels(String openaiApiKey) {
    //ask to openai for the list of models
    JSONArray modelJSONArray = OpenAIUtils.getModelList(openaiApiKey);
    //transfer ids of json array to a list of strings
    List<JSONObject> modelIds = new ArrayList<>();
    for (int i = 0; i < modelJSONArray.length(); i++) {
      try {
        JSONObject modelObj = modelJSONArray.getJSONObject(i);
        if (!StringUtils.startsWith(modelObj.getString("id"), "ft:") && //exclude the models that start with gpt-4o
            !StringUtils.equals(modelObj.getString("owned_by"), "openai-dev") &&
            !StringUtils.equals(modelObj.getString("owned_by"), "openai-internal")) {
          modelIds.add(modelObj);
        }
      } catch (JSONException e) {
        log.error("Error in syncOpenaiModels", e);
      }
    }
    //now we have a list of ids, we can get the list of models in the database
    List<CopilotOpenAIModel> modelsInDB = OBDal.getInstance().createCriteria(CopilotOpenAIModel.class).list();

    //now we will check the models of the database that are not in the list of models from openai, to mark them as not active
    for (CopilotOpenAIModel modelInDB : modelsInDB) {
      //check if the model is in the list of models from openai
      if (modelIds.stream().noneMatch(modelInModelList(modelInDB))) {
        modelInDB.setActive(false);
        OBDal.getInstance().save(modelInDB);
        continue;
      }
      modelIds.removeIf(modelInModelList(modelInDB));
    }
    //the models that are not in the database, we will create them,
    saveNewModels(modelIds);
  }

  /**
   * This method is used to save new models into the database.
   * It iterates over a list of JSONObjects, each representing a model, and creates a new CopilotOpenAIModel instance for each one.
   * The method sets the client, organization, search key, name, and active status for each new model.
   * It also retrieves the creation date of the model from the JSONObject, converts it from a Unix timestamp to a Date object, and sets it for the new model.
   * The new model is then saved into the database.
   * After all models have been saved, the method flushes the session to ensure that all changes are persisted to the database.
   * If an exception occurs during the execution of the method, it logs the error message and continues with the next iteration.
   * The method uses the OBContext to set and restore the admin mode before and after the execution of the method, respectively.
   *
   * @param modelIds
   *     A list of JSONObjects, each representing a model to be saved into the database.
   */
  private void saveNewModels(List<JSONObject> modelIds) {
    try {
      OBContext.setAdminMode();
      for (JSONObject modelData : modelIds) {
        CopilotOpenAIModel model = OBProvider.getInstance().get(CopilotOpenAIModel.class);
        model.setNewOBObject(true);
        model.setClient(OBDal.getInstance().get(Client.class, "0"));
        model.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
        model.setSearchkey(modelData.optString("id"));
        model.setName(modelData.optString("id"));
        model.setActive(true);
        //get the date in The Unix timestamp (in seconds) when the model was created. Convert to date
        long creationDate = modelData.optLong("created"); // Unix timestamp (in seconds) when the model was created
        model.setCreationDate(new java.util.Date(creationDate * 1000L));
        OBDal.getInstance().save(model);
      }
      OBDal.getInstance().flush();
    } catch (Exception e) {
      log.error("Error in saveNewModels", e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private Predicate<JSONObject> modelInModelList(CopilotOpenAIModel modelInDB) {
    return model -> StringUtils.equals(model.optString("id"), modelInDB.getSearchkey());
  }

  private int callSync(int syncCount, String openaiApiKey, CopilotApp app) {
    OpenAIUtils.syncAssistant(openaiApiKey, app);
    syncCount++;

    return syncCount;
  }

  private void returnSuccessMsg(JSONObject result, int syncCount,
      int totalRecords) throws JSONException {

    // Message in tab from where the process is executed
    JSONArray actions = new JSONArray();
    JSONObject showMsgInProcessView = new JSONObject();
    showMsgInProcessView.put("msgType", "success");
    showMsgInProcessView.put("msgTitle", OBMessageUtils.messageBD("Success"));
    showMsgInProcessView.put("msgText",
        String.format(String.format(OBMessageUtils.messageBD("ETCOP_SuccessSync"), syncCount, totalRecords)));
    showMsgInProcessView.put("wait", true);
    JSONObject showMsgInProcessViewAction = new JSONObject();
    showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
    actions.put(showMsgInProcessViewAction);
    result.put("responseActions", actions);
  }
}
