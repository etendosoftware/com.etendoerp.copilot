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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;
import com.etendoerp.copilot.rest.RequestUtils;

/**
 * Tests for the endpoints that write conversations and messages without running an agent:
 * {@link ConversationUtils#handleCreateConversation} and {@link ConversationUtils#handleAppendMessages},
 * plus the owner-checked operations in {@link ConversationWriteUtils} they delegate to.
 */
public class ConversationWriteEndpointsTest {

  private static final String CURRENT_USER = "currentUser";
  private static final String OTHER_USER = "otherUser";
  private static final String CONV_EXT_ID = "conv-ext-1";
  private static final String CONV_ID = "convPk1";
  private static final String MESSAGES = "messages";
  private static final String SAVED = "saved";
  private static final String SKIPPED = "skipped";
  private static final String EXTERNAL_ID = "external_id";
  private static final String CONVERSATION_ID = "conversation_id";
  private static final String ERR_CONVERSATION_REQUIRED = "Conversation ID is required";
  private static final String ERR_NOT_FOUND = "Conversation not found";
  private static final String ROLE_USER = "user";
  private static final String ROLE_ASSISTANT = "assistant";
  private static final String TEXT_HI = "hi";
  private static final String MSG_ID_1 = "m1";
  private static final String MSG_ID_2 = "m2";
  private static final String METADATA = "metadata";
  private static final String TOO_LONG_TEXT = "x".repeat(256);

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBProvider> mockedOBProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private MockedStatic<RequestUtils> mockedRequestUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;

  private OBDal obDal;
  private OBProvider obProvider;
  private User currentUser;
  private User otherUser;
  private Conversation newConversation;
  private Conversation storedConversation;
  private OBCriteria<Conversation> convCrit;
  private boolean projectionApplied;
  private final List<Message> savedMessages = new ArrayList<>();
  private Long currentMaxLineNo;

  /**
   * Installs the static mocks (DAL, context, provider, message utils) and the default stored
   * conversation owned by the current user.
   */
  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    obDal = mock(OBDal.class);
    obProvider = mock(OBProvider.class);
    currentUser = mock(User.class);
    otherUser = mock(User.class);
    when(currentUser.getId()).thenReturn(CURRENT_USER);
    when(otherUser.getId()).thenReturn(OTHER_USER);
    OBContext obContext = mock(OBContext.class);
    when(obContext.getUser()).thenReturn(currentUser);
    when(obContext.getCurrentClient()).thenReturn(mock(Client.class));
    when(obContext.getCurrentOrganization()).thenReturn(mock(Organization.class));

    mockedOBDal = mockStatic(OBDal.class);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    mockedOBContext.when(OBContext::setAdminMode).thenAnswer(i -> null);
    mockedOBContext.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
    mockedOBProvider = mockStatic(OBProvider.class);
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(obProvider);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ConversationRequired"))
        .thenReturn(ERR_CONVERSATION_REQUIRED);
    mockedRequestUtils = mockStatic(RequestUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);

    newConversation = mock(Conversation.class);
    when(obProvider.get(Conversation.class)).thenReturn(newConversation);
    when(obProvider.get(Message.class)).thenAnswer(i -> {
      Message m = mock(Message.class);
      savedMessages.add(m);
      return m;
    });

    // Conversation lookups: by primary key (miss) then by external id via criteria.
    convCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Conversation.class)).thenReturn(convCrit);
    when(convCrit.add(any())).thenReturn(convCrit);
    when(convCrit.setMaxResults(1)).thenReturn(convCrit);

    storedConversation = mock(Conversation.class);
    when(storedConversation.getUserContact()).thenReturn(currentUser);
    when(storedConversation.getExternalID()).thenReturn(CONV_EXT_ID);
    when(storedConversation.isActive()).thenReturn(true);
  }

  /**
   * Releases every static mock opened in {@link #setUp()}.
   */
  @After
  public void tearDown() {
    mockedOBDal.close();
    mockedOBContext.close();
    mockedOBProvider.close();
    mockedOBMessageUtils.close();
    mockedRequestUtils.close();
    mockedCopilotUtils.close();
  }

  /**
   * Stubs the message lookups: the duplicate check (uniqueResult) and the next line number
   * (projection).
   *
   * @param alreadyStored
   *     whether the duplicate check finds an already stored message
   */
  @SuppressWarnings("unchecked")
  private void stubMessageCriteria(boolean alreadyStored) {
    OBCriteria<Message> msgCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Message.class)).thenReturn(msgCrit);
    when(msgCrit.add(any())).thenReturn(msgCrit);
    when(msgCrit.setMaxResults(1)).thenReturn(msgCrit);
    // Real criteria are mutated in place: after setProjection(max) the same object yields the max line.
    when(msgCrit.setProjection(any())).thenAnswer(i -> {
      projectionApplied = true;
      return msgCrit;
    });
    when(msgCrit.uniqueResult()).thenAnswer(i -> {
      if (projectionApplied) {
        projectionApplied = false;
        return currentMaxLineNo;
      }
      return alreadyStored ? mock(Message.class) : null;
    });
  }

  private void storedConversationFoundByExternalId() {
    when(convCrit.uniqueResult()).thenReturn(storedConversation);
  }

  private JSONObject message(String role, String text, String externalId) throws JSONException {
    JSONObject m = new JSONObject().put("role", role).put("text", text);
    if (externalId != null) {
      m.put(EXTERNAL_ID, externalId);
    }
    return m;
  }

  private JSONObject appendBody(JSONObject... msgs) throws JSONException {
    JSONArray arr = new JSONArray();
    for (JSONObject m : msgs) {
      arr.put(m);
    }
    return new JSONObject().put(CONVERSATION_ID, CONV_EXT_ID).put(MESSAGES, arr);
  }

  /** A call under test that may throw the checked exception of the JSON API. */
  @FunctionalInterface
  private interface Call {
    void run() throws JSONException;
  }

  private void assertRejected(Call call, String expectedFragment) throws JSONException {
    try {
      call.run();
      fail("Expected OBException containing: " + expectedFragment);
    } catch (OBException e) {
      assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
    }
  }

  private void assertAppendRejected(JSONObject body, String expectedFragment) throws JSONException {
    assertRejected(() -> ConversationWriteUtils.appendMessages(body), expectedFragment);
  }

  // ---- create ----

  /**
   * Without an external id one is generated; the title, the app and the current user are stored.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void createGeneratesExternalIdAndStoresTitleAndApp() throws JSONException {
    CopilotApp app = mock(CopilotApp.class);
    mockedCopilotUtils.when(() -> CopilotUtils.getAssistantByIDOrName("appX")).thenReturn(app);

    JSONObject result = ConversationWriteUtils.createConversation(
        new JSONObject().put("title", "Hello").put("app_id", "appX"));

    assertTrue(result.getBoolean("success"));
    assertTrue(result.getBoolean("created"));
    assertFalse(result.getString(CONVERSATION_ID).isEmpty());
    verify(newConversation).setExternalID(result.getString(CONVERSATION_ID));
    verify(newConversation).setCopilotApp(app);
    verify(newConversation).setUserContact(currentUser);
    verify(newConversation).setTitle("Hello");
    verify(newConversation).setLastMsg(any());
  }

  /**
   * A client supplied external id is trimmed and honoured, and a conversation needs no app.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void createHonoursClientSuppliedExternalIdAndAllowsNoApp() throws JSONException {
    JSONObject result = ConversationWriteUtils.createConversation(new JSONObject().put(EXTERNAL_ID, "  my-id "));

    assertEquals("my-id", result.getString(CONVERSATION_ID));
    verify(newConversation).setExternalID("my-id");
    verify(newConversation).setCopilotApp(null);
    verify(newConversation, never()).setTitle(anyString());
  }

  /**
   * Creating with an external id that already belongs to the caller is idempotent.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void createIsIdempotentForTheOwner() throws JSONException {
    storedConversationFoundByExternalId();

    JSONObject result = ConversationWriteUtils.createConversation(new JSONObject().put(EXTERNAL_ID, CONV_EXT_ID));

    assertFalse(result.getBoolean("created"));
    assertEquals(CONV_EXT_ID, result.getString(CONVERSATION_ID));
    verify(obProvider, never()).get(Conversation.class);
  }

  /**
   * An external id owned by another user is rejected and nothing is created.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void createRejectsExternalIdOwnedBySomeoneElse() throws JSONException {
    when(storedConversation.getUserContact()).thenReturn(otherUser);
    storedConversationFoundByExternalId();

    assertRejected(() -> ConversationWriteUtils.createConversation(new JSONObject().put(EXTERNAL_ID, CONV_EXT_ID)),
        "not available");
    verify(obProvider, never()).get(Conversation.class);
  }

  /**
   * Titles and external ids over 255 characters are rejected.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void createRejectsTooLongTitleAndExternalId() throws JSONException {
    assertRejected(() -> ConversationWriteUtils.createConversation(new JSONObject().put("title", TOO_LONG_TEXT)),
        "Title is too long");
    assertRejected(() -> ConversationWriteUtils.createConversation(new JSONObject().put(EXTERNAL_ID, TOO_LONG_TEXT)),
        "external_id is too long");
  }

  // ---- append ----

  /**
   * Messages are stored in order, ten line numbers apart after the last one, and the conversation
   * last-message date is refreshed.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendStoresMessagesInOrderWithLineNumbersAndUpdatesLastMsg() throws JSONException {
    storedConversationFoundByExternalId();
    stubMessageCriteria(false);
    currentMaxLineNo = 20L;

    JSONObject result = ConversationWriteUtils.appendMessages(appendBody(
        message(ROLE_USER, TEXT_HI, MSG_ID_1), message("ASSISTANT", "hello", MSG_ID_2)));

    assertEquals(2, result.getInt(SAVED));
    assertEquals(0, result.getInt(SKIPPED));
    assertEquals(CONV_EXT_ID, result.getString(CONVERSATION_ID));
    assertEquals(2, savedMessages.size());
    verify(savedMessages.get(0)).setRole("USER");
    verify(savedMessages.get(0)).setLineno(30L);
    verify(savedMessages.get(0)).setExternalID(MSG_ID_1);
    verify(savedMessages.get(1)).setRole("ASSISTANT");
    verify(savedMessages.get(1)).setLineno(40L);
    verify(savedMessages.get(1)).setConversation(storedConversation);
    verify(storedConversation).setLastMsg(any());
    verify(obDal).flush();
  }

  /**
   * On an empty conversation the first message gets line 10, and metadata is stored as JSON.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendStartsAtTenOnAnEmptyConversationAndStoresMetadata() throws JSONException {
    storedConversationFoundByExternalId();
    stubMessageCriteria(false);
    currentMaxLineNo = null;
    JSONObject withMeta = message(ROLE_ASSISTANT, "answer", null).put(METADATA, new JSONObject().put("k", "v"));

    ConversationWriteUtils.appendMessages(appendBody(withMeta));

    verify(savedMessages.get(0)).setLineno(10L);
    verify(savedMessages.get(0)).setMetadata("{\"k\":\"v\"}");
    verify(savedMessages.get(0)).setExternalID(null);
  }

  /**
   * Messages whose external id is already stored are skipped and reported as duplicates.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendIsIdempotentOnMessageExternalId() throws JSONException {
    storedConversationFoundByExternalId();
    stubMessageCriteria(true);

    JSONObject result = ConversationWriteUtils.appendMessages(appendBody(
        message(ROLE_USER, TEXT_HI, MSG_ID_1), message(ROLE_ASSISTANT, "hello", MSG_ID_2)));

    assertEquals(0, result.getInt(SAVED));
    assertEquals(2, result.getInt(SKIPPED));
    assertTrue(result.getJSONArray(MESSAGES).getJSONObject(0).getBoolean("duplicate"));
    assertTrue(savedMessages.isEmpty());
    verify(storedConversation, never()).setLastMsg(any());
  }

  /**
   * A repeated external id inside the same batch is stored once.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendSkipsDuplicateExternalIdsInsideTheSameBatch() throws JSONException {
    storedConversationFoundByExternalId();
    stubMessageCriteria(false);

    JSONObject result = ConversationWriteUtils.appendMessages(appendBody(
        message(ROLE_USER, TEXT_HI, "same"), message(ROLE_USER, "hi again", "same")));

    assertEquals(1, result.getInt(SAVED));
    assertEquals(1, result.getInt(SKIPPED));
  }

  /**
   * Somebody else's conversation is reported exactly like a missing one and nothing is stored.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRejectsConversationOwnedBySomeoneElseAsNotFound() throws JSONException {
    when(storedConversation.getUserContact()).thenReturn(otherUser);
    storedConversationFoundByExternalId();

    assertAppendRejected(appendBody(message(ROLE_USER, TEXT_HI, null)), ERR_NOT_FOUND);
    assertTrue(savedMessages.isEmpty());
  }

  /**
   * A conversation that does not exist is rejected as not found.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRejectsMissingConversation() throws JSONException {
    when(convCrit.uniqueResult()).thenReturn(null);

    assertAppendRejected(appendBody(message(ROLE_USER, TEXT_HI, null)), ERR_NOT_FOUND);
  }

  /**
   * One invalid role rejects the whole batch before anything is saved.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRejectsInvalidRoleWithoutSavingAnything() throws JSONException {
    storedConversationFoundByExternalId();

    assertAppendRejected(appendBody(message(ROLE_USER, "ok", null), message("system", "no", null)), "Invalid role");
    assertTrue("a bad message must not leave a half-saved turn", savedMessages.isEmpty());
  }

  /**
   * A blank message text is rejected.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRejectsBlankText() throws JSONException {
    storedConversationFoundByExternalId();

    assertAppendRejected(appendBody(message(ROLE_USER, "   ", null)), ".text is required");
  }

  /**
   * An empty messages array is rejected.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRejectsEmptyBatch() throws JSONException {
    storedConversationFoundByExternalId();

    assertAppendRejected(appendBody(), "messages is required");
  }

  /**
   * More than 100 messages in one call are rejected.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRejectsOversizeBatch() throws JSONException {
    storedConversationFoundByExternalId();
    JSONObject[] many = new JSONObject[101];
    for (int i = 0; i < many.length; i++) {
      many[i] = message(ROLE_USER, "m" + i, null);
    }

    assertAppendRejected(appendBody(many), "Too many messages");
  }

  /**
   * Messages cannot be appended to an archived conversation.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRejectsArchivedConversation() throws JSONException {
    storedConversationFoundByExternalId();
    when(storedConversation.isActive()).thenReturn(false);

    assertAppendRejected(appendBody(message(ROLE_USER, TEXT_HI, null)), "archived");
  }

  /**
   * The conversation id is mandatory.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRequiresConversationId() throws JSONException {
    assertAppendRejected(new JSONObject().put(MESSAGES, new JSONArray()), ERR_CONVERSATION_REQUIRED);
  }

  /**
   * Message metadata must be a JSON object.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void appendRequiresObjectMetadata() throws JSONException {
    storedConversationFoundByExternalId();
    JSONObject badMeta = message(ROLE_USER, TEXT_HI, null).put(METADATA, "not-an-object");

    assertAppendRejected(appendBody(badMeta), "metadata must be a JSON object");
  }

  // ---- owner-checked operations (used by the Etendo Go agent-chat servlet) ----

  /**
   * Rename, archive, restore and delete act on the owner's conversation.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void ownedOperationsActOnTheOwnersConversation() throws JSONException {
    storedConversationFoundByExternalId();

    ConversationWriteUtils.renameOwnedConversation(CONV_EXT_ID, "New title");
    verify(storedConversation).setTitle("New title");

    ConversationWriteUtils.setOwnedConversationActive(CONV_EXT_ID, false);
    verify(storedConversation).setActive(false);
    ConversationWriteUtils.setOwnedConversationActive(CONV_EXT_ID, true);
    verify(storedConversation).setActive(true);

    Message stored = mock(Message.class);
    when(storedConversation.getETCOPMessageList()).thenReturn(new ArrayList<>(List.of(stored)));
    ConversationWriteUtils.deleteOwnedConversation(CONV_EXT_ID);
    verify(obDal).remove(stored);
    verify(obDal).remove(storedConversation);
  }

  /**
   * Somebody else's conversation is treated as missing by every owned operation and nothing changes.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void ownedOperationsTreatSomeoneElsesConversationAsMissingAndChangeNothing() throws JSONException {
    when(storedConversation.getUserContact()).thenReturn(otherUser);
    storedConversationFoundByExternalId();

    assertRejected(() -> ConversationWriteUtils.getOwnedConversationMessages(CONV_EXT_ID), ERR_NOT_FOUND);
    assertRejected(() -> ConversationWriteUtils.renameOwnedConversation(CONV_EXT_ID, "x"), ERR_NOT_FOUND);
    assertRejected(() -> ConversationWriteUtils.setOwnedConversationActive(CONV_EXT_ID, false), ERR_NOT_FOUND);
    assertRejected(() -> ConversationWriteUtils.setOwnedConversationActive(CONV_EXT_ID, true), ERR_NOT_FOUND);
    assertRejected(() -> ConversationWriteUtils.deleteOwnedConversation(CONV_EXT_ID), ERR_NOT_FOUND);
    verify(storedConversation, never()).setTitle(anyString());
    verify(storedConversation, never()).setActive(org.mockito.ArgumentMatchers.anyBoolean());
    verify(obDal, never()).remove(any());
  }

  /**
   * A missing conversation, a blank id and invalid titles are rejected.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void ownedOperationsRejectMissingConversationBlankIdAndBadTitles() throws JSONException {
    when(convCrit.uniqueResult()).thenReturn(null);

    assertRejected(() -> ConversationWriteUtils.deleteOwnedConversation("nope"), ERR_NOT_FOUND);
    assertRejected(() -> ConversationWriteUtils.deleteOwnedConversation(""), ERR_CONVERSATION_REQUIRED);
    assertRejected(() -> ConversationWriteUtils.renameOwnedConversation(CONV_EXT_ID, "  "), "Title is required");
    assertRejected(() -> ConversationWriteUtils.renameOwnedConversation(CONV_EXT_ID, TOO_LONG_TEXT), "too long");
  }

  /**
   * Once ownership is proven the messages are read by primary key.
   *
   * @throws JSONException if the test fails
   */
  @Test
  public void ownedMessagesAreReadByPrimaryKeyOnceOwnershipIsProven() throws JSONException {
    storedConversationFoundByExternalId();
    when(storedConversation.getId()).thenReturn(CONV_ID);
    when(obDal.get(Conversation.class, CONV_ID)).thenReturn(storedConversation);
    when(storedConversation.getETCOPMessageList()).thenReturn(new ArrayList<>());

    JSONArray result = ConversationWriteUtils.getOwnedConversationMessages(CONV_EXT_ID);

    assertEquals(0, result.length());
  }

  // ---- HTTP handlers ----

  /**
   * The HTTP handlers write the JSON result on success and send a 400 with the message on rejection.
   *
   * @throws IOException if a handler fails to write the response
   * @throws JSONException if the test fails
   */
  @Test
  public void handlersWriteJsonOnSuccessAndSendErrorOnRejection() throws IOException, JSONException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter out = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(out));

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(request))
        .thenReturn(new JSONObject().put(EXTERNAL_ID, "abc"));
    ConversationUtils.handleCreateConversation(request, response);
    assertTrue(new JSONObject(out.toString()).getBoolean("success"));

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(request))
        .thenReturn(new JSONObject().put(CONVERSATION_ID, "missing").put(MESSAGES,
            new JSONArray().put(message(ROLE_USER, TEXT_HI, null))));
    when(convCrit.uniqueResult()).thenReturn(null);
    ConversationUtils.handleAppendMessages(request, response);
    ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
    verify(response, times(1)).sendError(org.mockito.ArgumentMatchers.eq(HttpServletResponse.SC_BAD_REQUEST),
        msg.capture());
    assertNotNull(msg.getValue());
    assertEquals(ERR_NOT_FOUND, msg.getValue());
  }
}
