/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.eventhandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Unit tests for AssistantKBSyncStatusHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssistantKBSyncStatusHandlerNewTest {

  private AssistantKBSyncStatusHandler handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<CopilotAppInfoUtils> mockedAppInfoUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private CopilotAppSource copilotAppSource;
  @Mock private CopilotApp copilotApp;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity appSourceEntity;
  @Mock private Property fileProperty;

  /** Set up. */
  @Before
  public void setUp() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotAppSource.class)).thenReturn(appSourceEntity);

    handler = new AssistantKBSyncStatusHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    lenient().when(copilotAppSource.getEntity()).thenReturn(appSourceEntity);
    lenient().when(copilotAppSource.getEtcopApp()).thenReturn(copilotApp);
    lenient().when(appSourceEntity.getProperty(CopilotAppSource.PROPERTY_FILE)).thenReturn(fileProperty);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedAppInfoUtils.close();
    mockedCopilotUtils.close();
    mockedOBMessageUtils.close();
  }

  /** Test on update file changed. */
  @Test
  public void testOnUpdateFileChanged() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
    when(updateEvent.getPreviousState(fileProperty)).thenReturn("oldFile");
    when(updateEvent.getCurrentState(fileProperty)).thenReturn("newFile");
    when(copilotApp.getModule()).thenReturn(null);

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on update file unchanged. */
  @Test
  public void testOnUpdateFileUnchanged() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
    when(updateEvent.getPreviousState(fileProperty)).thenReturn("sameFile");
    when(updateEvent.getCurrentState(fileProperty)).thenReturn("sameFile");
    when(copilotApp.getModule()).thenReturn(null);

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  /** Test on save. */
  @Test
  public void testOnSave() {
    when(newEvent.getTargetInstance()).thenReturn(copilotAppSource);
    when(copilotApp.getModule()).thenReturn(null);

    handler.onSave(newEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on delete. */
  @Test
  public void testOnDelete() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotAppSource);

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test validate module export no assistant module. */
  @Test
  public void testValidateModuleExportNoAssistantModule() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
    when(updateEvent.getPreviousState(fileProperty)).thenReturn("same");
    when(updateEvent.getCurrentState(fileProperty)).thenReturn("same");
    when(copilotApp.getModule()).thenReturn(null);

    handler.onUpdate(updateEvent);

    // No exception thrown means validation passed (assistantModule == null returns early)
  }

  /** Test validate module export module in rel and file. */
  @Test
  public void testValidateModuleExportModuleInRelAndFile() {
    Module assistantModule = mock(Module.class);
    Module relModule = mock(Module.class);
    Module fileModule = mock(Module.class);
    CopilotFile file = mock(CopilotFile.class);

    when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
    when(updateEvent.getPreviousState(fileProperty)).thenReturn("same");
    when(updateEvent.getCurrentState(fileProperty)).thenReturn("same");
    when(copilotApp.getModule()).thenReturn(assistantModule);
    when(copilotAppSource.getModule()).thenReturn(relModule);
    when(copilotAppSource.getFile()).thenReturn(file);
    when(file.getModule()).thenReturn(fileModule);

    handler.onUpdate(updateEvent);

    // No exception - both module in rel and file have modules
  }

  /** Test validate module export module in rel but not in file. */
  @Test(expected = OBException.class)
  public void testValidateModuleExportModuleInRelButNotInFile() {
    Module assistantModule = mock(Module.class);
    Module relModule = mock(Module.class);
    CopilotFile file = mock(CopilotFile.class);

    when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
    when(updateEvent.getPreviousState(fileProperty)).thenReturn("same");
    when(updateEvent.getCurrentState(fileProperty)).thenReturn("same");
    when(copilotApp.getModule()).thenReturn(assistantModule);
    when(copilotAppSource.getModule()).thenReturn(relModule);
    when(copilotAppSource.getFile()).thenReturn(file);
    when(file.getModule()).thenReturn(null);
    when(file.getName()).thenReturn("test.pdf");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_FileNotBelonging"))
        .thenReturn("File %s does not belong to any module");

    handler.onUpdate(updateEvent);
  }

  /** Test validate module export no module in rel. */
  @Test
  public void testValidateModuleExportNoModuleInRel() {
    Module assistantModule = mock(Module.class);

    when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
    when(updateEvent.getPreviousState(fileProperty)).thenReturn("same");
    when(updateEvent.getCurrentState(fileProperty)).thenReturn("same");
    when(copilotApp.getModule()).thenReturn(assistantModule);
    when(copilotAppSource.getModule()).thenReturn(null);

    handler.onUpdate(updateEvent);

    // No exception - moduleInRel is false so validation passes
  }

  /** Test on save validate module export throws. */
  @Test
  public void testOnSaveValidateModuleExportThrows() {
    Module assistantModule = mock(Module.class);
    Module relModule = mock(Module.class);
    CopilotFile file = mock(CopilotFile.class);

    when(newEvent.getTargetInstance()).thenReturn(copilotAppSource);
    when(copilotApp.getModule()).thenReturn(assistantModule);
    when(copilotAppSource.getModule()).thenReturn(relModule);
    when(copilotAppSource.getFile()).thenReturn(file);
    when(file.getModule()).thenReturn(null);
    when(file.getName()).thenReturn("test.pdf");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_FileNotBelonging"))
        .thenReturn("File %s does not belong to any module");

    try {
      handler.onSave(newEvent);
      org.junit.Assert.fail("Expected OBException");
    } catch (OBException e) {
      // expected
    }
  }
}
