package com.etendoerp.copilot.process;

import com.etendoerp.copilot.data.ETCOPSchedule;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.OpenAIUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.ProcessRequest;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProcessScheduleApps extends DalBaseProcess {
  private static final Logger log = LogManager.getLogger(ProcessScheduleApps.class);

  @Override
  protected void doExecute(ProcessBundle processBundle) throws Exception {
    ProcessRequest processRequest = OBDal.getInstance()
        .get(ProcessRequest.class, processBundle.getProcessRequestId());
    OBCriteria<ETCOPSchedule> criteria = OBDal.getInstance().createCriteria(ETCOPSchedule.class);
    List<ETCOPSchedule> schedules = criteria.add(
        Restrictions.eq(ETCOPSchedule.PROPERTY_PROCESSREQUEST, processRequest)).list();
    refreshScheduleFiles(schedules);
    processSchedules(schedules);
  }

  private void refreshScheduleFiles(List<ETCOPSchedule> schedules)
      throws JSONException, IOException {
    String openaiApiKey = OpenAIUtils.getOpenaiApiKey();
    boolean fileSended = false;
    for (ETCOPSchedule schedule : schedules) {
      for (var source : schedule.getCopilotApp().getETCOPAppSourceList()) {
        if (source.getBehaviour() != null) {
          OpenAIUtils.syncFile(source.getFile(), openaiApiKey);
          fileSended = true;
        }
      }
    }
    if(fileSended) {
      try {
        Thread.sleep(60000 );
      } catch (InterruptedException e) {
        log.error(e);
        Thread.currentThread().interrupt();
      }
    }
  }

  private void processSchedules(List<ETCOPSchedule> schedules) {
    for (ETCOPSchedule schedule : schedules) {
      try {
        List<String> fileIds = new ArrayList<>();
        for (var source : schedule.getCopilotApp().getETCOPAppSourceList()) {
          if(StringUtils.isNotEmpty(source.getBehaviour()) && StringUtils.equals(source.getBehaviour(), RestServiceUtil.FILE_BEHAVIOUR_ATTACH)) {
            fileIds.add(source.getFile().getOpenaiIdFile());
          }
        }
        RestServiceUtil.handleQuestion(schedule.getCopilotApp(), schedule.getConversation(), schedule.getPrompt(), fileIds);
      } catch (JSONException | IOException e) {
        // for now just log the error and continue
        log.error(e);
      }
    }
  }
}
