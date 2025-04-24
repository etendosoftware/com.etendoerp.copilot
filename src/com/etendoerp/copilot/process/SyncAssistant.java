package com.etendoerp.copilot.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotModelUtils;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.OpenAIUtils;

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
      //append team members if there are selected graph agents
      List<CopilotApp> childs = new ArrayList<>();
      for (CopilotApp app : appList) {
        if (app.getAppType().equals(CopilotConstants.APP_TYPE_LANGGRAPH)) {
          List<TeamMember> teamMembers = app.getETCOPTeamMemberList();
          for (TeamMember teamMember : teamMembers) {
            childs.add(teamMember.getMember());
          }
        }
      }
      appList.addAll(childs);
      //remove duplicates
      appList = appList.stream().distinct().collect(Collectors.toList());

      // Sync models with Copilot remote dataset
      CopilotModelUtils.syncModels();
      // update accesses
      for (CopilotApp app : appList) {
        CopilotUtils.checkWebHookAccess(app);
      }
      // Generate attachment for each file
      CopilotUtils.generateFilesAttachment(appList);
      //validates OpenAI API key
      String openaiApiKey = OpenAIUtils.getOpenaiApiKey();
      if (openaiApiKey == null) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_ApiKeyNotFound"));
      }
      OpenAIUtils.getModelList(openaiApiKey);
      // Sync knowledge files to each assistant
      result = CopilotUtils.syncKnowledgeFiles(appList, openaiApiKey);
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
}
