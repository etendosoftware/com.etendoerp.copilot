package com.etendoerp.copilot.startup;

import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.AppInfo;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.process.SyncAssistant;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;
import org.openbravo.client.kernel.ApplicationInitializer;

/**
 * Startup component that synchronizes all Copilot agents with pending synchronization
 * when the application (Tomcat) starts.
 */
@ApplicationScoped
@ComponentProvider.Qualifier(CopilotSyncStartup.COPILOT_SYNC_STARTUP)
public class CopilotSyncStartup implements ApplicationInitializer {
  public static final String COPILOT_SYNC_STARTUP = "copilotSyncStartup";
  private static final Logger log = LogManager.getLogger(CopilotSyncStartup.class);


  @Override
  public void initialize() {
    log.info("CopilotSyncStartup: checking for agents pending synchronization");
    try {
      OBContext.setAdminMode();

      OBCriteria<CopilotApp> crit = OBDal.getInstance().createCriteria(CopilotApp.class);

      List<CopilotApp> allApps = crit.list();

      // The appInfo items are children of the app. They store the synchronization status of each agent.
      // If there is no appInfo or any of the appInfo has a pending synchronization status, then we will synchronize the agent.
      List<CopilotApp> appsToSync = allApps.stream().filter(app -> {
        if (Boolean.FALSE.equals(app.isSyncStartup())) {
          return false;
        }
        List<AppInfo> appInfo = app.getEtcopAppInfoList();
        if (appInfo == null || appInfo.isEmpty()) {
          return true;
        }
        return appInfo.stream().anyMatch(
            info -> !StringUtils.equalsIgnoreCase(info.getSyncStatus(), CopilotConstants.SYNCHRONIZED_STATE));
      }).collect(Collectors.toList());

      log.info("Found {} Copilot agents with pending synchronization", appsToSync.size());

      if (appsToSync.isEmpty()) {
        log.info("No Copilot agents pending synchronization");
        return;
      }

      JSONArray ids = new JSONArray();
      for (CopilotApp app : appsToSync) {
        ids.put(app.getId());
      }
      JSONObject content = new JSONObject();
      content.put("recordIds", ids);

      // Launch synchronization asynchronously so startup isn't blocked.
      runSyncThread(() -> executeSync(content));
      log.info("CopilotSyncStartup: launched async synchronization in background for {} agents", appsToSync.size());
    } catch (Exception e) {
      log.error("Error while running CopilotSyncStartup", e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  protected void runSyncThread(Runnable r) {
    Thread t = new Thread(r, "CopilotSyncStartup-Thread");
    t.start();
  }

  private void executeSync(JSONObject content) {
    SyncAssistant assistant = createSyncAssistant();
    // Manage a dedicated transaction for this worker thread
    try {
      // Ensure admin mode inside the worker thread (must be inside try)
      OBContext.setAdminMode();
      JSONObject res = assistant.doExecute(null, content.toString());
      log.info("Copilot startup sync result: {}", res);

      // Additionally, ensure AppInfo records are marked as synchronized for the provided ids
      JSONArray ids = content.optJSONArray("recordIds");
      if (ids != null) {
        for (int i = 0; i < ids.length(); i++) {
          String appId = ids.optString(i);
          if (appId == null || appId.isEmpty()) continue;
          CopilotApp app = OBDal.getInstance().get(CopilotApp.class, appId);
          if (app != null) {
            CopilotAppInfoUtils.markAsSynchronized(app);
          }
        }
        OBDal.getInstance().flush();
        log.info("Copilot startup: marked {} agents as synchronized", ids.length());
      }
      // Commit and close the DAL session/transaction used by this thread
      commitChanges();
    } catch (Exception e) {
      log.error("Error executing SyncAssistant during startup", e);
      try {
        OBDal.getInstance().rollbackAndClose();
      } catch (Exception rbEx) {
        log.warn("Failed to rollback transaction after startup sync failure", rbEx);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  protected SyncAssistant createSyncAssistant() {
    return new SyncAssistant();
  }

  private static void commitChanges() {
    try {
      OBDal.getInstance().commitAndClose();
    } catch (Exception commitEx) {
      log.warn("Failed to commit transaction after startup sync", commitEx);
    }
  }
}
