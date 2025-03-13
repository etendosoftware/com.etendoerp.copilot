package com.etendoerp.copilot.background;

import static com.etendoerp.copilot.process.AddBulkTasks.getCopilotTaskType;
import static com.etendoerp.copilot.process.AddBulkTasks.getStatus;
import static com.etendoerp.copilot.process.ExecTask.execTask;

import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;

import com.etendoerp.task.data.Task;

public class BulkTaskExec extends DalBaseProcess {

  private ProcessLogger logger;
  private int BATCH_SIZE = 1;


  @Override
  protected void doExecute(ProcessBundle processBundle) throws Exception {
    logger = processBundle.getLogger();
    logger.log("BulkTaskExec started\n");
    OBCriteria<Task> crit = OBDal.getInstance().createCriteria(Task.class);
    crit.add(Restrictions.eq(Task.PROPERTY_STATUS, getStatus("PE")));
    crit.add(Restrictions.eq(Task.PROPERTY_TASKTYPE, getCopilotTaskType()));
    crit.setMaxResults(BATCH_SIZE);
    List<Task> tasks = crit.list();
    logger.log("Found " + tasks.size() + " tasks\n");
    for (Task task : tasks) {
      try {
        logger.log("Processing task " + task.getId() + "\n");
        execTask(task);
        task.setStatus(getStatus("CO"));
        OBDal.getInstance().save(task);
        OBDal.getInstance().flush();
      } catch (Exception e) {
        logger.log("Error processing task " + task.getId() + ": " + e.getMessage() + "\n");
        task.setStatus(getStatus("IP"));
        OBDal.getInstance().save(task);
        OBDal.getInstance().flush();
      }
    }

  }


}
