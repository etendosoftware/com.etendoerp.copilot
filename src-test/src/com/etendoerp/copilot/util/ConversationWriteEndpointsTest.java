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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
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
 * {@link ConversationUtils#handleCreateConversation} and {@link ConversationUtils#handleAppendMessages}.
 */
public class ConversationWriteEndpointsTest {

  private static final String CURRENT_USER = "currentUser";
  private static final String OTHER_USER = "otherUser";
  private static final String CONV_EXT_ID = "conv-ext-1";
  private static final String CONV_ID = "convPk1";
  private static final String MESSAGES = "messages";

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
  private OBCriteria<Message> msgCrit;
  private boolean projectionApplied;
  private final List<Message> savedMessages = new ArrayList<>();
  private boolean messageAlreadyStored;
  private Long currentMaxLineNo;

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
        .thenReturn("Conversation ID is required");
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

    // Message lookups: duplicate check (uniqueResult) and next line number (projection).
    msgCrit = mock(OBCriteria.class);
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
      return messageAlreadyStored ? mock(Message.class) : null;
    });

    storedConversation = mock(Conversation.class);
    when(storedConversation.getUserContact()).thenReturn(currentUser);
    when(storedConversation.getExternalID()).thenReturn(CONV_EXT_ID);
    when(storedConversation.isActive()).thenReturn(true);
  }

  @After
  public void tearDown() {
    mockedOBDal.close();
    mockedOBContext.close();
    mockedOBProvider.close();
    mockedOBMessageUtils.close();
    mockedRequestUtils.close();
    mockedCopilotUtils.close();
  }

  private void storedConversationFoundByExternalId() {
    when(convCrit.uniqueResult()).thenReturn(storedConversation);
  }

  private JSONObject message(String role, String text, String externalId) throws Exception {
    JSONObject m = new JSONObject().put("role", role).put("text", text);
    if (externalId != null) {
      m.put("external_id", externalId);
    }
    return m;
  }

  private JSONObject appendBody(JSONObject... msgs) throws Exception {
    JSONArray arr = new JSONArray();
    for (JSONObject m : msgs) {
      arr.put(m);
    }
    return new JSONObject().put("conversation_id", CONV_EXT_ID).put(MESSAGES, arr);
  }

  private void assertRejected(Runnable call, String expectedFragment) {
    try {
      call.run();
      fail("Expected OBException containing: " + expectedFragment);
    } catch (OBException e) {
      assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
    }
  }

  // ---- create ----

  @Test
  public void createGeneratesExternalIdAndStoresTitleAndApp() throws Exception {
    CopilotApp app = mock(CopilotApp.class);
    mockedCopilotUtils.when(() -> CopilotUtils.getAssistantByIDOrName("appX")).thenReturn(app);

    JSONObject result = ConversationUtils.createConversation(
        new JSONObject().put("title", "Hello").put("app_id", "appX"));

    assertTrue(result.getBoolean("success"));
    assertTrue(result.getBoolean("created"));
    assertFalse(result.getString("conversation_id").isEmpty());
    verify(newConversation).setExternalID(result.getString("conversation_id"));
    verify(newConversation).setCopilotApp(app);
    verify(newConversation).setUserContact(currentUser);
    verify(newConversation).setTitle("Hello");
    verify(newConversation).setLastMsg(any());
  }

  @Test
  public void createHonoursClientSuppliedExternalIdAndAllowsNoApp() throws Exception {
    JSONObject result = ConversationUtils.createConversation(new JSONObject().put("external_id", "  my-id "));

    assertEquals("my-id", result.getString("conversation_id"));
    verify(newConversation).setExternalID("my-id");
    verify(newConversation).setCopilotApp(null);
    verify(newConversation, never()).setTitle(anyString());
  }

  @Test
  public void createIsIdempotentForTheOwner() throws Exception {
    storedConversationFoundByExternalId();

    JSONObject result = ConversationUtils.createConversation(new JSONObject().put("external_id", CONV_EXT_ID));

    assertFalse(result.getBoolean("created"));
    assertEquals(CONV_EXT_ID, result.getString("conversation_id"));
    verify(obProvider, never()).get(Conversation.class);
  }

  @Test
  public void createRejectsExternalIdOwnedBySomeoneElse() {
    when(storedConversation.getUserContact()).thenReturn(otherUser);
    storedConversationFoundByExternalId();

    assertRejected(() -> {
      try {
        ConversationUtils.createConversation(new JSONObject().put("external_id", CONV_EXT_ID));
      } catch (org.codehaus.jettison.json.JSONException e) {
        throw new IllegalStateException(e);
      }
    }, "not available");
    verify(obProvider, never()).get(Conversation.class);
  }

  @Test
  public void createRejectsTooLongTitleAndExternalId() {
    String tooLong = "x".repeat(256);
    assertRejected(() -> {
      try {
        ConversationUtils.createConversation(new JSONObject().put("title", tooLong));
      } catch (org.codehaus.jettison.json.JSONException e) {
        throw new IllegalStateException(e);
      }
    }, "Title is too long");
    assertRejected(() -> {
      try {
        ConversationUtils.createConversation(new JSONObject().put("external_id", tooLong));
      } catch (org.codehaus.jettison.json.JSONException e) {
        throw new IllegalStateException(e);
      }
    }, "external_id is too long");
  }

  // ---- append ----

  @Test
  public void appendStoresMessagesInOrderWithLineNumbersAndUpdatesLastMsg() throws Exception {
    storedConversationFoundByExternalId();
    currentMaxLineNo = 20L;

    JSONObject result = ConversationUtils.appendMessages(appendBody(
        message("user", "hi", "m1"), message("ASSISTANT", "hello", "m2")));

    assertEquals(2, result.getInt("saved"));
    assertEquals(0, result.getInt("skipped"));
    assertEquals(CONV_EXT_ID, result.getString("conversation_id"));
    assertEquals(2, savedMessages.size());
    verify(savedMessages.get(0)).setRole("USER");
    verify(savedMessages.get(0)).setLineno(30L);
    verify(savedMessages.get(0)).setExternalID("m1");
    verify(savedMessages.get(1)).setRole("ASSISTANT");
    verify(savedMessages.get(1)).setLineno(40L);
    verify(savedMessages.get(1)).setConversation(storedConversation);
    verify(storedConversation).setLastMsg(any());
    verify(obDal).flush();
  }

  @Test
  public void appendStartsAtTenOnAnEmptyConversationAndStoresMetadata() throws Exception {
    storedConversationFoundByExternalId();
    currentMaxLineNo = null;
    JSONObject withMeta = message("assistant", "answer", null).put("metadata", new JSONObject().put("k", "v"));

    ConversationUtils.appendMessages(appendBody(withMeta));

    verify(savedMessages.get(0)).setLineno(10L);
    verify(savedMessages.get(0)).setMetadata("{\"k\":\"v\"}");
    verify(savedMessages.get(0)).setExternalID(null);
  }

  @Test
  public void appendIsIdempotentOnMessageExternalId() throws Exception {
    storedConversationFoundByExternalId();
    messageAlreadyStored = true;

    JSONObject result = ConversationUtils.appendMessages(appendBody(
        message("user", "hi", "m1"), message("assistant", "hello", "m2")));

    assertEquals(0, result.getInt("saved"));
    assertEquals(2, result.getInt("skipped"));
    assertTrue(result.getJSONArray(MESSAGES).getJSONObject(0).getBoolean("duplicate"));
    assertTrue(savedMessages.isEmpty());
    verify(storedConversation, never()).setLastMsg(any());
  }

  @Test
  public void appendSkipsDuplicateExternalIdsInsideTheSameBatch() throws Exception {
    storedConversationFoundByExternalId();

    JSONObject result = ConversationUtils.appendMessages(appendBody(
        message("user", "hi", "same"), message("user", "hi again", "same")));

    assertEquals(1, result.getInt("saved"));
    assertEquals(1, result.getInt("skipped"));
  }

  @Test
  public void appendRejectsConversationOwnedBySomeoneElseAsNotFound() throws Exception {
    when(storedConversation.getUserContact()).thenReturn(otherUser);
    storedConversationFoundByExternalId();

    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(appendBody(message("user", "hi", null)));
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, "Conversation not found");
    assertTrue(savedMessages.isEmpty());
  }

  @Test
  public void appendRejectsMissingConversation() {
    when(convCrit.uniqueResult()).thenReturn(null);

    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(appendBody(message("user", "hi", null)));
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, "Conversation not found");
  }

  @Test
  public void appendRejectsInvalidRoleWithoutSavingAnything() throws Exception {
    storedConversationFoundByExternalId();

    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(appendBody(message("user", "ok", null), message("system", "no", null)));
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, "Invalid role");
    assertTrue("a bad message must not leave a half-saved turn", savedMessages.isEmpty());
  }

  @Test
  public void appendRejectsBlankTextEmptyBatchOversizeBatchAndArchivedConversation() throws Exception {
    storedConversationFoundByExternalId();
    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(appendBody(message("user", "   ", null)));
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, ".text is required");
    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(appendBody());
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, "messages is required");
    JSONObject[] many = new JSONObject[101];
    for (int i = 0; i < many.length; i++) {
      many[i] = message("user", "m" + i, null);
    }
    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(appendBody(many));
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, "Too many messages");
    when(storedConversation.isActive()).thenReturn(false);
    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(appendBody(message("user", "hi", null)));
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, "archived");
  }

  @Test
  public void appendRequiresConversationIdAndValidObjectMetadata() throws Exception {
    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(new JSONObject().put(MESSAGES, new JSONArray()));
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, "Conversation ID is required");
    storedConversationFoundByExternalId();
    JSONObject badMeta = message("user", "hi", null).put("metadata", "not-an-object");
    assertRejected(() -> {
      try {
        ConversationUtils.appendMessages(appendBody(badMeta));
      } catch (Exception e) {
        throw e instanceof OBException ? (OBException) e : new IllegalStateException(e);
      }
    }, "metadata must be a JSON object");
  }

  // ---- owner-checked operations (used by the Etendo Go agent-chat servlet) ----

  @Test
  public void ownedOperationsActOnTheOwnersConversation() throws Exception {
    storedConversationFoundByExternalId();

    ConversationUtils.renameOwnedConversation(CONV_EXT_ID, "New title");
    verify(storedConversation).setTitle("New title");

    ConversationUtils.setOwnedConversationActive(CONV_EXT_ID, false);
    verify(storedConversation).setActive(false);
    ConversationUtils.setOwnedConversationActive(CONV_EXT_ID, true);
    verify(storedConversation).setActive(true);

    Message stored = mock(Message.class);
    when(storedConversation.getETCOPMessageList()).thenReturn(new ArrayList<>(List.of(stored)));
    ConversationUtils.deleteOwnedConversation(CONV_EXT_ID);
    verify(obDal).remove(stored);
    verify(obDal).remove(storedConversation);
  }

  @Test
  public void ownedOperationsTreatSomeoneElsesConversationAsMissingAndChangeNothing() throws Exception {
    when(storedConversation.getUserContact()).thenReturn(otherUser);
    storedConversationFoundByExternalId();

    assertRejected(() -> {
      try {
        ConversationUtils.getOwnedConversationMessages(CONV_EXT_ID);
      } catch (org.codehaus.jettison.json.JSONException e) {
        throw new IllegalStateException(e);
      }
    }, "Conversation not found");
    assertRejected(() -> ownedCall(() -> ConversationUtils.renameOwnedConversation(CONV_EXT_ID, "x")), "Conversation not found");
    assertRejected(() -> ownedCall(() -> ConversationUtils.setOwnedConversationActive(CONV_EXT_ID, false)), "Conversation not found");
    assertRejected(() -> ownedCall(() -> ConversationUtils.setOwnedConversationActive(CONV_EXT_ID, true)), "Conversation not found");
    assertRejected(() -> ownedCall(() -> ConversationUtils.deleteOwnedConversation(CONV_EXT_ID)), "Conversation not found");
    verify(storedConversation, never()).setTitle(anyString());
    verify(storedConversation, never()).setActive(org.mockito.ArgumentMatchers.anyBoolean());
    verify(obDal, never()).remove(any());
  }

  @Test
  public void ownedOperationsRejectMissingConversationBlankIdAndBadTitles() {
    when(convCrit.uniqueResult()).thenReturn(null);
    assertRejected(() -> ownedCall(() -> ConversationUtils.deleteOwnedConversation("nope")), "Conversation not found");
    assertRejected(() -> ownedCall(() -> ConversationUtils.deleteOwnedConversation("")), "Conversation ID is required");
    assertRejected(() -> ownedCall(() -> ConversationUtils.renameOwnedConversation(CONV_EXT_ID, "  ")), "Title is required");
    assertRejected(() -> ownedCall(() -> ConversationUtils.renameOwnedConversation(CONV_EXT_ID, "x".repeat(256))), "too long");
  }

  @Test
  public void ownedMessagesAreReadByPrimaryKeyOnceOwnershipIsProven() throws Exception {
    storedConversationFoundByExternalId();
    when(storedConversation.getId()).thenReturn(CONV_ID);
    when(obDal.get(Conversation.class, CONV_ID)).thenReturn(storedConversation);
    when(storedConversation.getETCOPMessageList()).thenReturn(new ArrayList<>());

    JSONArray result = ConversationUtils.getOwnedConversationMessages(CONV_EXT_ID);

    assertEquals(0, result.length());
  }

  @FunctionalInterface
  private interface OwnedCall {
    void run() throws Exception;
  }

  private void ownedCall(OwnedCall call) {
    try {
      call.run();
    } catch (OBException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // ---- HTTP handlers ----

  @Test
  public void handlersWriteJsonOnSuccessAndSendErrorOnRejection() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter out = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(out));

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(request))
        .thenReturn(new JSONObject().put("external_id", "abc"));
    ConversationUtils.handleCreateConversation(request, response);
    assertTrue(new JSONObject(out.toString()).getBoolean("success"));

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(request))
        .thenReturn(new JSONObject().put("conversation_id", "missing").put(MESSAGES,
            new JSONArray().put(message("user", "hi", null))));
    when(convCrit.uniqueResult()).thenReturn(null);
    ConversationUtils.handleAppendMessages(request, response);
    ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
    verify(response, times(1)).sendError(org.mockito.ArgumentMatchers.eq(HttpServletResponse.SC_BAD_REQUEST),
        msg.capture());
    assertNotNull(msg.getValue());
    assertEquals("Conversation not found", msg.getValue());
  }
}
