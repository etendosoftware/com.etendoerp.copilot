# Java Knowledge Base / Vector DB Design

## Summary

Replace the Python-local Chroma knowledge base with an in-process Java indexer/retriever backed by pgvector. The safest destination is **a copilot-owned schema/table design using LangChain4j's `EmbeddingStore` abstraction and pgvector implementation**, while reusing the existing Etendo embedding proxy configuration and, if a compatible provider contract can be shared without a module dependency, the deployment's embedding endpoint. Do **not** call `com.etendoerp.db.extended` classes: parity plan prohibits a Copilot→GO dependency; it does not explicitly prohibit db.extended, but a direct dependency should be decided separately. Current DB-extended vector storage is generic and source-configuration/outbox oriented, not a drop-in LangChain4j document store.

Current Python indexes persist independently at a Chroma directory per `kb_vectordb_id`; text and image collections are distinct. MemoryTool is also Chroma-backed but is per-agent/user memory, not automatically the KB collection: [INFERENCE] same Chroma technology, different collection/configuration and keying, so avoid treating them as shared data absent explicit migration proof. Legacy indexes cannot be read directly by Java without a Chroma-compatible service/import; re-index source files from Etendo's `ETCOP_APP_SOURCE` records as the migration path.

## Observed Python behavior

- `copilot/core/routes.py:342-409` `POST /ResetVectorDB`: for `LANGCHAIN_DEFAULT_COLLECTION_NAME` and `IMAGES_COLLECTION_NAME`, marks rows `purge=True` for client `0` plus current client (or only `0` for system client); does not immediately delete.
- `copilot/core/routes.py:412-505` `POST /addToVectorDB`: multipart fields include `kb_vectordb_id`, filename, extension, overwrite, file, skip_splitting, max_chunk_size, chunk_overlap, `ad_client_id`. ZIP is expanded; image extensions go to image indexing; other documents are split then embedded into Chroma. `overwrite` removes the on-disk database path, which is broader than deleting one file.
- `copilot/core/routes.py:507+` `POST /purgeVectorDB`: deletes only purge-marked records scoped by client and in both collections. Existing Java sync deliberately brackets file synchronization with reset then purge, so stale documents are removed while newly synchronized content survives.
- `copilot/core/kb_utils.py:21-57` `get_kb_tool`: loads `kb_vectordb_id`, embeds query, retrieves `k=kb_search_k` default 4 and filters `ad_client_id` to `0` or current client; exports tool `KnowledgeBaseSearch(query)`.
- `copilot/core/vectordb_utils.py:120-160,203-~450` ZIP and per-file indexing, dedupe and split logic; supported KB text extensions in Java `CopilotConstants.java:44-46`: pdf, md, markdown, txt, zip, java, py, js, xml, json, png, jpg, jpeg. Python image route supports more extensions; inventory specifically notes bmp/tiff/gif/webp (`parity-runtime-inventory.md:63`).
- `parity-runtime-inventory.md:60-62` records current parsing/splitter semantics: markdown splitter, character splitter for text/pdf/xml, recursive JSON splitter, language-aware recursive splitter for java/js/py; chunking config plus hash-based dedupe.

## Java triggers and current call paths

- `modules/com.etendoerp.copilot/src/com/etendoerp/copilot/util/CopilotUtils.java:114-123` builds `addToVectorDB` request; `:343-357` calls `ResetVectorDB`; `:736-749` calls `purgeVectorDB`.
- `CopilotUtils.java:419-~470` derives `KB_<appId>` and obtains skip-splitting/max-chunk-size/overlap from the source file configuration. `CopilotUtils.java:1094-1099` adds `kb_vectordb_id=KB_<appId>` and `kb_search_k` (configured result quantity, default 4) to agent request.
- `SyncAssistant.java:483-489` `syncKBFilesToLangChain` resets, loops sources through `syncAppLangchainSource`, then purges; this is the bulk/full-refresh flow.
- `FileUtils.java:~150-180` validates supported KB file extension and invokes the source upload path. `ETCOP_APP_SOURCE` is the source record driving file sync and is referenced by `CopilotUtils.syncAppLangchainSource` (`CopilotUtils.java:419+`).
- Java callsite search confirms these exact route calls and synchronization flow. Other caller categories (attachment REST, scheduled/background hooks) were not shown in the direct route search output; their behavior should be confirmed against `FileUtils` and attachment handlers before implementing a trigger migration. [INFERENCE] likely indexing is synchronous on existing sync hooks, not an independent database trigger.
- Java engine plan `docs/plans/2026-10-23-java-engine-parity.md:53-55, implementation order 1-9` schedules knowledge base/indexing as work package 5; Python calls must be removed as part of Python runtime removal.

## Existing Etendo vector facilities

`com.etendoerp.db.extended/README.md:18-28,56-83` documents optional pgvector; off by default and provisioned deliberately during `update.database`. It provides generic namespace/external-key/vector/JSON metadata operations and tenant-scoped nearest-neighbor query, but explicitly no RAG or REST endpoint. Its `Search Sources` config indexes chosen dictionary table columns, with trigger→transactional outbox→scheduled `Process Vector Outbox`; `GenerateVectorSourceTriggers` (referenced by `VectorTriggerService` Javadoc) provisions database triggers after update. This captures changes to configured business-table records, not uploaded document files.

Relevant implementation evidence:
- `modules/com.etendoerp.db.extended/src/com/etendoerp/db/extended/vector/VectorStoreService.java:31+`: generic vector record store API.
- `VectorOutboxService.java:36+`, `VectorTriggerService.java:39-51`, `VectorReindexService.java:38+`: asynchronous change capture, trigger deployment, reindex.
- `OpenAiEmbeddingProvider.java:40+`: embedding provider client in db.extended; model/provider configuration is attached to a search source. `ETARC_VECTOR_EMBED_PROVIDER.xml:44-48` defaults `text-embedding-3-small`; AD element help explicitly supports proxy model naming `provider/model` and endpoint configuration. Verify exact request/auth compatibility before reuse.
- README says extension/storage is administrator opt-in; storage is `etarc_vector`, collections are provisioned from Search Sources. Do not assume it is enabled on every installation.

The Java engine already has `OpenAiChatModel` proxy mode: `ChatModelFactory.java:35-48,59-61,104-110`; `copilot.proxy.url` points at `https://llm.etendo.software`, the gateway expects provider-prefixed model names and uses `dummy` API key fallback. For embeddings use LangChain4j 1.19.0 `dev.langchain4j.model.openai.OpenAiEmbeddingModel` with the equivalent `baseUrl`, provider/model name, and configured API key/dummy fallback. Confirm proxy embeddings endpoint model naming and dimensions in integration tests. [INFERENCE] Existing `OpenAiEmbeddingProvider` may be reusable only through a compatible configuration/API boundary; avoid copying private db.extended implementation or introducing forbidden GO dependency.

## Store decision and MemoryTool

**Recommendation:** a dedicated copilot-owned pgvector embedding store, queried through LangChain4j `EmbeddingStore` APIs, with stable document IDs and metadata fields `kb_vectordb_id`, `ad_client_id`, original filename/source ID, content hash, and purge generation. Use `dev.langchain4j:langchain4j-pgvector` at version 1.19.0 if that artifact/API is confirmed in the chosen release; otherwise implement a small adapter over `EmbeddingStore` backed by parameterized JDBC/Etendo datasource, writing `<=>`/cosine SQL and ensuring pgvector activation is explicit. [INFERENCE] The precise LangChain4j artifact coordinates/API availability require verification against 1.19.0 Maven metadata before coding.

Prefer a dedicated store namespace/table over inserting document chunks into db.extended's source model: DB-extended sources represent live rows and have a DB-trigger/outbox contract, whereas KB documents need parsing, chunk provenance, overwrite and app-scoped refresh. Reuse the database only if its pgvector activation policy/storage lifecycle is acceptable; otherwise a separate Chroma-compatible server is not recommended because it reintroduces runtime infrastructure.

`MemoryTool` uses `langchain_chroma`/Chroma (`parity-tools-inventory.md:28`) and is keyed by agent/user memory operations. It is not the same logical corpus as KB (`KB_<appId>` and tenant filter); keep separate namespaces and IDs even if both migrate to one physical pgvector database. [INFERENCE] Chroma persistence paths/collection names may be distinct; inspect `MemoryTool` and `vectordb_utils` before asserting path-level separation.

## Embedding, chunking, retrieval interfaces

Suggested boundaries:
1. `KnowledgeBaseIndexer.index(appId, sourceId, file, IndexOptions, TenantScope)` parses and chunks then embeds/upserts; `reconcile(appId, sources, scope)` runs mark-and-sweep transactionally by generation; `removeSource` deletes source chunks.
2. `KnowledgeBaseStore` implements upsert/search/delete scoped by app and client; expose a small interface so pgvector implementation can be changed without coupling tool code.
3. `KnowledgeBaseSearchTool` is a LangChain4j `@Tool`/`@ToolExecutor` server tool taking `query` and explicit `ToolExecutionContext`; read `OBContext` user/client from the captured context, never thread-local state after executor hops. Bind `kb_vectordb_id` and `search_k` from assistant configuration, cap/validate k, apply client filter at SQL/store level (`0` plus current client), return document text and citation metadata.
4. Parsing: Java `ZipInputStream` plus Apache Tika/PDFBox for file extraction; preserve supported file formats and current chunk controls. LangChain4j `Document`, `TextSegment`, `Embedding`, `EmbeddingStore`, `EmbeddingSearchRequest`, `EmbeddingMatch`, `ContentRetriever`/`EmbeddingStoreContentRetriever`, `DocumentSplitters` are the core APIs; a direct `@Tool` search wrapper is preferable if filtering/context is easier to guarantee than generic retriever defaults.
5. Splitting: retain source extension-sensitive semantics, implement JSON-aware recursive splitting and code-aware boundaries explicitly where LangChain4j splitter primitives do not match Python. Preserve stable IDs/hash-based duplicate detection and overwrite behavior.

Build dependencies: existing `dev.langchain4j:langchain4j` and `langchain4j-open-ai` are already declared at `${langchain4jVersion}` (1.19.0) in copilot `build.gradle`; add `dev.langchain4j:langchain4j-pgvector:${langchain4jVersion}` if selected. Add Apache Tika and/or PDFBox coordinates only if not already supplied by Etendo core; use JDBC/pgvector driver already deployed, avoid duplicate driver. [INFERENCE] pgvector module versioned artifact coordinate must be checked against actual Maven Central 1.19.0 availability.

## Migration and cutover

Chroma local files contain data not transparently queryable through LangChain4j's pgvector store. Provide a full reindex from existing `ETCOP_APP_SOURCE` attachments using the same reset/sync/purge contract. For each app, create new namespace, index every current source using the selected embedding model, verify counts and sampled retrieval under tenant/client scope, then switch assistant configuration/engine to Java. Keep Python and Java stores side by side during staged migration; do not dual-write indefinitely. If original source files are unavailable, require an explicit Chroma export/import utility; embeddings cannot safely be reused if model/dimensions/store layout differ. Image collection/CLIP references are separate behavior and need a separate parity decision, especially the `OcrTool` consumer.

## OCR reference search

Python `find_similar_reference` embeds the first input image with FastEmbed's
`Qdrant/clip-ViT-B-32-vision` model, then queries the agent's Chroma `IMAGES`
collection for the nearest vector (`copilot/core/vectordb_utils.py:595-613`).
The result carries the stored reference path in metadata and its base64 image
in the document (`:648-655`). Matching requires compatible image embeddings
and cannot be reproduced by comparing Etendo files directly.

The toolpack exposes `com.etendoerp.copilot.toolpack.tools.ocr.ReferenceSearch`:
`find(agentId, imagePath, maxDistance, ignoreThreshold)` returns the best
reference (path and image base64) or empty. The KB package must provide the
implementation backed by its pgvector/image embedding store; it must query
only the agent's image-reference corpus, return the nearest reference, and
reject it when L2 distance exceeds `maxDistance` unless `ignoreThreshold` is
true. The threshold is resolved from the same configured OCR similarity
threshold used by Python. Until that implementation is available, OcrTool
runs without a reference and uses its standard extraction prompt.

## Risks

- pgvector is opt-in in db.extended and vectors require extension/schema provisioning; fail clearly without activation rather than silently falling back.
- Embedding model, dimensions, normalization, distance metric mismatch invalidates similarity; persist model/dimension metadata and block mismatched queries.
- Tenant isolation is a security boundary: enforce client visibility in SQL and current-user context; never trust tool arguments for app/client scope.
- Existing current-client refresh intentionally preserves system (`0`) documents; bulk delete must match reset/purge semantics and not erase shared rows.
- Long indexing and model calls should not hold a DAL transaction/session; background/process integration and retries need explicit design.
- Java parsing parity and image embeddings are not guaranteed by plain text pgvector.
- `OBContext` is thread-bound (parity plan risk); LangChain4j concurrent tool execution must propagate/clean context or remain synchronous.
- No direct Copilot→GO dependency; if sharing db.extended APIs, establish its dependency boundary and activation contract explicitly.

## Ordered independently implementable work packages

1. **Contract inventory and store schema** — specify metadata, app/client scope, IDs, refresh/delete semantics, model version and activation gate. Interface: `KnowledgeBaseStore`.
2. **Embedding model factory** — implement `EmbeddingModelFactory` using `copilot.proxy.url` and OpenAI embedding config. Interface: `EmbeddingModel` with dimension/model identity. Add proxy model integration test.
3. **Store adapter** — pgvector implementation with scoped nearest-neighbor query/upsert/delete; test migrations/schema activation, metadata filters, dimension mismatch and client boundaries. Keep independent from parser/tool.
4. **Document ingestion** — parsers, ZIP handling, extension validation, splitters, stable hash IDs, options. Interface: `KnowledgeBaseIndexer`; fixtures for each supported extension and chunk edge cases.
5. **Source synchronization hooks** — replace `CopilotUtils.toVectorDB/resetVectorDB/purgeVectorDB` HTTP calls and `SyncAssistant.syncKBFilesToLangChain` with in-process reconciliation, retaining ETCOP_APP_SOURCE order and failure behavior; identify all attachment/background hooks before cutover.
6. **Retrieval server tool** — add `KnowledgeBaseSearch` to Java tool registry when app has KB; preserve `kb_search_k=4`, app ID binding, client `0` visibility and citations. Tests include cross-client isolation and no-index case.
7. **Migration operation** — process existing app sources into the new store, provide progress/failure visibility and cutover instructions; validate index count/retrieval before enabling Java.
8. **MemoryTool separation/migration** — inventory path/collection and migrate independently to a separate namespace/table; no implicit KB sharing.
9. **Parity/cutover cleanup** — attachments, image/OCR decision, remove route calls and Python KB branches only once all consumers are in Java; add end-to-end acceptance tests.

## Test strategy

- Unit: parser and splitter fixtures (empty/large/overlap, malformed JSON, nested ZIP, invalid/unsupported extension); deterministic chunk hashes, overwrite and refresh reconciliation.
- Store integration: pgvector enabled/disabled, upsert/delete idempotency, dimension/model mismatch, cosine ordering, metadata filter, `ad_client_id=0` and current-client inclusion/exclusion.
- Model: mocked OpenAI-compatible endpoint validates proxy URL, provider-prefixed model name, API key fallback, batch response and dimensions; live proxy smoke test only in a credentialed environment.
- Tool: registry loading, configured k, app namespace binding, tool context as asking non-admin user, context cleanup, tenant isolation, empty index response and citation metadata.
- Synchronization: ETCOP_APP_SOURCE addition/update/removal, bulk reset/reindex/purge equivalent, attachment route/background hooks and failure recovery; migration count and representative query parity.
- End-to-end: Java engine conversation invokes `KnowledgeBaseSearch`; ensure no call to Python vector routes and no GO module dependency.

## Evidence files

- `worktrees/copilot-ETP-5136/copilot/core/routes.py:342-505,507+` — Python reset/index/purge.
- `worktrees/copilot-ETP-5136/copilot/core/kb_utils.py:21-57` — retrieval semantics.
- `worktrees/copilot-ETP-5136/docs/plans/parity/parity-runtime-inventory.md:54-64` — parity inventory and known gaps.
- `modules/com.etendoerp.copilot/src/com/etendoerp/copilot/util/CopilotUtils.java:114-123,343-357,419+,736-749,1094-1099` — Java route/config callers.
- `modules/com.etendoerp.copilot/src/com/etendoerp/copilot/process/SyncAssistant.java:483-489` — full source refresh orchestration.
- `modules/com.etendoerp.copilot/src/com/etendoerp/copilot/util/CopilotConstants.java:44-46` — Java file types.
- `modules/com.etendoerp.db.extended/README.md:18-28,56-83` — optional pgvector and architecture.
- `modules/com.etendoerp.db.extended/src/com/etendoerp/db/extended/vector/VectorStoreService.java:31+`, `VectorOutboxService.java:36+`, `VectorTriggerService.java:39-51`, `OpenAiEmbeddingProvider.java:40+` — generic storage, async changes, trigger provisioning and provider.
- `worktrees/copilot-ETP-5136/src/com/etendoerp/copilot/engine/ChatModelFactory.java:35-48,59-61,104-110` — proxy configuration convention.
- `worktrees/copilot-ETP-5136/build.gradle:18,55-95` — LangChain4j version and current dependencies.
- `worktrees/copilot-ETP-5136/docs/plans/parity/parity-tools-inventory.md:28` — MemoryTool Chroma vector usage.
## Implemented Java slice and remaining cutover work

The Java module now has a parameterized pgvector store, proxy-compatible OpenAI embedding
factory, parser/chunker/indexer, in-process source refresh calls, and an implicit
`KnowledgeBaseSearch` server tool for agents with KB sources. The Copilot update.database
module script creates the table and indexes only when `vector` is already installed; when it is
absent or DDL fails, it logs a warning and lets the update continue. Provisioning is idempotent.
Runtime indexing/search never creates schema and fails with
`pgvector not available / KB table not provisioned, run update.database` when unavailable.

Text sources supported by the Java parser are PDF, Markdown, plain text, Java, Python, JavaScript,
XML, JSON, and ZIP (expanded in memory with a 64 MiB per-entry cap). The existing file extension
allow-list also includes PNG/JPG/JPEG. CLIP vision embeddings are not currently provided: this
classpath has no ONNX Runtime/model artifact and the input pipeline lacks a CLIP-compatible
preprocessor. `ReferenceSearch` therefore remains empty and OCR follows its standard extraction
prompt, matching the documented “no reference found” behavior. BMP/TIFF/GIF/WebP indexing is also
not available in the Java KB.

Legacy Chroma vectors are not imported. Run the existing `SyncAssistant` knowledge-base refresh
for each agent after deploying Java; it resets the agent namespace, indexes current
`ETCOP_APP_SOURCE` records, then purges rows left marked stale. Memory rows use the same physical
pgvector table but `MEMORY_<agentId>` namespaces and user-id scoping, separate from KB collections.
