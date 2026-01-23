package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotConstants.KB_FILE_VALID_EXTENSIONS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.KnowledgeBaseFileVariant;
import com.etendoerp.copilot.hook.ProcessHQLAppSource;

/**
 * FileUtils is a utility class that provides methods for handling files
 * associated with the CopilotFile entity.
 */

public class FileUtils {
  private static final Logger log = LogManager.getLogger(FileUtils.class);

  private FileUtils() {
    // Private constructor to prevent instantiation
  }

  /**
   * Returns the secure temporary directory for Copilot.
   *
   * @return The path to the secure temporary directory
   */
  private static Path getSecureTempDir() {
    String baseAttachPath = OBPropertiesProvider.getInstance()
        .getOpenbravoProperties()
        .getProperty("attach.path", "/opt/EtendoERP");

    File secureDir = new File(baseAttachPath, "tmp_copilot");
    if (!secureDir.exists()) {
      secureDir.mkdirs();
    }
    return secureDir.toPath();
  }

  /**
   * Creates a temporary file in a secure directory.
   *
   * @param prefix
   *     The prefix string to be used in generating the file's name; may be null
   * @param suffix
   *     The suffix string to be used in generating the file's name; may be null, in which case ".tmp" is used
   * @return The path to the created temporary file
   * @throws IOException
   *     If an I/O error occurs
   */
  public static Path createSecureTempFile(String prefix, String suffix) throws IOException {
    return Files.createTempFile(getSecureTempDir(), prefix, suffix);
  }

  /**
   * Creates a temporary directory in a secure directory.
   *
   * @param prefix
   *     The prefix string to be used in generating the directory's name; may be null
   * @return The path to the created temporary directory
   * @throws IOException
   *     If an I/O error occurs
   */
  public static Path createSecureTempDirectory(String prefix) throws IOException {
    return Files.createTempDirectory(getSecureTempDir(), prefix);
  }

  /**
   * Retrieves a file associated with the given {@link CopilotFile} instance.
   * <p>
   * This method fetches the attachment corresponding to the provided {@link CopilotFile},
   * downloads its content, and creates a temporary file with the same name and extension.
   * If the attachment is missing or the file lacks an extension, appropriate exceptions
   * are thrown.
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
    var variant = getOrCreateVariant(fileToSync, fileToSync.getClient());
    if (StringUtils.isNotEmpty( variant.getInternalPath())) {
      return new File(variant.getInternalPath());
    } //so the file is stored in the DB
    var fileName = fileToSync.getFilename(); //includes extension
    File result = createSecureTempFile(null, "_" + fileName).toFile();
    try {
      Files.write(result.toPath(), variant.getFiledata());
    } catch (IOException e) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_TempFileErr"), fileName), e);
    }
    if (!result.setWritable(true)) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_TempFilePermErr"), fileName));
    }
    return result;
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
      Long maxChunkSize, Long chunkOverlap, String clientId) throws JSONException {
    CopilotUtils.toVectorDB(fileFromCopilotFile, dbName, extension, skipSplitting, maxChunkSize, chunkOverlap, clientId);
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

  /**
   * Cleans up a temporary file and optionally its containing directory if empty.
   * <p>
   * This method safely deletes the specified file and, if requested, removes the parent
   * directory if it becomes empty after the file deletion. The method handles null paths
   * gracefully and logs any I/O errors that occur during the cleanup process.
   *
   * @param path
   *     The {@link Path} to the temporary file to be deleted. If null, the method returns immediately.
   * @param deleteContainerIfEmpty
   *     If true, the parent directory will be deleted if it becomes empty after
   *     removing the file. If false, only the file itself is deleted.
   */
  public static void cleanupTempFile(Path path, boolean deleteContainerIfEmpty) {
    if (path == null) return;

    try {
      // Delete the file itself
      Files.deleteIfExists(path);

      // Optionally delete the containing folder if empty
      if (deleteContainerIfEmpty) {
        Path parentDir = path.getParent();
        if (parentDir != null && Files.isDirectory(parentDir)) {
          try (Stream<Path> files = Files.list(parentDir)) {
            if (files.findAny().isEmpty()) {
              Files.deleteIfExists(parentDir);
            }
          }
        }
      }
    } catch (IOException e) {
      log.error("Failed to delete temporary file or folder: {}", e.getMessage());
    }
  }

  /**
   * Refreshes the file for a non-multi-client environment.
   * Depending on the behavior, it either saves the internal path or the file data in the variant.
   *
   * @param hookObject The Copilot file object.
   * @param clientPathMap A map of clients and their corresponding file paths.
   * @throws IOException If an I/O error occurs.
   */
  public static void refreshFileForNonMultiClient(CopilotFile hookObject, Map<Client, Path> clientPathMap) throws IOException {
    boolean useTemp = useFileFromTemp(hookObject);
    for (Map.Entry<Client, Path> entry : clientPathMap.entrySet()) {
      Client client = entry.getKey();
      Path path = entry.getValue();
      if (useTemp) {
        //Store only the internal path to the file
        savePathInVariant(hookObject, client, path);
      } else { //any type of behavior except the KB
        //Persist the file in the database as attachment
        //save data in variant
        saveDataInVariant(hookObject, client, path);
      }
    }
    OBDal.getInstance().flush();
  }

  /**
   * Saves the internal file path in the variant for a specific client.
   *
   * @param hookObject The Copilot file object.
   * @param client The client associated with the variant.
   * @param path The path to be saved.
   */
  public static void savePathInVariant(CopilotFile hookObject, Client client, Path path) {
    var variant = getOrCreateVariant(hookObject, client);
    variant.setFiledata(null);
    variant.setInternalPath(path.toString());
    OBDal.getInstance().save(variant);
  }

  /**
   * Retrieves or creates a KnowledgeBaseFileVariant for the given CopilotFile and client.
   * <p>
   * This method searches for an existing variant based on the client and CopilotFile.
   * If found, it returns the existing variant. Otherwise, it creates a new one,
   * sets its initial properties (including organization "0"), saves it, and returns it.
   *
   * @param hookObject
   *     The CopilotFile for which to retrieve or create the variant.
   * @param client
   *     The client associated with the variant.
   * @return The existing or newly created KnowledgeBaseFileVariant.
   */
  public static KnowledgeBaseFileVariant getOrCreateVariant(CopilotFile hookObject, Client client) {
    KnowledgeBaseFileVariant variant = (KnowledgeBaseFileVariant) OBDal.getInstance().createCriteria(
            KnowledgeBaseFileVariant.class)
        .add(Restrictions.eq(KnowledgeBaseFileVariant.PROPERTY_CLIENT, client))
        .add(Restrictions.eq(KnowledgeBaseFileVariant.PROPERTY_KBFILE, hookObject)).uniqueResult();
    if (variant != null) {
      return variant;
    }
    variant = OBProvider.getInstance().get(KnowledgeBaseFileVariant.class);
    variant.setNewOBObject(true);
    variant.setClient(client);
    variant.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
    variant.setKBFile(hookObject);
    OBDal.getInstance().save(variant);
    OBDal.getInstance().flush();
    return variant;
  }

  /**
   * Saves the file data in the variant for a specific client.
   *
   * @param hookObject The Copilot file object.
   * @param client The client associated with the variant.
   * @param path The path to the file to be read.
   * @throws IOException If an I/O error occurs while reading the file.
   */
  public static void saveDataInVariant(CopilotFile hookObject, Client client, Path path) throws IOException {
    var variant = getOrCreateVariant(hookObject, client);
    variant.setInternalPath(null);
    variant.setFiledata(Files.readAllBytes(path));
    OBDal.getInstance().save(variant);
  }

  /**
   * Processes the file attachment for the CopilotFile.
   * If multi-client is enabled, it associates the file with all clients.
   * Otherwise, it associates it with the CopilotFile's client.
   *
   * @param hookObject The Copilot file object.
   * @param filePath The path to the file to be attached.
   * @param isMultiClient Whether the file supports multiple clients.
   * @throws IOException If an I/O error occurs.
   */
  public static void processFileAttachment(CopilotFile hookObject, Path filePath, boolean isMultiClient) throws IOException {
    Map<Client, Path> clientPathMap = new HashMap<>();
    if (isMultiClient) {
      List<Client> clientList = OBDal.getInstance().createCriteria(Client.class).list();
      for (Client client : clientList) {
        clientPathMap.put(client, filePath);
      }
    } else {
      clientPathMap.put(hookObject.getClient(), filePath);
    }
    refreshFileForNonMultiClient(hookObject, clientPathMap);
  }

  /**
   * Cleans up the temporary file if it is not required for Knowledge Base usage.
   *
   * @param hookObject The Copilot file object.
   * @param path The path to the temporary file.
   */
  public static void cleanupTempFileIfNeeded(CopilotFile hookObject, Path path) {
    if (path != null && !useFileFromTemp(hookObject)) {
      cleanupTempFile(path, true);
    }
  }

  /**
   * Determines if the file should be used from the temporary path based on the associated app sources.
   *
   * @param hookObject The Copilot file object.
   * @return true if all app sources are of Knowledge Base behavior, false otherwise.
   */
  public static boolean useFileFromTemp(CopilotFile hookObject) {
    // If all app sources are of KB behavior, we can use from the temp path, not saving in DB. The file will be deleted after Sync.
    List<CopilotAppSource> appSources = hookObject.getETCOPAppSourceList();
    if (appSources.isEmpty()) {
      return false;
    }
    for (CopilotAppSource appSource : appSources) {
      if (!CopilotConstants.isKbBehaviour(appSource)) {
        return false;
      }
    }
    return true;
  }
}
