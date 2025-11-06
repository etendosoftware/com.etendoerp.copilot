package com.etendoerp.copilot.hook;

import static com.etendoerp.copilot.util.CopilotUtils.getEtendoHostDocker;

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
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.FileUtils;
import com.etendoerp.openapi.OpenAPIController;
import com.etendoerp.openapi.data.OpenApiFlow;
import com.google.gson.JsonObject;

/**
 *
 */
public class OpenAPISpecFlowFile implements CopilotFileHook {

  // Logger for this class
  private static final Logger log = LogManager.getLogger(OpenAPISpecFlowFile.class);
  // Tab ID for CopilotFile
  public static final String COPILOT_FILE_TAB_ID = "09F802E423924081BC2947A64DDB5AF5";
  public static final String COPILOT_FILE_AD_TABLE_ID = "6B246B1B3A6F4DE8AFC208E07DB29CE2";

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
    var flow = hookObject.getEtapiOpenapiFlow();
    String fileName = getFileName(hookObject, flow);
    try {
      Path path = getOpenAPIFile(flow, fileName);
      AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
      removeAttachment(aim, hookObject);
      File file = new File(path.toString());
      aim.upload(new HashMap<>(), COPILOT_FILE_TAB_ID, hookObject.getId(),
          hookObject.getOrganization().getId(), file);
      FileUtils.cleanupTempFile(path, false);
    } catch (Exception e) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_GenFileError"), getFileName(hookObject, flow), e.getMessage()),
          e);
    }
  }

  private String getFileName(CopilotFile hookObject, OpenApiFlow flow) {
    String fileName = hookObject.getFilename();
    if (StringUtils.isBlank(fileName)) {
      fileName = flow.getName() + "OpenAPISpec.json";
    }
    return fileName;
  }

  /**
   * Retrieves the OpenAPI specification file for the given flow and writes it to a temporary file.
   * <p>
   * This method generates the OpenAPI specification JSON for the specified flow and writes it to a temporary file
   * with the provided file name. The temporary file is created in the default temporary-file directory.
   *
   * @param flow
   *     the OpenApiFlow object representing the flow for which to generate the OpenAPI specification
   * @param fileName
   *     the name of the file to be created
   * @return the Path of the created temporary file containing the OpenAPI specification
   * @throws OBException
   *     if an error occurs while generating or writing the OpenAPI specification
   */
  private Path getOpenAPIFile(OpenApiFlow flow, String fileName) throws OBException {
    try {
      String openAPISpec = new OpenAPIController().getOpenAPIJson(null, flow.getName(), getEtendoHostDocker());
      openAPISpec = addInfoForCopilot(openAPISpec);
      return Files.writeString(Files.createTempFile(fileName, ".json"), openAPISpec);
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  /**
   * Adds information for Copilot to the OpenAPI specification.
   * <p>
   * This method modifies the description field of the OpenAPI specification's info object to include
   * instructions for using the API with Copilot. It appends a note about using the literal string 'ETENDO_TOKEN'
   * as a Bearer token in all requests, which will be replaced by the real token in the request to the API.
   *
   * @param openAPISpec
   *     the original OpenAPI specification as a JSON string
   * @return the modified OpenAPI specification as a formatted JSON string
   * @throws JSONException
   *     if there is an error parsing or modifying the JSON
   */
  private String addInfoForCopilot(String openAPISpec) throws JSONException {
    JSONObject openapi = new JSONObject(openAPISpec);
    String description = openapi.getJSONObject("info").getString("description");
    description = description + "\n ## Using this API with Copilot" +
        "\n To use this API with Copilot, its necessary to use the literal string 'ETENDO_TOKEN' as Bearer token in " +
        "all the requests. This special token will be replaced by the real token in the request to the API.";
    openapi.getJSONObject("info").put("description", description);
    return openapi.toString(2);
  }

  private void removeAttachment(AttachImplementationManager aim, CopilotFile hookObject) {
    Attachment attachment = getAttachment(hookObject);
    if (attachment != null) {
      aim.delete(attachment);
    }
  }

  public static Attachment getAttachment(CopilotFile targetInstance) {
    OBCriteria<Attachment> attchCriteria = OBDal.getInstance().createCriteria(Attachment.class);
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, targetInstance.getId()));
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE,
        OBDal.getInstance().get(Table.class, COPILOT_FILE_AD_TABLE_ID)));
    attchCriteria.add(Restrictions.ne(Attachment.PROPERTY_ID, targetInstance.getId()));
    return (Attachment) attchCriteria.setMaxResults(1).uniqueResult();
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
    return StringUtils.equals(type, "FLOW");
  }
}
