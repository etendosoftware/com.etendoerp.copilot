package com.etendoerp.copilot.kb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Parses the text formats supported by ETCOP_FILE and expands ZIP archives in memory. */
public final class KnowledgeBaseDocumentParser {
  private static final int MAX_ARCHIVE_ENTRY_BYTES = 64 * 1024 * 1024;
  private KnowledgeBaseDocumentParser() { }

  public static List<ParsedDocument> parse(String fileName, byte[] content) throws IOException {
    if (content == null) throw new IllegalArgumentException("Document content is required");
    String extension = extension(fileName);
    if (extension.equals("zip")) return parseZip(content);
    return List.of(parseOne(fileName, extension, content));
  }

  private static List<ParsedDocument> parseZip(byte[] archive) throws IOException {
    List<ParsedDocument> result = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        String name = entry.getName();
        if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
          throw new IOException("ZIP entry escapes the archive root: " + name);
        }
        String ext = extension(name);
        if (!isTextExtension(ext)) continue;
        byte[] bytes = readLimited(zip, MAX_ARCHIVE_ENTRY_BYTES);
        result.add(parseOne(name, ext, bytes));
      }
    }
    return result;
  }

  private static ParsedDocument parseOne(String name, String extension, byte[] bytes) throws IOException {
    String text;
    if (extension.equals("pdf")) {
      try (org.apache.pdfbox.pdmodel.PDDocument pdf = Loader.loadPDF(bytes)) {
        text = new PDFTextStripper().getText(pdf);
      }
    } else if (isTextExtension(extension)) {
      text = new String(bytes, StandardCharsets.UTF_8);
    } else {
      throw new IOException("No Java text parser is available for ." + extension + " documents");
    }
    return new ParsedDocument(name, extension, text);
  }

  private static byte[] readLimited(InputStream input, int maximum) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) != -1) {
      if (output.size() + read > maximum) throw new IOException("ZIP entry exceeds the 64 MiB indexing limit");
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  public static boolean isTextExtension(String extension) {
    String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    return List.of("pdf", "md", "markdown", "txt", "java", "py", "js", "xml", "json").contains(ext);
  }

  private static String extension(String name) {
    int dot = name == null ? -1 : name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  public static final class ParsedDocument {
    private final String name;
    private final String extension;
    private final String text;
    ParsedDocument(String name, String extension, String text) {
      this.name = name;
      this.extension = extension;
      this.text = text;
    }
    public String name() { return name; }
    public String extension() { return extension; }
    public String text() { return text; }
  }
}
