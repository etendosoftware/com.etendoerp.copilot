package com.etendoerp.copilot.history;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;
import com.etendoerp.copilot.util.CopilotConstants;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

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
    Conversation conversation = OBDal.getInstance()
        .createQuery(Conversation.class, "as c where c.externalID = :conversationId")
        .setNamedParameter("conversationId", conversationId)
        .setMaxResult(1)
        .uniqueResult();
    if (conversation == null) {
      conversation = new Conversation();
      conversation.setExternalID(conversationId);
      conversation.setUserContact(OBContext.getOBContext().getUser());
      OBDal.getInstance().save(conversation);
    }
    return conversation;
  }

  private void createMessage(String conversationId, String messageRole, String question) {
    Message message = new Message();
    message.setEtcopConversation(getConversation(conversationId));
    message.setMessage(question);
    message.setRole(messageRole);
    OBDal.getInstance().save(message);
    OBDal.getInstance().flush();
  }

  public void trackQuestion(String conversationId, String question) {
    createMessage(conversationId, CopilotConstants.MESSAGE_USER, question);
  }

  public void trackResponse(String conversationId, String string) {
    createMessage(conversationId, CopilotConstants.MESSAGE_ASSISTANT, string);
  }

  public static JSONArray getHistory(String conversationId) throws JSONException {
    List<Message> messages = OBDal.getInstance()
        .createQuery(Message.class, "as m where m.etcopConversation.externalID = :conversationId order by m.creationDate asc")
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
