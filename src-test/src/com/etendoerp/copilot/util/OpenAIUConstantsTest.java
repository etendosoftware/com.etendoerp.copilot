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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OpenAIUConstants}.
 * <p>
 * This test suite validates the behavior of the {@code getMimeType} method,
 * ensuring that all supported file extensions are correctly translated to
 * their respective MIME types. It also verifies handling of mixed-case
 * extensions, file paths, multiple dots, and edge cases such as unknown
 * extensions or missing extensions.
 * </p>
 *
 * <p>
 * The structure is organized using nested test classes for clarity and
 * maintainability.
 * </p>
 */
class OpenAIUConstantsTest {

  /**
   * Tests that validate the basic mappings of file extensions to MIME types.
   * <p>
   * This includes programming languages, document formats, web formats,
   * data formats, image formats, and archive formats—all declared in
   * {@link OpenAIUConstants}.
   * </p>
   */
  @Nested
  @DisplayName("MIME Type basic mappings")
  class BasicMappingTests {

    /**
     * Parameterized test that checks a list of file names with known extensions
     * and verifies that the returned MIME type matches the expected value.
     *
     * @param fileName Name of the file (including extension)
     * @param expected Expected MIME type
     */
    @ParameterizedTest(name = "Extension {0} → {1}")
    @CsvSource({
        // Programming
        "file.c, text/x-c",
        "file.cpp, text/x-c++",
        "file.java, text/x-java",
        "file.py, text/x-python",
        "file.rb, text/x-ruby",
        "file.php, text/x-php",

        // Documents
        "file.txt, text/plain",
        "file.md, text/markdown",
        "file.pdf, application/pdf",
        "file.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "file.pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "file.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "file.tex, text/x-tex",

        // Web
        "file.html, text/html",
        "file.css, text/css",
        "file.js, text/javascript",
        "file.ts, application/typescript",

        // Data
        "file.json, application/json",
        "file.xml, application/xml",
        "file.csv, application/csv",

        // Images
        "file.png, image/png",
        "file.jpg, image/jpeg",
        "file.jpeg, image/jpeg",
        "file.gif, image/gif",

        // Archive
        "file.zip, application/zip",
        "file.tar, application/x-tar"
    })
    void testMimeTypes(String fileName, String expected) {
      assertEquals(expected, OpenAIUConstants.getMimeType(fileName));
    }
  }

  /**
   * Tests focused on verifying that the method handles uppercase and mixed-case
   * file extensions correctly by normalizing them to lowercase.
   */
  @Nested
  @DisplayName("Case-insensitive handling")
  class CaseInsensitiveTests {

    /**
     * Ensures MIME type lookup is case-insensitive when extensions contain
     * uppercase or mixed-case letters.
     *
     * @param fileName A filename with mixed-case extension
     */
    @ParameterizedTest(name = "Mixed case: {0} → image/png")
    @CsvSource({
        "IMAGE.PNG",
        "img.PnG",
        "file.pNg"
    })
    void testMixedCase(String fileName) {
      assertEquals("image/png", OpenAIUConstants.getMimeType(fileName));
    }
  }

  /**
   * Tests covering behavior with full paths, Windows-style paths, filenames
   * containing multiple dots, and nested directory structures.
   */
  @Nested
  @DisplayName("Paths and dotted filenames")
  class PathTests {

    /**
     * Ensures the method correctly resolves the file extension even when the
     * filename includes paths or multiple dots.
     *
     * @param fileName File path or dotted name containing an extension
     * @param expected Expected MIME type for the resolved extension
     */
    @ParameterizedTest
    @CsvSource({
        "/path/to/file.json, application/json",
        "C:\\\\Windows\\\\file.xml, application/xml",
        "dir/sub.dir/name.ext.md, text/markdown",
        "multi.dot.name.java, text/x-java"
    })
    void testPaths(String fileName, String expected) {
      assertEquals(expected, OpenAIUConstants.getMimeType(fileName));
    }
  }

  /**
   * Tests that validate behavior for special or borderline cases such as
   * unknown extensions, filenames without extensions, empty extensions,
   * or filenames that contain only the extension.
   */
  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    /**
     * Ensures that unknown extensions return {@code null}.
     */
    @Test
    void testUnknownExtension() {
      assertNull(OpenAIUConstants.getMimeType("file.unknown"));
    }

    /**
     * Ensures that passing only an extension (e.g., ".java") still works and
     * returns the correct MIME type.
     */
    @Test
    void testOnlyExtension() {
      assertEquals("text/x-java", OpenAIUConstants.getMimeType(".java"));
    }

    /**
     * Ensures that an empty extension (e.g., "file.") correctly returns {@code null}.
     */
    @Test
    void testEmptyExtension() {
      assertNull(OpenAIUConstants.getMimeType("file."));
    }

    /**
     * Ensures that calling the method with no extension throws the expected
     * {@link StringIndexOutOfBoundsException}, since substring(-1) is invalid.
     */
    @Test
    void testNoExtensionThrows() {
      assertThrows(StringIndexOutOfBoundsException.class,
          () -> OpenAIUConstants.getMimeType("fileWithoutExt"));
    }
  }
}
