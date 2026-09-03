package com.etendoerp.copilot.engine;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * Runs the agent inside Tomcat with LangChain4j, so a plain chat agent needs no Python container.
 * <p>
 * The engine deliberately reads everything from the request payload rather than from the database:
 * the system prompt, the conversation history, the provider, the model and the temperature are all
 * already resolved by {@code RestServiceUtil.buildRequestJson}. The conversation memory is the one
 * Etendo already keeps in {@code ETCOP_MESSAGE} and ships as {@code history}, so no additional
 * memory store is involved.
 * <p>
 * Server-sent events are emitted in the exact shape the Python runtime produces
 * ({@code data: {"answer": {...}}}), which is why the chat front-end needs no change. The current
 * implementation emits a single answer frame; the front-end renders every frame as its own bubble
 * and does not accumulate token deltas, so token-level streaming would need a front-end change and
 * is intentionally left out of this first version.
 */
public class LangChain4jEngine implements CopilotEngine {

  private static final Logger log = LogManager.getLogger(LangChain4jEngine.class);

  private static final String PROP_ROLE = "role";
  private static final String PROP_CONTENT = "content";

  @Override
  public JSONObject execute(boolean asyncRequest, HttpServletResponse queue, JSONObject request,
      CopilotApp app) throws IOException, JSONException {

    List<ChatMessage> messages = buildMessages(request);
    List<ToolSpecification> clientTools = ClientToolSupport.toolSpecifications(request);
    ChatModel model = ChatModelFactory.build(request);
    String conversationId = request.optString(RestServiceUtil.PROP_CONVERSATION_ID, null);

    AiMessage aiMessage;
    try {
      ChatRequest.Builder chatRequest = ChatRequest.builder().messages(messages);
      if (!clientTools.isEmpty()) {
        chatRequest.toolSpecifications(clientTools);
      }
      aiMessage = model.chat(chatRequest.build()).aiMessage();
    } catch (RuntimeException e) {
      log.error("The Java Copilot engine failed while calling the model for agent '{}'", app.getName(), e);
      throw new OBException("Copilot (Java engine) error: " + e.getMessage(), e);
    }

    if (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
      // The model wants a client-side tool. End the turn and let the client execute and come back.
      JSONObject toolCall = ClientToolSupport.toolCallAnswer(aiMessage, conversationId);
      log.info("Copilot engine: asking the client to execute {} tool(s) for agent '{}'",
          aiMessage.toolExecutionRequests().size(), app.getName());
      if (asyncRequest) {
        stream(queue, toolCall);
        return toolCall;
      }
      return new JSONObject().put(RestServiceUtil.PROP_ANSWER, toolCall);
    }

    String answerText = aiMessage == null ? null : aiMessage.text();
    if (StringUtils.isEmpty(answerText)) {
      throw new OBException("Copilot (Java engine) returned an empty response.");
    }

    JSONObject answer = new JSONObject();
    answer.put(RestServiceUtil.PROP_RESPONSE, answerText);
    if (StringUtils.isNotEmpty(conversationId)) {
      answer.put(RestServiceUtil.PROP_CONVERSATION_ID, conversationId);
    }

    if (!asyncRequest) {
      // Mirrors the Python synchronous body: {"answer": {...}}
      return new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer);
    }

    stream(queue, answer);
    // Mirrors what RestServiceUtil.serverSideEvents returns for the streaming path: the bare answer.
    return answer;
  }

  /**
   * Write the answer to the client as server-sent events, in the same frame shape the Python
   * runtime emits.
   *
   * @param queue
   *     the servlet response to stream to
   * @param answer
   *     the answer object to send
   * @throws IOException
   *     when the response writer cannot be obtained
   * @throws JSONException
   *     when the frame cannot be assembled
   */
  private void stream(HttpServletResponse queue, JSONObject answer) throws IOException, JSONException {
    RestServiceUtil.setEventStreamMode(queue);
    PrintWriter writerToFront = queue.getWriter();
    // The Python relay opens the stream with an empty frame; keep the same handshake.
    RestServiceUtil.sendEventToFront(writerToFront, new JSONObject(), true);
    RestServiceUtil.sendEventToFront(writerToFront, new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer),
        true);
    writerToFront.flush();
  }

  /**
   * Turn the request payload into the message list handed to the model: the system prompt, the
   * stored conversation history and the current question.
   *
   * @param request
   *     the payload built by {@code RestServiceUtil.buildRequestJson}
   * @return the ordered message list
   * @throws JSONException
   *     when the history cannot be read
   */
  private List<ChatMessage> buildMessages(JSONObject request) throws JSONException {
    List<ChatMessage> messages = new ArrayList<>();

    String systemPrompt = request.optString(RestServiceUtil.PROP_SYSTEM_PROMPT, null);
    if (StringUtils.isNotBlank(systemPrompt)) {
      messages.add(SystemMessage.from(systemPrompt));
    }

    JSONArray history = request.optJSONArray(RestServiceUtil.PROP_HISTORY);
    if (history != null) {
      for (int i = 0; i < history.length(); i++) {
        addHistoryMessage(messages, history.getJSONObject(i));
      }
    }

    String question = request.optString(RestServiceUtil.PROP_QUESTION, null);
    if (StringUtils.isBlank(question)) {
      throw new OBException("Copilot (Java engine): the request carries no question.");
    }
    messages.add(UserMessage.from(question));

    // Rounds of client-executed tools already played out for this same question.
    ClientToolSupport.appendExchange(messages, request);

    return messages;
  }

  /**
   * Append one stored message to the model conversation. Messages tracked with the error role are
   * skipped: they describe a failure of a previous turn, not something the model said.
   *
   * @param messages
   *     the list being built
   * @param stored
   *     one entry of the {@code history} array, with {@code role} and {@code content}
   */
  private void addHistoryMessage(List<ChatMessage> messages, JSONObject stored) {
    String content = stored.optString(PROP_CONTENT, null);
    if (StringUtils.isBlank(content)) {
      return;
    }
    String role = stored.optString(PROP_ROLE, null);
    if (StringUtils.equalsIgnoreCase(role, CopilotConstants.MESSAGE_USER)) {
      messages.add(UserMessage.from(content));
    } else if (StringUtils.equalsIgnoreCase(role, CopilotConstants.MESSAGE_ASSISTANT)) {
      messages.add(AiMessage.from(content));
    } else {
      log.debug("Skipping history message with role '{}'", role);
    }
  }
}
