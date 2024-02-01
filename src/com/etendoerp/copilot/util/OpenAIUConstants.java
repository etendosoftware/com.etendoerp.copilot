package com.etendoerp.copilot.util;

import java.util.HashMap;
import java.util.Map;

public class OpenAIUConstants {

  private static final Map<String, String> EXTENSION_TO_MIMETYPE;

  static {
    EXTENSION_TO_MIMETYPE = new HashMap<>();
    EXTENSION_TO_MIMETYPE.put(".c", "text/x-c");
    EXTENSION_TO_MIMETYPE.put(".cpp", "text/x-c++");
    EXTENSION_TO_MIMETYPE.put(".csv", "application/csv");
    EXTENSION_TO_MIMETYPE.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    EXTENSION_TO_MIMETYPE.put(".html", "text/html");
    EXTENSION_TO_MIMETYPE.put(".java", "text/x-java");
    EXTENSION_TO_MIMETYPE.put(".json", "application/json");
    EXTENSION_TO_MIMETYPE.put(".md", "text/markdown");
    EXTENSION_TO_MIMETYPE.put(".pdf", "application/pdf");
    EXTENSION_TO_MIMETYPE.put(".php", "text/x-php");
    EXTENSION_TO_MIMETYPE.put(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    EXTENSION_TO_MIMETYPE.put(".py", "text/x-python");
    // Nota: La extensión .py está duplicada en tu lista, solo deberías tener una entrada para cada extensión
    EXTENSION_TO_MIMETYPE.put(".rb", "text/x-ruby");
    EXTENSION_TO_MIMETYPE.put(".tex", "text/x-tex");
    EXTENSION_TO_MIMETYPE.put(".txt", "text/plain");
    EXTENSION_TO_MIMETYPE.put(".css", "text/css");
    EXTENSION_TO_MIMETYPE.put(".jpeg", "image/jpeg");
    EXTENSION_TO_MIMETYPE.put(".jpg", "image/jpeg");
    EXTENSION_TO_MIMETYPE.put(".js", "text/javascript");
    EXTENSION_TO_MIMETYPE.put(".gif", "image/gif");
    EXTENSION_TO_MIMETYPE.put(".png", "image/png");
    EXTENSION_TO_MIMETYPE.put(".tar", "application/x-tar");
    EXTENSION_TO_MIMETYPE.put(".ts", "application/typescript");
    EXTENSION_TO_MIMETYPE.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    EXTENSION_TO_MIMETYPE.put(".xml", "application/xml"); // o "text/xml" dependiendo del uso
    EXTENSION_TO_MIMETYPE.put(".zip", "application/zip");
  }

  public static String getMimeType(String fileName) {
    String extension = fileName.substring(fileName.lastIndexOf("."));
    return EXTENSION_TO_MIMETYPE.get(extension.toLowerCase());
  }
}