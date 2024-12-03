package com.etendoerp.copilot.process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.OpenAIUtils;
import com.etendoerp.openapi.data.OpenAPIRequest;
import com.etendoerp.openapi.data.OpenApiFlow;
import com.etendoerp.openapi.data.OpenApiFlowPoint;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;
import com.etendoerp.webhookevents.data.OpenAPIWebhook;

/**
 * The {@code SyncAssistant} class is responsible for synchronizing assistant knowledge bases and file attachments.
 * It provides methods to manage synchronization of files, application access, and webhook roles, and interacts
 * with external APIs like OpenAI and LangChain for knowledge file synchronization.
 *
 * <p>Core responsibilities include:
 * <ul>
 *   <li>Retrieving selected applications based on record IDs</li>
 *   <li>Generating attachments for knowledge files</li>
 *   <li>Synchronizing files to external APIs (OpenAI, LangChain)</li>
 *   <li>Managing webhook access for roles</li>
 * </ul>
 *
 * <p>Each method within this class focuses on a distinct aspect of the synchronization process,
 * making the code modular and aiding in maintenance and future extensibility.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * SyncAssistant syncAssistant = new SyncAssistant();
 * JSONObject result = syncAssistant.doExecute(parameters, content);
 * }
 * </pre>
 *
 * <p>This class logs events at various points using the {@link Logger} to facilitate troubleshooting.
 *
 * <p>Additionally, this class extends {@link BaseProcessActionHandler}, so it must implement the
 * {@code doExecute} method, which is invoked when the process is run.
 *
 * @see BaseProcessActionHandler
 * @see OpenAIUtils
 * @see CopilotUtils
 */
public class SyncAssistant extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger(SyncAssistant.class);
  public static final String ERROR = "error";

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    // Declare json to be returned
    JSONObject result = new JSONObject();
    try {
      OBContext.setAdminMode();
      // Get request parameters
      JSONObject request = new JSONObject(content);
      JSONArray selectedRecords = request.optJSONArray("recordIds");
      List<CopilotApp> appList = getSelectedApps(selectedRecords);
      // update accesses
      for (CopilotApp app : appList) {
        checkWebHookAccess(app);
      }
      // Generate attachment for each file
      generateFilesAttachment(appList);
      // Sync knowledge files to each assistant
      result = syncKnowledgeFiles(appList);
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
      } catch (Exception ex) {
        log.error("Error in process", ex);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return result;
  }

  /**
   * This method retrieves a list of {@link CopilotApp} instances based on the given selected records.
   * It processes the provided {@link JSONArray} of record IDs, fetching each corresponding
   * {@link CopilotApp} from the database and adding it to the resulting list.
   * If the input array is null or empty, an {@link OBException} is thrown to indicate that no
   * records were selected. If a record ID does not correspond to a valid {@link CopilotApp},
   * it is ignored and not added to the list.
   *
   * @param selectedRecords
   *     The {@link JSONArray} containing the IDs of the selected records to be retrieved.
   * @return A list of {@link CopilotApp} objects corresponding to the provided record IDs.
   * @throws JSONException
   *     If an error occurs while processing the {@link JSONArray}.
   * @throws OBException
   *     If the {@link JSONArray} is null or contains no records.
   */
  private List<CopilotApp> getSelectedApps(JSONArray selectedRecords) throws JSONException {
    List<CopilotApp> appList = new ArrayList<>();
    if (selectedRecords == null || selectedRecords.length() == 0) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_NoSelectedRecords"));
    }

    for (int i = 0; i < selectedRecords.length(); i++) {
      CopilotApp app = OBDal.getInstance().get(CopilotApp.class, selectedRecords.getString(i));
      if (app != null) {
        appList.add(app);
      }
    }
    return appList;
  }

  /**
   * This method generates file attachments for a list of {@link CopilotApp} instances.
   * It processes each application in the given list, verifying webhook access and retrieving
   * the associated {@link CopilotAppSource} objects to be refreshed.
   * The method updates the synchronization status of each {@link CopilotApp} to "synchronized"
   * and collects all the related {@link CopilotAppSource} instances. It then processes each
   * source by logging the file name and executing the relevant hooks using the
   * {@link CopilotFileHookManager}.
   *
   * @param appList
   *     A list of {@link CopilotApp} instances for which file attachments are being generated.
   */
  private void generateFilesAttachment(List<CopilotApp> appList) {
    Set<CopilotAppSource> appSourcesToRefresh = new HashSet<>();
    for (CopilotApp app : appList) {
      List<CopilotAppSource> appSources = app.getETCOPAppSourceList();
      app.setSyncStatus(CopilotConstants.SYNCHRONIZED_STATE);
      appSourcesToRefresh.addAll(appSources);
    }

    for (CopilotAppSource as : appSourcesToRefresh) {
      log.debug("Syncing file {}", as.getFile().getName());
      CopilotFile fileToSync = as.getFile();
      WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class)
          .executeHooks(fileToSync);
    }
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

    /// Create a set of hooks used by the assistant
    Set<DefinedWebHook> hooks = app.getETCOPAppSourceList().stream()
        // Get the file associated with each source
        .map(CopilotAppSource::getFile)
        // Filter files of type "FLOW"
        .filter(f -> StringUtils.equalsIgnoreCase(f.getType(), "FLOW"))
        // Map to the corresponding OpenAPI flow objects
        .map(CopilotFile::getEtapiOpenapiFlow)
        // Safeguard against null values
        .filter(Objects::nonNull)
        // Extract and process flow points to retrieve defined webhooks
        .flatMap(flow -> flow.getETAPIOpenApiFlowPointList().stream())
        // Get the request from each flow point
        .map(OpenApiFlowPoint::getEtapiOpenapiReq)
        // Safeguard against null requests
        .filter(Objects::nonNull)
        // Extract the list of webhooks from the requests
        .flatMap(req -> req.getSmfwheOpenapiWebhkList().stream())
        // Get the defined webhook from each webhook object
        .map(OpenAPIWebhook::getSmfwheDefinedwebhook)
        // Safeguard against null webhooks
        .filter(Objects::nonNull)
        // Collect all defined webhooks into a Set
        .collect(Collectors.toSet());

        log.debug("Hooks: {}", hooks);


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

  /**
   * This method synchronizes knowledge base files for a list of {@link CopilotApp} instances.
   * It iterates over the provided list of applications, filtering the associated
   * {@link CopilotAppSource} objects to include only those that represent knowledge base files.
   * The synchronization is handled differently based on the application type.
   * <p>Supported application types:
   * <ul>
   *   <li>{@link CopilotConstants#APP_TYPE_OPENAI}: Synchronizes files with the OpenAI API.</li>
   *   <li>{@link CopilotConstants#APP_TYPE_LANGCHAIN}: Synchronizes files with the LangChain API.</li>
   *   <li>{@link CopilotConstants#APP_TYPE_LANGGRAPH}: No synchronization is performed.</li>
   *   <li>For other application types, an error is logged.</li>
   * </ul>
   *
   * @param appList
   *     A list of {@link CopilotApp} instances for which knowledge base files are being synchronized.
   * @return A {@link JSONObject} containing a message indicating the number of successfully
   *     synchronized applications and the total number of applications processed.
   * @throws JSONException
   *     If an error occurs while building the result message.
   * @throws IOException
   *     If an input/output error occurs during synchronization.
   */
  private JSONObject syncKnowledgeFiles(List<CopilotApp> appList) throws JSONException, IOException {
    int syncCount = 0;
    String openaiApiKey = OpenAIUtils.getOpenaiApiKey();
    if (openaiApiKey != null) {
      OpenAIUtils.syncOpenaiModels(openaiApiKey);
    } else {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ApiKeyNotFound"));
    }
    for (CopilotApp app : appList) {
      List<CopilotAppSource> knowledgeBaseFiles = app.getETCOPAppSourceList().stream()
          .filter(CopilotConstants::isKbBehaviour)
          .collect(Collectors.toList());
      switch (app.getAppType()) {
        case CopilotConstants.APP_TYPE_OPENAI:
          syncKBFilesToOpenAI(app, knowledgeBaseFiles, openaiApiKey);
          break;
        case CopilotConstants.APP_TYPE_LANGCHAIN:
          syncKBFilesToLangChain(app, knowledgeBaseFiles);
          break;
        case CopilotConstants.APP_TYPE_MULTIMODEL:
          syncKBFilesToLangChain(app, knowledgeBaseFiles);
          break;
        case CopilotConstants.APP_TYPE_LANGGRAPH:
          log.debug("Sync not needed for LangGraph");
          break;
        default:
          log.warn("Unsupported application type encountered: {}", app.getAppType());
      }
      syncCount++;
    }
    return buildMessage(syncCount, appList.size());
  }

  /**
   * This method synchronizes knowledge base files with the OpenAI API for a given {@link CopilotApp}.
   * It processes the list of provided {@link CopilotAppSource} instances, sending each one to
   * the OpenAI API for synchronization. The method also performs additional steps to refresh
   * the application's state and synchronize the assistant configuration.
   *
   * <p>If the application is configured for OpenAI but the knowledge base files are present,
   * and it is neither a code interpreter nor retrieval-enabled, an {@link OBException} is thrown
   * to indicate that the knowledge base files are ignored for the given app configuration.
   *
   * <p>After synchronizing each knowledge base file, the application's state is refreshed from
   * the database, the vector database is updated, and the assistant configuration is synchronized
   * with the OpenAI API.
   *
   * @param app
   *     The {@link CopilotApp} instance for which knowledge base files are being synchronized.
   * @param knowledgeBaseFiles
   *     A list of {@link CopilotAppSource} objects representing the knowledge base files to be synchronized.
   * @param openaiApiKey
   *     The API key used for authentication with the OpenAI API.
   * @throws JSONException
   *     If an error occurs while processing JSON data.
   * @throws IOException
   *     If an input/output error occurs during the synchronization process.
   * @throws OBException
   *     If the application's configuration does not allow for knowledge base synchronization.
   */
  private void syncKBFilesToOpenAI(CopilotApp app, List<CopilotAppSource> knowledgeBaseFiles,
      String openaiApiKey) throws JSONException, IOException {
    OpenAIUtils.checkIfAppCanUseAttachedFiles(app, knowledgeBaseFiles);
    for (CopilotAppSource appSource : knowledgeBaseFiles) {
      OpenAIUtils.syncAppSource(appSource, openaiApiKey);
    }
    OBDal.getInstance().refresh(app);
    OpenAIUtils.refreshVectorDb(app);
    OBDal.getInstance().refresh(app);
    OpenAIUtils.syncAssistant(openaiApiKey, app);
  }

  /**
   * This method synchronizes knowledge base files with the LangChain API for a given {@link CopilotApp}.
   * It performs several steps to ensure that the application's vector database is properly updated
   * and the knowledge base files are synchronized.
   *
   * <p>The synchronization process involves resetting the vector database, processing each
   * {@link CopilotAppSource} in the list to synchronize it with the LangChain API, and then
   * purging the vector database to remove any outdated information.
   *
   * @param app
   *     The {@link CopilotApp} instance for which knowledge base files are being synchronized.
   * @param knowledgeBaseFiles
   *     A list of {@link CopilotAppSource} objects representing the knowledge base files to be synchronized.
   * @throws JSONException
   *     If an error occurs while processing JSON data.
   * @throws IOException
   *     If an input/output error occurs during the synchronization process.
   */
  private void syncKBFilesToLangChain(CopilotApp app,
      List<CopilotAppSource> knowledgeBaseFiles) throws JSONException, IOException {
    CopilotUtils.resetVectorDB(app);
    for (CopilotAppSource appSource : knowledgeBaseFiles) {
      CopilotUtils.syncAppLangchainSource(appSource);
    }
    CopilotUtils.purgeVectorDB(app);
  }

  private JSONObject buildMessage(int syncCount, int totalRecords) throws JSONException {
    JSONObject result = new JSONObject();
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
    return result;
  }
}
