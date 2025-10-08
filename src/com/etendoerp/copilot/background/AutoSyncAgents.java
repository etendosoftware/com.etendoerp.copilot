package com.etendoerp.copilot.background;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.etendoerp.copilot.process.SyncAssistant;
import com.etendoerp.copilot.util.CopilotConstants;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;
import org.quartz.JobExecutionException;

import com.etendoerp.copilot.data.CopilotApp;
import com.smf.securewebservices.data.SMFSWSConfig;

/**
 * AutoSyncAssistants is a scheduled process that synchronizes active OpenAI Copilot applications.
 * <p>
 * This class extends DalBaseProcess and overrides the doExecute method to perform the synchronization.
 * It retrieves the list of active OpenAI Copilot applications and calls the syncApp method from the Utils class.
 */
public class AutoSyncAgents extends DalBaseProcess {
    private ProcessLogger logger;

    /**
     * Executes the synchronization process.
     * <p>
     * This method retrieves the list of active OpenAI Copilot applications and calls the syncApp method from the Utils class.
     * If an exception occurs, it logs the error and throws a JobExecutionException.
     *
     * @param bundle
     *     the ProcessBundle containing the process context
     * @throws Exception
     *     if an error occurs during the synchronization process
     */
    public void doExecute(ProcessBundle bundle) throws Exception {
        logger = bundle.getLogger();
        boolean adminModeOn = false;

        try {
            checkMasterToken();
            boolean isSysAdmin = "0".equals(OBContext.getOBContext().getCurrentClient().getId());
            var appCrit = OBDal.getInstance().createCriteria(CopilotApp.class);
            appCrit.add(Restrictions.eq(CopilotApp.PROPERTY_ACTIVE, true));
            appCrit.add(Restrictions.eq(CopilotApp.PROPERTY_APPTYPE, CopilotConstants.APP_TYPE_MULTIMODEL));
            Client clientSysAdm = OBDal.getInstance().get(Client.class, "0");
            if (isSysAdmin) {
                appCrit.add(Restrictions.eq(CopilotApp.PROPERTY_CLIENT, clientSysAdm));
            } else {
                appCrit.add(Restrictions.ne(CopilotApp.PROPERTY_CLIENT, clientSysAdm));
            }

            // Find apps that have files with type "COPDEV_CI" and exclude them
            var excludeQuery = OBDal.getInstance().createCriteria(CopilotApp.class);
            excludeQuery.createAlias("eTCOPAppSourceList", "appSource");
            excludeQuery.createAlias("appSource.file", "file");
            excludeQuery.add(Restrictions.eq("file.type", "COPDEV_CI"));

            List<CopilotApp> excludedApps = excludeQuery.list();

            if (!excludedApps.isEmpty()) {
                List<String> excludedAppIds = excludedApps.stream()
                        .map(CopilotApp::getId)
                        .collect(Collectors.toList());
                appCrit.add(Restrictions.not(Restrictions.in(CopilotApp.PROPERTY_ID, excludedAppIds)));
            }

            appCrit.addOrderBy(CopilotApp.PROPERTY_NAME, true);
            var apps = appCrit.list();

            if (!apps.isEmpty()) {
                OBContext.setAdminMode(true);
                adminModeOn = true;
                JSONArray recordIds = new JSONArray();
                logger.logln(apps.size() + " Agents to synchronize:");
                for (CopilotApp app : apps) {
                    recordIds.put(app.getId());
                    logger.logln(app.getName());
                }
                JSONObject content = new JSONObject();
                content.put("recordIds", recordIds);
                Map<String, Object> parameters = new HashMap<>();
                SyncAssistant syncAssistant = new SyncAssistant();
                JSONObject result = syncAssistant.doExecute(parameters, content.toString());
                JSONArray responseActions = result.has("responseActions") ? result.getJSONArray("responseActions") : new JSONArray();
                if (responseActions.length() > 0) {
                    JSONObject firstAction = responseActions.getJSONObject(0);
                    JSONObject showMsgInProcessView = firstAction.getJSONObject("showMsgInProcessView");
                    String msgText = showMsgInProcessView.getString("msgText");
                    logger.logln(msgText);
                } else {
                    logger.logln(result.toString());
                }
            } else {
                logger.logln("0 assistants found.");
            }
        } catch (Exception e) {
            // catch any possible exception and throw it as a Quartz
            // JobExecutionException
            logger.log("Error: " + e);
            throw new JobExecutionException(e.getMessage(), e);
        } finally {
            if (adminModeOn) {
                OBContext.restorePreviousMode();
            }
        }
    }

    private void checkMasterToken() {
        try {
            OBContext.setAdminMode();
            OBCriteria<SMFSWSConfig> swsConfigCriteria = OBDal.getInstance().createCriteria(SMFSWSConfig.class);
            List<SMFSWSConfig> list = swsConfigCriteria.list();
            if (list.isEmpty()) {
                SMFSWSConfig config = OBProvider.getInstance().get(SMFSWSConfig.class);
                config.setClient(OBDal.getInstance().get(Client.class, "0"));
                config.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
                config.setActive(true);
                config.setExpirationTime(0L);
                String pKey64 = getPKey64();
                config.setPrivateKey(pKey64);
                OBDal.getInstance().save(config);
                OBDal.getInstance().flush();
                logger.log("Master token created:  \n");
            } else {
                logger.log("Master token already exists" + "\n");
            }

        } catch (Exception e) {
            logger.log("Error: " + e);

        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private String getPKey64() {
        StringBuilder sb = new StringBuilder(64);
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()+=-[]\\';,./{}|\":<>?~_";
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 64; i++) {
            int index = rnd.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
}
