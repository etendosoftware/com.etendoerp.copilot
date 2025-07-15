package com.etendoerp.copilot.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

/**
 * Utility class to manage webhook permission assignment.
 * Automatically assigns missing webhook permissions to a given role and application.
 */
public class WebhookPermissionUtils {

  /**
   * Assigns all missing webhook permissions for the given role and app.
   * Only those not currently assigned are inserted into the permissions table.
   *
   * @param roleId The ID of the role to assign permissions to.
   * @param appId  The ID of the assistant application.
   */
  public static void assignMissingPermissions(String roleId, String appId) {
    final String sql = """
      SELECT smfwhe_definedwebhook_id
      FROM etcop_role_webhookaccess_v
      WHERE ad_role_id = ? AND etcop_app_id = ? AND has_role_webhook = false
    """;

    try (PreparedStatement ps = OBDal.getInstance().getConnection().prepareStatement(sql)) {
      ps.setString(1, roleId);
      ps.setString(2, appId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String webhookId = rs.getString("smfwhe_definedwebhook_id");
          createPermission(roleId, webhookId);
        }
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  /**
   * Inserts a new permission entry into the webhook role table.
   *
   * @param roleId    The role ID to assign the webhook to.
   * @param webhookId The webhook ID.
   */
  private static void createPermission(String roleId, String webhookId) {
    final String insertSql = """
      INSERT INTO smfwhe_definedwebhook_role (
        smfwhe_definedwebhook_role_id, ad_client_id, ad_org_id, isactive,
        created, createdby, updated, updatedby,
        smfwhe_definedwebhook_id, ad_role_id
      )
      VALUES (get_uuid(), '0', '0', 'Y', now(), '0', now(), '0', ?, ?)
    """;

    try (PreparedStatement ps = OBDal.getInstance().getConnection().prepareStatement(insertSql)) {
      ps.setString(1, webhookId);
      ps.setString(2, roleId);
      ps.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}
