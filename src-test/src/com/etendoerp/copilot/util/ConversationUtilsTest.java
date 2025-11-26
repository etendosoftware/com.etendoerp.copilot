package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;
import com.etendoerp.copilot.rest.RequestUtils;
import com.etendoerp.copilot.rest.RestServiceUtil;

/**
 * ConversationUtils test class.
 */
public class ConversationUtilsTest {

  private AutoCloseable mocks;

  @Mock
  private HttpServletRequest mockRequest;
  @Mock
  private HttpServletResponse mockResponse;
  @Mock
  private OBDal obDal;
  @Mock
  private OBContext obContext;
  @Mock
  private User mockUser;
  @Mock
  private CopilotApp mockAssistant;
  @Mock
  private Conversation mockConversation;
  @Mock
  private Message mockMessage;
  @Mock
  private OBCriteria<Conversation> mockConversationCriteria;
  @Mock
  private PrintWriter mockWriter;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private MockedStatic<RequestUtils> mockedRequestUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<RestServiceUtil> mockedRestServiceUtil;

  private static final String TEST_CONVERSATION_ID = "testConvId123";
  private static final String TEST_EXTERNAL_ID = "testExtId123";
  private static final String TEST_APP_ID = "testAppId123";
  private static final String TEST_TITLE = "Test Conversation Title";
  private static final String TEST_USER_ID = "testUserId123";
  private static final String ERROR_CONVERSATION_REQUIRED = "Conversation ID is required";
  private static final String ERROR_APP_ID_REQUIRED = "App ID is required";

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
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedRequestUtils = mockStatic(RequestUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedRestServiceUtil = mockStatic(RestServiceUtil.class);

    // Configure OBDal mock
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
    when(obDal.get(Conversation.class, TEST_CONVERSATION_ID)).thenReturn(mockConversation);
    when(obDal.createCriteria(Conversation.class)).thenReturn(mockConversationCriteria);
    doNothing().when(obDal).save(any(Conversation.class));
    doNothing().when(obDal).flush();

    // Configure OBContext mock
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    mockedOBContext.when(OBContext::setAdminMode).thenAnswer(invocation -> null);
    mockedOBContext.when(OBContext::restorePreviousMode).thenAnswer(invocation -> null);
    when(obContext.getUser()).thenReturn(mockUser);
    when(mockUser.getId()).thenReturn(TEST_USER_ID);

    // Configure OBCriteria mock
    when(mockConversationCriteria.add(any())).thenReturn(mockConversationCriteria);
    when(mockConversationCriteria.addOrder(any(Order.class))).thenReturn(mockConversationCriteria);
    when(mockConversationCriteria.setMaxResults(1)).thenReturn(mockConversationCriteria);

    // Configure Conversation mock
    when(mockConversation.getId()).thenReturn(TEST_CONVERSATION_ID);
    when(mockConversation.getExternalID()).thenReturn(TEST_EXTERNAL_ID);
    when(mockConversation.getTitle()).thenReturn(TEST_TITLE);

    // Configure Message mock
    when(mockMessage.getId()).thenReturn("msgId1");
    when(mockMessage.getRole()).thenReturn("user");
    when(mockMessage.getMessage()).thenReturn("Test message");
    when(mockMessage.getCreationDate()).thenReturn(new Date());
    when(mockMessage.getLineno()).thenReturn(1L);

    // Configure HttpServletResponse mock
    when(mockResponse.getWriter()).thenReturn(mockWriter);
    doNothing().when(mockWriter).write(anyString());
    doNothing().when(mockResponse).setContentType(anyString());

    // Configure OBMessageUtils mock
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ConversationRequired"))
        .thenReturn(ERROR_CONVERSATION_REQUIRED);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_AppIDRequired"))
        .thenReturn(ERROR_APP_ID_REQUIRED);
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
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
    if (mockedRequestUtils != null) {
      mockedRequestUtils.close();
    }
    if (mockedCopilotUtils != null) {
      mockedCopilotUtils.close();
    }
    if (mockedRestServiceUtil != null) {
      mockedRestServiceUtil.close();
    }
  }

  /**
   * Test handleGetTitleConversation with valid conversation ID from parameter.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testHandleGetTitleConversationFromParameter() throws Exception {
    // Given
    when(mockRequest.getParameter(CopilotConstants.PROP_CONVERSATION_ID))
        .thenReturn(TEST_CONVERSATION_ID);
    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest))
        .thenReturn(new JSONObject());

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(mockResponse.getWriter()).thenReturn(writer);

    // When
    ConversationUtils.handleGetTitleConversation(mockRequest, mockResponse);

    // Then
    writer.flush();
    String response = stringWriter.toString();
    assertTrue("Response should contain title", response.contains(TEST_TITLE));
    verify(mockResponse, times(1)).setContentType(anyString());
  }

  /**
   * Test handleGetTitleConversation with valid conversation ID from JSON body.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testHandleGetTitleConversationFromJSONBody() throws Exception {
    // Given
    when(mockRequest.getParameter(CopilotConstants.PROP_CONVERSATION_ID)).thenReturn(null);
    JSONObject requestBody = new JSONObject();
    requestBody.put(CopilotConstants.PROP_CONVERSATION_ID, TEST_CONVERSATION_ID);
    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest))
        .thenReturn(requestBody);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(mockResponse.getWriter()).thenReturn(writer);

    // When
    ConversationUtils.handleGetTitleConversation(mockRequest, mockResponse);

    // Then
    writer.flush();
    String response = stringWriter.toString();
    assertTrue("Response should contain title", response.contains(TEST_TITLE));
  }

  /**
   * Test handleGetTitleConversation with missing conversation ID.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testHandleGetTitleConversationMissingID() throws Exception {
    // Given
    when(mockRequest.getParameter(CopilotConstants.PROP_CONVERSATION_ID)).thenReturn(null);
    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest))
        .thenReturn(new JSONObject());

    // When
    ConversationUtils.handleGetTitleConversation(mockRequest, mockResponse);

    // Then
    verify(mockResponse, times(1)).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
  }

  /**
   * Test handleConversations with valid app ID.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testHandleConversationsWithValidAppId() throws Exception {
    // Given
    when(mockRequest.getParameter(CopilotConstants.PROP_APP_ID)).thenReturn(TEST_APP_ID);
    mockedCopilotUtils.when(() -> CopilotUtils.getAssistantByIDOrName(TEST_APP_ID))
        .thenReturn(mockAssistant);

    List<Conversation> conversations = new ArrayList<>();
    conversations.add(mockConversation);
    when(mockConversationCriteria.list()).thenReturn(conversations);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(mockResponse.getWriter()).thenReturn(writer);

    // When
    ConversationUtils.handleConversations(mockRequest, mockResponse);

    // Then
    writer.flush();
    String response = stringWriter.toString();
    assertTrue("Response should be a JSON array", response.startsWith("["));
    verify(mockResponse, times(1)).setContentType(anyString());
  }

  /**
   * Test handleConversations with missing app ID.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testHandleConversationsMissingAppId() throws Exception {
    // Given
    when(mockRequest.getParameter(CopilotConstants.PROP_APP_ID)).thenReturn(null);

    // When
    ConversationUtils.handleConversations(mockRequest, mockResponse);

    // Then
    verify(mockResponse, times(1)).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
  }

  /**
   * Test handleConversationMessages with valid conversation ID.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testHandleConversationMessagesWithValidId() throws Exception {
    // Given
    when(mockRequest.getParameter("conversation_id")).thenReturn(TEST_CONVERSATION_ID);

    List<Message> messages = new ArrayList<>();
    messages.add(mockMessage);
    when(mockConversation.getETCOPMessageList()).thenReturn(messages);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(mockResponse.getWriter()).thenReturn(writer);

    // When
    ConversationUtils.handleConversationMessages(mockRequest, mockResponse);

    // Then
    writer.flush();
    String response = stringWriter.toString();
    assertTrue("Response should be a JSON array", response.startsWith("["));
    verify(mockResponse, times(1)).setContentType(anyString());
  }

  /**
   * Test handleConversationMessages with missing conversation ID.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testHandleConversationMessagesMissingId() throws Exception {
    // Given
    when(mockRequest.getParameter("conversation_id")).thenReturn(null);

    // When
    ConversationUtils.handleConversationMessages(mockRequest, mockResponse);

    // Then
    verify(mockResponse, times(1)).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
  }

  /**
   * Test getConversations with valid assistant.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetConversationsWithValidAssistant() throws Exception {
    // Given
    List<Conversation> conversations = new ArrayList<>();
    conversations.add(mockConversation);
    when(mockConversationCriteria.list()).thenReturn(conversations);

    // When
    JSONArray result = ConversationUtils.getConversations(mockAssistant);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one conversation", 1, result.length());
    JSONObject convJson = result.getJSONObject(0);
    assertEquals("Should have correct external ID", TEST_EXTERNAL_ID, convJson.getString("id"));
    assertEquals("Should have correct title", TEST_TITLE, convJson.getString("title"));
  }

  /**
   * Test getConversations with empty external ID (should be skipped).
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetConversationsWithEmptyExternalId() throws Exception {
    // Given
    when(mockConversation.getExternalID()).thenReturn("");
    List<Conversation> conversations = new ArrayList<>();
    conversations.add(mockConversation);
    when(mockConversationCriteria.list()).thenReturn(conversations);

    // When
    JSONArray result = ConversationUtils.getConversations(mockAssistant);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have no conversations (empty external ID skipped)", 0, result.length());
  }

  /**
   * Test getTitleConversation with existing title.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetTitleConversationWithExistingTitle() throws Exception {
    // Given
    when(mockConversation.getTitle()).thenReturn(TEST_TITLE);

    // When
    String result = ConversationUtils.getTitleConversation(TEST_CONVERSATION_ID);

    // Then
    assertEquals("Should return existing title", TEST_TITLE, result);
    verify(obDal, times(0)).save(any());
  }

  /**
   * Test getTitleConversation that generates new title.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetTitleConversationGenerateNewTitle() throws Exception {
    // Given
    when(mockConversation.getTitle()).thenReturn(null);
    List<Message> messages = new ArrayList<>();
    messages.add(mockMessage);
    when(mockConversation.getETCOPMessageList()).thenReturn(messages);

    JSONObject responseObject = new JSONObject();
    responseObject.put("response", "Generated Title");
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(
        anyBoolean(), any(), any(JSONObject.class))).thenReturn(responseObject);

    doNothing().when(mockConversation).setTitle(anyString());

    // When
    String result = ConversationUtils.getTitleConversation(TEST_CONVERSATION_ID);

    // Then
    assertEquals("Should return generated title", "Generated Title", result);
    verify(mockConversation, times(1)).setTitle("Generated Title");
    verify(obDal, times(1)).save(mockConversation);
    verify(obDal, times(1)).flush();
  }

  /**
   * Test getTitleConversation with null conversation.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetTitleConversationWithNullConversation() throws Exception {
    // Given
    when(obDal.get(Conversation.class, TEST_CONVERSATION_ID)).thenReturn(null);
    when(mockConversationCriteria.uniqueResult()).thenReturn(null);

    // When
    String result = ConversationUtils.getTitleConversation(TEST_CONVERSATION_ID);

    // Then
    assertEquals("Should return empty string for null conversation", "", result);
  }

  /**
   * Test getTitleConversation with exception during generation.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetTitleConversationWithException() throws Exception {
    // Given
    when(mockConversation.getTitle()).thenReturn(null);
    when(mockConversation.getETCOPMessageList()).thenThrow(new RuntimeException("Test error"));

    // When
    String result = ConversationUtils.getTitleConversation(TEST_CONVERSATION_ID);

    // Then
    assertEquals("Should return empty string on exception", "", result);
  }

  /**
   * Test getConversationMessages with valid conversation.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetConversationMessagesWithValidConversation() throws Exception {
    // Given
    List<Message> messages = new ArrayList<>();
    messages.add(mockMessage);
    when(mockConversation.getETCOPMessageList()).thenReturn(messages);

    // When
    JSONArray result = ConversationUtils.getConversationMessages(TEST_CONVERSATION_ID);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one message", 1, result.length());
    JSONObject msgJson = result.getJSONObject(0);
    assertEquals("Should have correct role", "user", msgJson.getString("role"));
    assertEquals("Should have correct content", "Test message", msgJson.getString("content"));
  }

  /**
   * Test getConversationMessages with exception.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetConversationMessagesWithException() throws Exception {
    // Given
    when(obDal.get(Conversation.class, TEST_CONVERSATION_ID))
        .thenThrow(new RuntimeException("Test error"));

    // When
    JSONArray result = ConversationUtils.getConversationMessages(TEST_CONVERSATION_ID);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should return empty array on exception", 0, result.length());
  }

  /**
   * Test getConversationMessages with multiple messages sorted by line number.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetConversationMessagesWithMultipleMessagesSorted() throws Exception {
    // Given
    Message msg1 = mock(Message.class);
    when(msg1.getId()).thenReturn("msg1");
    when(msg1.getRole()).thenReturn("user");
    when(msg1.getMessage()).thenReturn("First message");
    when(msg1.getCreationDate()).thenReturn(new Date());
    when(msg1.getLineno()).thenReturn(2L);

    Message msg2 = mock(Message.class);
    when(msg2.getId()).thenReturn("msg2");
    when(msg2.getRole()).thenReturn("assistant");
    when(msg2.getMessage()).thenReturn("Second message");
    when(msg2.getCreationDate()).thenReturn(new Date());
    when(msg2.getLineno()).thenReturn(1L);

    List<Message> messages = new ArrayList<>();
    messages.add(msg1);
    messages.add(msg2);
    when(mockConversation.getETCOPMessageList()).thenReturn(messages);

    // When
    JSONArray result = ConversationUtils.getConversationMessages(TEST_CONVERSATION_ID);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have two messages", 2, result.length());
    // Messages should be sorted by line number
    assertEquals("First message should be msg2", "msg2", result.getJSONObject(0).getString("id"));
    assertEquals("Second message should be msg1", "msg1", result.getJSONObject(1).getString("id"));
  }

  /**
   * Test getConversations with no title.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetConversationsWithNoTitle() throws Exception {
    // Given
    when(mockConversation.getTitle()).thenReturn(null);
    List<Conversation> conversations = new ArrayList<>();
    conversations.add(mockConversation);
    when(mockConversationCriteria.list()).thenReturn(conversations);

    // When
    JSONArray result = ConversationUtils.getConversations(mockAssistant);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one conversation", 1, result.length());
    JSONObject convJson = result.getJSONObject(0);
    assertEquals("Should have correct external ID", TEST_EXTERNAL_ID, convJson.getString("id"));
    // Title should not be present
    assertTrue("Should not have title field", !convJson.has("title") || convJson.isNull("title"));
  }

  /**
   * Test handleGetTitleConversation with IOException during response writing.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testHandleGetTitleConversationWithIOException() throws Exception {
    // Given
    when(mockRequest.getParameter(CopilotConstants.PROP_CONVERSATION_ID))
        .thenReturn(TEST_CONVERSATION_ID);
    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest))
        .thenReturn(new JSONObject());
    when(mockResponse.getWriter()).thenThrow(new IOException("Write error"));

    // When
    ConversationUtils.handleGetTitleConversation(mockRequest, mockResponse);

    // Then
    verify(mockResponse, times(1)).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
  }
}