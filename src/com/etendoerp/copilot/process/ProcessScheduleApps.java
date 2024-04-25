package com.etendoerp.copilot.process;

import com.etendoerp.copilot.data.ETCOPSchedule;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.OpenAIUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
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

  private void processSchedules(List<ETCOPSchedule> schedules) {
    for (ETCOPSchedule schedule : schedules) {
      try {
        List<String> fileIds = new ArrayList<>();
        for (var source : schedule.getCopilotApp().getETCOPAppSourceList()) {
          if (CopilotConstants.isAttachBehaviour(source)) {
            fileIds.add(source.getFile().getOpenaiIdFile());
          }
        }
        logger.log("-> Send question to copilot:\n---\n " + schedule.getPrompt() + "\n---\n");
        JSONObject response = RestServiceUtil.handleQuestion(schedule.getCopilotApp(), schedule.getConversation(),
            schedule.getPrompt(), fileIds);
        if(response.has("response")) {
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
}
