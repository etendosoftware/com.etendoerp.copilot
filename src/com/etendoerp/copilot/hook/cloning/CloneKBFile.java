package com.etendoerp.copilot.hook.cloning;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.ComponentProvider.Qualifier;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotFile;
import com.smf.jobs.hooks.CloneRecordHook;

@ApplicationScoped
@Qualifier(CopilotFile.ENTITY_NAME)
public class CloneKBFile extends CloneRecordHook {

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
    CopilotFile originalFile = (CopilotFile) originalRecord;
    CopilotFile cloneFile = (CopilotFile) newRecord;
    cloneFile.setName("Copy of " + originalFile.getName());
    cloneFile.setModule(null);

    OBDal.getInstance().save(cloneFile);
    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(cloneFile);
    return cloneFile;
  }
}
