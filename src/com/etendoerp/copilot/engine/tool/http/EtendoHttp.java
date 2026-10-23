/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"). You may not use this file except in compliance with
 * the License.
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.util.CopilotUtils;

/** Shared HTTP transport for server tools, with identity attached only to Etendo loopback calls. */
public final class EtendoHttp {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
  private static final int MAX_REDIRECTS = 5;
  private static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
  // Redirects are handled per hop so Etendo credentials cannot be forwarded cross-origin.
  private EtendoHttp() {
  }

  /**
   * Send one request. An Etendo URL or an absolute path is called through the local Etendo host
   * with a fresh token. External destinations never receive identity from the tool context.
   */
  public static HttpResult call(ToolExecutionContext ctx, String method, String urlOrPath,
      Map<String, String> headers, String body, Duration timeout) throws Exception {
    String verb = method == null ? "" : method.toUpperCase(java.util.Locale.ROOT);
    HttpRequest.BodyPublisher publisher = body == null || "GET".equals(verb) || "HEAD".equals(verb)
        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
    return callBody(ctx, method, urlOrPath, headers, publisher, timeout);
  }

  /** Send a request with a caller-provided body publisher, useful for streaming multipart content. */
  public static HttpResult callBody(ToolExecutionContext ctx, String method, String urlOrPath,
      Map<String, String> headers, HttpRequest.BodyPublisher body, Duration timeout) throws Exception {
    String verb = method == null ? "" : method.toUpperCase(java.util.Locale.ROOT);
    HttpRequest.BodyPublisher publisher = body == null || "GET".equals(verb) || "HEAD".equals(verb)
        ? HttpRequest.BodyPublishers.noBody() : body;
    return send(ctx, method, urlOrPath, headers, publisher, timeout);
  }

  private static HttpResult send(ToolExecutionContext ctx, String method, String urlOrPath,
      Map<String, String> headers, HttpRequest.BodyPublisher publisher, Duration timeout) throws Exception {
    if (method == null || method.isBlank()) {
      throw new IllegalArgumentException("An HTTP method is required.");
    }
    if (urlOrPath == null || urlOrPath.isBlank()) {
      throw new IllegalArgumentException("An HTTP URL or path is required.");
    }

    String etendoHost = CopilotUtils.getEtendoHost();
    String target = urlOrPath.startsWith("/") ? appendPath(etendoHost, urlOrPath) : urlOrPath;
    URI etendoOrigin = URI.create(etendoHost);
    URI initialUri = URI.create(target);
    URI currentUri = initialUri;
    boolean initialEtendo = isSameOrigin(initialUri, etendoOrigin);
    String currentMethod = method.toUpperCase(java.util.Locale.ROOT);
    HttpRequest.BodyPublisher currentPublisher = publisher;
    Map<String, String> originalHeaders = new LinkedHashMap<>();
    if (headers != null) {
      originalHeaders.putAll(headers);
    }
    Duration effectiveTimeout = timeout == null ? DEFAULT_TIMEOUT : timeout;

    for (int redirects = 0;; redirects++) {
      Map<String, String> requestHeaders = new LinkedHashMap<>(originalHeaders);
      if (mayAttachIdentity(initialEtendo, currentUri, etendoOrigin)) {
        if (ctx == null || !ctx.webServiceEnabled()) {
          throw new IllegalStateException("This Etendo HTTP operation requires a web-service-enabled role.");
        }
        String token = ctx.mintToken();
        if (token == null || token.isBlank()) {
          throw new IllegalStateException("Unable to obtain an Etendo access token for this role.");
        }
        requestHeaders.replaceAll((name, value) -> replaceTokenPlaceholder(value, token));
        removeHeader(requestHeaders, "Authorization");
        requestHeaders.put("Authorization", "Bearer " + token);
      } else {
        requestHeaders.entrySet().removeIf(entry -> "Authorization".equalsIgnoreCase(entry.getKey())
            && entry.getValue() != null && entry.getValue().contains("ETENDO_TOKEN"));
        if (initialEtendo || !isSameOrigin(initialUri, currentUri)) {
          removeHeader(requestHeaders, "Authorization");
        }
      }

      HttpRequest.Builder request = HttpRequest.newBuilder(currentUri).timeout(effectiveTimeout);
      requestHeaders.forEach(request::header);
      request.method(currentMethod, currentPublisher);
      HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
      String location = response.headers().firstValue("location").orElse(null);
      if (!isRedirect(response.statusCode()) || location == null || location.isBlank()) {
        return new HttpResult(response.statusCode(), response.body(), response.headers().map());
      }
      if (redirects >= MAX_REDIRECTS) {
        throw new IOException("Too many HTTP redirects.");
      }
      URI redirectUri = currentUri.resolve(location);
      if (!"http".equalsIgnoreCase(redirectUri.getScheme())
          && !"https".equalsIgnoreCase(redirectUri.getScheme())) {
        return new HttpResult(response.statusCode(), response.body(), response.headers().map());
      }
      int status = response.statusCode();
      if ((status == 303 && !"HEAD".equals(currentMethod))
          || (status == 302 && !"HEAD".equals(currentMethod))
          || (status == 301 && "POST".equals(currentMethod))) {
        currentMethod = "GET";
        currentPublisher = HttpRequest.BodyPublishers.noBody();
        removeHeader(originalHeaders, "Content-Length");
        removeHeader(originalHeaders, "Content-Type");
        removeHeader(originalHeaders, "Transfer-Encoding");
      }
      currentUri = redirectUri;
    }
  }

  private static boolean isRedirect(int status) {
    return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
  }

  static boolean mayAttachIdentity(boolean initialEtendo, URI current, URI etendoOrigin) {
    return initialEtendo && isSameOrigin(current, etendoOrigin);
  }

  static boolean isSameOrigin(URI left, URI right) {
    return left != null && right != null && left.getScheme() != null
        && left.getScheme().equalsIgnoreCase(right.getScheme()) && isSameHost(left, right);
  }

  private static void removeHeader(Map<String, String> headers, String name) {
    headers.keySet().removeIf(header -> name.equalsIgnoreCase(header));
  }


  static boolean isSameHost(URI requested, URI configured) {
    if (requested == null || configured == null || requested.getHost() == null || configured.getHost() == null) {
      return false;
    }
    return requested.getHost().equalsIgnoreCase(configured.getHost())
        && effectivePort(requested) == effectivePort(configured);
  }

  private static int effectivePort(URI uri) {
    if (uri.getPort() >= 0) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  static String appendPath(String host, String path) {
    String base = host == null ? "" : host;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + (path.startsWith("/") ? path : "/" + path);
  }

  static String replaceTokenPlaceholder(String value, String token) {
    return value == null ? null : value.replace("ETENDO_TOKEN", token);
  }

  /** Immutable HTTP response. */
  public record HttpResult(int status, String body, Map<String, List<String>> headers) {
  }
}
