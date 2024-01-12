package com.etendoerp.copilot.hook;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotFile;

public class RemoteFileHook implements CopilotFileHook {

  private static final Logger log = LogManager.getLogger(RemoteFileHook.class);
  public static final String COPILOT_FILE_TAB_ID = "09F802E423924081BC2947A64DDB5AF5";

  @Override
  public void exec(CopilotFile hookObject) throws OBException {
    if(log.isDebugEnabled()){
      log.debug(String.format("RemoteFileHook for file: %s executed start", hookObject.getName()));
    }
    String url = hookObject.getUrl();
    String fileName = hookObject.getFilename();
    //download the file from the URL, preserving the original name, if filename is not empty, use it instead. The file must be
    //stored in a temporary folder.
    try {
      Path path = downloadFile(url, fileName);
      AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
      File file = new File(path.toString());
      aim.upload(new HashMap<>(), COPILOT_FILE_TAB_ID, hookObject.getId(),
          hookObject.getOrganization().getId(), file);

    } catch (IOException e) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_FileDownErr"), url), e);
    }

  }

  public static Path downloadFile(String fileUrl, String customName) throws IOException {
    URL url = new URL(fileUrl);
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

  @Override
  public boolean typeCheck(String type) {
    return StringUtils.equals(type, "RF");
  }
}
