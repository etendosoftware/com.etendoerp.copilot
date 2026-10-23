package com.etendoerp.copilot.tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.imageio.ImageIO;
import javax.inject.Named;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.copilot.engine.ChatModelFactory;
import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.util.OcrSchemaBuilder;
import com.etendoerp.copilot.util.PdfRenderer;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;

/** Lightweight OCR using the model configured on the SimpleOcrTool agent tool. */
@ApplicationScoped
@Named("SimpleOcrTool")
public class SimpleOcrTool implements ServerTool {
  static final String TOOL_ID = "BD5CBAAA442F42E289F5C7438E3C2EDF";
  static final String DEFAULT_MODEL = "gemini-3.8-flash";
  static final String DEFAULT_PROVIDER = "gemini";
  static final String PROMPT = "You are an expert OCR agent. Extract ALL information from the provided document thoroughly. Do not summarize or omit any detail, even if information is repeated or there are similar parts. Return the result in JSON format.";
  static final String DESCRIPTION = "Extracts structured data from images and PDF documents using direct vision AI APIs. Sends files natively without image conversion. Best for: invoices, receipts, forms, contracts, and any structured document. Supports structured output schemas from tools/schemas/ directory. Supports: JPEG, PNG, WebP, GIF, and PDF files.";

  @Override public String name() { return "SimpleOcrTool"; }

  @Override public ToolSpecification specification() {
    try {
      JSONObject props = new JSONObject()
          .put("path", new JSONObject().put("type", "string").put("description", "Absolute or relative path to the image or PDF file to be processed. The file must exist in the local file system."))
          .put("question", new JSONObject().put("type", "string").put("description", "Specific instructions describing what information to extract from the document. Be precise about the data fields needed."))
          .put("structured_output", new JSONObject().put("type", "string").put("description", "Schema name for structured output (e.g., 'Testdocument'). Leave empty for unstructured JSON extraction."))
          .put("structured_output_schema", new JSONObject().put("type", "object").put("description", "Literal JSON Schema dict for structured output; takes precedence over structured_output."));
      JsonObjectSchema.Builder schema = JsonObjectSchema.builder();
      JSONArray keys = props.names();
      for (int i = 0; i < keys.length(); i++) {
        String key = keys.getString(i);
        schema.addProperty(key, JsonRawSchema.from(props.getJSONObject(key).toString()));
      }
      schema.required("path", "question");
      return ToolSpecification.builder().name(name()).description(DESCRIPTION).parameters(schema.build()).build();
    } catch (JSONException e) { throw new IllegalStateException(e); }
  }

  @Override public String execute(JSONObject arguments, ToolExecutionContext context) throws Exception {
    String input = arguments.optString("path", null);
    if (input == null || input.isBlank()) throw new IllegalArgumentException("'path' is required");
    Path file = resolvePath(input);
    byte[] bytes = Files.readAllBytes(file);
    String mime = detectMime(bytes);
    if (!List.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf").contains(mime)) {
      throw new IllegalArgumentException("Unsupported MIME type '" + mime + "' for " + file + ". Supported: [application/pdf, image/gif, image/jpeg, image/png, image/webp]");
    }
    JSONObject schema = arguments.optJSONObject("structured_output_schema");
    if (schema == null || schema.length() == 0) schema = namedSchema(arguments.optString("structured_output", ""));
    String question = arguments.optString("question", "");
    List<ChatMessage> messages = buildMessages(bytes, mime, question, schema);
    ModelConfig config = modelConfig(context);
    JSONObject request = new JSONObject().put("provider", config.provider).put("model", config.model).put("temperature", 0.2);
    ChatModel model = ChatModelFactory.build(request);
    return model.chat(ChatRequest.builder().messages(messages).build()).aiMessage().text();
  }

  static List<ChatMessage> buildMessages(byte[] bytes, String mime, String question, JSONObject schema) throws Exception {
    String prompt = PROMPT;
    if (schema != null && schema.length() > 0) prompt += "\nUse this JSON Schema for the response:\n" + schema.toString();
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(SystemMessage.from(prompt));
    List<Content> content = new ArrayList<>();
    if ("application/pdf".equals(mime)) {
      for (java.awt.image.BufferedImage page : PdfRenderer.render(bytes, 2f)) {
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        ImageIO.write(page, "png", image);
        content.add(ImageContent.from(Image.builder()
            .base64Data(Base64.getEncoder().encodeToString(image.toByteArray()))
            .mimeType("image/png").build()));
      }
    } else content.add(ImageContent.from(Image.builder()
        .base64Data(Base64.getEncoder().encodeToString(bytes)).mimeType(mime).build()));
    content.add(TextContent.from(question));
    messages.add(UserMessage.from(content));
    return messages;
  }

  static ModelConfig modelConfig(ToolExecutionContext context) {
    JSONObject extra = context.extraInfo();
    JSONObject appConfig = extra.optJSONObject("tool_config");
    appConfig = appConfig == null ? null : appConfig.optJSONObject(context.agentId());
    JSONObject tool = appConfig == null ? null : appConfig.optJSONObject(TOOL_ID);
    String model = tool == null ? null : tool.optString("model", null);
    String provider = tool == null ? null : tool.optString("provider", null);
    return new ModelConfig(model == null || model.isBlank() ? DEFAULT_MODEL : model,
        provider == null || provider.isBlank() ? DEFAULT_PROVIDER : provider);
  }

  static String detectMime(byte[] bytes) {
    if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') return "application/pdf";
    if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return "image/jpeg";
    if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
    if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "image/gif";
    if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
    return null;
  }

  static Path resolvePath(String path) throws IOException {
    for (String prefix : List.of("/app", "..", "")) {
      Path candidate = prefix.isEmpty() ? Path.of(path) : Path.of(prefix).resolve(path.startsWith("/") ? path.substring(1) : path);
      if (Files.isRegularFile(candidate)) return candidate.toRealPath();
    }
    throw new java.io.FileNotFoundException("File not found: " + path);
  }

  static JSONObject namedSchema(String name) throws JSONException {
    return OcrSchemaBuilder.loadSchema(name);
  }


  static final class ModelConfig {
    final String model;
    final String provider;
    ModelConfig(String model, String provider) { this.model = model; this.provider = provider; }
  }
}
