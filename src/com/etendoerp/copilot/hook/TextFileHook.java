package com.etendoerp.copilot.hook;


import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Client;

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
    Map<Client, Path> clientPathMap = new HashMap<>();
    if (isMultiClient()) {
      List<Client> clientList = OBDal.getInstance().createCriteria(Client.class).list();
      for (Client client : clientList) {
        clientPathMap.put(client, path);
      }
    } else {
      clientPathMap.put(hookObject.getClient(), path);
    }
    try {
      FileUtils.refreshFileForNonMultiClient(hookObject, clientPathMap);
    } catch (Exception e) {
      log.error("Error refreshing file", e);
      throw new OBException("Error refreshing file", e);
    }
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
