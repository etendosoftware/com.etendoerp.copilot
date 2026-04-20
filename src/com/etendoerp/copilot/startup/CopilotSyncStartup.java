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
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.kernel.ApplicationInitializer;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.AppInfo;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.process.SyncAssistant;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

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
  private static final String RECORD_IDS = "recordIds";
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

  @Override
  public void initialize() {
    log.info("CopilotSyncStartup: checking for agents pending synchronization");
    try {
      OBContext.setAdminMode();

      OBCriteria<CopilotApp> crit = OBDal.getInstance().createCriteria(CopilotApp.class);

      List<CopilotApp> allApps = crit.list();

      List<CopilotApp> appsToSync = allApps.stream().filter(app -> {
        if (!Boolean.TRUE.equals(app.isSyncStartup())) {
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
      content.put(RECORD_IDS, ids);

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
    try {
      OBContext.setAdminMode();
      ensureClientAdminRoleAccess(content);
      waitForCopilotService();
      JSONObject res = executeSyncWithProgress(assistant, content);
      handleSyncResult(res, content);
      commitChanges();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Sync thread interrupted during startup", e);
      rollbackQuietly();
    } catch (Exception e) {
      log.error("Error executing SyncAssistant during startup", e);
      rollbackQuietly();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private JSONObject executeSyncWithProgress(SyncAssistant assistant, JSONObject content) {
    JSONArray recordIds = content.optJSONArray(RECORD_IDS);
    int agentCount = recordIds != null ? recordIds.length() : 0;
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

    try {
      return assistant.doExecute(null, content.toString());
    } finally {
      progressTimer.cancel();
    }
  }

  private void ensureClientAdminRoleAccess(JSONObject content) {
    JSONArray ids = content.optJSONArray(RECORD_IDS);
    if (ids == null) {
      return;
    }

    for (int i = 0; i < ids.length(); i++) {
      String appId = ids.optString(i);
      if (StringUtils.isBlank(appId)) {
        continue;
      }

      CopilotApp app = OBDal.getInstance().get(CopilotApp.class, appId);
      if (app != null) {
        ensureClientAdminRoleAccess(app);
      }
    }

    OBDal.getInstance().flush();
  }

  private void ensureClientAdminRoleAccess(CopilotApp app) {
    String appClientId = app.getClient() != null ? app.getClient().getId() : null;
    StringBuilder hql = new StringBuilder();
    hql.append("select r ");
    hql.append("from ADRole r ");
    hql.append("where r.clientAdmin = true ");
    hql.append("and r.active = true ");

    if (StringUtils.isNotBlank(appClientId) && !"0".equals(appClientId)) {
      hql.append("and r.client.id = :clientId");
    } else {
      hql.append("and r.client.id <> '0'");
    }

    Query<Role> roleQuery = OBDal.getInstance().getSession().createQuery(hql.toString(), Role.class);
    if (StringUtils.isNotBlank(appClientId) && !"0".equals(appClientId)) {
      roleQuery.setParameter("clientId", appClientId);
    }

    for (Role role : roleQuery.list()) {
      ensureRoleAccess(app, role);
    }
  }

  private void ensureRoleAccess(CopilotApp app, Role role) {
    OBCriteria<CopilotRoleApp> roleAppCriteria = OBDal.getInstance().createCriteria(CopilotRoleApp.class);
    roleAppCriteria.add(Restrictions.eq(CopilotRoleApp.PROPERTY_COPILOTAPP, app));
    roleAppCriteria.add(Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, role));
    roleAppCriteria.setMaxResults(1);
    if (roleAppCriteria.uniqueResult() != null) {
      return;
    }

    CopilotRoleApp newRoleApp = OBProvider.getInstance().get(CopilotRoleApp.class);
    newRoleApp.setOrganization(role.getOrganization());
    newRoleApp.setClient(role.getClient());
    newRoleApp.setCopilotApp(app);
    newRoleApp.setRole(role);
    OBDal.getInstance().save(newRoleApp);
  }

  private void handleSyncResult(JSONObject res, JSONObject content) {
    log.info("Copilot startup sync result: {}", res);
    if (isSyncError(res)) {
      log.warn("Copilot startup sync returned an error, agents will NOT be marked as synchronized");
      return;
    }
    JSONArray ids = content.optJSONArray(RECORD_IDS);
    if (ids == null) {
      return;
    }
    for (int i = 0; i < ids.length(); i++) {
      String appId = ids.optString(i);
      if (appId == null || appId.isEmpty()) {
        continue;
      }
      CopilotApp app = OBDal.getInstance().get(CopilotApp.class, appId);
      if (app != null) {
        CopilotAppInfoUtils.markAsSynchronized(app);
      }
    }
    OBDal.getInstance().flush();
    log.info("Copilot startup: marked {} agents as synchronized", ids.length());
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
        HttpRequest req = HttpRequest.newBuilder()
            .uri(new URI(healthUrl))
            .GET()
            .build();
        HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("Copilot service is reachable at {}", healthUrl);
        return;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
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
    return message != null && CopilotConstants.ERROR.equalsIgnoreCase(message.optString("severity"));
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

  private static void rollbackQuietly() {
    try {
      OBDal.getInstance().rollbackAndClose();
    } catch (Exception rbEx) {
      log.warn("Failed to rollback transaction after startup sync failure", rbEx);
    }
  }
}
