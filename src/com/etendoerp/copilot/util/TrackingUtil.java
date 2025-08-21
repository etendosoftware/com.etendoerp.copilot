package com.etendoerp.copilot.util;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import java.util.Date;
import java.util.List;

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

  private void createMessage(String conversationId, String messageRole, String question, CopilotApp app) {
    Message message = new Message();
    Conversation conversation = getConversation(conversationId, app);
    conversation.setLastMsg(new Date());
    message.setConversation(conversation);
    message.setMessage(question);
    message.setRole(messageRole);

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

  public void trackQuestion(String conversationId, String question, CopilotApp app) {
    createMessage(conversationId, CopilotConstants.MESSAGE_USER, question, app);
  }

  public void trackResponse(String conversationId, String string, CopilotApp app) {
    trackResponse(conversationId, string, app, false);
  }

  public void trackResponse(String conversationId, String string, CopilotApp app, boolean isError) {
    String messageRole = isError ? CopilotConstants.MESSAGE_ERROR : CopilotConstants.MESSAGE_ASSISTANT;
    createMessage(conversationId, messageRole, string, app);
  }

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

}
