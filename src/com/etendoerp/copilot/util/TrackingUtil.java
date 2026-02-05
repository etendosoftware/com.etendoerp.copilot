package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotModelUtils.getModelProviderResult;

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
import com.etendoerp.telemetry.TelemetryUsageInfo;

public class TrackingUtil {

  private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(
      TrackingUtil.class);
  public static final String COPILOT_MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";
  private static TrackingUtil instance = null;

  private TrackingUtil() {
  }

  public static TrackingUtil getInstance() {
    if (instance == null) {
      instance = new TrackingUtil();
    }
    return instance;
  }

  /**
   * Sends usage data for a Copilot agent to the telemetry system for analytics and monitoring.
   * This method collects comprehensive information about the agent including its configuration,
   * model details, module association, and team member structure.
   *
   * <p>The telemetry data includes:
   * <ul>
   *   <li>Agent basic information (name, ID, type)</li>
   *   <li>Model configuration (name and provider)</li>
   *   <li>Module association (if any)</li>
   *   <li>Team members structure (2-level hierarchy)</li>
   * </ul>
   *
   * <p>This method fails silently if any error occurs during the telemetry data collection
   * or transmission to avoid disrupting the main application flow.
   *
   * @param agent
   *     the CopilotApp instance for which usage data should be sent
   */
  public static void sendUsageData(CopilotApp agent) {
    try {
      JSONObject jsonData = buildTelemetryJson(agent);
      OBContext context = OBContext.getOBContext();
      long time = System.currentTimeMillis() - TelemetryUsageInfo.getInstance().getTimeMillis();
      TelemetryUsageInfo.getInstance().setModuleId(COPILOT_MODULE_ID);
      TelemetryUsageInfo.getInstance().setUserId(context.getUser().getId());
      TelemetryUsageInfo.getInstance().setObjectId(agent.getId());
      TelemetryUsageInfo.getInstance().setClassname(TrackingUtil.class.getName());

      TelemetryUsageInfo.getInstance().setTimeMillis(time);
      TelemetryUsageInfo.getInstance().setJsonObject(jsonData);
      TelemetryUsageInfo.getInstance().saveUsageAudit();
    } catch (Exception e) {
      //Fail silently
      logger.error("Error sending Copilot usage data", e);
    }
  }

  /**
   * Builds the complete telemetry JSON structure for the given agent.
   * This is the entry point for telemetry data construction, which includes
   * the Langgraph agent data and all its team members in a 2-level hierarchy.
   *
   * @param agent
   *     the CopilotApp instance to build telemetry data for
   * @return a JSONObject containing the complete telemetry structure
   */
  private static JSONObject buildTelemetryJson(CopilotApp agent) {
    return buildAgentData(agent, true);
  }

  /**
   * Builds a standardized JSON representation of agent data for telemetry purposes.
   * This method creates a consistent structure for both Langgraph agents and team members,
   * ensuring all agents are represented with the same data format.
   *
   * <p>The generated JSON structure includes:
   * <ul>
   *   <li><code>agent_name</code> - The display name of the agent</li>
   *   <li><code>agent_id</code> - The unique identifier of the agent</li>
   *   <li><code>agent_type</code> - The type/category of the agent</li>
   *   <li><code>model</code> - Model configuration with name and provider</li>
   *   <li><code>module</code> - Associated module information (if any)</li>
   *   <li><code>team_members</code> - Array of team member agents (only for Langgraph agent)</li>
   * </ul>
   *
   * @param agent
   *     the CopilotApp instance to build data for
   * @param includeTeamMembers
   *     if true, includes team members data; if false, excludes them
   *     (used to create a 2-level hierarchy without infinite recursion)
   * @return a JSONObject containing the agent's structured data for telemetry
   */
  private static JSONObject buildAgentData(CopilotApp agent, boolean includeTeamMembers) {
    JSONObject jsonData = new JSONObject();
    try {
      jsonData.put("agent_name", agent.getName());
      jsonData.put("agent_id", agent.getId());
      jsonData.put("agent_type", agent.getAppType());

      // Add model information with provider details
      JSONObject modelData = new JSONObject();
      CopilotModelUtils.ModelProviderResult result = getModelProviderResult(agent);
      modelData.put("model_name", result.modelStr);
      modelData.put("model_provider", result.providerStr);
      jsonData.put("model", modelData);


      // Add module information
      if (agent.getModule() != null) {
        JSONObject moduleData = new JSONObject();
        moduleData.put("module_name", agent.getModule().getName());
        moduleData.put("module_id", agent.getModule().getId());
        jsonData.put("module", moduleData);
      }

      // Add team members information (only for the Langgraph agent, not for members)
      if (includeTeamMembers) {
        var tmL = agent.getETCOPTeamMemberList();
        if (tmL != null && !tmL.isEmpty()) {
          jsonData.put("team_members_qty", tmL.size());
          JSONArray membersArray = new JSONArray();
          for (var member : tmL) {
            // Each member has the same structure as the Langgraph agent, but without their own team members
            membersArray.put(buildAgentData(member.getCopilotApp(), false));
          }
          jsonData.put("team_members", membersArray);
        }
      }

    } catch (JSONException e) {
      logger.error("Error building telemetry JSON data for Copilot usage", e);
    }
    return jsonData;
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
