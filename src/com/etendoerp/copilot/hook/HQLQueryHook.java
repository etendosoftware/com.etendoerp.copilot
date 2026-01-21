package com.etendoerp.copilot.hook;

import static com.etendoerp.copilot.util.FileUtils.refreshFileForNonMultiClient;
import com.etendoerp.copilot.util.FileUtils;

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
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotVarReplacerUtil;
import com.etendoerp.copilot.util.FileUtils;

/**
 * This class implements the CopilotFileHook interface and provides functionality
 * for handling remote files.
 */
public class HQLQueryHook implements CopilotFileHook {

  // Logger for this class
  private static final Logger log = LogManager.getLogger(HQLQueryHook.class);

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
    url = CopilotVarReplacerUtil.replaceCopilotPromptVariables(url);
    String fileName = hookObject.getFilename();
    //download the file from the URL, preserving the original name, if filename is not empty, use it instead. The file must be
    //stored in a temporary folder.
    Map<Client, Path> clientPathMap = new HashMap<>();
    try {
      String hql = hookObject.getHql();
      String extension = StringUtils.substringAfterLast(fileName, ".");

        List<Client> clientList = OBDal.getInstance().createCriteria(Client.class).list();
        for (Client client : clientList) {
          String hqlResult = ProcessHQLAppSource.getHQLResult(hql, "e", extension, client.getId());
          Path path = FileUtils.createSecureTempFile("hql_query_result_" + client.getId() + "_", "." + extension);
          try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            fos.write(hqlResult.getBytes());
          }
          clientPathMap.put(client, path);
        }
      refreshFileForNonMultiClient(hookObject, clientPathMap);
    } catch (IOException e) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_FileDownErr"), url), e);
    } finally {
        // Clean up the temporary file if it's not being used as a Knowledge Base file
          for (Path path : clientPathMap.values()) {
            FileUtils.cleanupTempFileIfNeeded(hookObject, path);
          }
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
    return StringUtils.equals(type, "HQL");
  }

  /**
   * Indicates whether the hook supports multi-client file versions. This means that the file will be "obtained" for each client with access
   * to an agent related to the Knowledge Base file.
   * By default, this method returns false.
   *
   * @return true if the hook supports multi-client file versions, false otherwise.
   */
  @Override
  public boolean isMultiClient() {
    return true;
  }
}
