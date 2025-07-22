package com.etendoerp.copilot.util;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.RoleWebhookaccessV;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;


/**
 * Utility class to manage webhook permission assignment using the DAL.
 * Automatically assigns missing webhook permissions to a given role and application.
 */
public class WebhookPermissionUtils {

  private static final Logger log = LogManager.getLogger();
  private WebhookPermissionUtils() {
    throw new UnsupportedOperationException("Utility class - cannot be instantiated");
  }

  /**
   * Assigns all missing webhook permissions for the given role and app using OBCriteria.
   *
   * @param role The role to assign permissions to.
   * @param app  The ID of the assistant application.
   */
  public static void assignMissingPermissions(Role role, CopilotApp app) {
    try {
      OBDal.getInstance().getSession().clear();
      OBCriteria<RoleWebhookaccessV> criteria = OBDal.getInstance()
          .createCriteria(RoleWebhookaccessV.class);

      criteria.add(Restrictions.eq(RoleWebhookaccessV.PROPERTY_ROLE , role));
      criteria.add(Restrictions.eq(RoleWebhookaccessV.PROPERTY_ASSISTANT, app));
      criteria.add(Restrictions.eq(RoleWebhookaccessV.PROPERTY_HASROLEWEBHOOK, false));

      List<RoleWebhookaccessV> missingPermissions = criteria.list();

      if (missingPermissions.isEmpty()) {
        log.debug("No missing webhook permissions to assign for role {} and app {}", role.getId(), app.getId());
        return;
      }

      log.info("Assigning {} missing webhook permissions for role {}...", missingPermissions.size(), role.getId());
      for (RoleWebhookaccessV permissionInfo : missingPermissions) {
        createPermission(permissionInfo.getRole(), permissionInfo.getWebHook());
      }

      OBDal.getInstance().flush();

    } catch (Exception e) {
      log.error("Error assigning webhook permissions for role {} and app {}", role.getId(), app.getId(), e);
      OBDal.getInstance().rollbackAndClose();
    }
  }

  /**
   * Creates a new permission entry by instantiating the DAL object.
   *
   * @param role    The Role object to assign the webhook to.
   * @param webhook The DefinedWebhook object.
   */
  private static void createPermission(Role role, DefinedWebHook webhook) {
    DefinedwebhookRole webhookPermission = OBProvider.getInstance().get(DefinedwebhookRole.class);

    webhookPermission.setClient(role.getClient());
    webhookPermission.setOrganization(role.getOrganization());
    webhookPermission.setActive(true);
    webhookPermission.setRole(role);
    webhookPermission.setSmfwheDefinedwebhook(webhook);

    OBDal.getInstance().save(webhookPermission);
  }
}

