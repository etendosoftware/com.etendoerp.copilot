package com.etendoerp.copilot.hook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.FileUtils;

/**
 * This class implements the CopilotFileHook interface and provides functionality
 * for handling attached files (type 'F').
 */
public class AttachedFileHook implements CopilotFileHook {

  private static final Logger log = LogManager.getLogger(AttachedFileHook.class);

  @Inject
  private AttachImplementationManager aim;

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
      log.debug(String.format("AttachedFileHook for file: %s executed start", hookObject.getName()));
    }

    Attachment attachment = FileUtils.getAttachment(hookObject);
    if (attachment == null) {
      FileUtils.throwMissingAttachException(hookObject);
    }

    Path path = null;
    try {
      path = downloadAttachment(attachment);
      FileUtils.processFileAttachment(hookObject, path, isMultiClient());
    } catch (Exception e) {
      log.error("Error processing attached file", e);
      throw new OBException("Error processing attached file", e);
    } finally {
      // Clean up the temporary file if it's not being used as a Knowledge Base file
      FileUtils.cleanupTempFileIfNeeded(hookObject, path);
    }
  }

  /**
   * Downloads the attachment content to a temporary file.
   *
   * @param attachment
   *     The attachment to download.
   * @return The Path to the generated temporary file.
   * @throws IOException
   *     If an I/O error occurs.
   */
  private Path downloadAttachment(Attachment attachment) throws IOException {
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      aim.download(attachment.getId(), os);

      String filename = attachment.getName();
      int lastDot = filename.lastIndexOf(".");
      String fileWithoutExtension = lastDot > 0 ? filename.substring(0, lastDot) : filename;
      String extension = lastDot > 0 ? filename.substring(lastDot) : "";

      File tempFile = File.createTempFile(fileWithoutExtension, extension);
      try (FileOutputStream fos = new FileOutputStream(tempFile)) {
        os.writeTo(fos);
      }
      return tempFile.toPath();
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
    return StringUtils.equals(type, CopilotConstants.FILE_TYPE_F) ||
           StringUtils.equals(type, CopilotConstants.KBF_TYPE_ATTACHED);
  }
}
