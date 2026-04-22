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
package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for TextFileHook focusing on utility methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class TextFileHookNewTest {
  private static final String GENERATE_TEXT_FILE_METHOD = "generateTextFile";
  private static final String SPECIAL_CHARS_CONTENT = "Line1\nLine2\nLine3 with special chars: @#$%^&*()";

  private TextFileHook hook;

  /** Set up the TextFileHook instance for each test. */
  @Before
  public void setUp() {
    hook = createTextFileHook();
  }

  private TextFileHook createTextFileHook() {
    return new TextFileHook();
  }

  // --- typeCheck tests ---

  /** Test type check t x t. */
  @Test
  public void testTypeCheckTXT() {
    assertTrue("TXT type should be accepted", hook.typeCheck("TXT"));
  }

  /** Test type check other. */
  @Test
  public void testTypeCheckOther() {
    assertFalse(hook.typeCheck("RF"));
  }

  /** Test type check null. */
  @Test
  public void testTypeCheckNull() {
    String nullType = null;
    assertFalse("Null type should return false", hook.typeCheck(nullType));
  }

  /** Test type check empty. */
  @Test
  public void testTypeCheckEmpty() {
    assertFalse("Empty string should not match any type", hook.typeCheck(""));
  }

  /** Test type check lower case. */
  @Test
  public void testTypeCheckLowerCase() {
    assertFalse(hook.typeCheck("txt"));
  }

  /** Test type check f l o w. */
  @Test
  public void testTypeCheckFLOW() {
    assertFalse(hook.typeCheck("FLOW"));
  }

  // --- generateTextFile tests (via reflection) ---

  /**
   * Invokes the private generateTextFile method via reflection and returns the resulting Path.
   * @throws ReflectiveOperationException if reflection fails
   */
  private Path invokeGenerateTextFile(String text, String name) throws ReflectiveOperationException {
    Method method = TextFileHook.class.getDeclaredMethod(
        GENERATE_TEXT_FILE_METHOD, String.class, String.class);
    method.setAccessible(true);
    return (Path) method.invoke(hook, text, name);
  }

  /**
   * Invokes generateTextFile, asserts the file exists with the expected content, and cleans up.
   * @throws ReflectiveOperationException if reflection fails
   * @throws IOException if file I/O fails
   */
  private void assertGeneratedFileContent(String inputText, String fileName,
      String expectedContent) throws ReflectiveOperationException, IOException {
    Path generatedFile = invokeGenerateTextFile(inputText, fileName);
    assertNotNull("Generated file path should not be null", generatedFile);
    assertTrue(Files.exists(generatedFile));
    assertEquals(expectedContent, Files.readString(generatedFile));
    Files.deleteIfExists(generatedFile);
  }

  /**
   * Test generate text file.
   * @throws ReflectiveOperationException if reflection fails
   * @throws IOException if file I/O fails
   */
  @Test
  public void testGenerateTextFile() throws ReflectiveOperationException, IOException {
    assertGeneratedFileContent("Hello World", "test", "Hello World");
  }

  /**
   * Test generate text file empty content.
   * @throws ReflectiveOperationException if reflection fails
   * @throws IOException if file I/O fails
   */
  @Test
  public void testGenerateTextFileEmptyContent() throws ReflectiveOperationException, IOException {
    assertGeneratedFileContent("", "empty", "");
  }

  /**
   * Test generate text file special chars.
   * @throws ReflectiveOperationException if reflection fails
   * @throws IOException if file I/O fails
   */
  @Test
  public void testGenerateTextFileSpecialChars() throws ReflectiveOperationException, IOException {
    assertGeneratedFileContent(SPECIAL_CHARS_CONTENT, "special", SPECIAL_CHARS_CONTENT);
  }

  // --- getPriority test ---

  /** Test get priority default. */
  @Test
  public void testGetPriorityDefault() {
    int priority = hook.getPriority();
    assertEquals("Default priority should be 100", 100, priority);
  }

  // --- implements CopilotFileHook ---

  /** Test implements copilot file hook. */
  @Test
  public void testImplementsCopilotFileHook() {
    assertTrue(hook instanceof CopilotFileHook);
  }
}
