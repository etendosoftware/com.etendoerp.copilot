package com.etendoerp.copilot.kb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.openbravo.base.exception.OBException;
import dev.langchain4j.model.embedding.EmbeddingModel;

/** Parses, chunks, embeds and persists ETCOP_APP_SOURCE file content. */
public final class KnowledgeBaseIndexer {
  private final KnowledgeBaseStore store;
  private volatile EmbeddingModel embeddingModel;

  public KnowledgeBaseIndexer() { this(new KnowledgeBaseStore(), null); }
  public KnowledgeBaseIndexer(KnowledgeBaseStore store, EmbeddingModel embeddingModel) {
    this.store = store; this.embeddingModel = embeddingModel;
  }

  public void index(String appId, String sourceId, String namespace, String clientId, Path file,
      boolean skipSplitting, Long maxChunkSize, Long chunkOverlap) {
    if (file == null || !Files.isRegularFile(file)) throw new OBException("Knowledge-base source file is unavailable.");
    try { index(appId, sourceId, namespace, clientId, file.getFileName().toString(), Files.readAllBytes(file),
        skipSplitting, maxChunkSize, chunkOverlap); }
    catch (IOException e) { throw new OBException("Could not read the knowledge-base source file.", e); }
  }

  public void index(String appId, String sourceId, String namespace, String clientId, String fileName, byte[] bytes,
      boolean skipSplitting, Long maxChunkSize, Long chunkOverlap) {
    String ext = extension(fileName);
    if (isImageExtension(ext)) {
      // The Java runtime has no CLIP model/preprocessor; OCR safely continues without references.
      return;
    }
    if (!KnowledgeBaseDocumentParser.isTextExtension(ext) && !ext.equals("zip")) {
      throw new OBException("Unsupported knowledge-base file extension: ." + ext);
    }
    store.ensureReady();
    int size = maxChunkSize == null ? 1000 : Math.toIntExact(maxChunkSize);
    int overlap = chunkOverlap == null ? 200 : Math.toIntExact(chunkOverlap);
    try {
      int ordinal = 0;
      List<KnowledgeBaseDocumentParser.ParsedDocument> documents = KnowledgeBaseDocumentParser.parse(fileName, bytes);
      EmbeddingModel model = getEmbeddingModel();
      for (KnowledgeBaseDocumentParser.ParsedDocument document : documents) {
        for (String chunk : KnowledgeBaseChunker.split(document.text(), document.extension(), skipSplitting, size, overlap)) {
          String contentHash = sha256(chunk);
          String id = sha256(appId + '|' + clientId + '|' + namespace + '|' + sourceId + '|' + ordinal++ + '|' + contentHash);
          store.upsert(id, appId, clientId, namespace, sourceId, document.name(), chunk, contentHash,
              KnowledgeBaseEmbeddingModel.vector(model, chunk));
        }
      }
    } catch (IOException e) { throw new OBException("Could not parse knowledge-base source " + fileName + '.', e); }
  }

  private EmbeddingModel getEmbeddingModel() {
    EmbeddingModel current = embeddingModel;
    if (current != null) return current;
    synchronized (this) {
      if (embeddingModel == null) embeddingModel = KnowledgeBaseEmbeddingModel.build();
      return embeddingModel;
    }
  }
  private static boolean isImageExtension(String extension) {
    return List.of("png", "jpg", "jpeg", "bmp", "tif", "tiff", "gif", "webp").contains(extension);
  }
  private static String extension(String name) {
    int dot = name == null ? -1 : name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }
  private static String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
  }
}
