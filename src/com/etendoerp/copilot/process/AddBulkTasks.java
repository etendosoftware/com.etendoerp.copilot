package com.etendoerp.copilot.process;


import static io.swagger.v3.core.util.Constants.COMMA;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.client.application.process.ResponseActionsBuilder;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.background.BulkTaskExec;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;

/**
 * ExampleButtonProcess class to handle the process execution for a button.
 */
public class AddBulkTasks extends BaseProcessActionHandler {


  public static final String COPILOT = "Copilot";

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    JSONObject result = new JSONObject();
    try {
      String paramValues = (String) parameters.get("paramValues");
      JSONObject paramValuesJson = new JSONObject(paramValues);
      JSONObject params = paramValuesJson.getJSONObject("_params");
      String agentid = params.getString("agentid");
      String question = params.getString("question");
      String group = params.getString("group");
      String separator = params.optString("separator", COMMA);
      if (StringUtils.equalsIgnoreCase(separator, "null")) {
        separator = COMMA;
      }
      String fileName = (String) ((Map) parameters.get("file")).get("fileName");
      if (StringUtils.isEmpty(fileName)) {
        group = fileName;
      }
      ByteArrayInputStream fileInputStream = getInputStream(parameters);

      //dump the file content into a temp file
      String extension = fileName.substring(fileName.lastIndexOf("."));
      String name = fileName.substring(0, fileName.lastIndexOf("."));

      File tempFile = File.createTempFile(name, extension);
      try (FileOutputStream fos = new FileOutputStream(tempFile)) {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = fileInputStream.read(buffer)) > 0) {
          fos.write(buffer, 0, len);
        }
      }

      String[] strings;
      if (StringUtils.equalsIgnoreCase(extension, ".zip")) {
        strings = unzipFile(tempFile);
      } else if (StringUtils.equalsIgnoreCase(extension, ".csv")) {
        strings = readCsvFile(tempFile, separator);
      } else {
        throw new RuntimeException("Unsupported file type");
      }
      //unzip file

      for (String path : strings) {
        if (path.contains("__MACOSX")) {
          continue;
        }
        //create task
        Task tsk = OBProvider.getInstance().get(Task.class);
        tsk.setNewOBObject(true);
        tsk.setETCOPAgent(OBDal.getInstance().get(CopilotApp.class, agentid));
        tsk.setEtcopQuestion(question + ":" + path);
        tsk.setStatus(getStatus(BulkTaskExec.TASK_STATUS_PENDING));
        tsk.setEtcopGroup(group);
        tsk.setAssignedUser(OBContext.getOBContext().getUser());
        tsk.setTaskType(getCopilotTaskType());

        OBDal.getInstance().save(tsk);
      }
      OBDal.getInstance().flush();


      return getResponseBuilder().showMsgInProcessView(ResponseActionsBuilder.MessageType.SUCCESS,
          OBMessageUtils.messageBD("Success"),
          String.format(OBMessageUtils.messageBD("ETCOP_AddBulkTasks_Success"), strings.length), false).build();
    } catch (Exception e) {
      try {
        result.put("message", "Error during process execution: " + e.getMessage());
        result.put("severity", "error");
      } catch (Exception ex) {
        // Logging in case of failure in error handling
      }
    }
    return result;
  }

  /**
   * Retrieves the input stream from the parameters map.
   *
   * @param parameters
   *     The parameters map containing the file information.
   * @return The input stream of the file.
   * @throws OBException
   *     if the file is not found or cannot be cast to ByteArrayInputStream.
   */
  @SuppressWarnings("unchecked")
  private ByteArrayInputStream getInputStream(Map<String, Object> parameters) {
    try {
      Map<String, Object> fileMap = (Map<String, Object>) parameters.get("file");
      if (fileMap == null) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_NoFile"));
      }
      ByteArrayInputStream fileInputStream = (ByteArrayInputStream) fileMap.get("content");
      if (fileInputStream == null) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_NoFile"));
      }
      return fileInputStream;
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }

  public static Status getStatus(String identifier) {
    return (Status) OBDal.getInstance().createCriteria(Status.class).add(
        Restrictions.eq(Status.PROPERTY_SEARCHKEY, identifier)).setMaxResults(1).uniqueResult();
  }

  public static TaskType getCopilotTaskType() {
    //get by criteria
    TaskType tasktype = (TaskType) OBDal.getInstance().createCriteria(TaskType.class).add(
        Restrictions.eq(TaskType.PROPERTY_NAME, COPILOT)).setMaxResults(1).uniqueResult();
    if (tasktype == null) {
      tasktype = OBProvider.getInstance().get(TaskType.class);
      tasktype.setNewOBObject(true);
      tasktype.setName(COPILOT);
      OBDal.getInstance().save(tasktype);
      OBDal.getInstance().flush();
    }
    return tasktype;
  }

  public static String[] readCsvFile(File csv, String separator) throws IOException, JSONException {
    List<String> jsonList = new ArrayList<>();

    try (BufferedReader br = new BufferedReader(new FileReader(csv))) {
      String headerLine = br.readLine();
      if (headerLine == null) {
        return new String[0];
      }

      String[] headers = headerLine.split(separator);
      String line;

      while ((line = br.readLine()) != null) {
        String[] values = line.split(separator);
        JSONObject json = new JSONObject();

        for (int i = 0; i < headers.length; i++) {
          json.put(headers[i].trim(), i < values.length ? values[i].trim() : "");
        }

        jsonList.add(json.toString());
      }
    }

    return jsonList.toArray(new String[0]);
  }

  public static String[] unzipFile(File zipFile) throws IOException {
    // Definir el directorio de salida en /tmp con el nombre del ZIP
    String zipFileName = zipFile.getName().replaceFirst("\\.zip$", ""); // Eliminar extensión .zip
    File outputDir = Files.createTempDirectory(zipFileName).toFile();

    if (!outputDir.exists()) {
      outputDir.mkdirs(); // Crear el directorio si no existe
    }

    List<String> resultPathsArray = new ArrayList<>();

    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        File extractedFile = new File(outputDir, entry.getName());

        if (entry.isDirectory()) {
          extractedFile.mkdirs();
        } else {
          File parent = extractedFile.getParentFile();
          if (parent != null) {
            parent.mkdirs(); // Crear directorios padres si es necesario
          }
          try (FileOutputStream fos = new FileOutputStream(extractedFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = zis.read(buffer)) > 0) {
              fos.write(buffer, 0, len);
            }
          }
          try {
            String path = RestServiceUtil.handleFile(extractedFile, "attachFile");
            resultPathsArray.add(path);
          } catch (Exception e) {
            resultPathsArray.add("Error: " + e.getMessage());
          }
        }
        zis.closeEntry();
      }
    }
    return resultPathsArray.toArray(new String[0]);
  }
}
