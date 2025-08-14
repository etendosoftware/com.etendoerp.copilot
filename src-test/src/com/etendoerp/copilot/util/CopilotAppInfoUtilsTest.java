package com.etendoerp.copilot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.AppInfo;
import com.etendoerp.copilot.data.CopilotApp;

/**
 * Unit tests for CopilotAppInfoUtils class.
 * Tests all utility methods for managing AppInfo records and sync status.
 */
public class CopilotAppInfoUtilsTest extends WeldBaseTest {

  private CopilotApp mockCopilotApp;
  private AppInfo mockAppInfo;
  private Client mockClient;
  private Organization mockOrganization;

  /**
   * Sets up the test environment and mocks before each test.
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

    // Setup mocks
    mockCopilotApp = mock(CopilotApp.class);
    mockAppInfo = mock(AppInfo.class);
    mockClient = mock(Client.class);
    mockOrganization = mock(Organization.class);

    when(mockCopilotApp.getClient()).thenReturn(mockClient);
    when(mockCopilotApp.getOrganization()).thenReturn(mockOrganization);
    when(mockCopilotApp.getId()).thenReturn("test-app-id");
  }

  /**
   * Test getSyncStatus when AppInfo exists and has a sync status.
   */
  @Test
  public void testGetSyncStatus_WithExistingAppInfo() {
    // Arrange
    when(mockAppInfo.getSyncStatus()).thenReturn(CopilotConstants.SYNCHRONIZED_STATE);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      String result = CopilotAppInfoUtils.getSyncStatus(mockCopilotApp);

      // Assert
      assertEquals(CopilotConstants.SYNCHRONIZED_STATE, result);
      verify(mockCriteria).setMaxResults(1);
    }
  }

  /**
   * Test getSyncStatus when no AppInfo exists - should return default PS status.
   */
  @Test
  public void testGetSyncStatus_WithNoAppInfo() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(null);

      // Act
      String result = CopilotAppInfoUtils.getSyncStatus(mockCopilotApp);

      // Assert
      assertEquals(CopilotConstants.PENDING_SYNCHRONIZATION_STATE, result);
    }
  }

  /**
   * Test getSyncStatus with null CopilotApp - should return default PS status.
   */
  @Test
  public void testGetSyncStatus_WithNullCopilotApp() {
    // Act
    String result = CopilotAppInfoUtils.getSyncStatus(null);

    // Assert
    assertEquals(CopilotConstants.PENDING_SYNCHRONIZATION_STATE, result);
  }

  /**
   * Test setSyncStatus when creating a new AppInfo record.
   */
  @Test
  public void testSetSyncStatus_CreateNewAppInfo() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBProvider> obProviderMock = mockStatic(OBProvider.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      // Setup mocks
      OBDal mockOBDal = mock(OBDal.class);
      OBProvider mockOBProvider = mock(OBProvider.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      obProviderMock.when(OBProvider::getInstance).thenReturn(mockOBProvider);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(null); // No existing AppInfo
      when(mockOBProvider.get(AppInfo.class)).thenReturn(mockAppInfo);

      // Act
      CopilotAppInfoUtils.setSyncStatus(mockCopilotApp, CopilotConstants.SYNCHRONIZED_STATE);

      // Assert
      verify(mockAppInfo).setAgent(mockCopilotApp);
      verify(mockAppInfo).setClient(mockClient);
      verify(mockAppInfo).setOrganization(mockOrganization);
      verify(mockAppInfo).setActive(true);
      verify(mockAppInfo).setSyncStatus(CopilotConstants.SYNCHRONIZED_STATE);
      verify(mockOBDal).save(mockAppInfo);
      obContextMock.verify(OBContext::restorePreviousMode);
    }
  }

  /**
   * Test setSyncStatus when updating an existing AppInfo record.
   */
  @Test
  public void testSetSyncStatus_UpdateExistingAppInfo() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      // Setup mocks
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo); // Existing AppInfo

      // Act
      CopilotAppInfoUtils.setSyncStatus(mockCopilotApp, CopilotConstants.PENDING_SYNCHRONIZATION_STATE);

      // Assert
      verify(mockAppInfo).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
      verify(mockOBDal).save(mockAppInfo);
      obContextMock.verify(OBContext::restorePreviousMode);
    }
  }

  /**
   * Test setSyncStatus with null parameters - should handle gracefully.
   */
  @Test
  public void testSetSyncStatus_WithNullParameters() {
    // Test with null CopilotApp
    CopilotAppInfoUtils.setSyncStatus(null, CopilotConstants.SYNCHRONIZED_STATE);

    // Test with null sync status
    CopilotAppInfoUtils.setSyncStatus(mockCopilotApp, null);

    // No exceptions should be thrown
    assertTrue(true);
  }

  /**
   * Test getAppInfo when AppInfo exists.
   */
  @Test
  public void testGetAppInfo_Exists() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      AppInfo result = CopilotAppInfoUtils.getAppInfo(mockCopilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(mockAppInfo, result);
      verify(mockCriteria).setMaxResults(1);
    }
  }

  /**
   * Test getAppInfo when no AppInfo exists.
   */
  @Test
  public void testGetAppInfo_NotExists() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(null);

      // Act
      AppInfo result = CopilotAppInfoUtils.getAppInfo(mockCopilotApp);

      // Assert
      assertNull(result);
    }
  }

  /**
   * Test getAppInfo with null CopilotApp.
   */
  @Test
  public void testGetAppInfo_WithNullCopilotApp() {
    // Act
    AppInfo result = CopilotAppInfoUtils.getAppInfo(null);

    // Assert
    assertNull(result);
  }

  /**
   * Test isPendingSynchronization when status is PS.
   */
  @Test
  public void testIsPendingSynchronization_True() {
    // Arrange
    when(mockAppInfo.getSyncStatus()).thenReturn(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      boolean result = CopilotAppInfoUtils.isPendingSynchronization(mockCopilotApp);

      // Assert
      assertTrue(result);
    }
  }

  /**
   * Test isPendingSynchronization when status is not PS.
   */
  @Test
  public void testIsPendingSynchronization_False() {
    // Arrange
    when(mockAppInfo.getSyncStatus()).thenReturn(CopilotConstants.SYNCHRONIZED_STATE);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      boolean result = CopilotAppInfoUtils.isPendingSynchronization(mockCopilotApp);

      // Assert
      assertFalse(result);
    }
  }

  /**
   * Test isSynchronized when status is S.
   */
  @Test
  public void testIsSynchronized_True() {
    // Arrange
    when(mockAppInfo.getSyncStatus()).thenReturn(CopilotConstants.SYNCHRONIZED_STATE);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      boolean result = CopilotAppInfoUtils.isSynchronized(mockCopilotApp);

      // Assert
      assertTrue(result);
    }
  }

  /**
   * Test isSynchronized when status is not S.
   */
  @Test
  public void testIsSynchronized_False() {
    // Arrange
    when(mockAppInfo.getSyncStatus()).thenReturn(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      boolean result = CopilotAppInfoUtils.isSynchronized(mockCopilotApp);

      // Assert
      assertFalse(result);
    }
  }

  /**
   * Test markAsPendingSynchronization method.
   */
  @Test
  public void testMarkAsPendingSynchronization() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBProvider> obProviderMock = mockStatic(OBProvider.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      // Setup mocks
      OBDal mockOBDal = mock(OBDal.class);
      OBProvider mockOBProvider = mock(OBProvider.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      obProviderMock.when(OBProvider::getInstance).thenReturn(mockOBProvider);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(null); // No existing AppInfo
      when(mockOBProvider.get(AppInfo.class)).thenReturn(mockAppInfo);

      // Act
      CopilotAppInfoUtils.markAsPendingSynchronization(mockCopilotApp);

      // Assert
      verify(mockAppInfo).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
      verify(mockOBDal).save(mockAppInfo);
    }
  }

  /**
   * Test markAsSynchronized method.
   */
  @Test
  public void testMarkAsSynchronized() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBProvider> obProviderMock = mockStatic(OBProvider.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      // Setup mocks
      OBDal mockOBDal = mock(OBDal.class);
      OBProvider mockOBProvider = mock(OBProvider.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      obProviderMock.when(OBProvider::getInstance).thenReturn(mockOBProvider);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(null); // No existing AppInfo
      when(mockOBProvider.get(AppInfo.class)).thenReturn(mockAppInfo);

      // Act
      CopilotAppInfoUtils.markAsSynchronized(mockCopilotApp);

      // Assert
      verify(mockAppInfo).setSyncStatus(CopilotConstants.SYNCHRONIZED_STATE);
      verify(mockOBDal).save(mockAppInfo);
    }
  }

  /**
   * Test edge case: multiple rapid calls to setSyncStatus.
   */
  @Test
  public void testSetSyncStatus_MultipleCalls() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      // Setup mocks
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act - multiple calls
      CopilotAppInfoUtils.setSyncStatus(mockCopilotApp, CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
      CopilotAppInfoUtils.setSyncStatus(mockCopilotApp, CopilotConstants.SYNCHRONIZED_STATE);

      // Assert
      verify(mockAppInfo).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
      verify(mockAppInfo).setSyncStatus(CopilotConstants.SYNCHRONIZED_STATE);
      verify(mockOBDal, org.mockito.Mockito.times(2)).save(mockAppInfo);
    }
  }

  /**
   * Test setSyncStatus with null sync status - should handle gracefully.
   */
  @Test
  public void testSetSyncStatus_WithNullSyncStatus() {
    // This should not throw any exception and should exit early
    CopilotAppInfoUtils.setSyncStatus(mockCopilotApp, null);

    // No database operations should happen
    assertTrue(true); // Test passes if no exception is thrown
  }

  /**
   * Test getSyncStatus with null AppInfo that returns null status.
   */
  @Test
  public void testGetSyncStatus_WithNullAppInfoSyncStatus() {
    // Arrange
    when(mockAppInfo.getSyncStatus()).thenReturn(null);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      String result = CopilotAppInfoUtils.getSyncStatus(mockCopilotApp);

      // Assert - should return null (the actual value from AppInfo)
      assertNull(result);
    }
  }

  /**
   * Test boolean checks with null sync status.
   */
  @Test
  public void testBooleanChecks_WithNullSyncStatus() {
    // Arrange
    when(mockAppInfo.getSyncStatus()).thenReturn(null);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act & Assert
      assertFalse(CopilotAppInfoUtils.isPendingSynchronization(mockCopilotApp));
      assertFalse(CopilotAppInfoUtils.isSynchronized(mockCopilotApp));
    }
  }

  // ==================== GRAPH_IMG Tests ====================

  /**
   * Test getGraphImg when AppInfo exists and has a graph image.
   */
  @Test
  public void testGetGraphImg_WithExistingAppInfo() {
    // Arrange
    String testGraphImg = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
    when(mockAppInfo.getGraphPreview()).thenReturn(testGraphImg);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      String result = CopilotAppInfoUtils.getGraphImg(mockCopilotApp);

      // Assert
      assertEquals(testGraphImg, result);
    }
  }

  /**
   * Test getGraphImg when no AppInfo exists - should return null.
   */
  @Test
  public void testGetGraphImg_WithNoAppInfo() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(null);

      // Act
      String result = CopilotAppInfoUtils.getGraphImg(mockCopilotApp);

      // Assert
      assertNull(result);
    }
  }

  /**
   * Test getGraphImg with null CopilotApp - should return null.
   */
  @Test
  public void testGetGraphImg_WithNullCopilotApp() {
    // Act
    String result = CopilotAppInfoUtils.getGraphImg(null);

    // Assert
    assertNull(result);
  }

  /**
   * Test setGraphImg when creating a new AppInfo record.
   */
  @Test
  public void testSetGraphImg_CreateNewAppInfo() {
    String testGraphImg = "test-graph-image-data";

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBProvider> obProviderMock = mockStatic(OBProvider.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      // Setup mocks
      OBDal mockOBDal = mock(OBDal.class);
      OBProvider mockOBProvider = mock(OBProvider.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      obProviderMock.when(OBProvider::getInstance).thenReturn(mockOBProvider);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(null); // No existing AppInfo
      when(mockOBProvider.get(AppInfo.class)).thenReturn(mockAppInfo);

      // Act
      CopilotAppInfoUtils.setGraphImg(mockCopilotApp, testGraphImg);

      // Assert
      verify(mockAppInfo).setAgent(mockCopilotApp);
      verify(mockAppInfo).setGraphPreview(testGraphImg);
      verify(mockOBDal).save(mockAppInfo);
    }
  }

  /**
   * Test setGraphImg when updating an existing AppInfo record.
   */
  @Test
  public void testSetGraphImg_UpdateExistingAppInfo() {
    String testGraphImg = "updated-graph-image-data";

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      // Setup mocks
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo); // Existing AppInfo

      // Act
      CopilotAppInfoUtils.setGraphImg(mockCopilotApp, testGraphImg);

      // Assert
      verify(mockAppInfo).setGraphPreview(testGraphImg);
      verify(mockOBDal).save(mockAppInfo);
    }
  }

  /**
   * Test hasGraphImg when AppInfo has a graph image.
   */
  @Test
  public void testHasGraphImg_True() {
    // Arrange
    when(mockAppInfo.getGraphPreview()).thenReturn("some-graph-data");

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      boolean result = CopilotAppInfoUtils.hasGraphImg(mockCopilotApp);

      // Assert
      assertTrue(result);
    }
  }

  /**
   * Test hasGraphImg when AppInfo has no graph image.
   */
  @Test
  public void testHasGraphImg_False() {
    // Arrange
    when(mockAppInfo.getGraphPreview()).thenReturn(null);

    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      boolean result = CopilotAppInfoUtils.hasGraphImg(mockCopilotApp);

      // Assert
      assertFalse(result);
    }
  }

  /**
   * Test clearGraphImg method.
   */
  @Test
  public void testClearGraphImg() {
    try (MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      // Setup mocks
      OBDal mockOBDal = mock(OBDal.class);
      OBCriteria<AppInfo> mockCriteria = mock(OBCriteria.class);

      obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
      when(mockOBDal.createCriteria(AppInfo.class)).thenReturn(mockCriteria);
      when(mockCriteria.uniqueResult()).thenReturn(mockAppInfo);

      // Act
      CopilotAppInfoUtils.clearGraphImg(mockCopilotApp);

      // Assert
      verify(mockAppInfo).setGraphPreview(null);
      verify(mockOBDal).save(mockAppInfo);
    }
  }
}
