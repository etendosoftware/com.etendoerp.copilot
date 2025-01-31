package com.etendoerp.copilot.hook.cloning;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.ComponentProvider.Qualifier;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.smf.jobs.hooks.CloneRecordHook;

/**
 * CloneAssistant class for handling the cloning of CopilotApp records.
 * <p>
 * This class extends CloneRecordHook and provides custom implementations for
 * pre-copy, post-copy, and child record handling during the cloning process.
 */
@ApplicationScoped
@Qualifier(CopilotApp.ENTITY_NAME)
public class CloneAssistant extends CloneRecordHook {

  /**
   * Determines whether to copy child records.
   * <p>
   * This method always returns true, indicating that child records should be copied.
   *
   * @param uiCopyChildren
   *     A boolean indicating if the UI requests to copy children.
   * @return true, indicating that child records should be copied.
   */
  @Override
  public boolean shouldCopyChildren(boolean uiCopyChildren) {
    return true;
  }

  /**
   * Pre-copy hook.
   * <p>
   * This method is called before the original record is copied. It returns the original record without modifications.
   *
   * @param originalRecord
   *     The original record to be copied.
   * @return The original record.
   */
  @Override
  public BaseOBObject preCopy(BaseOBObject originalRecord) {
    return originalRecord;
  }

  /**
   * Post-copy hook.
   * <p>
   * This method is called after the original record is copied. It modifies the cloned record by setting a new name and nullifying the module.
   * The cloned record is then saved, flushed, and refreshed in the database.
   *
   * @param originalRecord
   *     The original record that was copied.
   * @param newRecord
   *     The newly created cloned record.
   * @return The modified cloned record.
   */
  @Override
  public BaseOBObject postCopy(BaseOBObject originalRecord, BaseOBObject newRecord) {
    CopilotApp originalAssistant = (CopilotApp) originalRecord;
    CopilotApp cloneAssistant = (CopilotApp) newRecord;
    cloneAssistant.setName("Copy of " + originalAssistant.getName());
    cloneAssistant.setModule(null);

    OBDal.getInstance().save(cloneAssistant);
    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(cloneAssistant);
    return cloneAssistant;
  }
}
