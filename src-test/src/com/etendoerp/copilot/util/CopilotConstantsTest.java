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

  @Mock
  private CopilotAppSource source;

  @Mock
  private CopilotFile file;

  // --- isSystemPromptBehaviour ---

  @Test
  public void testIsSystemPromptBehaviourTrue() {
    when(source.getBehaviour()).thenReturn("system");
    assertTrue(CopilotConstants.isSystemPromptBehaviour(source));
  }

  @Test
  public void testIsSystemPromptBehaviourFalseWithOtherValue() {
    when(source.getBehaviour()).thenReturn("question");
    assertFalse(CopilotConstants.isSystemPromptBehaviour(source));
  }

  @Test
  public void testIsSystemPromptBehaviourFalseWithNull() {
    when(source.getBehaviour()).thenReturn(null);
    assertFalse(CopilotConstants.isSystemPromptBehaviour(source));
  }

  // --- isQuestionBehaviour ---

  @Test
  public void testIsQuestionBehaviourTrue() {
    when(source.getBehaviour()).thenReturn("question");
    assertTrue(CopilotConstants.isQuestionBehaviour(source));
  }

  @Test
  public void testIsQuestionBehaviourFalse() {
    when(source.getBehaviour()).thenReturn("attach");
    assertFalse(CopilotConstants.isQuestionBehaviour(source));
  }

  // --- isAttachBehaviour ---

  @Test
  public void testIsAttachBehaviourTrue() {
    when(source.getBehaviour()).thenReturn("attach");
    assertTrue(CopilotConstants.isAttachBehaviour(source));
  }

  @Test
  public void testIsAttachBehaviourFalse() {
    when(source.getBehaviour()).thenReturn("system");
    assertFalse(CopilotConstants.isAttachBehaviour(source));
  }

  // --- isKbBehaviour ---

  @Test
  public void testIsKbBehaviourTrueWithKbValue() {
    when(source.getBehaviour()).thenReturn("kb");
    assertTrue(CopilotConstants.isKbBehaviour(source));
  }

  @Test
  public void testIsKbBehaviourTrueWithEmptyString() {
    when(source.getBehaviour()).thenReturn("");
    assertTrue(CopilotConstants.isKbBehaviour(source));
  }

  @Test
  public void testIsKbBehaviourTrueWithNull() {
    when(source.getBehaviour()).thenReturn(null);
    assertTrue(CopilotConstants.isKbBehaviour(source));
  }

  @Test
  public void testIsKbBehaviourFalse() {
    when(source.getBehaviour()).thenReturn("system");
    assertFalse(CopilotConstants.isKbBehaviour(source));
  }

  // --- isFileTypeLocalOrRemoteFile ---

  @Test
  public void testIsFileTypeLocalOrRemoteFileWithRF() {
    when(file.getType()).thenReturn("RF");
    assertTrue(CopilotConstants.isFileTypeLocalOrRemoteFile(file));
  }

  @Test
  public void testIsFileTypeLocalOrRemoteFileWithF() {
    when(file.getType()).thenReturn("F");
    assertTrue(CopilotConstants.isFileTypeLocalOrRemoteFile(file));
  }

  @Test
  public void testIsFileTypeLocalOrRemoteFileFalseWithOtherType() {
    when(file.getType()).thenReturn("HQL");
    assertFalse(CopilotConstants.isFileTypeLocalOrRemoteFile(file));
  }

  @Test
  public void testIsFileTypeLocalOrRemoteFileFalseWithNull() {
    when(file.getType()).thenReturn(null);
    assertFalse(CopilotConstants.isFileTypeLocalOrRemoteFile(file));
  }

  // --- isFileTypeRemoteFile ---

  @Test
  public void testIsFileTypeRemoteFileTrue() {
    when(file.getType()).thenReturn("RF");
    assertTrue(CopilotConstants.isFileTypeRemoteFile(file));
  }

  @Test
  public void testIsFileTypeRemoteFileFalseWithF() {
    when(file.getType()).thenReturn("F");
    assertFalse(CopilotConstants.isFileTypeRemoteFile(file));
  }

  @Test
  public void testIsFileTypeRemoteFileFalseWithNull() {
    when(file.getType()).thenReturn(null);
    assertFalse(CopilotConstants.isFileTypeRemoteFile(file));
  }

  // --- isHQLQueryFile ---

  @Test
  public void testIsHQLQueryFileTrue() {
    when(file.getType()).thenReturn("HQL");
    assertTrue(CopilotConstants.isHQLQueryFile(file));
  }

  @Test
  public void testIsHQLQueryFileFalseWithF() {
    when(file.getType()).thenReturn("F");
    assertFalse(CopilotConstants.isHQLQueryFile(file));
  }

  @Test
  public void testIsHQLQueryFileFalseWithNull() {
    when(file.getType()).thenReturn(null);
    assertFalse(CopilotConstants.isHQLQueryFile(file));
  }
}
