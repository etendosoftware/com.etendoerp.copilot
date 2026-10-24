package com.etendoerp.copilot.kb;

import java.util.ArrayList;
import java.util.List;

/** Extension-aware deterministic chunking for indexed knowledge-base documents. */
public final class KnowledgeBaseChunker {

  private KnowledgeBaseChunker() {
  }

  public static List<String> split(String text, String extension, boolean skipSplitting, int maxChunkSize,
      int overlap) {
    if (text == null || text.isBlank()) return List.of();
    if (skipSplitting) return List.of(text);
    if (maxChunkSize < 1 || overlap < 0 || overlap >= maxChunkSize) {
      throw new IllegalArgumentException("Chunk size must be positive and overlap smaller than chunk size");
    }
    String ext = extension == null ? "" : extension.toLowerCase(java.util.Locale.ROOT);
    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(start + maxChunkSize, text.length());
      if (end < text.length()) {
        int boundary = ext.equals("json") ? jsonBoundary(text, start, end) : bestBoundary(text, start, end, ext);
        if (boundary > start) end = boundary;
      }
      String chunk = text.substring(start, end).trim();
      if (!chunk.isEmpty()) chunks.add(chunk);
      if (end >= text.length()) break;
      int next = ext.equals("json") ? end : Math.max(start + 1, end - overlap);
      while (next < end && !Character.isWhitespace(text.charAt(next))) next++;
      if (next < end) next++;
      start = next;
    }
    return chunks;
  }

  private static int jsonBoundary(String text, int start, int end) {
    boolean quoted = false;
    boolean escaped = false;
    int candidate = -1;
    for (int i = 0; i < end; i++) {
      if (i < start) {
        char c = text.charAt(i);
        if (quoted) {
          if (escaped) escaped = false;
          else if (c == '\\') escaped = true;
          else if (c == '"') quoted = false;
        } else if (c == '"') quoted = true;
        continue;
      }
      char c = text.charAt(i);
      if (quoted) {
        if (escaped) escaped = false;
        else if (c == '\\') escaped = true;
        else if (c == '"') quoted = false;
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',' || c == '}' || c == ']') {
        if (i + 1 >= start) candidate = i + 1;
      }
    }
    return candidate >= start + (end - start) / 2 ? candidate : end;
  }

  private static int bestBoundary(String text, int start, int end, String extension) {
    String[] delimiters;
    if (extension.equals("md") || extension.equals("markdown")) {
      delimiters = new String[] {"\n#", "\n\n", "\n", " "};
    } else if (extension.equals("java") || extension.equals("js") || extension.equals("py")) {
      delimiters = new String[] {"\nclass ", "\ndef ", "\nfunction ", "\n}\n", "\n\n", "\n", " "};
    } else {
      delimiters = new String[] {"\n\n", "\n", " "};
    }
    for (String delimiter : delimiters) {
      int position = text.lastIndexOf(delimiter, end);
      if (position >= start + (end - start) / 2) return position + delimiter.length();
    }
    return end;
  }

}
