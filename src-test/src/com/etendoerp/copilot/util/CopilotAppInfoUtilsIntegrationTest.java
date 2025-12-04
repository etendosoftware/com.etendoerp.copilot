/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.util;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.AppInfo;
import com.etendoerp.copilot.data.CopilotApp;

/**
 * Integration tests for CopilotAppInfoUtils class.
 * These tests use real database operations to validate end-to-end functionality.
 */
public class CopilotAppInfoUtilsIntegrationTest extends WeldBaseTest {

  private CopilotApp testCopilotApp;
  private boolean createdTestApp = false; // Flag to track if we created the test app

  /**
   * Sets up the test environment before each test.
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN,
        TestConstants.Clients.FB_GRP, TestConstants.Orgs.ESP_NORTE);
    VariablesSecureApp vsa = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(),
        OBContext.getOBContext().getCurrentOrganization().getId(),
        OBContext.getOBContext().getRole().getId());
    RequestContext.get().setVariableSecureApp(vsa);

    // Create a test CopilotApp for integration testing
    testCopilotApp = createTestCopilotApp();
  }

  /**
   * Creates a test CopilotApp for integration testing.
   *
   * @return a test CopilotApp instance
   */
  private CopilotApp createTestCopilotApp() {

    // If no existing app found, create a new one for testing
    CopilotApp newApp = OBProvider.getInstance().get(CopilotApp.class);
    newApp.setNewOBObject(true);
    newApp.setClient(OBContext.getOBContext().getCurrentClient());
    newApp.setOrganization(OBContext.getOBContext().getCurrentOrganization());

    // Set required fields based on ETCOP_APP table structure
    newApp.setName("Test Copilot App - Integration Test");
    newApp.setActive(true);

    // Set APPTYPE - this is required field
    newApp.setAppType("multimodel"); // You may need to check valid values for this field

    // Set boolean fields with defaults (they are required)
    newApp.setCodeInterpreter(false); // Default is 'N'
    newApp.setRetrieval(false); // Default is 'N'
    newApp.setSystemApp(false); // Default is 'N'

    // Save the new app
    OBDal.getInstance().save(newApp);
    OBDal.getInstance().commitAndClose();

    createdTestApp = true; // Mark that we created this app
    return newApp;
  }


  /**
   * Test that setSyncStatus throws exception for invalid sync_status values.
   */
  @Test
  public void testSetSyncStatusWithInvalidValues() {
    if (testCopilotApp == null) {
      return; // Skip if no test data available
    }

    try {
      // Test with sync_status longer than 60 characters (database limit)
      String tooLongStatus = "A".repeat(61); // 61 characters, exceeds limit of 60

      // This should throw an exception when trying to save to database

      // The exception should be thrown during the commit
      assertThrows(Exception.class, () -> CopilotAppInfoUtils.setSyncStatus(testCopilotApp, tooLongStatus),
          "Expected exception for sync_status longer than 60 characters");

    } finally {
      // Cleanup - rollback any pending changes
      try {
        OBDal.getInstance().rollbackAndClose();
      } catch (Exception e) {
        // Ignore cleanup errors
      }
      cleanupTestData();
    }
  }

  // ==================== GRAPH_IMG Integration Tests ====================

  /**
   * Test graph image operations: set, get, clear.
   */
  @Test
  public void testGraphImgOperations() {
    if (testCopilotApp == null) {
      return; // Skip if no test data available
    }

    try {
      String testGraphImg = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";

      // Initially should have no graph image
      assertFalse(CopilotAppInfoUtils.hasGraphImg(testCopilotApp));
      assertNull(CopilotAppInfoUtils.getGraphImg(testCopilotApp));

      // Set graph image
      CopilotAppInfoUtils.setGraphImg(testCopilotApp, testGraphImg);
      OBDal.getInstance().commitAndClose();

      // Verify it was set
      assertTrue(CopilotAppInfoUtils.hasGraphImg(testCopilotApp));
      assertEquals(testGraphImg, CopilotAppInfoUtils.getGraphImg(testCopilotApp));

      // Clear graph image
      try {
        OBContext.setAdminMode(false);
        CopilotAppInfoUtils.clearGraphImg(testCopilotApp);
        OBDal.getInstance().commitAndClose();
      } finally {
        OBContext.restorePreviousMode();
      }

      // Verify it was cleared
      assertFalse(CopilotAppInfoUtils.hasGraphImg(testCopilotApp));
      assertNull(CopilotAppInfoUtils.getGraphImg(testCopilotApp));

    } finally {
      // Cleanup
      cleanupTestData();
    }
  }

  /**
   * Test combined sync status and graph image operations.
   */
  @Test
  public void testCombinedSyncStatusAndGraphImg() {
    if (testCopilotApp == null) {
      return; // Skip if no test data available
    }

    try {
      OBContext.setAdminMode(false);
      String testGraphImg = "test-graph-data";

      // Set both sync status and graph image


      CopilotAppInfoUtils.setSyncStatus(testCopilotApp, CopilotConstants.SYNCHRONIZED_STATE);
      OBDal.getInstance().commitAndClose();
      CopilotAppInfoUtils.setGraphImg(testCopilotApp, testGraphImg);
      OBDal.getInstance().commitAndClose();


      // Verify both were set
      assertEquals(CopilotConstants.SYNCHRONIZED_STATE, CopilotAppInfoUtils.getSyncStatus(testCopilotApp));
      assertEquals(testGraphImg, CopilotAppInfoUtils.getGraphImg(testCopilotApp));
      assertTrue(CopilotAppInfoUtils.isSynchronized(testCopilotApp));
      assertTrue(CopilotAppInfoUtils.hasGraphImg(testCopilotApp));

      // Verify they use the same AppInfo record

      AppInfo appInfo = CopilotAppInfoUtils.getAppInfo(testCopilotApp);
      assertNotNull(appInfo);
      assertEquals(CopilotConstants.SYNCHRONIZED_STATE, appInfo.getSyncStatus());
      assertEquals(testGraphImg, appInfo.getGraphPreview());

    } finally {
      // Cleanup
      cleanupTestData();
      OBContext.restorePreviousMode();
    }
  }


  /**
   * Cleanup test data created during tests.
   */
  private void cleanupTestData() {
    try {
      OBContext.setAdminMode(false);
      if (testCopilotApp != null) {
        // Clean up AppInfo first
        AppInfo appInfo = CopilotAppInfoUtils.getAppInfo(testCopilotApp);
        if (appInfo != null) {
          OBDal.getInstance().remove(appInfo);
        }

        // If we created the test app, remove it as well
        if (createdTestApp) {
          OBDal.getInstance().remove(testCopilotApp);
        }

        OBDal.getInstance().commitAndClose();
      }
    } catch (Exception e) {
      // Ignore cleanup errors
      e.printStackTrace();
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}
