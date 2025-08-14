package com.etendoerp.copilot.util;

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
import com.etendoerp.copilot.rest.RestServiceUtil;

public class ConversationUtils {
  public static final Logger log4j = LogManager.getLogger(ConversationUtils.class);
  private static final String TITLE_GENERATOR_ID = "1844CE5E2BCB404DAAC470216B7D6495";


  private ConversationUtils() {
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
    try {
      // Set the admin mode for the current context
      OBContext.setAdminMode();

      // Retrieve the conversation ID from the request parameters
      String conversationId = request.getParameter(CopilotConstants.PROP_CONVERSATION_ID);

      // Extract additional parameters from the JSON body if available
      JSONObject json = RestServiceUtil.extractRequestBody(request);
      if (json.has(CopilotConstants.PROP_CONVERSATION_ID)) {
        conversationId = json.getString(CopilotConstants.PROP_CONVERSATION_ID);
      }

      // Validate that the conversation ID is not empty
      if (StringUtils.isEmpty(conversationId)) {
        throwConversationIDRequired();
      }

      // Retrieve the title of the conversation
      String title = ConversationUtils.getTitleConversation(conversationId);

      // Write the title as a JSON response
      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(new JSONObject().put("title", title).toString());
    } catch (Exception e) {
      // Log the error and send a BAD_REQUEST response
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        // Log the IOException and send an INTERNAL_SERVER_ERROR response
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    } finally {
      // Restore the previous context mode
      OBContext.restorePreviousMode();
    }
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
    try {
      // Set the admin mode for the current context
      OBContext.setAdminMode();

      // Retrieve the assistant ID from the request parameters
      String appId = request.getParameter(CopilotConstants.PROP_APP_ID);

      // Validate that the assistant ID is not empty
      if (StringUtils.isEmpty(appId)) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_AppIDRequired"));
      }

      // Fetch the assistant object using the provided ID
      CopilotApp assistant = CopilotUtils.getAssistantByIDOrName(appId);

      // Retrieve the conversations associated with the assistant
      JSONArray conversations = ConversationUtils.getConversations(assistant);

      // Write the conversations as a JSON response
      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(conversations.toString());
    } catch (Exception e) {
      // Log the error and send a BAD_REQUEST response
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        // Log the IOException and send an INTERNAL_SERVER_ERROR response
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    } finally {
      // Restore the previous context mode
      OBContext.restorePreviousMode();
    }
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
    try {
      // Set the admin mode for the current context
      OBContext.setAdminMode();

      // Retrieve the conversation ID from the request parameters
      String conversationId = request.getParameter("conversation_id");

      // Validate that the conversation ID is not empty
      if (StringUtils.isEmpty(conversationId)) {
        throwConversationIDRequired();
      }

      // Retrieve the messages associated with the conversation
      JSONArray messages = ConversationUtils.getConversationMessages(conversationId);

      // Write the messages as a JSON response
      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(messages.toString());
    } catch (Exception e) {
      // Log the error and send a BAD_REQUEST response
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        // Log the IOException and send an INTERNAL_SERVER_ERROR response
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    } finally {
      // Restore the previous context mode
      OBContext.restorePreviousMode();
    }
  }

  private static void throwConversationIDRequired() {
    throw new OBException(OBMessageUtils.messageBD("ETCOP_ConversationRequired")
    );
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
    // Create a criteria query to fetch conversations for the given assistant and current user
    OBCriteria<Conversation> convCrit = OBDal.getReadOnlyInstance().createCriteria(Conversation.class);
    convCrit.add(Restrictions.eq(Conversation.PROPERTY_COPILOTAPP, assistant));
    convCrit.add(Restrictions.eq(Conversation.PROPERTY_USERCONTACT, OBContext.getOBContext().getUser()));
    convCrit.addOrder(Order.desc(Conversation.PROPERTY_LASTMSG));

    // Execute the query and retrieve the list of conversations
    List<Conversation> conversations = convCrit.list();
    JSONArray conversationsJson = new JSONArray();

    // Iterate through the conversations and convert each to a JSON object
    conversations.forEach(
        conv -> {
          try {
            JSONObject convJson = new JSONObject();
            convJson.put("id", conv.getExternalID());
            String title = conv.getTitle();
            if (!StringUtils.isEmpty(title)) {
              convJson.put("title", title);
            }
            conversationsJson.put(convJson);
          } catch (JSONException e) {
            // Log any errors encountered while creating the JSON object
            log4j.error("Error creating JSON for conversation: {}", conv.getId(), e);
          }
        }
    );

    // Return the JSON array of conversations
    return conversationsJson;
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
      msgList.stream().sorted(Comparator.comparing(com.etendoerp.copilot.data.Message::getCreationDate)).
          forEach(
              msg -> sb.append(String.format("%s: %s \n", msg.getRole(), msg.getMessage()))
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
      List<com.etendoerp.copilot.data.Message> msgs = conversation.getETCOPMessageList().stream()
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

  /**
   * Retrieves a conversation by its ID or external reference ID.
   * <p>
   * This method first attempts to fetch a conversation using the provided ID. If no conversation
   * is found, it queries the database for a conversation with a matching external reference ID.
   * The first matching conversation is returned.
   *
   * @param conversationId
   *     the ID or external reference ID of the conversation to retrieve
   * @return the {@link Conversation} object if found, or null if no matching conversation exists
   */
  private static Conversation getConversationByIDorExtRef(String conversationId) {
    // Attempt to retrieve the conversation by its primary ID
    Conversation conversation = OBDal.getInstance().get(Conversation.class, conversationId);

    // If not found, query the database for a conversation with the matching external reference ID
    if (conversation == null) {
      OBCriteria<Conversation> conversationcrit = OBDal.getInstance().createCriteria(Conversation.class);
      conversationcrit.add(Restrictions.eq(Conversation.PROPERTY_EXTERNALID, conversationId));
      conversationcrit.setMaxResults(1);
      conversation = (Conversation) conversationcrit.uniqueResult();
    }

    // Return the retrieved conversation or null if not found
    return conversation;
  }

}
