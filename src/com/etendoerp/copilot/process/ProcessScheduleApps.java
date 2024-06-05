package com.etendoerp.copilot.process;

import static org.openbravo.client.application.process.BaseProcessActionHandler.hasAccess;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.data.ETCOPSchedule;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.OpenAIUtils;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.ui.ProcessRequest;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

public class ProcessScheduleApps extends DalBaseProcess {

  private ProcessLogger logger;

  /**
   * This method is the main execution point for the ProcessScheduleApps process.
   * It retrieves the ProcessRequest associated with the current process execution and fetches all ETCOPSchedule objects related to this ProcessRequest.
   * It then logs the number of schedules to be processed and calls the refreshScheduleFiles method to update the files associated with the schedules.
   * After refreshing the files, it logs the number of schedules to be processed again and calls the processSchedules method to process each schedule.
   *
   * @param processBundle
   *     The ProcessBundle object associated with the current process execution. It contains information about the process request and other related data.
   * @throws Exception
   *     If any error occurs during the execution of the method.
   */
  @Override
  protected void doExecute(ProcessBundle processBundle) throws Exception {
    logger = processBundle.getLogger();
    ProcessRequest processRequest = OBDal.getInstance()
        .get(ProcessRequest.class, processBundle.getProcessRequestId());
    OBCriteria<ETCOPSchedule> criteria = OBDal.getInstance().createCriteria(ETCOPSchedule.class);
    List<ETCOPSchedule> schedules = criteria.add(
        Restrictions.eq(ETCOPSchedule.PROPERTY_PROCESSREQUEST, processRequest)).list();
    logger.log("Refreshing " + schedules.size() + " schedules\n");
    refreshScheduleFiles(schedules);
    logger.log("Processing " + schedules.size() + " schedules\n");
    processSchedules(schedules);
  }

  /**
   * This method refreshes the files associated with a list of ETCOPSchedule objects.
   * It retrieves the OpenAI API key and iterates over each ETCOPSchedule in the list.
   * For each ETCOPSchedule, it iterates over its associated CopilotApp sources.
   * If a source is identified as an attachment or a question, it logs the name of the source file and syncs the source with the OpenAI API.
   *
   * @param schedules
   *     The list of ETCOPSchedule objects whose associated files need to be refreshed.
   * @throws JSONException
   *     If an error occurs while processing the JSON data.
   * @throws IOException
   *     If an I/O error occurs during the operation.
   */
  private void refreshScheduleFiles(List<ETCOPSchedule> schedules)
      throws JSONException, IOException {
    String openaiApiKey = OpenAIUtils.getOpenaiApiKey();
    for (ETCOPSchedule schedule : schedules) {
      for (var source : schedule.getCopilotApp().getETCOPAppSourceList()) {
        if (CopilotConstants.isAttachBehaviour(source) || CopilotConstants.isQuestionBehaviour(source)) {
          logger.log("- Syncing source " + source.getFile().getName() + "\n");
          OpenAIUtils.syncAppSource(source, openaiApiKey);
        }
      }
    }
  }

  /**
   * This method processes a list of ETCOPSchedule objects.
   * It iterates over each ETCOPSchedule in the list and performs the following steps:
   * 1. It retrieves the Role from the current OBContext and checks if the Role has access to the CopilotApp associated with the ETCOPSchedule.
   * 2. If the Role does not have access, it logs an error message and breaks the loop.
   * 3. If the Role has access, it iterates over the CopilotApp sources and adds the OpenAI file ID of each source identified as an attachment to a list.
   * 4. It then logs the prompt of the ETCOPSchedule and sends a question to the Copilot service using the RestServiceUtil.handleQuestion method.
   * 5. If the response from the Copilot service contains a "response" field, it logs the response.
   * 6. If a ConnectException occurs during the execution of the method, it logs an error message and throws an OBException.
   * 7. If any other Exception occurs during the execution of the method, it logs the error message and throws an OBException.
   *
   * @param schedules
   *     The list of ETCOPSchedule objects to be processed.
   * @throws OBException
   *     If an error occurs during the execution of the method.
   */
  private void processSchedules(List<ETCOPSchedule> schedules) {
    for (ETCOPSchedule schedule : schedules) {
      try {
        List<String> fileIds = new ArrayList<>();
        CopilotApp copilotApp = schedule.getCopilotApp();
        Role role = OBContext.getOBContext().getRole();
        if (!checkRoleAccessApp(role, copilotApp)) {
          logger.log(
              "<- Error: The Role" + role.getName() + " does not have access to Copilot App " + copilotApp.getName() + "\n");
          break;
        }
        for (var source : copilotApp.getETCOPAppSourceList()) {
          if (CopilotConstants.isAttachBehaviour(source)) {
            fileIds.add(source.getFile().getOpenaiIdFile());
          }
        }
        logger.log("-> Send question to copilot:\n---\n " + schedule.getPrompt() + "\n---\n");
        JSONObject response = RestServiceUtil.handleQuestion(copilotApp, schedule.getConversation(),
            schedule.getPrompt(), fileIds);
        if (response.has("response")) {
          logger.log("<- Copilot response:\n---\n" + response.getString("response") + "\n---\n");
        }
      } catch (ConnectException e) {
        logger.log("<- Copilot response ERROR: Error connecting with Copilot Service.\n");
        throw new OBException(e);
      } catch (Exception e) {
        logger.log("<- Copilot response ERROR: " + e.getMessage() + "\n");
        throw new OBException(e);
      }
    }
  }

  /**
   * This method checks if a given role has access to a specific CopilotApp.
   * It sets the OBContext to admin mode and creates a criteria query to find a CopilotRoleApp that matches the provided role and CopilotApp.
   * If a matching CopilotRoleApp is found, it returns true, indicating that the role has access to the app.
   * If no matching CopilotRoleApp is found, it returns false.
   * If an exception occurs during the execution of the method, it logs the error and returns false.
   * After the check, it restores the previous OBContext mode.
   *
   * @param role
   *     The Role object to check for access to the CopilotApp.
   * @param copilotApp
   *     The CopilotApp object to check for access by the Role.
   * @return true if the Role has access to the CopilotApp, false otherwise.
   */
  private boolean checkRoleAccessApp(Role role, CopilotApp copilotApp) {
    try {
      OBContext.setAdminMode();
      CopilotRoleApp roleApp = (CopilotRoleApp) OBDal.getInstance().createCriteria(CopilotRoleApp.class)
          .add(Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, role))
          .add(Restrictions.eq(CopilotRoleApp.PROPERTY_COPILOTAPP, copilotApp)).setMaxResults(1).uniqueResult();
      return roleApp != null;
    } catch (OBException e) {
      logger.log("Error checking role access to Copilot App " + copilotApp.getName() + ": " + e.getMessage());
      return false;
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}
