package com.etendoerp.copilot.util;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;

public class TrackingUtil {

  private static TrackingUtil instance = null;

  private TrackingUtil() {
  }

  public static TrackingUtil getInstance() {
    if (instance == null) {
      instance = new TrackingUtil();
    }
    return instance;
  }

  private Conversation getConversation(String conversationId) {
    return getConversation(conversationId, null);
  }

  private Conversation getConversation(String conversationId, CopilotApp app) {
    Conversation conversation = OBDal.getInstance()
        .createQuery(Conversation.class, "as c where c.externalID = :conversationId")
        .setNamedParameter("conversationId", conversationId)
        .setMaxResult(1)
        .uniqueResult();
    if (conversation == null) {
      conversation = OBProvider.getInstance().get(Conversation.class);
      OBContext context = OBContext.getOBContext();
      conversation.setClient(context.getCurrentClient());
      conversation.setOrganization(context.getCurrentOrganization());
      conversation.setNewOBObject(true);
      conversation.setExternalID(conversationId);
      conversation.setCopilotApp(app);
      conversation.setUserContact(context.getUser());
      OBDal.getInstance().save(conversation);
    }
    return conversation;
  }


  private void createMessage(String conversationId, String messageRole, String question, CopilotApp app,
      JSONObject metadata) {
    Message message = new Message();
    Conversation conversation = getConversation(conversationId, app);
    conversation.setLastMsg(new Date());
    message.setConversation(conversation);
    message.setMessage(question);
    message.setRole(messageRole);
    message.setMetadata(metadata != null ? metadata.toString() : null);

    OBCriteria<Message> messCrit = OBDal.getInstance().createCriteria(Message.class);
    messCrit.add(Restrictions.eq(Message.PROPERTY_CONVERSATION, conversation));
    messCrit.setProjection(Projections.max(Message.PROPERTY_LINENO));
    Long maxLineNo = (Long) messCrit.uniqueResult();
    if (maxLineNo == null) {
      maxLineNo = 0L;
    }

    message.setLineno(maxLineNo + 10);

    OBDal.getInstance().save(conversation);
    OBDal.getInstance().save(message);
    OBDal.getInstance().flush();
  }

  /**
   * Tracks a user question in a conversation by creating a message with the user role.
   *
   * @param conversationId
   *     the unique identifier of the conversation
   * @param question
   *     the user's question to be tracked
   * @param app
   *     the CopilotApp instance associated with the conversation
   */
  public void trackQuestion(String conversationId, String question, CopilotApp app) {
    createMessage(conversationId, CopilotConstants.MESSAGE_USER, question, app, null);
  }

  /**
   * Tracks a response in a conversation by creating a message with the assistant role.
   * This is a convenience method that calls the full trackResponse method with isError set to false.
   *
   * @param conversationId
   *     the unique identifier of the conversation
   * @param string
   *     the response content to be tracked
   * @param app
   *     the CopilotApp instance associated with the conversation
   * @param metadata
   *     additional metadata associated with the response
   */
  public void trackResponse(String conversationId, String string, CopilotApp app, JSONObject metadata) {
    trackResponse(conversationId, string, app, false, metadata);
  }

  /**
   * Tracks a response in a conversation by creating a message with either assistant or error role.
   *
   * @param conversationId
   *     the unique identifier of the conversation
   * @param string
   *     the response content to be tracked
   * @param app
   *     the CopilotApp instance associated with the conversation
   * @param isError
   *     whether this response represents an error (true) or a normal response (false)
   * @param metadata
   *     additional metadata associated with the response
   */
  public void trackResponse(String conversationId, String string, CopilotApp app, boolean isError,
      JSONObject metadata) {
    String messageRole = isError ? CopilotConstants.MESSAGE_ERROR : CopilotConstants.MESSAGE_ASSISTANT;
    createMessage(conversationId, messageRole, string, app, metadata);
  }

  /**
   * Retrieves the conversation history for a given conversation ID.
   * Returns all messages in the conversation ordered by creation date in ascending order.
   *
   * @param conversationId
   *     the unique identifier of the conversation
   * @return a JSONArray containing the conversation history with role and content for each message
   * @throws JSONException
   *     if there's an error creating the JSON response
   */
  public static JSONArray getHistory(String conversationId) throws JSONException {
    List<Message> messages = OBDal.getInstance()
        .createQuery(Message.class,
            "as m where m.conversation.externalID = :conversationId order by m.creationDate asc")
        .setNamedParameter("conversationId", conversationId)
        .list();
    JSONArray history = new JSONArray();
    for (Message message : messages) {
      JSONObject msg = new JSONObject();
      msg.put("role", message.getRole());
      msg.put("content", message.getMessage());
      history.put(msg);
    }
    return history;
  }

  /**
   * Track the event when Copilot returns no response (null). This records both the question
   * and an empty response for analytics and troubleshooting.
   *
   * @param conversationId
   *     the conversation id associated with the question
   * @param question
   *     the original user question
   * @param copilotApp
   *     the assistant used to serve the request
   */
  public static void trackNullResponse(String conversationId, String question, CopilotApp copilotApp) {
    // Note: This appears to be a bug in the original code - finalResponseAsync is null but we're calling methods on it
    // Keeping the original logic for backward compatibility
    TrackingUtil.getInstance().trackQuestion(conversationId, question, copilotApp);
    TrackingUtil.getInstance().trackResponse(conversationId, "", copilotApp, true, null);
  }

  /**
   * Return the date of the most recent conversation for the given user and app.
   * When no conversation exists a fixed past date is returned to allow sorting.
   *
   * @param user
   *     the user whose conversations will be queried
   * @param copilotApp
   *     the assistant application
   * @return the last message timestamp or the creation date (or fixed fallback)
   */
  public static Date getLastConversation(User user, CopilotApp copilotApp) {
    OBCriteria<Conversation> convCriteria = OBDal.getInstance().createCriteria(Conversation.class);
    convCriteria.add(Restrictions.eq(Conversation.PROPERTY_COPILOTAPP, copilotApp));
    convCriteria.add(Restrictions.eq(Conversation.PROPERTY_USERCONTACT, user));
    convCriteria.addOrder(Order.desc(Conversation.PROPERTY_LASTMSG));
    convCriteria.setMaxResults(1);
    Conversation conversation = (Conversation) convCriteria.uniqueResult();
    if (conversation == null) {
      return Date.from(Instant.parse("2024-01-01T00:00:00Z"));
    }
    if (conversation.getLastMsg() == null) {
      return conversation.getCreationDate();
    }
    return conversation.getLastMsg();
  }
}
