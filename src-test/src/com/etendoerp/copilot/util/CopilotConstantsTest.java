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
package com.etendoerp.copilot.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

/**
 * Unit tests for {@link CopilotConstants} boolean helper methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class CopilotConstantsTest {
  private static final String SYSTEM = "system";


  @Mock
  private CopilotAppSource source;

  @Mock
  private CopilotFile file;

  // --- isSystemPromptBehaviour ---

  /** Test is system prompt behaviour true. */
  @Test
  public void testIsSystemPromptBehaviourTrue() {
    when(source.getBehaviour()).thenReturn(SYSTEM);
    assertTrue(CopilotConstants.isSystemPromptBehaviour(source));
  }

  /** Test is system prompt behaviour false with other value. */
  @Test
  public void testIsSystemPromptBehaviourFalseWithOtherValue() {
    when(source.getBehaviour()).thenReturn("question");
    assertFalse(CopilotConstants.isSystemPromptBehaviour(source));
  }

  /** Test is system prompt behaviour false with null. */
  @Test
  public void testIsSystemPromptBehaviourFalseWithNull() {
    when(source.getBehaviour()).thenReturn(null);
    assertFalse(CopilotConstants.isSystemPromptBehaviour(source));
  }

  // --- isQuestionBehaviour ---

  /** Test is question behaviour true. */
  @Test
  public void testIsQuestionBehaviourTrue() {
    when(source.getBehaviour()).thenReturn("question");
    assertTrue(CopilotConstants.isQuestionBehaviour(source));
  }

  /** Test is question behaviour false. */
  @Test
  public void testIsQuestionBehaviourFalse() {
    when(source.getBehaviour()).thenReturn("attach");
    assertFalse(CopilotConstants.isQuestionBehaviour(source));
  }

  // --- isAttachBehaviour ---

  /** Test is attach behaviour true. */
  @Test
  public void testIsAttachBehaviourTrue() {
    when(source.getBehaviour()).thenReturn("attach");
    assertTrue(CopilotConstants.isAttachBehaviour(source));
  }

  /** Test is attach behaviour false. */
  @Test
  public void testIsAttachBehaviourFalse() {
    when(source.getBehaviour()).thenReturn(SYSTEM);
    assertFalse(CopilotConstants.isAttachBehaviour(source));
  }

  // --- isKbBehaviour ---

  /** Test is kb behaviour true with kb value. */
  @Test
  public void testIsKbBehaviourTrueWithKbValue() {
    when(source.getBehaviour()).thenReturn("kb");
    assertTrue(CopilotConstants.isKbBehaviour(source));
  }

  /** Test is kb behaviour true with empty string. */
  @Test
  public void testIsKbBehaviourTrueWithEmptyString() {
    when(source.getBehaviour()).thenReturn("");
    assertTrue(CopilotConstants.isKbBehaviour(source));
  }

  /** Test is kb behaviour true with null. */
  @Test
  public void testIsKbBehaviourTrueWithNull() {
    when(source.getBehaviour()).thenReturn(null);
    assertTrue(CopilotConstants.isKbBehaviour(source));
  }

  /** Test is kb behaviour false. */
  @Test
  public void testIsKbBehaviourFalse() {
    when(source.getBehaviour()).thenReturn(SYSTEM);
    assertFalse(CopilotConstants.isKbBehaviour(source));
  }

  // --- isFileTypeLocalOrRemoteFile ---

  /** Test is file type local or remote file with r f. */
  @Test
  public void testIsFileTypeLocalOrRemoteFileWithRF() {
    when(file.getType()).thenReturn("RF");
    assertTrue(CopilotConstants.isFileTypeLocalOrRemoteFile(file));
  }

  /** Test is file type local or remote file with f. */
  @Test
  public void testIsFileTypeLocalOrRemoteFileWithF() {
    when(file.getType()).thenReturn("F");
    assertTrue(CopilotConstants.isFileTypeLocalOrRemoteFile(file));
  }

  /** Test is file type local or remote file false with other type. */
  @Test
  public void testIsFileTypeLocalOrRemoteFileFalseWithOtherType() {
    when(file.getType()).thenReturn("HQL");
    assertFalse(CopilotConstants.isFileTypeLocalOrRemoteFile(file));
  }

  /** Test is file type local or remote file false with null. */
  @Test
  public void testIsFileTypeLocalOrRemoteFileFalseWithNull() {
    when(file.getType()).thenReturn(null);
    assertFalse(CopilotConstants.isFileTypeLocalOrRemoteFile(file));
  }

  // --- isFileTypeRemoteFile ---

  /** Test is file type remote file true. */
  @Test
  public void testIsFileTypeRemoteFileTrue() {
    when(file.getType()).thenReturn("RF");
    assertTrue(CopilotConstants.isFileTypeRemoteFile(file));
  }

  /** Test is file type remote file false with f. */
  @Test
  public void testIsFileTypeRemoteFileFalseWithF() {
    when(file.getType()).thenReturn("F");
    assertFalse(CopilotConstants.isFileTypeRemoteFile(file));
  }

  /** Test is file type remote file false with null. */
  @Test
  public void testIsFileTypeRemoteFileFalseWithNull() {
    when(file.getType()).thenReturn(null);
    assertFalse(CopilotConstants.isFileTypeRemoteFile(file));
  }

  // --- isHQLQueryFile ---

  /** Test is h q l query file true. */
  @Test
  public void testIsHQLQueryFileTrue() {
    when(file.getType()).thenReturn("HQL");
    assertTrue(CopilotConstants.isHQLQueryFile(file));
  }

  /** Test is h q l query file false with f. */
  @Test
  public void testIsHQLQueryFileFalseWithF() {
    when(file.getType()).thenReturn("F");
    assertFalse(CopilotConstants.isHQLQueryFile(file));
  }

  /** Test is h q l query file false with null. */
  @Test
  public void testIsHQLQueryFileFalseWithNull() {
    when(file.getType()).thenReturn(null);
    assertFalse(CopilotConstants.isHQLQueryFile(file));
  }
}
