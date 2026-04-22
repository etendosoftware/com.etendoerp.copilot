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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
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
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Unit tests for KBFEventHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class KBFEventHandlerNewTest {

  private KBFEventHandler handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private CopilotFile copilotFile;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;

  /** Set up. */
  @Before
  public void setUp() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotFile.class)).thenReturn(entity);

    handler = new KBFEventHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedCopilotUtils.close();
    mockedOBMessageUtils.close();
  }

  /** Test on update null max chunk size. */
  @Test
  public void testOnUpdateNullMaxChunkSize() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getMaxChunkSize()).thenReturn(null);

    handler.onUpdate(updateEvent);
    // No exception - null maxChunkSize returns early
  }

  /** Test on update valid chunk size. */
  @Test
  public void testOnUpdateValidChunkSize() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getMaxChunkSize()).thenReturn(1000L);
    when(copilotFile.getChunkOverlap()).thenReturn(100L);

    handler.onUpdate(updateEvent);
    // No exception - overlap < maxChunkSize
  }

  /** Test on update overlap exceeds max chunk size. */
  @Test(expected = OBException.class)
  public void testOnUpdateOverlapExceedsMaxChunkSize() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getMaxChunkSize()).thenReturn(100L);
    when(copilotFile.getChunkOverlap()).thenReturn(200L);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_OverlapMaxSizeError"))
        .thenReturn("Overlap exceeds max size");

    handler.onUpdate(updateEvent);
  }

  /** Test on update null chunk overlap. */
  @Test
  public void testOnUpdateNullChunkOverlap() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getMaxChunkSize()).thenReturn(1000L);
    when(copilotFile.getChunkOverlap()).thenReturn(null);

    handler.onUpdate(updateEvent);
    // No exception - null overlap is fine
  }

  /** Test on save null max chunk size. */
  @Test
  public void testOnSaveNullMaxChunkSize() {
    when(newEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getMaxChunkSize()).thenReturn(null);

    handler.onSave(newEvent);
  }

  /** Test on save valid chunk size. */
  @Test
  public void testOnSaveValidChunkSize() {
    when(newEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getMaxChunkSize()).thenReturn(500L);
    when(copilotFile.getChunkOverlap()).thenReturn(100L);

    handler.onSave(newEvent);
  }

  /** Test on save overlap exceeds max chunk size. */
  @Test(expected = OBException.class)
  public void testOnSaveOverlapExceedsMaxChunkSize() {
    when(newEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getMaxChunkSize()).thenReturn(50L);
    when(copilotFile.getChunkOverlap()).thenReturn(100L);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_OverlapMaxSizeError"))
        .thenReturn("Overlap exceeds max size");

    handler.onSave(newEvent);
  }

  /** Test on update equal chunk size and overlap. */
  @Test
  public void testOnUpdateEqualChunkSizeAndOverlap() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getMaxChunkSize()).thenReturn(100L);
    when(copilotFile.getChunkOverlap()).thenReturn(100L);

    handler.onUpdate(updateEvent);
    // No exception - equal is fine (not less than)
  }
}
