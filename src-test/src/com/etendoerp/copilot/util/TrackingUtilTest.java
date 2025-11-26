package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.telemetry.TelemetryUsageInfo;
import org.openbravo.model.ad.system.Client;

/**
 * TrackingUtil test class.
 */
public class TrackingUtilTest {

  private AutoCloseable mocks;

  @Mock
  private OBDal obDal;
  @Mock
  private OBContext obContext;
  @Mock
  private OBProvider obProvider;
  @Mock
  private User mockUser;
  @Mock
  private Client mockClient;
  @Mock
  private Organization mockOrganization;
  @Mock
  private CopilotApp mockApp;
  @Mock
  private Module mockModule;
  @Mock
  private Conversation mockConversation;
  @Mock
  private Message mockMessage;
  @Mock
  private OBQuery<Conversation> mockConversationQuery;
  @Mock
  private OBQuery<Message> mockMessageQuery;
  @Mock
  private OBCriteria<Conversation> mockConversationCriteria;
  @Mock
  private OBCriteria<Message> mockMessageCriteria;
  @Mock
  private TelemetryUsageInfo mockTelemetry;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBProvider> mockedOBProvider;
  private MockedStatic<CopilotModelUtils> mockedCopilotModelUtils;
  private MockedStatic<TelemetryUsageInfo> mockedTelemetryUsageInfo;

  private static final String TEST_CONVERSATION_ID = "testConvId123";
  private static final String TEST_APP_ID = "testAppId123";
  private static final String TEST_APP_NAME = "Test Assistant";
  private static final String TEST_USER_ID = "testUserId123";
  private static final String TEST_MODULE_ID = "testModuleId123";
  private static final String TEST_MODULE_NAME = "Test Module";
  private static final String TEST_MODEL_NAME = "gpt-4";
  private static final String TEST_PROVIDER = "OpenAI";
  private static final String RESULT_NOT_NULL_MESSAGE = "Result should not be null";

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    // Set up static mocks
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBProvider = mockStatic(OBProvider.class);
    mockedCopilotModelUtils = mockStatic(CopilotModelUtils.class);
    mockedTelemetryUsageInfo = mockStatic(TelemetryUsageInfo.class);

    // Configure OBDal mock
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.createQuery(eq(Conversation.class), anyString())).thenReturn(mockConversationQuery);
    when(obDal.createQuery(eq(Message.class), anyString())).thenReturn(mockMessageQuery);
    when(obDal.createCriteria(Conversation.class)).thenReturn(mockConversationCriteria);
    when(obDal.createCriteria(Message.class)).thenReturn(mockMessageCriteria);
    doNothing().when(obDal).save(any());
    doNothing().when(obDal).flush();

    // Configure Query mocks
    when(mockConversationQuery.setNamedParameter(anyString(), any())).thenReturn(mockConversationQuery);
    when(mockConversationQuery.setMaxResult(1)).thenReturn(mockConversationQuery);
    when(mockMessageQuery.setNamedParameter(anyString(), any())).thenReturn(mockMessageQuery);

    // Configure OBContext mock
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getUser()).thenReturn(mockUser);
    when(obContext.getCurrentClient()).thenReturn(mockClient);
    when(obContext.getCurrentOrganization()).thenReturn(mockOrganization);

    // Configure OBProvider mock
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(obProvider);
    when(obProvider.get(Conversation.class)).thenReturn(mockConversation);
    when(obProvider.get(Message.class)).thenReturn(mockMessage);

    // Configure User mock
    when(mockUser.getId()).thenReturn(TEST_USER_ID);

    // Configure CopilotApp mock
    when(mockApp.getId()).thenReturn(TEST_APP_ID);
    when(mockApp.getName()).thenReturn(TEST_APP_NAME);
    when(mockApp.getAppType()).thenReturn("Assistant");
    when(mockApp.getModule()).thenReturn(mockModule);
    when(mockApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());

    // Configure Module mock
    when(mockModule.getId()).thenReturn(TEST_MODULE_ID);
    when(mockModule.getName()).thenReturn(TEST_MODULE_NAME);

    // Configure Conversation mock
    when(mockConversation.getExternalID()).thenReturn(TEST_CONVERSATION_ID);
    doNothing().when(mockConversation).setClient(any());
    doNothing().when(mockConversation).setOrganization(any());
    doNothing().when(mockConversation).setNewOBObject(true);
    doNothing().when(mockConversation).setExternalID(anyString());
    doNothing().when(mockConversation).setCopilotApp(any());
    doNothing().when(mockConversation).setUserContact(any());
    doNothing().when(mockConversation).setLastMsg(any());

    // Configure Message mock - Return a new mock each time to avoid ModelProvider issues
    when(obProvider.get(Message.class)).thenAnswer(invocation -> {
      Message newMockMessage = mock(Message.class);
      doNothing().when(newMockMessage).setConversation(any());
      doNothing().when(newMockMessage).setMessage(anyString());
      doNothing().when(newMockMessage).setRole(anyString());
      doNothing().when(newMockMessage).setMetadata(anyString());
      doNothing().when(newMockMessage).setLineno(any());
      return newMockMessage;
    });

    // Configure Criteria mocks
    when(mockConversationCriteria.add(any())).thenReturn(mockConversationCriteria);
    when(mockConversationCriteria.addOrder(any(Order.class))).thenReturn(mockConversationCriteria);
    when(mockConversationCriteria.setMaxResults(1)).thenReturn(mockConversationCriteria);
    when(mockMessageCriteria.add(any())).thenReturn(mockMessageCriteria);
    when(mockMessageCriteria.setProjection(any())).thenReturn(mockMessageCriteria);

    // Configure CopilotModelUtils mock
    mockedCopilotModelUtils.when(() -> CopilotModelUtils.getAppModel(mockApp)).thenReturn(TEST_MODEL_NAME);
    mockedCopilotModelUtils.when(() -> CopilotModelUtils.getProvider(mockApp)).thenReturn(TEST_PROVIDER);

    // Configure TelemetryUsageInfo mock
    mockedTelemetryUsageInfo.when(TelemetryUsageInfo::getInstance).thenReturn(mockTelemetry);
    when(mockTelemetry.getTimeMillis()).thenReturn(System.currentTimeMillis());
    doNothing().when(mockTelemetry).setModuleId(anyString());
    doNothing().when(mockTelemetry).setUserId(anyString());
    doNothing().when(mockTelemetry).setObjectId(anyString());
    doNothing().when(mockTelemetry).setClassname(anyString());
    doNothing().when(mockTelemetry).setTimeMillis(any(Long.class));
    doNothing().when(mockTelemetry).setJsonObject(any(JSONObject.class));
    doNothing().when(mockTelemetry).saveUsageAudit();

    // Get TrackingUtil instance

  }

  /**
   * Tears down the test environment after each test.
   *
   * @throws Exception if teardown fails
   */
  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mockedOBContext != null) {
      mockedOBContext.close();
    }
    if (mockedOBProvider != null) {
      mockedOBProvider.close();
    }
    if (mockedCopilotModelUtils != null) {
      mockedCopilotModelUtils.close();
    }
    if (mockedTelemetryUsageInfo != null) {
      mockedTelemetryUsageInfo.close();
    }
  }

  /**
   * Test getInstance returns singleton instance.
   */
  @Test
  public void testGetInstance() {
    // When
    TrackingUtil instance1 = TrackingUtil.getInstance();
    TrackingUtil instance2 = TrackingUtil.getInstance();

    // Then
    assertNotNull("Instance should not be null", instance1);
    assertEquals("Should return same instance (singleton)", instance1, instance2);
  }

  /**
   * Test sendUsageData builds and sends telemetry data.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testSendUsageData() throws Exception {
    // When
    TrackingUtil.sendUsageData(mockApp);

    // Then
    verify(mockTelemetry, times(1)).setModuleId(TrackingUtil.COPILOT_MODULE_ID);
    verify(mockTelemetry, times(1)).setUserId(TEST_USER_ID);
    verify(mockTelemetry, times(1)).setObjectId(TEST_APP_ID);
    verify(mockTelemetry, times(1)).setClassname(TrackingUtil.class.getName());
    verify(mockTelemetry, times(1)).setTimeMillis(any(Long.class));
    verify(mockTelemetry, times(1)).setJsonObject(any(JSONObject.class));
    verify(mockTelemetry, times(1)).saveUsageAudit();
  }

  /**
   * Test sendUsageData handles exceptions silently.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testSendUsageDataWithException() throws Exception {
    // Given
    when(mockTelemetry.getTimeMillis()).thenThrow(new RuntimeException("Test error"));

    // When - Should not throw exception
    TrackingUtil.sendUsageData(mockApp);

    // Then - Exception is handled silently
  }

  /**
   * Note: Tests for trackQuestion, trackResponse, and trackNullResponse are not included
   * because they use "new Message()" directly in the production code (line 186 of TrackingUtil.java),
   * which cannot be mocked with standard Mockito. These methods would require either:
   * 1. Mockito Inline (for constructor mocking)
   * 2. PowerMock
   * 3. Refactoring the production code to use OBProvider.getInstance().get(Message.class)
   * 4. Full Openbravo infrastructure for integration tests
   */

  /**
   * Test getHistory returns messages in correct format.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetHistory() throws Exception {
    // Given
    Message msg1 = mock(Message.class);
    when(msg1.getRole()).thenReturn("user");
    when(msg1.getMessage()).thenReturn("Question 1");

    Message msg2 = mock(Message.class);
    when(msg2.getRole()).thenReturn("assistant");
    when(msg2.getMessage()).thenReturn("Answer 1");

    List<Message> messages = new ArrayList<>();
    messages.add(msg1);
    messages.add(msg2);

    when(mockMessageQuery.list()).thenReturn(messages);

    // When
    JSONArray history = TrackingUtil.getHistory(TEST_CONVERSATION_ID);

    // Then
    assertNotNull("History should not be null", history);
    assertEquals("Should have 2 messages", 2, history.length());

    JSONObject firstMsg = history.getJSONObject(0);
    assertEquals("First message should be user", "user", firstMsg.getString("role"));
    assertEquals("First message content", "Question 1", firstMsg.getString("content"));

    JSONObject secondMsg = history.getJSONObject(1);
    assertEquals("Second message should be assistant", "assistant", secondMsg.getString("role"));
    assertEquals("Second message content", "Answer 1", secondMsg.getString("content"));
  }

  /**
   * Test getLastConversation returns last message date.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetLastConversationWithLastMsg() throws Exception {
    // Given
    Date testDate = new Date();
    when(mockConversation.getLastMsg()).thenReturn(testDate);
    when(mockConversationCriteria.uniqueResult()).thenReturn(mockConversation);

    // When
    Date result = TrackingUtil.getLastConversation(mockUser, mockApp);

    // Then
    assertNotNull(RESULT_NOT_NULL_MESSAGE, result);
    assertEquals("Should return last message date", testDate, result);
  }

  /**
   * Test getLastConversation returns creation date when lastMsg is null.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetLastConversationWithoutLastMsg() throws Exception {
    // Given
    Date creationDate = new Date();
    when(mockConversation.getLastMsg()).thenReturn(null);
    when(mockConversation.getCreationDate()).thenReturn(creationDate);
    when(mockConversationCriteria.uniqueResult()).thenReturn(mockConversation);

    // When
    Date result = TrackingUtil.getLastConversation(mockUser, mockApp);

    // Then
    assertNotNull(RESULT_NOT_NULL_MESSAGE, result);
    assertEquals("Should return creation date", creationDate, result);
  }

  /**
   * Test getLastConversation returns fallback date when no conversation exists.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetLastConversationNoConversation() throws Exception {
    // Given
    when(mockConversationCriteria.uniqueResult()).thenReturn(null);

    // When
    Date result = TrackingUtil.getLastConversation(mockUser, mockApp);

    // Then
    assertNotNull(RESULT_NOT_NULL_MESSAGE, result);
    Date expectedDate = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
    assertEquals("Should return fallback date", expectedDate, result);
  }


  /**
   * Test telemetry JSON structure for agent with team members.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testSendUsageDataWithTeamMembers() throws Exception {
    // Given
    CopilotApp memberApp = mock(CopilotApp.class);
    when(memberApp.getId()).thenReturn("memberId");
    when(memberApp.getName()).thenReturn("Member Agent");
    when(memberApp.getAppType()).thenReturn("Tool");
    when(memberApp.getModule()).thenReturn(null);
    when(memberApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());

    TeamMember teamMember = mock(TeamMember.class);
    when(teamMember.getCopilotApp()).thenReturn(memberApp);
    when(teamMember.getMember()).thenReturn(memberApp);

    List<TeamMember> teamMembers = new ArrayList<>();
    teamMembers.add(teamMember);
    when(mockApp.getETCOPTeamMemberList()).thenReturn(teamMembers);

    mockedCopilotModelUtils.when(() -> CopilotModelUtils.getAppModel(memberApp)).thenReturn("gpt-3.5");
    mockedCopilotModelUtils.when(() -> CopilotModelUtils.getProvider(memberApp)).thenReturn(TEST_PROVIDER);

    // When
    TrackingUtil.sendUsageData(mockApp);

    // Then
    verify(mockTelemetry, times(1)).setJsonObject(any(JSONObject.class));
    verify(mockTelemetry, times(1)).saveUsageAudit();
  }

  /**
   * Test constant values.
   */
  @Test
  public void testConstants() {
    assertEquals("COPILOT_MODULE_ID should match", "0B8480670F614D4CA99921D68BB0DD87",
        TrackingUtil.COPILOT_MODULE_ID);
  }

  /**
   * Test getHistory with empty message list.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetHistoryEmpty() throws Exception {
    // Given
    when(mockMessageQuery.list()).thenReturn(new ArrayList<>());

    // When
    JSONArray history = TrackingUtil.getHistory(TEST_CONVERSATION_ID);

    // Then
    assertNotNull("History should not be null", history);
    assertEquals("Should have 0 messages", 0, history.length());
  }
}
