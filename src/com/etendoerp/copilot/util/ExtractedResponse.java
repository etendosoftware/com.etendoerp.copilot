package com.etendoerp.copilot.util;


import org.codehaus.jettison.json.JSONObject;

/**
 * Result class to encapsulate the response data extracted from Copilot's response.
 */
public class ExtractedResponse {
  private final String response;
  private final String conversationId;
  private final JSONObject metadata;

  /**
   * Constructs an ExtractedResponse with the given response, conversation ID, and metadata.
   *
   * @param response the response text
   * @param conversationId the conversation ID
   * @param metadata the metadata JSON object, or null
   */
  public ExtractedResponse(String response, String conversationId, JSONObject metadata) {
    this.response = response;
    this.conversationId = conversationId;
    this.metadata = metadata != null ? metadata : new JSONObject();
  }

  /**
   * Gets the response text.
   *
   * @return the response string
   */
  public String getResponse() {
    return response;
  }

  /**
   * Gets the conversation ID.
   *
   * @return the conversation ID string
   */
  public String getConversationId() {
    return conversationId;
  }

  /**
   * Gets the metadata JSON object.
   *
   * @return the metadata JSONObject
   */
  public JSONObject getMetadata() {
    return metadata;
  }
}
