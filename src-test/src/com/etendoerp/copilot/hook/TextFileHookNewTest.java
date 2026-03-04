package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

  private TextFileHook hook;

  @Before
  public void setUp() {
    hook = new TextFileHook();
  }

  // --- typeCheck tests ---

  @Test
  public void testTypeCheckTXT() {
    assertTrue(hook.typeCheck("TXT"));
  }

  @Test
  public void testTypeCheckOther() {
    assertFalse(hook.typeCheck("RF"));
  }

  @Test
  public void testTypeCheckNull() {
    assertFalse(hook.typeCheck(null));
  }

  @Test
  public void testTypeCheckEmpty() {
    assertFalse(hook.typeCheck(""));
  }

  @Test
  public void testTypeCheckLowerCase() {
    assertFalse(hook.typeCheck("txt"));
  }

  @Test
  public void testTypeCheckFLOW() {
    assertFalse(hook.typeCheck("FLOW"));
  }

  // --- generateTextFile tests (via reflection) ---

  @Test
  public void testGenerateTextFile() throws Exception {
    Method method = TextFileHook.class.getDeclaredMethod("generateTextFile", String.class, String.class);
    method.setAccessible(true);

    Path result = (Path) method.invoke(hook, "Hello World", "test");
    assertNotNull(result);
    assertTrue(Files.exists(result));

    String content = Files.readString(result);
    assertEquals("Hello World", content);

    // Cleanup
    Files.deleteIfExists(result);
  }

  @Test
  public void testGenerateTextFileEmptyContent() throws Exception {
    Method method = TextFileHook.class.getDeclaredMethod("generateTextFile", String.class, String.class);
    method.setAccessible(true);

    Path result = (Path) method.invoke(hook, "", "empty");
    assertNotNull(result);
    assertTrue(Files.exists(result));

    String content = Files.readString(result);
    assertEquals("", content);

    // Cleanup
    Files.deleteIfExists(result);
  }

  @Test
  public void testGenerateTextFileSpecialChars() throws Exception {
    Method method = TextFileHook.class.getDeclaredMethod("generateTextFile", String.class, String.class);
    method.setAccessible(true);

    String text = "Line1\nLine2\nLine3 with special chars: @#$%^&*()";
    Path result = (Path) method.invoke(hook, text, "special");
    assertNotNull(result);
    assertTrue(Files.exists(result));

    String content = Files.readString(result);
    assertEquals(text, content);

    // Cleanup
    Files.deleteIfExists(result);
  }

  // --- getPriority test ---

  @Test
  public void testGetPriorityDefault() {
    assertEquals(100, hook.getPriority());
  }

  // --- implements CopilotFileHook ---

  @Test
  public void testImplementsCopilotFileHook() {
    assertTrue(hook instanceof CopilotFileHook);
  }
}
