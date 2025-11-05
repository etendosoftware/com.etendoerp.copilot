package com.etendoerp.copilot.hook;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.FileUtils;

/**
 * This class implements the CopilotFileHook interface and provides functionality
 * for handling remote files.
 */
public class TextFileHook implements CopilotFileHook {

  // Logger for this class
  private static final Logger log = LogManager.getLogger(TextFileHook.class);

  /**
   * Executes the hook for a given CopilotFile.
   *
   * @param hookObject
   *     The CopilotFile for which to execute the hook.
   * @throws OBException
   *     If there is an error executing the hook.
   */
  @Override
  public void exec(CopilotFile hookObject) throws OBException {
    if (log.isDebugEnabled()) {
      log.debug(String.format("TextFileHook for file: %s executed start", hookObject.getName()));
    }
    String text = hookObject.getText();
    String fileName = StringUtils.isNotEmpty(
        hookObject.getFilename()) ? hookObject.getFilename() : hookObject.getName();
    if (!StringUtils.endsWithIgnoreCase(fileName, ".txt")) {
      fileName = fileName + ".txt";
    }
    //download the file from the URL, preserving the original name, if filename is not empty, use it instead. The file must be
    //stored in a temporary folder.
    Path path = generateTextFile(text, fileName);
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
    FileUtils.removeAttachment(aim, hookObject);
    File file = new File(path.toString());
    FileUtils.attachFile(hookObject, aim, file);
    FileUtils.cleanupTempFile(path, false);

  }

  /**
   * Generates a temporary text file with the given content and file name.
   * <p>
   * This method creates a temporary file with the specified file name and writes the provided text content to it.
   * If an IOException occurs during file creation or writing, an OBException is thrown.
   *
   * @param text
   *     The text content to be written to the file.
   * @param fileName
   *     The name of the file to be created.
   * @return The Path to the generated temporary file.
   * @throws OBException
   *     If an error occurs while generating the text file.
   */
  private Path generateTextFile(String text, String fileName) {
    try {
      Path tempFile = Files.createTempFile(fileName, ".txt");
      try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
        fos.write(text.getBytes());
      }
      return tempFile;
    } catch (IOException e) {
      throw new OBException("Error generating text file", e);
    }
  }

  /**
   * Checks if the hook is applicable for the given type.
   *
   * @param type
   *     The type to check.
   * @return true if the hook is applicable, false otherwise.
   */
  @Override
  public boolean typeCheck(String type) {
    return StringUtils.equals(type, "TXT");
  }
}
