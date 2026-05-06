package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.rest.RequestUtils.extractRequestBody;
import static com.etendoerp.copilot.rest.RestServiceUtil.APPLICATION_JSON_CHARSET_UTF_8;
import static com.etendoerp.copilot.rest.RestServiceUtil.APP_ID;
import static com.etendoerp.copilot.rest.RestServiceUtil.PROP_RESPONSE;
import static com.etendoerp.copilot.util.CopilotConstants.PROP_QUESTION;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;
import com.etendoerp.copilot.rest.RestServiceUtil;

/**
 * ConversationUtils
 *
 * <p>Utility class that exposes static helper methods to manage conversations used by the
 * Copilot application. It provides methods to fetch conversations and messages, generate
 * conversation titles by delegating to the title generator service, and handle HTTP
 * endpoints that respond with JSON. Methods in this class interact with the database and
 * external services and therefore should be executed under an appropriate Openbravo
 * context (for example, using {@code OBContext.setAdminMode()} / {@code OBContext.restorePreviousMode()}).
 *
 * <p>This class is not instantiable and exposes only static methods.
 */
public class ConversationUtils {
  public static final Logger log4j = LogManager.getLogger(ConversationUtils.class);
  private static final String TITLE_GENERATOR_ID = "1844CE5E2BCB404DAAC470216B7D6495";
  private static final String PROP_TITLE = "title";
  private static final String PROP_SUCCESS = "success";
  private static final String CONVERSATION_NOT_FOUND = "Conversation not found";


  private ConversationUtils() {
  }

  /**
   * Executes the given action within an admin mode context, writing error responses
   * if an exception occurs. This eliminates the repeated try/catch/finally pattern
   * across all handler methods.
   *
   * @param response
   *     the {@link HttpServletResponse} used to send error responses on failure
   * @param action
   *     the action to execute in admin mode
   * @throws IOException
   *     if an I/O error occurs while sending error responses
   */
  private static void executeInAdminMode(HttpServletResponse response,
      AdminAction action) throws IOException {
    try {
      OBContext.setAdminMode();
      action.execute();
    } catch (Exception e) {
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Functional interface for actions executed within an admin mode context.
   */
  @FunctionalInterface
  private interface AdminAction {
    void execute() throws IOException, JSONException;
  }

  /**
   * Extracts the conversation ID from the request body JSON, validates it,
   * and retrieves the corresponding conversation.
   *
   * @param json
   *     the parsed request body
   * @param includeInactive
   *     whether to include inactive (archived) conversations in the lookup
   * @return the found {@link Conversation}, never null
   * @throws JSONException
   *     if the conversation ID cannot be extracted from the JSON
   */
  private static Conversation extractAndValidateConversation(JSONObject json,
      boolean includeInactive) throws JSONException {
    String conversationId = json.getString(CopilotConstants.PROP_CONVERSATION_ID);
    if (StringUtils.isEmpty(conversationId)) {
      throwConversationIDRequired();
    }
    Conversation conversation = getConversationByIDorExtRef(conversationId, includeInactive);
    if (conversation == null) {
      throw new OBException(CONVERSATION_NOT_FOUND);
    }
    return conversation;
  }

  /**
   * Converts a list of conversations to a JSON array, including each conversation's
   * external ID and title (if present). Conversations without an external ID are skipped.
   *
   * @param conversations
   *     the list of conversations to convert
   * @return a {@link JSONArray} with the conversation data
   */
  private static JSONArray conversationsToJson(List<Conversation> conversations) {
    JSONArray conversationsJson = new JSONArray();
    conversations.forEach(conv -> {
      try {
        if (StringUtils.isEmpty(conv.getExternalID())) {
          return;
        }
        JSONObject convJson = new JSONObject();
        convJson.put("id", conv.getExternalID());
        String title = conv.getTitle();
        if (!StringUtils.isEmpty(title)) {
          convJson.put(PROP_TITLE, title);
        }
        conversationsJson.put(convJson);
      } catch (JSONException e) {
        log4j.error("Error creating JSON for conversation: {}", conv.getId(), e);
      }
    });
    return conversationsJson;
  }

  /**
   * Writes a JSON success response to the HTTP response.
   *
   * @param response
   *     the {@link HttpServletResponse} to write to
   * @param jsonResponse
   *     the JSON object to send as the response body
   * @throws IOException
   *     if an I/O error occurs writing the response
   * @throws JSONException
   *     if a JSON serialization error occurs
   */
  private static void writeJsonResponse(HttpServletResponse response,
      JSONObject jsonResponse) throws IOException, JSONException {
    response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
    response.getWriter().write(jsonResponse.toString());
  }

  /**
   * Handles the retrieval of a conversation title based on the provided conversation ID.
   * <p>
   * This method extracts the conversation ID from the request parameters or JSON body,
   * validates its presence, and retrieves the corresponding title. The title is then
   * returned as a JSON response. If an error occurs, appropriate error responses are sent.
   *
   * @param request
   *     the {@link HttpServletRequest} object containing client request information
   * @param response
   *     the {@link HttpServletResponse} object used to return the response to the client
   * @throws IOException
   *     if an input or output error occurs during the handling of the request
   */
  public static void handleGetTitleConversation(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    executeInAdminMode(response, () -> {
      String conversationId = request.getParameter(CopilotConstants.PROP_CONVERSATION_ID);

      JSONObject json = extractRequestBody(request);
      if (json.has(CopilotConstants.PROP_CONVERSATION_ID)) {
        conversationId = json.getString(CopilotConstants.PROP_CONVERSATION_ID);
      }

      if (StringUtils.isEmpty(conversationId)) {
        throwConversationIDRequired();
      }

      String title = ConversationUtils.getTitleConversation(conversationId);
      writeJsonResponse(response, new JSONObject().put(PROP_TITLE, title));
    });
  }

  /**
   * Handles the retrieval of conversations for a specific assistant and writes them to the HTTP response.
   * <p>
   * This method retrieves the assistant ID from the request parameters, validates its presence,
   * fetches the assistant object, and retrieves the associated conversations. The conversations
   * are then returned as a JSON response. If an error occurs, appropriate error responses are sent.
   *
   * @param request
   *     the {@link HttpServletRequest} object containing client request information
   * @param response
   *     the {@link HttpServletResponse} object used to return the response to the client
   * @throws IOException
   *     if an input or output error occurs during the handling of the request
   */
  public static void handleConversations(HttpServletRequest request, HttpServletResponse response) throws IOException {
    executeInAdminMode(response, () -> {
      String appId = request.getParameter(CopilotConstants.PROP_APP_ID);
      if (StringUtils.isEmpty(appId)) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_AppIDRequired"));
      }

      CopilotApp assistant = CopilotUtils.getAssistantByIDOrName(appId);
      JSONArray conversations = ConversationUtils.getConversations(assistant);

      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(conversations.toString());
    });
  }

  /**
   * Handles the retrieval of messages for a specific conversation and writes them to the HTTP response.
   * <p>
   * This method retrieves the conversation ID from the request parameters, validates its presence,
   * fetches the associated messages, and returns them as a JSON response. If an error occurs,
   * appropriate error responses are sent.
   *
   * @param request
   *     the {@link HttpServletRequest} object containing client request information
   * @param response
   *     the {@link HttpServletResponse} object used to return the response to the client
   * @throws IOException
   *     if an input or output error occurs during the handling of the request
   */
  public static void handleConversationMessages(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    executeInAdminMode(response, () -> {
      String conversationId = request.getParameter("conversation_id");
      if (StringUtils.isEmpty(conversationId)) {
        throwConversationIDRequired();
      }

      JSONArray messages = ConversationUtils.getConversationMessages(conversationId);

      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(messages.toString());
    });
  }

  /**
   * Handles renaming a conversation. Extracts the conversation ID and new title
   * from the request body, validates both, and updates the conversation title.
   *
   * @param request
   *     the {@link HttpServletRequest} containing the conversation ID and new title
   * @param response
   *     the {@link HttpServletResponse} used to return the result
   * @throws IOException
   *     if an I/O error occurs during request or response handling
   */
  public static void handleRenameConversation(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    executeInAdminMode(response, () -> {
      JSONObject json = extractRequestBody(request);
      String title = json.getString(PROP_TITLE);

      if (StringUtils.isEmpty(title)) {
        throw new OBException("Title is required");
      }

      Conversation conversation = extractAndValidateConversation(json, false);
      conversation.setTitle(title);
      OBDal.getInstance().save(conversation);
      OBDal.getInstance().flush();

      writeJsonResponse(response, new JSONObject().put(PROP_SUCCESS, true).put(PROP_TITLE, title));
    });
  }

  /**
   * Handles soft-deleting a conversation by setting it as inactive.
   * Extracts the conversation ID from the request body and deactivates the conversation.
   *
   * @param request
   *     the {@link HttpServletRequest} containing the conversation ID
   * @param response
   *     the {@link HttpServletResponse} used to return the result
   * @throws IOException
   *     if an I/O error occurs during request or response handling
   */
  public static void handleDeleteConversation(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    executeInAdminMode(response, () -> {
      JSONObject json = extractRequestBody(request);
      Conversation conversation = extractAndValidateConversation(json, false);

      conversation.setActive(false);
      OBDal.getInstance().save(conversation);
      OBDal.getInstance().flush();

      writeJsonResponse(response, new JSONObject().put(PROP_SUCCESS, true));
    });
  }

  /**
   * Handles restoring a previously deleted (inactive) conversation by reactivating it.
   * Extracts the conversation ID from the request body and sets the conversation as active.
   *
   * @param request
   *     the {@link HttpServletRequest} containing the conversation ID
   * @param response
   *     the {@link HttpServletResponse} used to return the result
   * @throws IOException
   *     if an I/O error occurs during request or response handling
   */
  public static void handleRestoreConversation(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    executeInAdminMode(response, () -> {
      JSONObject json = extractRequestBody(request);
      Conversation conversation = extractAndValidateConversation(json, true);

      conversation.setActive(true);
      OBDal.getInstance().save(conversation);
      OBDal.getInstance().flush();

      writeJsonResponse(response, new JSONObject().put(PROP_SUCCESS, true));
    });
  }

  /**
   * Handles permanently deleting a conversation and all its messages from the database.
   * Extracts the conversation ID from the request body, removes all associated messages,
   * and then removes the conversation itself.
   *
   * @param request
   *     the {@link HttpServletRequest} containing the conversation ID
   * @param response
   *     the {@link HttpServletResponse} used to return the result
   * @throws IOException
   *     if an I/O error occurs during request or response handling
   */
  public static void handlePermanentDeleteConversation(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    executeInAdminMode(response, () -> {
      JSONObject json = extractRequestBody(request);
      Conversation conversation = extractAndValidateConversation(json, true);

      List<Message> messages = conversation.getETCOPMessageList();
      for (Message msg : messages) {
        OBDal.getInstance().remove(msg);
      }
      OBDal.getInstance().remove(conversation);
      OBDal.getInstance().flush();

      writeJsonResponse(response, new JSONObject().put(PROP_SUCCESS, true));
    });
  }

  /**
   * Handles the retrieval of archived (inactive) conversations for a specific assistant
   * and writes them to the HTTP response as a JSON array.
   *
   * @param request
   *     the {@link HttpServletRequest} containing the assistant app ID parameter
   * @param response
   *     the {@link HttpServletResponse} used to return the archived conversations
   * @throws IOException
   *     if an I/O error occurs during request or response handling
   */
  public static void handleArchivedConversations(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    executeInAdminMode(response, () -> {
      String appId = request.getParameter(CopilotConstants.PROP_APP_ID);
      if (StringUtils.isEmpty(appId)) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_AppIDRequired"));
      }

      CopilotApp assistant = CopilotUtils.getAssistantByIDOrName(appId);
      JSONArray conversations = getArchivedConversations(assistant);

      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(conversations.toString());
    });
  }

  private static void throwConversationIDRequired() {
    throw new OBException(OBMessageUtils.messageBD("ETCOP_ConversationRequired"));
  }

  /**
   * Retrieves a list of conversations associated with a specific assistant and formats them as a JSON array.
   * <p>
   * This method queries the database for conversations linked to the provided assistant and the current user.
   * The conversations are sorted in descending order by their last message timestamp. Each conversation is
   * converted into a JSON object containing its external ID and title (if available) and added to the resulting
   * JSON array.
   *
   * @param assistant
   *     the {@link CopilotApp} instance representing the assistant whose conversations are to be retrieved
   * @return a {@link JSONArray} containing the conversations as JSON objects with their external ID and title
   */
  public static JSONArray getConversations(CopilotApp assistant) {
    OBCriteria<Conversation> convCrit = OBDal.getReadOnlyInstance().createCriteria(Conversation.class);
    convCrit.add(Restrictions.eq(Conversation.PROPERTY_COPILOTAPP, assistant));
    convCrit.add(Restrictions.eq(Conversation.PROPERTY_USERCONTACT, OBContext.getOBContext().getUser()));
    convCrit.addOrder(Order.desc(Conversation.PROPERTY_LASTMSG));

    return conversationsToJson(convCrit.list());
  }

  /**
   * Retrieves archived (inactive) conversations for a specific assistant and formats them as a JSON array.
   * Similar to {@link #getConversations(CopilotApp)} but only returns conversations marked as inactive.
   *
   * @param assistant
   *     the {@link CopilotApp} instance representing the assistant whose archived conversations are to be retrieved
   * @return a {@link JSONArray} containing the archived conversations as JSON objects with their external ID and title
   */
  public static JSONArray getArchivedConversations(CopilotApp assistant) {
    OBCriteria<Conversation> convCrit = OBDal.getInstance().createCriteria(Conversation.class);
    convCrit.setFilterOnActive(false);
    convCrit.add(Restrictions.eq(Conversation.PROPERTY_COPILOTAPP, assistant));
    convCrit.add(Restrictions.eq(Conversation.PROPERTY_USERCONTACT, OBContext.getOBContext().getUser()));
    convCrit.add(Restrictions.eq(Conversation.PROPERTY_ACTIVE, false));
    convCrit.addOrder(Order.desc(Conversation.PROPERTY_LASTMSG));

    return conversationsToJson(convCrit.list());
  }

  /**
   * Retrieves the title of a conversation based on the provided conversation ID.
   * <p>
   * This method first attempts to fetch the conversation using the given ID. If the conversation
   * exists and already has a title, the title is returned. If the title is not set, the method
   * generates a title by processing the conversation's messages and sending them to a title
   * generation service. The generated title is then saved to the database and returned.
   * <p>
   * If an error occurs during title generation, an empty string is returned.
   *
   * @param conversationId
   *     the ID of the conversation whose title is to be retrieved
   * @return the title of the conversation, or an empty string if the conversation does not exist
   *     or an error occurs
   */
  public static String getTitleConversation(String conversationId) {
    var conversation = getConversationByIDorExtRef(conversationId);
    if (conversation == null) {
      return "";
    }
    String title = conversation.getTitle();
    if (!StringUtils.isEmpty(title)) {
      return title;
    }
    try {
      JSONObject body = new JSONObject();

      body.put(APP_ID, TITLE_GENERATOR_ID);
      StringBuilder sb = new StringBuilder();
      List<com.etendoerp.copilot.data.Message> msgList = conversation.getETCOPMessageList();
      msgList.stream().sorted(Comparator.comparing(com.etendoerp.copilot.data.Message::getCreationDate))
          .forEach(
              msg -> sb.append(String.format("%s: %s %n", msg.getRole(), msg.getMessage()))
          );
      body.put(PROP_QUESTION, sb.toString());
      var responseQuest = RestServiceUtil.handleQuestion(false, null, body);
      title = responseQuest.getString(PROP_RESPONSE);
      conversation.setTitle(title);
      OBDal.getInstance().save(conversation);
      OBDal.getInstance().flush();
      return title;
    } catch (Exception e) {
      log4j.error("Error generating title for conversation: {}", conversationId, e);
      return "";
    }
  }

  /**
   * Retrieves the messages of a specific conversation and formats them as a JSON array.
   * <p>
   * This method fetches a conversation by its ID, retrieves its associated messages,
   * sorts them by their line number, and converts each message into a JSON object.
   * The resulting JSON objects are added to a JSON array, which is returned as the response.
   * <p>
   * If an error occurs during the process, an empty JSON array is returned.
   *
   * @param conversationId
   *     the ID of the conversation whose messages are to be retrieved
   * @return a {@link JSONArray} containing the messages of the conversation as JSON objects
   * @throws JSONException
   *     if an error occurs while creating the JSON objects
   */
  public static JSONArray getConversationMessages(String conversationId) throws JSONException {
    try {
      // Retrieve the conversation by its ID or external reference
      Conversation conversation = getConversationByIDorExtRef(conversationId);

      // Fetch and sort the messages of the conversation by their line number
      List<Message> etcopMessageList = conversation.getETCOPMessageList();
      List<com.etendoerp.copilot.data.Message> msgs = etcopMessageList.stream()
          .sorted(Comparator.comparing(com.etendoerp.copilot.data.Message::getLineno))
          .collect(Collectors.toList());

      // Create a JSON array to store the messages
      JSONArray arr = new JSONArray();

      // Convert each message into a JSON object and add it to the array
      for (com.etendoerp.copilot.data.Message msg : msgs) {
        JSONObject msgJson = new JSONObject();
        msgJson.put("id", msg.getId());
        msgJson.put("role", msg.getRole());
        msgJson.put("content", msg.getMessage());
        msgJson.put("timestamp", msg.getCreationDate().toString());
        arr.put(msgJson);
      }

      // Return the JSON array of messages
      return arr;
    } catch (Exception e) {
      // Log the error and return an empty JSON array
      log4j.error("Error getting messages for conversation: {}", conversationId, e);
      return new JSONArray();
    }
  }

  private static Conversation getConversationByIDorExtRef(String conversationId) {
    return getConversationByIDorExtRef(conversationId, false);
  }

  private static Conversation getConversationByIDorExtRef(String conversationId, boolean includeInactive) {
    Conversation conversation = OBDal.getInstance().get(Conversation.class, conversationId);

    if (conversation == null) {
      OBCriteria<Conversation> conversationcrit = OBDal.getInstance().createCriteria(Conversation.class);
      if (includeInactive) {
        conversationcrit.setFilterOnActive(false);
      }
      conversationcrit.add(Restrictions.eq(Conversation.PROPERTY_EXTERNALID, conversationId));
      conversationcrit.setMaxResults(1);
      conversation = (Conversation) conversationcrit.uniqueResult();
    }

    return conversation;
  }

}
