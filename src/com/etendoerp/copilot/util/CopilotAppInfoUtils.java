package com.etendoerp.copilot.util;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.AppInfo;
import com.etendoerp.copilot.data.CopilotApp;

/**
 * Utility class for managing AppInfo records.
 * This class provides methods to read and modify sync_status using the new etcop_app_info table
 * instead of directly modifying the sync_status in the etcop_app table.
 */
public class CopilotAppInfoUtils {

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private CopilotAppInfoUtils() {
    // Private constructor to hide the implicit public one
  }

  /**
   * Gets the current sync status for a CopilotApp from the AppInfo table.
   * If no AppInfo record exists, it returns the default value "PS" (Pending Synchronization).
   *
   * @param copilotApp
   *     the CopilotApp entity to get the sync status for
   * @return the sync status string, or "PS" if no record exists
   */
  public static String getSyncStatus(CopilotApp copilotApp) {
    if (copilotApp == null) {
      return CopilotConstants.PENDING_SYNCHRONIZATION_STATE;
    }

    AppInfo appInfo = getAppInfo(copilotApp);
    if (appInfo != null) {
      return appInfo.getSyncStatus();
    }

    // Return default value if no AppInfo record exists
    return CopilotConstants.PENDING_SYNCHRONIZATION_STATE;
  }

  /**
   * Sets the sync status for a CopilotApp by creating or updating a AppInfo record.
   *
   * @param copilotApp
   *     the CopilotApp entity to set the sync status for
   * @param syncStatus
   *     the new sync status value
   */
  public static void setSyncStatus(CopilotApp copilotApp, String syncStatus) {
    if (copilotApp == null || syncStatus == null) {
      return;
    }

    try {
      OBContext.setAdminMode(false);
      AppInfo appInfo = getAppInfo(copilotApp);

      if (appInfo == null) {
        // Create new AppInfo record
        appInfo = OBProvider.getInstance().get(AppInfo.class);
        appInfo.setAgent(copilotApp);
        appInfo.setClient(copilotApp.getClient());
        // Use the same organization as the CopilotApp
        appInfo.setOrganization(copilotApp.getOrganization());
        appInfo.setActive(true);
        appInfo.setNewOBObject(true);
      }

      // Update the sync status
      appInfo.setSyncStatus(syncStatus);
      OBDal.getInstance().save(appInfo);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Gets the AppInfo record for a given CopilotApp.
   * Returns null if no record exists.
   *
   * @param copilotApp
   *     the CopilotApp entity to find the AppInfo for
   * @return the AppInfo record or null if not found
   */
  public static AppInfo getAppInfo(CopilotApp copilotApp) {
    if (copilotApp == null) {
      return null;
    }

    OBCriteria<AppInfo> criteria = OBDal.getInstance().createCriteria(AppInfo.class);
    criteria.add(Restrictions.eq(AppInfo.PROPERTY_AGENT, copilotApp));
    criteria.setMaxResults(1);

    return (AppInfo) criteria.uniqueResult();
  }

  /**
   * Checks if a CopilotApp has a pending synchronization status.
   *
   * @param copilotApp
   *     the CopilotApp entity to check
   * @return true if the sync status is "PS" (Pending Synchronization), false otherwise
   */
  public static boolean isPendingSynchronization(CopilotApp copilotApp) {
    String syncStatus = getSyncStatus(copilotApp);
    return CopilotConstants.PENDING_SYNCHRONIZATION_STATE.equals(syncStatus);
  }

  /**
   * Checks if a CopilotApp has a synchronized status.
   *
   * @param copilotApp
   *     the CopilotApp entity to check
   * @return true if the sync status is "S" (Synchronized), false otherwise
   */
  public static boolean isSynchronized(CopilotApp copilotApp) {
    String syncStatus = getSyncStatus(copilotApp);
    return CopilotConstants.SYNCHRONIZED_STATE.equals(syncStatus);
  }

  /**
   * Sets the sync status to "PS" (Pending Synchronization) for a CopilotApp.
   *
   * @param copilotApp
   *     the CopilotApp entity to mark as pending synchronization
   */
  public static void markAsPendingSynchronization(CopilotApp copilotApp) {
    setSyncStatus(copilotApp, CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
  }

  /**
   * Sets the sync status to "S" (Synchronized) for a CopilotApp.
   *
   * @param copilotApp
   *     the CopilotApp entity to mark as synchronized
   */
  public static void markAsSynchronized(CopilotApp copilotApp) {
    setSyncStatus(copilotApp, CopilotConstants.SYNCHRONIZED_STATE);
  }

  // ==================== GRAPH_IMG Methods ====================

  /**
   * Gets the current graph image for a CopilotApp from the AppInfo table.
   * If no AppInfo record exists, it returns null.
   *
   * @param copilotApp
   *     the CopilotApp entity to get the graph image for
   * @return the graph image string, or null if no record exists
   */
  public static String getGraphImg(CopilotApp copilotApp) {
    try {
      OBContext.setAdminMode(false);
      if (copilotApp == null) {
        return null;
      }

      AppInfo appInfo = getAppInfo(copilotApp);
      if (appInfo != null) {
        OBDal.getInstance().refresh(appInfo);
        return appInfo.getGraphPreview();
      }

      // Return null if no AppInfo record exists
      return null;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Sets the graph image for a CopilotApp by creating or updating a AppInfo record.
   *
   * @param copilotApp
   *     the CopilotApp entity to set the graph image for
   * @param graphImg
   *     the new graph image value (can be null)
   */
  public static void setGraphImg(CopilotApp copilotApp, String graphImg) {
    if (copilotApp == null) {
      return;
    }

    try {
      OBContext.setAdminMode(false);
      AppInfo appInfo = getAppInfo(copilotApp);

      if (appInfo == null) {
        // Create new AppInfo record
        appInfo = OBProvider.getInstance().get(AppInfo.class);
        appInfo.setAgent(copilotApp);
        appInfo.setClient(copilotApp.getClient());
        // Use the same organization as the CopilotApp
        appInfo.setOrganization(copilotApp.getOrganization());
        appInfo.setActive(true);
      }

      // Update the graph image
      appInfo.setGraphPreview(graphImg);
      OBDal.getInstance().save(appInfo);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Checks if a CopilotApp has a graph image.
   *
   * @param copilotApp
   *     the CopilotApp entity to check
   * @return true if the graph image is not null and not empty, false otherwise
   */
  public static boolean hasGraphImg(CopilotApp copilotApp) {

    String graphImg = getGraphImg(copilotApp);
    return graphImg != null && !graphImg.trim().isEmpty();

  }

  /**
   * Clears the graph image for a CopilotApp (sets it to null).
   *
   * @param copilotApp
   *     the CopilotApp entity to clear the graph image for
   */
  public static void clearGraphImg(CopilotApp copilotApp) {
    setGraphImg(copilotApp, null);
  }
}
