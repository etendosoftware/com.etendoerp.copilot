package com.etendoerp.copilot.background;

import static com.etendoerp.copilot.process.AddBulkTasks.getCopilotTaskType;
import static com.etendoerp.copilot.process.AddBulkTasks.getStatus;
import static com.etendoerp.copilot.process.ExecTask.processTask;

import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;

import com.etendoerp.task.data.Task;

public class BulkTaskExec extends DalBaseProcess {

  public static final String TASK_STATUS_IN_PROGRESS = "IP";
  public static final String TASK_STATUS_COMPLETED = "CO";
  public static final String TASK_STATUS_PENDING = "PE";
  private ProcessLogger logger;
  private static final int BATCH_SIZE = 10;

  @Override
  protected void doExecute(ProcessBundle processBundle) throws Exception {
    logger = processBundle.getLogger();
    logger.log("BulkTaskExec started\n");


    OBCriteria<Task> crit = OBDal.getInstance().createCriteria(Task.class);
    crit.add(Restrictions.eq(Task.PROPERTY_STATUS, getStatus(TASK_STATUS_PENDING)));
    crit.add(Restrictions.eq(Task.PROPERTY_TASKTYPE, getCopilotTaskType()));
    crit.setMaxResults(BATCH_SIZE);

    List<Task> tasks = crit.list();
    logger.log("Found " + tasks.size() + " tasks\n");

    for (Task task : tasks) {
      task.setStatus(getStatus(TASK_STATUS_IN_PROGRESS));
      OBDal.getInstance().save(task);
    }
    OBDal.getInstance().flush();
    SessionHandler.getInstance().commitAndStart();
    for (Task task : tasks) {
      OBDal.getInstance().refresh(task);
      processTask(task, logger);
    }
  }


}
