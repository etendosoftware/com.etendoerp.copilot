package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotConstants.KB_FILE_VALID_EXTENSIONS;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.hook.ProcessHQLAppSource;

/**
 * FileUtils is a utility class that provides methods for handling files
 * associated with the CopilotFile entity.
 */

public class FileUtils {

  private FileUtils() {
    // Private constructor to prevent instantiation
  }

  /**
   * Retrieves a file associated with the given {@link CopilotFile} instance.
   * <p>
   * This method fetches the attachment corresponding to the provided {@link CopilotFile},
   * downloads its content, and creates a temporary file with the same name and extension.
   * If the attachment is missing or the file lacks an extension, appropriate exceptions
   * are thrown. The temporary file is marked for deletion upon JVM exit.
   *
   * @param fileToSync
   *     The {@link CopilotFile} instance for which the associated file is to be retrieved.
   * @return A {@link File} object representing the temporary file created from the attachment.
   * @throws IOException
   *     If an I/O error occurs during file creation or writing.
   * @throws OBException
   *     If the attachment is missing, the file lacks an extension, or the temporary file
   *     cannot be made writable.
   */
  public static File getFileFromCopilotFile(CopilotFile fileToSync) throws IOException {
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
    OBCriteria<Attachment> attCrit = OBDal.getInstance().createCriteria(Attachment.class);
    attCrit.add(Restrictions.eq(Attachment.PROPERTY_RECORD, fileToSync.getId()));
    Attachment attach = (Attachment) attCrit.setMaxResults(1).uniqueResult();
    if (attach == null) {
      throwMissingAttachException(fileToSync);
    }
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    aim.download(attach.getId(), os);
    // create a temp file
    String filename = attach.getName();
    if (filename.lastIndexOf(".") < 0) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingExtension"), filename));
    }

    String fileWithoutExtension = filename.substring(0, filename.lastIndexOf("."));
    String extension = filename.substring(filename.lastIndexOf(".") + 1);

    File tempFile = File.createTempFile(fileWithoutExtension, "." + extension);
    boolean setW = tempFile.setWritable(true);
    if (!setW) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorTempFile"), fileToSync.getName()));
    }
    os.writeTo(new FileOutputStream(tempFile));
    return tempFile;
  }

  /**
   * Checks if the given file extension is valid.
   * This method compares the provided extension against a list of valid
   * extensions
   * defined in the KB\_FILE\_VALID\_EXTENSIONS constant.
   *
   * @param extension
   *     The file extension to be checked.
   * @return true if the extension is valid, false otherwise.
   */
  static boolean isValidExtension(String extension) {
    return Arrays.stream(KB_FILE_VALID_EXTENSIONS).anyMatch(
        validExt -> StringUtils.equalsIgnoreCase(validExt, extension));
  }

  static void binaryFileToVectorDB(File fileFromCopilotFile, String dbName, String extension, boolean skipSplitting,
      Long maxChunkSize, Long chunkOverlap) throws JSONException {
    CopilotUtils.toVectorDB(fileFromCopilotFile, dbName, extension, skipSplitting, maxChunkSize, chunkOverlap);
  }

  static File generateHQLFile(CopilotAppSource appSource) {
    String fileNameToCheck = ProcessHQLAppSource.getFileName(appSource);
    if (StringUtils.equalsIgnoreCase("kb", appSource.getBehaviour()) && (StringUtils.isEmpty(
        fileNameToCheck) || StringUtils.endsWithIgnoreCase(fileNameToCheck, ".csv"))) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_Error_Csv_KB"), appSource.getFile().getName()));
    }

    return ProcessHQLAppSource.getInstance().generate(appSource);
  }

  /**
   * Throws an OBException indicating that an attachment is missing.
   * This method checks the type of the CopilotFile and throws an exception with a
   * specific error message
   * based on whether the file type is attached or not.
   *
   * @param fileToSync
   *     The CopilotFile instance for which the attachment is
   *     missing.
   * @throws OBException
   *     Always thrown to indicate the missing attachment.
   */
  public static void throwMissingAttachException(CopilotFile fileToSync) {
    String errMsg;
    String type = fileToSync.getType();
    if (StringUtils.equalsIgnoreCase(type, CopilotConstants.KBF_TYPE_ATTACHED)) {
      errMsg = String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttach"), fileToSync.getName());
    } else {
      errMsg = String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttachSync"), fileToSync.getName());
    }
    throw new OBException(errMsg);
  }

  /**
   * Retrieves an attachment associated with the given CopilotFile instance.
   * <p>
   * This method creates a criteria query to find an attachment that matches the
   * given CopilotFile instance.
   * It filters the attachments by the record ID and table ID, and excludes the
   * attachment with the same ID as the target instance.
   *
   * @param targetInstance
   *     The CopilotFile instance for which the attachment is to
   *     be retrieved.
   * @return The Attachment associated with the given CopilotFile instance, or
   *     null if no attachment is found.
   */
  public static Attachment getAttachment(CopilotFile targetInstance) {
    OBCriteria<Attachment> attchCriteria = OBDal.getInstance().createCriteria(Attachment.class);
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, targetInstance.getId()));
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE,
        OBDal.getInstance().get(Table.class, CopilotConstants.COPILOT_FILE_AD_TABLE_ID)));
    attchCriteria.add(Restrictions.ne(Attachment.PROPERTY_ID, targetInstance.getId()));
    return (Attachment) attchCriteria.setMaxResults(1).uniqueResult();
  }

  /**
   * Attaches a file to the given CopilotFile instance.
   * <p>
   * This method uploads a file and associates it with the specified CopilotFile
   * instance.
   *
   * @param hookObject
   *     The CopilotFile instance to which the file is to be
   *     attached.
   * @param aim
   *     The AttachImplementationManager used to handle the file
   *     upload.
   * @param file
   *     The file to be attached.
   */
  public static void attachFile(CopilotFile hookObject, AttachImplementationManager aim, File file) {
    aim.upload(new HashMap<>(), CopilotConstants.COPILOT_FILE_TAB_ID, hookObject.getId(),
        hookObject.getOrganization().getId(), file);
  }

  /**
   * Removes the attachment associated with the given CopilotFile instance.
   * <p>
   * This method retrieves the attachment associated with the specified
   * CopilotFile instance and deletes it.
   *
   * @param aim
   *     The AttachImplementationManager used to handle the file
   *     deletion.
   * @param hookObject
   *     The CopilotFile instance whose attachment is to be removed.
   */
  public static void removeAttachment(AttachImplementationManager aim, CopilotFile hookObject) {
    Attachment attachment = getAttachment(hookObject);
    if (attachment != null) {
      aim.delete(attachment);
    }
  }
}
