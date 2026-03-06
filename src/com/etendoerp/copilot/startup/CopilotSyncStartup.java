package com.etendoerp.copilot.startup;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
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
import com.etendoerp.copilot.util.CopilotUtils;
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
  private static final int MAX_RETRIES = 5;
  private static final long RETRY_DELAY_MS = 30_000L;
  private static final long PROGRESS_INTERVAL_MS = 60_000L;


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

      // Wait for the Copilot service to be reachable before attempting sync
      waitForCopilotService();

      int agentCount = content.optJSONArray("recordIds") != null
          ? content.optJSONArray("recordIds").length() : 0;
      AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
      Timer progressTimer = new Timer("CopilotSync-Progress", true);
      progressTimer.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
          long elapsed = (System.currentTimeMillis() - startTime.get()) / 1000;
          log.info("Copilot startup sync still in progress... ({} agents, {} seconds elapsed)",
              agentCount, elapsed);
        }
      }, PROGRESS_INTERVAL_MS, PROGRESS_INTERVAL_MS);

      JSONObject res;
      try {
        res = assistant.doExecute(null, content.toString());
      } finally {
        progressTimer.cancel();
      }
      log.info("Copilot startup sync result: {}", res);

      boolean syncFailed = isSyncError(res);
      if (syncFailed) {
        log.warn("Copilot startup sync returned an error, agents will NOT be marked as synchronized");
      } else {
        // Only mark as synchronized when the sync actually succeeded
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

  private void waitForCopilotService() throws InterruptedException {
    String host = CopilotUtils.getCopilotHost();
    String port = CopilotUtils.getCopilotPort();
    if (StringUtils.isEmpty(host)) {
      host = "localhost";
    }
    String healthUrl = String.format("http://%s:%s/", host, port);

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(new URI(healthUrl))
            .GET()
            .build();
        client.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("Copilot service is reachable at {}", healthUrl);
        return;
      } catch (Exception e) {
        if (attempt < MAX_RETRIES) {
          log.info("Copilot service not available yet at {} (attempt {}/{}). Retrying in {} seconds...",
              healthUrl, attempt, MAX_RETRIES, RETRY_DELAY_MS / 1000);
          Thread.sleep(RETRY_DELAY_MS);
        } else {
          log.warn("Copilot service not reachable after {} attempts. Proceeding with sync attempt anyway.", MAX_RETRIES);
        }
      }
    }
  }

  private boolean isSyncError(JSONObject res) {
    if (res == null) {
      return true;
    }
    JSONObject message = res.optJSONObject("message");
    if (message != null && "error".equalsIgnoreCase(message.optString("severity"))) {
      return true;
    }
    return false;
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
