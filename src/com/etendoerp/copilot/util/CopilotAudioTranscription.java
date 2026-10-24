package com.etendoerp.copilot.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.config.ProviderCredentials;

/** Shared Whisper transcription client for the upload endpoint and AudioTool. */
public final class CopilotAudioTranscription {
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private CopilotAudioTranscription() {
  }

  public static String transcribe(File file) throws IOException, InterruptedException,
      org.codehaus.jettison.json.JSONException {
    ProviderCredentials.ProviderConfig config = ProviderCredentials.create().resolveDefault("openai");
    String baseUrl = StringUtils.defaultIfBlank(config.baseUrl(), "https://api.openai.com");
    String endpoint = trimSlash(baseUrl) + "/v1/audio/transcriptions";
    String key = StringUtils.defaultIfBlank(config.apiKey(), config.gatewayMode() ? "dummy" : null);
    if (key == null) {
      throw new OBException("No API key is configured for audio transcription.");
    }
    String boundary = "----EtendoCopilot" + UUID.randomUUID();
    byte[] bytes = Files.readAllBytes(file.toPath());
    byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-1\r\n--"
        + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + file.getName()
        + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] body = new byte[prefix.length + bytes.length + suffix.length];
    System.arraycopy(prefix, 0, body, 0, prefix.length);
    System.arraycopy(bytes, 0, body, prefix.length, bytes.length);
    System.arraycopy(suffix, 0, body, prefix.length + bytes.length, suffix.length);
    HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofMinutes(2))
        .header("Authorization", "Bearer " + key)
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new OBException("Audio transcription failed (HTTP " + response.statusCode() + ")");
    }
    return new JSONObject(response.body()).optString("text", response.body());
  }

  private static String trimSlash(String value) {
    return value.replaceAll("/+$", "");
  }
}
