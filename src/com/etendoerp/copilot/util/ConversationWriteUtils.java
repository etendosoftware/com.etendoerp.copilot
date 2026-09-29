package com.etendoerp.copilot.util;

import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;

/**
 * ConversationWriteUtils
 *
 * <p>Owner-checked write operations on conversations and messages that do not run any agent:
 * creating a conversation, appending messages to it, and the by-owner read, rename, archive and
 * delete operations used by callers that authenticate the user themselves (the Etendo Go
 * agent-chat servlet, which accepts the cookie session that {@code /sws/copilot} does not).
 *
 * <p>Unlike the legacy by-id handlers in {@link ConversationUtils}, EVERY operation here requires
 * the conversation to belong to the current {@link OBContext} user; a foreign or missing
 * conversation is reported identically as {@link ConversationUtils#CONVERSATION_NOT_FOUND}. The
 * methods take plain arguments, run under whatever {@link OBContext} the caller set up, and do not
 * touch the HTTP request or response.
 *
 * <p>This class is not instantiable and exposes only static methods.
 */
public class ConversationWriteUtils {
  private static final String PROP_EXTERNAL_ID = "external_id";
  private static final String PROP_MESSAGES = "messages";
  private static final String PROP_ROLE = "role";
  private static final String PROP_TEXT = "text";
  private static final String PROP_METADATA = "metadata";
  private static final String PROP_DUPLICATE = "duplicate";
  private static final String MESSAGE_PREFIX = "messages[";
  private static final String TITLE_TOO_LONG = "Title is too long (max ";
  private static final int MAX_TITLE_LENGTH = 255;
  private static final int MAX_EXTERNAL_ID_LENGTH = 255;
  private static final int MAX_MESSAGES_PER_CALL = 100;
  private static final long LINE_NO_STEP = 10;

  private ConversationWriteUtils() {
  }

  /**
   * Creates an empty conversation for the current user, without running any agent. Reusing an
   * {@code external_id} that already belongs to the current user is idempotent ({@code created:
   * false}); one that belongs to somebody else is rejected.
   *
   * @param json
   *     the request body: {@code {"title"?: string, "app_id"?: string, "external_id"?: string}}
   * @return {@code {"success": true, "conversation_id": "<external id>", "created": true|false}}
   * @throws JSONException
   *     if the response cannot be built
   * @throws OBException
   *     if the title or external id is too long, or the external id belongs to another user
   */
  public static JSONObject createConversation(JSONObject json) throws JSONException {
    String title = optText(json, ConversationUtils.PROP_TITLE);
    if (title != null && title.length() > MAX_TITLE_LENGTH) {
      throw new OBException(TITLE_TOO_LONG + MAX_TITLE_LENGTH + ")");
    }
    String externalId = StringUtils.trimToNull(optText(json, PROP_EXTERNAL_ID));
    if (externalId != null && externalId.length() > MAX_EXTERNAL_ID_LENGTH) {
      throw new OBException("external_id is too long (max " + MAX_EXTERNAL_ID_LENGTH + ")");
    }
    String appId = StringUtils.trimToNull(optText(json, CopilotConstants.PROP_APP_ID));
    CopilotApp app = appId == null ? null : CopilotUtils.getAssistantByIDOrName(appId);

    boolean created = true;
    Conversation conversation = null;
    if (externalId == null) {
      externalId = UUID.randomUUID().toString();
    } else {
      conversation = findByExternalId(externalId);
    }
    if (conversation != null) {
      // external_id is globally unique: reusing one is only allowed for its owner.
      if (!isOwnedByCurrentUser(conversation)) {
        throw new OBException("external_id is not available");
      }
      created = false;
    } else {
      conversation = TrackingUtil.newConversation(externalId, app);
      if (StringUtils.isNotBlank(title)) {
        conversation.setTitle(title);
      }
      conversation.setLastMsg(new Date());
      OBDal.getInstance().save(conversation);
      OBDal.getInstance().flush();
    }
    return new JSONObject().put(ConversationUtils.PROP_SUCCESS, true)
        .put(CopilotConstants.PROP_CONVERSATION_ID, externalId)
        .put("created", created);
  }

  /**
   * Appends messages to a conversation owned by the current user, without running any agent.
   * Messages are stored in array order after the existing ones. A message whose
   * {@code external_id} is already stored in the conversation is skipped, so a retried call does
   * not duplicate it. Everything is validated before anything is written.
   *
   * @param json
   *     the request body: {@code {"conversation_id": string, "messages": [{"role":
   *     "user"|"assistant", "text": string, "metadata"?: object, "external_id"?: string}]}}
   * @return {@code {"success", "conversation_id", "saved", "skipped", "messages": [...]}} with one
   *     result per submitted message
   * @throws JSONException
   *     if the request or the response cannot be processed as JSON
   * @throws OBException
   *     if the request is invalid or the conversation is missing, foreign or archived
   */
  public static JSONObject appendMessages(JSONObject json) throws JSONException {
    String conversationId = optText(json, CopilotConstants.PROP_CONVERSATION_ID);
    if (StringUtils.isEmpty(conversationId)) {
      ConversationUtils.throwConversationIDRequired();
    }
    JSONArray messages = requireMessages(json);
    Conversation conversation = requireOwnedConversation(conversationId);
    if (!conversation.isActive()) {
      throw new OBException("Conversation is archived");
    }
    // Validate everything before writing anything: a bad message must not leave a half-saved turn.
    for (int i = 0; i < messages.length(); i++) {
      validateMessage(messages.optJSONObject(i), i);
    }
    return storeMessages(conversation, messages);
  }

  /**
   * Looks a conversation up by id or external id, including archived ones, and requires it to be
   * owned by the current user.
   *
   * @param conversationId
   *     the conversation primary key or external id
   * @return the conversation, never null
   * @throws OBException
   *     {@link ConversationUtils#CONVERSATION_NOT_FOUND} when it does not exist or belongs to
   *     another user
   */
  public static Conversation requireOwnedConversation(String conversationId) {
    if (StringUtils.isEmpty(conversationId)) {
      ConversationUtils.throwConversationIDRequired();
    }
    Conversation conversation = ConversationUtils.getConversationByIDorExtRef(conversationId, true);
    if (conversation == null || !isOwnedByCurrentUser(conversation)) {
      throw new OBException(ConversationUtils.CONVERSATION_NOT_FOUND);
    }
    return conversation;
  }

  /**
   * Returns the messages of an owned conversation, oldest line first.
   *
   * @param conversationId
   *     the conversation primary key or external id
   * @return the messages as {@code {id, role, content, timestamp}} objects
   * @throws JSONException
   *     if a message cannot be converted to JSON
   * @throws OBException
   *     if the conversation is missing or belongs to another user
   */
  public static JSONArray getOwnedConversationMessages(String conversationId) throws JSONException {
    Conversation conversation = requireOwnedConversation(conversationId);
    return ConversationUtils.getConversationMessages(conversation.getId());
  }

  /**
   * Renames an owned conversation.
   *
   * @param conversationId
   *     the conversation primary key or external id
   * @param title
   *     the new title, not blank and at most 255 characters
   * @return {@code {"success": true, "title": <title>}}
   * @throws JSONException
   *     if the response cannot be built
   * @throws OBException
   *     if the title is invalid or the conversation is missing or belongs to another user
   */
  public static JSONObject renameOwnedConversation(String conversationId, String title) throws JSONException {
    if (StringUtils.isBlank(title)) {
      throw new OBException("Title is required");
    }
    if (title.length() > MAX_TITLE_LENGTH) {
      throw new OBException(TITLE_TOO_LONG + MAX_TITLE_LENGTH + ")");
    }
    Conversation conversation = requireOwnedConversation(conversationId);
    conversation.setTitle(title);
    OBDal.getInstance().save(conversation);
    OBDal.getInstance().flush();
    return new JSONObject().put(ConversationUtils.PROP_SUCCESS, true).put(ConversationUtils.PROP_TITLE, title);
  }

  /**
   * Archives ({@code active=false}) or restores ({@code active=true}) an owned conversation.
   *
   * @param conversationId
   *     the conversation primary key or external id
   * @param active
   *     {@code false} to archive the conversation, {@code true} to restore it
   * @return {@code {"success": true}}
   * @throws JSONException
   *     if the response cannot be built
   * @throws OBException
   *     if the conversation is missing or belongs to another user
   */
  public static JSONObject setOwnedConversationActive(String conversationId, boolean active) throws JSONException {
    Conversation conversation = requireOwnedConversation(conversationId);
    conversation.setActive(active);
    OBDal.getInstance().save(conversation);
    OBDal.getInstance().flush();
    return new JSONObject().put(ConversationUtils.PROP_SUCCESS, true);
  }

  /**
   * Permanently deletes an owned conversation and all its messages.
   *
   * @param conversationId
   *     the conversation primary key or external id
   * @return {@code {"success": true}}
   * @throws JSONException
   *     if the response cannot be built
   * @throws OBException
   *     if the conversation is missing or belongs to another user
   */
  public static JSONObject deleteOwnedConversation(String conversationId) throws JSONException {
    ConversationUtils.removeConversationWithMessages(requireOwnedConversation(conversationId));
    return new JSONObject().put(ConversationUtils.PROP_SUCCESS, true);
  }

  /** Reads and bounds-checks the {@code messages} array of an append request. */
  private static JSONArray requireMessages(JSONObject json) {
    JSONArray messages = json.optJSONArray(PROP_MESSAGES);
    if (messages == null || messages.length() == 0) {
      throw new OBException("messages is required and must not be empty");
    }
    if (messages.length() > MAX_MESSAGES_PER_CALL) {
      throw new OBException("Too many messages (max " + MAX_MESSAGES_PER_CALL + " per call)");
    }
    return messages;
  }

  /** Persists the already validated messages after the existing ones and builds the response. */
  private static JSONObject storeMessages(Conversation conversation, JSONArray messages) throws JSONException {
    long lineNo = TrackingUtil.nextLineNo(conversation);
    Set<String> seenInBatch = new HashSet<>();
    JSONArray results = new JSONArray();
    int saved = 0;
    for (int i = 0; i < messages.length(); i++) {
      JSONObject item = messages.getJSONObject(i);
      String extId = StringUtils.trimToNull(optText(item, PROP_EXTERNAL_ID));
      JSONObject result = new JSONObject();
      if (extId != null) {
        result.put(PROP_EXTERNAL_ID, extId);
      }
      if (isDuplicate(conversation, extId, seenInBatch)) {
        results.put(result.put(PROP_DUPLICATE, true));
        continue;
      }
      saveMessage(conversation, item, extId, lineNo);
      results.put(result.put("lineno", lineNo).put(PROP_DUPLICATE, false));
      lineNo += LINE_NO_STEP;
      saved++;
    }
    if (saved > 0) {
      conversation.setLastMsg(new Date());
      OBDal.getInstance().save(conversation);
    }
    OBDal.getInstance().flush();
    return new JSONObject().put(ConversationUtils.PROP_SUCCESS, true)
        .put(CopilotConstants.PROP_CONVERSATION_ID, conversation.getExternalID())
        .put("saved", saved)
        .put("skipped", messages.length() - saved)
        .put(PROP_MESSAGES, results);
  }

  /** Whether the external id was already seen in this batch or is already stored in the conversation. */
  private static boolean isDuplicate(Conversation conversation, String extId, Set<String> seenInBatch) {
    return extId != null && (!seenInBatch.add(extId) || messageExists(conversation, extId));
  }

  private static void saveMessage(Conversation conversation, JSONObject item, String extId,
      long lineNo) throws JSONException {
    Message message = OBProvider.getInstance().get(Message.class);
    message.setClient(conversation.getClient());
    message.setOrganization(conversation.getOrganization());
    message.setConversation(conversation);
    message.setRole(roleOf(item));
    message.setMessage(item.getString(PROP_TEXT));
    JSONObject metadata = item.optJSONObject(PROP_METADATA);
    message.setMetadata(metadata != null ? metadata.toString() : null);
    message.setExternalID(extId);
    message.setLineno(lineNo);
    OBDal.getInstance().save(message);
  }

  /** String value of a key, or null when absent or JSON null (jettison would return "null"). */
  private static String optText(JSONObject json, String key) {
    return json.has(key) && !json.isNull(key) ? json.optString(key, null) : null;
  }

  private static void validateMessage(JSONObject item, int index) {
    if (item == null) {
      throw new OBException(MESSAGE_PREFIX + index + "] must be an object");
    }
    roleOf(item);
    if (StringUtils.isBlank(optText(item, PROP_TEXT))) {
      throw new OBException(MESSAGE_PREFIX + index + "].text is required");
    }
    String extId = optText(item, PROP_EXTERNAL_ID);
    if (extId != null && extId.length() > MAX_EXTERNAL_ID_LENGTH) {
      throw new OBException(MESSAGE_PREFIX + index + "].external_id is too long");
    }
    if (item.has(PROP_METADATA) && !item.isNull(PROP_METADATA) && item.optJSONObject(PROP_METADATA) == null) {
      throw new OBException(MESSAGE_PREFIX + index + "].metadata must be a JSON object");
    }
  }

  /** Maps the request role (user|assistant) to the stored constant; anything else is rejected. */
  private static String roleOf(JSONObject item) {
    String role = StringUtils.lowerCase(StringUtils.defaultString(optText(item, PROP_ROLE)), Locale.ROOT);
    if ("user".equals(role)) {
      return CopilotConstants.MESSAGE_USER;
    }
    if ("assistant".equals(role)) {
      return CopilotConstants.MESSAGE_ASSISTANT;
    }
    throw new OBException("Invalid role '" + role + "': must be 'user' or 'assistant'");
  }

  private static boolean isOwnedByCurrentUser(Conversation conversation) {
    User owner = conversation.getUserContact();
    User current = OBContext.getOBContext().getUser();
    return owner != null && current != null && StringUtils.equals(owner.getId(), current.getId());
  }

  private static Conversation findByExternalId(String externalId) {
    OBCriteria<Conversation> crit = OBDal.getInstance().createCriteria(Conversation.class);
    crit.setFilterOnActive(false);
    crit.add(Restrictions.eq(Conversation.PROPERTY_EXTERNALID, externalId));
    crit.setMaxResults(1);
    return (Conversation) crit.uniqueResult();
  }

  private static boolean messageExists(Conversation conversation, String externalId) {
    OBCriteria<Message> crit = OBDal.getInstance().createCriteria(Message.class);
    crit.add(Restrictions.eq(Message.PROPERTY_CONVERSATION, conversation));
    crit.add(Restrictions.eq(Message.PROPERTY_EXTERNALID, externalId));
    crit.setMaxResults(1);
    return crit.uniqueResult() != null;
  }
}
