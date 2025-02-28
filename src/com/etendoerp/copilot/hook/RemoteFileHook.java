package com.etendoerp.copilot.hook;

import static com.etendoerp.copilot.util.CopilotUtils.replaceCopilotPromptVariables;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * This class implements the CopilotFileHook interface and provides functionality
 * for handling remote files.
 */
public class RemoteFileHook implements CopilotFileHook {

  // Logger for this class
  private static final Logger log = LogManager.getLogger(RemoteFileHook.class);

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
      log.debug(String.format("RemoteFileHook for file: %s executed start", hookObject.getName()));
    }
    String url = hookObject.getUrl();
    url = replaceCopilotPromptVariables(url);
    String fileName = hookObject.getFilename();
    //download the file from the URL, preserving the original name, if filename is not empty, use it instead. The file must be
    //stored in a temporary folder.
    try {
      Path path = downloadFile(url, fileName);
      AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
      CopilotUtils.removeAttachment(aim, hookObject);
      File file = new File(path.toString());
      CopilotUtils.attachFile(hookObject, aim, file);

    } catch (IOException e) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_FileDownErr"), url), e);
    }

  }


  /**
   * Downloads a file from a given URL and stores it in a temporary directory.
   *
   * @param fileUrl
   *     The URL of the file to download.
   * @param customName
   *     The custom name for the downloaded file.
   * @return The path of the downloaded file.
   * @throws IOException
   *     If there is an error downloading the file.
   */
  public static Path downloadFile(String fileUrl, String customName) throws IOException {
    URL url = new URL(fileUrl);
    String finalName = getFinalName(customName, url);

    // Create a temporary directory
    Path tempDirectory = Files.createTempDirectory("temporary_downloads");

    // Full path of the file in the temporary directory
    Path destinationPath = tempDirectory.resolve(finalName);

    try (BufferedInputStream in = new BufferedInputStream(url.openStream());
         FileOutputStream fileOutputStream = new FileOutputStream(destinationPath.toFile())) {
      byte[] dataBuffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
        fileOutputStream.write(dataBuffer, 0, bytesRead);
      }
    }
    return destinationPath;

  }

  /**
   * Determines the final name of the downloaded file.
   *
   * @param customName
   *     The custom name for the downloaded file.
   * @param url
   *     The URL of the file to download.
   * @return The final name of the downloaded file.
   */
  public static String getFinalName(String customName, URL url) {
    String finalName = customName;
    String extension = "";

    // Extract the extension of the original file
    String originalFileName = url.getFile().substring(url.getFile().lastIndexOf('/') + 1);
    int dotIndex = originalFileName.lastIndexOf('.');
    if (dotIndex != -1) {
      extension = originalFileName.substring(dotIndex);
    }

    // Use the original file name if no custom name is provided
    if (finalName == null || finalName.isEmpty()) {
      finalName = originalFileName;
    } else if (!finalName.contains(".")) {
      // Add the original extension to the custom name if it doesn't have one
      finalName += extension;
    }
    return finalName;
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
    return StringUtils.equals(type, "RF");
  }
}
