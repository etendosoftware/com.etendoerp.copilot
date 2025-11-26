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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test class for FileUtils utility methods.
 */
public class FileUtilsTest {

  private static final String TEST_FILE_NAME = "testFile.txt";
  private static final String SUB_DIR_NAME = "subdir";
  private static final String TEST_FILE_2_NAME = "testFile2.txt";
  private static final String NON_EXISTENT_FILE = "nonexistent.txt";

  @TempDir
  Path tempDir;

  /**
   * Test cleanupTempFile with null path - should handle gracefully and return immediately.
   */
  @Test
  public void testCleanupTempFileNullPath() {
    // This should not throw any exception and should handle null gracefully
    FileUtils.cleanupTempFile(null, false);
    FileUtils.cleanupTempFile(null, true);
  }

  /**
   * Test cleanupTempFile with valid file path and deleteContainerIfEmpty=false.
   * Should delete only the file, not the parent directory.
   *
   * @throws IOException if an I/O error occurs during file operations
   */
  @Test
  public void testCleanupTempFileValidFileNoDeleteContainer() throws IOException {
    // Create a test file
    Path testFile = tempDir.resolve(TEST_FILE_NAME);
    Files.createFile(testFile);

    // Verify file exists before cleanup
    assertTrue(Files.exists(testFile));
    assertTrue(Files.exists(tempDir));

    // Call the method under test
    FileUtils.cleanupTempFile(testFile, false);

    // Verify file is deleted but parent directory still exists
    assertFalse(Files.exists(testFile));
    assertTrue(Files.exists(tempDir));
  }

  /**
   * Test cleanupTempFile with valid file path and deleteContainerIfEmpty=true,
   * where the parent directory becomes empty after file deletion.
   * Should delete both file and parent directory.
   *
   * @throws IOException if an I/O error occurs during file operations
   */
  @Test
  public void testCleanupTempFileValidFileDeleteEmptyContainer() throws IOException {
    // Create a subdirectory with a single file
    Path subDir = tempDir.resolve(SUB_DIR_NAME);
    Files.createDirectory(subDir);
    Path testFile = subDir.resolve(TEST_FILE_NAME);
    Files.createFile(testFile);

    // Verify file and directory exist before cleanup
    assertTrue(Files.exists(testFile));
    assertTrue(Files.exists(subDir));

    // Call the method under test
    FileUtils.cleanupTempFile(testFile, true);

    // Verify both file and empty parent directory are deleted
    assertFalse(Files.exists(testFile));
    assertFalse(Files.exists(subDir));
  }

  /**
   * Test cleanupTempFile with valid file path and deleteContainerIfEmpty=true,
   * where the parent directory is not empty after file deletion.
   * Should delete only the file, not the parent directory.
   *
   * @throws IOException if an I/O error occurs during file operations
   */
  @Test
  public void testCleanupTempFileValidFileDontDeleteNonEmptyContainer() throws IOException {
    // Create a subdirectory with multiple files
    Path subDir = tempDir.resolve(SUB_DIR_NAME);
    Files.createDirectory(subDir);
    Path testFile1 = subDir.resolve(TEST_FILE_NAME);
    Path testFile2 = subDir.resolve(TEST_FILE_2_NAME);
    Files.createFile(testFile1);
    Files.createFile(testFile2);

    // Verify files and directory exist before cleanup
    assertTrue(Files.exists(testFile1));
    assertTrue(Files.exists(testFile2));
    assertTrue(Files.exists(subDir));

    // Call the method under test on one file
    FileUtils.cleanupTempFile(testFile1, true);

    // Verify only the target file is deleted, other file and directory remain
    assertFalse(Files.exists(testFile1));
    assertTrue(Files.exists(testFile2));
    assertTrue(Files.exists(subDir));
  }

  /**
   * Test cleanupTempFile with non-existent file path.
   * Should handle gracefully without throwing exceptions.
   */
  @Test
  public void testCleanupTempFileNonExistentFile() {
    Path nonExistentFile = tempDir.resolve(NON_EXISTENT_FILE);

    // Verify file doesn't exist
    assertFalse(Files.exists(nonExistentFile));

    // This should not throw any exception
    FileUtils.cleanupTempFile(nonExistentFile, false);
    FileUtils.cleanupTempFile(nonExistentFile, true);
  }

  /**
   * Test cleanupTempFile with file that has no parent directory.
   * Should handle gracefully without attempting directory operations.
   */
  @Test
  public void testCleanupTempFileFileWithNoParent() {
    // Test with a simple path that has no parent
    Path rootPath = Paths.get("testfile.txt");

    // This should not throw any exception even with deleteContainerIfEmpty=true
    // since getParent() returns null
    FileUtils.cleanupTempFile(rootPath, true);
    FileUtils.cleanupTempFile(rootPath, false);
  }
}
