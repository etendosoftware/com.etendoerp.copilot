# Python runtime vs Java engine — runtime feature parity (ETP-5136)

Scope: `modules/com.etendoerp.copilot/copilot/` (Python) vs worktree `worktrees/copilot-ETP-5136/src/com/etendoerp/copilot/engine/` (Java).
Paths below are relative to the copilot module's `copilot/core/` unless stated. `routes.py`, `app.py` are in `core/` and `copilot/` respectively.
Java engine = `CopilotEngineSelector`, `LangChain4jEngine`, `ChatModelFactory`, `ClientToolSupport`, `ServerToolRegistry`, `ToolExecutionContext`, `PythonCopilotEngine`. It builds system/history/question messages, offers server-side Java tools and client-executed WebMCP tools in a bounded hand-written loop, and retains stateless history replay.

Size key: S ≤ 1–2 days, M ≈ 3–7 days, L > 1 week.

Guard in `CopilotEngineSelector.unsupportedReason` (engine/CopilotEngineSelector.java ~L130-160) — everything in it falls back to Python (or fails in `java-strict`):
1. type not in {`langchain`,`multimodel`} → unsupported (`langgraph`, `openai-assistant`)
2. `tools[]` with a missing Java implementation → unsupported; fully registered Java tools execute in the engine
3. `specs[]` non-empty → unsupported
4. `mcp_servers[]` non-empty → unsupported
5. any `CopilotAppSource` with KB behaviour → unsupported

---

## 1. Agent types

Python type dispatch: `agent/__init__.py:5-20` (`_get_agent_executors`) maps `openai-assistant`, `langchain`, `multimodel` → all to `MultimodelAgent`. `AgentEnum` at `agent/agent.py:14-17`. Default type from env `agent.type` (`routes.py:123-131`). Java constants: `CopilotConstants.java:9-12` (`langchain`, `langgraph`, `multimodel`, `openai-assistant`). Java routes to graph endpoints via `CopilotUtils.checkIfGraphQuestion` (CopilotUtils.java:1139) → `RestServiceUtil.determineEndpoint` (RestServiceUtil.java:550).

| Feature | Python implementation (file:line) | Java engine status | What Java needs | Size |
|---|---|---|---|---|
| `multimodel` / `langchain` single agent | `agent/multimodel_agent.py:225` `MultimodelAgent`; `get_agent` L253; uses `langchain.agents.create_agent` (L304) with tools + `system_prompt` + checkpointer + `response_format` | **partial** — plain chat and generic CDI server-tool loop are supported; concrete `ETCOP_TOOL` implementations, KB/MCP/specs remain unavailable (guard 2–5); `LangChain4jEngine.execute` replays server tool results and short-circuits client tools | Add concrete tool implementations and continue through KB/MCP/spec parity work | M |
| `openai-assistant` | Type string still accepted and mapped to `MultimodelAgent` (`agent/__init__.py:9`); `GET /assistant` raises "no longer supports OpenAI Assistant" (`routes.py:339-341`); `members_util.py:97-98` raises NotImplementedError for it in teams. Java still has Assistants API code in `OpenAIUtils.java` (ENDPOINT_ASSISTANTS L53) and `CopilotConstants.PROVIDER_OPENAI_VALUE` | **missing but dead in Python** — selector rejects the type (guard 1) | Decide: treat as alias of `multimodel` in Java (what Python does effectively). No Assistants-API client needed. | S |
| `langgraph` supervisor / team | `routes.py:171` `POST /graph`, `routes.py:219` `POST /agraph`; `agent/langgraph_agent.py:238` `LanggraphAgent` (execute/aexecute); `langgraph/members_util.py:57` `MembersUtil.get_members/get_member` (each member = `create_agent` with its own LLM+tools); `langgraph/patterns/langsupervisor_pattern.py:21` `LangSupervisorPattern` → `langgraph/patterns/supervisor.py:69` `create_supervisor` (own copy of langgraph-supervisor); supervisor prompt/temperature `special_nodes/supervisor_node.py:122,143`; optional `TaskManagementTool` supervisor tool `langgraph/tool_utils/TaskManagementTool.py:27` (mark_done/status/add_tasks/get_next); structured output via `response_format=get_structured_output` | **missing** — guard 1 (type langgraph) | Own supervisor loop in Java: supervisor `ChatModel` with one `transfer_to_<member>` tool per member, each member an `AiServices`/loop agent with its own tools; handoff + message-history trimming; TaskManagementTool needs a state object. LangChain4j has `langchain4j-agentic` (`SupervisorAgent`, `AgenticServices`) — [INFERENCE] could replace most of this but needs evaluation | L |
| Legacy graph patterns (dead code) | `patterns/supervisor_pattern.py:12` `SupervisorPattern`, `patterns/loop_pattern.py:10` `LoopPattern`, `patterns/base_pattern.py:14`, `special_nodes/output_node.py:8`, `OutputNode`, `SupervisorNode.build` (supervisor_node.py:69). `setup_graph` (langgraph_agent.py:60-72) only instantiates `LangSupervisorPattern`; `AssistantGraph/stages` from request (`schemas.py:20-27`) is passed but not used by it | n/a — [INFERENCE] unreachable from routes; verify with grep before dropping | Do not port. Delete with Python. | – |
| Graph diagram | `generate_image` flag → `cgraph.get_graph().draw_mermaid_png()` (`langgraph_agent.py:~290`); Java caller `RestServiceUtil.getGraphImg` (RestServiceUtil.java:~1005) posts to `/graph` with `generate_image=true` | **missing** | No LangChain4j equivalent; build Mermaid text from members/supervisor in Java and render (mermaid-cli/kroki) or return Mermaid text and change the UI | M |
| CodeAct / `code_execution` | `agent/multimodel_agent.py:85` `is_code_act_enabled` (reads `code_execution`), `:233` `_create_code_act_agent`; `agent/codeact/__init__.py:54` `create_codeact` (LLM writes python block → sandbox node → loop; `create_default_prompt` L28); `agent/codeact/utils.py:6` block extraction. Java sends `code_execution` = `CopilotApp.isCodeInterpreter()` (CopilotUtils.java:1094) | **missing** — not in guard explicitly; `code_execution` is simply ignored by Java engine (silent capability loss) [observed: selector doesn't check it] | Java cannot exec Python. Either (a) drop feature, (b) JVM scripting (GraalJS / Groovy / JShell) with tools as bound functions, or (c) keep a minimal remote sandbox service. Also add `code_execution` to `unsupportedReason`. | L |
| Evaluators (`code_evaluators`) | `agent/eval/code_evaluators.py:14` `OriginalExecutor` (raw `exec` with `builtins`, redirect stdout), `:52` `CodeExecutor` (types `original`/`riza`; injects `ETENDO_TOKEN` into locals L64-70), `:73` `RizaExecutor` (remote Riza sandbox, needs `riza` SDK). Only `CodeExecutor("original")` is used (multimodel_agent.py:240). Note: `pypy_path` param is accepted but pypy branch is not implemented in `__init__` | **missing** | Same decision as CodeAct. Java option: Riza HTTP API client (S) or GraalPy/GraalJS sandbox (M). `exec` on host is a security issue regardless | M (tied to CodeAct) |

## 2. Memory / checkpointing / history

| Feature | Python implementation | Java engine status | Needs | Size |
|---|---|---|---|---|
| SQLite checkpointer per conversation | `agent/agent_utils.py:102` `get_checkpoint_file` → `/checkpoints/<agent>_<conv>_checkpoints.sqlite` (docker) or `./checkpoints/`; `AsyncSqliteSaver.from_conn_string` in `multimodel_agent.py:353,~400` and `langgraph_agent.py:~280,~345`; graph also opens a shared `store.sqlite` store (`langgraph_agent.py:29,~281`) | **missing / not needed today** — Java engine is stateless; replays `ETCOP_MESSAGE` history each turn (`LangChain4jEngine.buildMessages` L~100-130) | LangChain4j `ChatMemory`/`ChatMemoryStore` backed by DAL (or just keep stateless replay). Tool-call/tool-result messages are lost in a stateless replay (Python checkpoint keeps them); store them if needed for fidelity. Per-conversation SQLite files go away | M |
| History replay when no thread | `memory/memory_handler.py:5` `MemoryHandler.get_memory` (USER→Human, ASSISTANT→AI); used only when checkpointer has no thread (`multimodel_agent.py:195,421-451` `get_messages_array`, `has_thread`) | **supported** — `LangChain4jEngine.addHistoryMessage` replays USER/ASSISTANT from `history` | – (note: Python ignores `history` when a checkpoint exists; Java always replays — semantics differ slightly but are consistent) | S |
| Thread context / extra_info | `threadcontext.py:22` `ThreadContext` (ContextVar + `_conversation_contexts` dict keyed by conversation_id); `routes.py:135-143` `load_thread_context` stores `extra_info`; `app.py:37-60` middleware | **partial** — `ToolExecutionContext` captures identity and defensive `extra_info` for Java server tools; client-only requests do not consume it | Pass the snapshot to each server tool; remove Python ThreadContext with runtime cutover | S |
| Local history CSV | `local_history.py:21` `LocalHistoryRecorder` writes `/tmp/chat_history.csv` per answered question (`routes.py:114-118,176-178`); `GET /history` `routes.py:333` | **missing**; debug-only feature, nothing in Java calls `/history` (grep) | Drop | S |
| Recursion / iteration limit | `langgraph_agent.py:~225` `build_config`: `recursion_limit` from `langgraph.recursion.limit` (500), `max_iterations` 100 | **supported for client/server tool loops** — both use `copilot.engine.java.max.tool.rounds` (default 25) | Keep the shared cap | S |

## 3. Streaming / SSE protocol

| Feature | Python implementation | Java engine status | Needs | Size |
|---|---|---|---|---|
| `POST /question` (sync) | `routes.py:267` → `_serve_question_sync` L111; returns `{"answer": <AssistantResponse>}` | **supported** (`LangChain4jEngine.execute`, asyncRequest=false returns `{"answer":{response,conversation_id}}`) | – | – |
| `POST /aquestion` (SSE) | `routes.py:315`, `_serve_question_async` L272 (queue pump `gather_responses` L102), `_response` L70 serialises `AssistantResponse`; SSE `data: {"answer": {...}}` | **partial** — Java emits only an empty handshake frame + ONE final frame (`LangChain4jEngine.stream` L~85). "No token-level streaming" is deliberate (docs §5) | Emit intermediate `node`/`tool` frames per step; LangChain4j `StreamingChatModel`/`ToolExecutor` callbacks give the hooks | M |
| `POST /graph` / `POST /agraph` | `routes.py:171,219,224` (`_serve_agraph`), `LanggraphAgent` | **missing** (see §1) | – | counted in §1 |
| Event handling → roles | `agent/langgraph_agent.py:`  `handle_events` (L~201), `_handle_on_chain_start` → role `node` ("X is thinking...", `get_message`), `_handle_on_tool_start` → role `tool` (tool name; only when `len(parent_ids)==1`), `_handle_on_chain_end` → final answer (+ `structured_response`, usage `metadata`), `copilot.stream.debug` → role `debug` (`str(event)`), errors → role `error` (multimodel_agent.py:~415, routes.py:296-306) | **partial** — Java engine only produces the final answer and `role:"tool_call"` (client tools, ClientToolSupport). Java *relay* side (`RestServiceUtil.serverSideEvents` L339, `isErrorOrNull` L~413) already understands roles `error`/`null`; `node`/`tool` frames pass through to UI (chat front-end renders each frame as a bubble) | Map LangChain4j listeners (`ChatModelListener`, `ToolExecutionListener`/`BeforeToolExecution`) to the same frame shapes; produce `role:"error"` frames on exceptions (today Java throws OBException) | M |
| Error shaping | `routes.py:151-163` `_handle_exception` (extracts `e.response.content.error.message` from provider HTTP errors), `routes.py:288-306` yields `role=error` frame | **partial** — `LangChain4jEngine` wraps in `OBException("Copilot (Java engine) error: ...")`; not shaped into an SSE error frame | Provider-exception → message mapping; emit `role:error` frame | S |
| Empty-response check | `multimodel_agent.py:~361`, `langgraph_agent.py:~323`: raise if empty content and 0 output tokens | **partial** — Java throws on empty text (L~65) but doesn't consider output tokens | trivial | S |
| Content normalisation (Gemini list blocks) | `agent/agent_utils.py:153` `normalize_content` | **supported by LangChain4j** (`AiMessage.text()`) | – | – |

## 4. Knowledge base / vector DB

| Feature | Python implementation | Java engine status | Needs | Size |
|---|---|---|---|---|
| KB retrieval tool `KnowledgeBaseSearch` | `kb_utils.py:21` `get_kb_tool`: Chroma persisted at `get_vector_db_path` (`vectordb_utils.py:65-93`, `/app/vectordbs/<id>.db` or `./vectordbs`), `retriever.as_tool` with filter `ad_client_id in ["0", client]`, `k=kb_search_k` (default 4), id = `KB_<appId>` (Java: CopilotUtils.java:1095) ; added by `tool_loader.py:264 _add_kb_tool` | **missing** — guard 5 (`isKbBehaviour`) | LangChain4j `EmbeddingStore` + `ContentRetriever`/`EmbeddingStoreContentRetriever` exposed as a tool. Store: `pgvector` (Etendo already on PostgreSQL, can live in the same DB — removes per-agent chroma folders) or in-memory/file store. Must preserve client isolation filter | L |
| Embeddings | `vectordb_utils.py:37` `get_embedding` → `OpenAIEmbeddings(base_url=proxy, api_key=openai.api.key or "dummy")`; model = langchain-openai default [INFERENCE: `text-embedding-ada-002`, verify in lib]. Proxy through `copilot.proxy.url` | **missing** | `OpenAiEmbeddingModel` (langchain4j-open-ai) with same proxy/baseUrl logic as `ChatModelFactory`. **Existing Chroma indexes can't be reused** if model/dimension differ — plan full re-index (`SyncAssistant` already supports it) | S |
| Indexing route `POST /addToVectorDB` | `routes.py:414-507` (multipart: `kb_vectordb_id, filename, extension, overwrite, file, skip_splitting, max_chunk_size, chunk_overlap, ad_client_id`); `index_file` `vectordb_utils.py:203`, `handle_zip_file` L120 (zip extraction, ALLOWED_EXTENSIONS pdf/txt/md/markdown/java/js/py/xml/json), `process_file` L340 + `process_pdf` L390 (PDF via temp file), sha256 dedupe (L372, `hash_splitter_config` L184), `overwrite` flag. Java caller: `CopilotUtils.toVectorDB` (CopilotUtils.java:18-131) ← `FileUtils.java:170` | **missing** | Java parsing: Apache Tika / PDFBox, LangChain4j `DocumentSplitters`/`DocumentByRegex`/language splitters; zip via `java.util.zip`. Port sha256 + `overwrite` + purge semantics into metadata | L |
| Splitters | `vectordb_utils.py:272` `get_text_splitter` (Markdown, CharacterTextSplitter for txt/pdf/xml, `RecursiveJsonSplitter` json, `RecursiveCharacterTextSplitter.from_language` java/js/py); `splitters.py:6` `CopilotRecursiveJsonSplitter` | **missing** | LangChain4j has recursive/paragraph/sentence/regex splitters; no language-aware or JSON splitter built in → own code for JSON and code-aware splitting (or accept recursive char splitting) | M |
| Reset / purge routes | `POST /ResetVectorDB` `routes.py:344` (marks every doc in `langchain` + `IMAGES` collections with `purge=True`, batches of 5000, filter via `get_where_filter` L404); `POST /purgeVectorDB` `routes.py:509` (deletes docs flagged `purge`). Java callers: `CopilotUtils.resetVectorDB` (L343) and `purgeVectorDB` (L736), both from `SyncAssistant.syncKBFilesToLangChain` (SyncAssistant.java:483-489) | **missing** | Mark-and-sweep in metadata, or simply delete-by-filter (`EmbeddingStore.removeAll(Filter)`) since both calls are always paired | S |
| Image reference index (visual similarity) | `vectordb_utils.py:451` `index_image_file` (fastembed `ImageEmbedding`/CLIP, collection `IMAGES`, md5 dedupe), `:554` `find_similar_reference`, `:623 filter_by_distance`, `:683,701,721,740` helpers; routed in `addToVectorDB` for `IMAGE_EXTENSIONS` (png/jpg/jpeg/bmp/tiff/gif/webp, `routes.py:~446-465`). Consumer = toolpack `OcrTool.py` (only user, grep) | **missing** | CLIP via ONNX (`langchain4j-embeddings`? no CLIP model shipped — [INFERENCE]) or DJL; consider dropping/rewriting with a vision-LLM compare. Tied to OcrTool in ToolInventory | L |
| KB on non-Python path (OpenAI hosted) | `OpenAIUtils.java` Assistants/vector_stores code (ENDPOINT_VECTORDB L54) used when `!isCodeInterpreter && !isRetrieval` (OpenAIUtils.java:548) | n/a (Java) | Legacy; remove with openai-assistant type | S |

## 5. MCP

| Feature | Python implementation | Java engine status | Needs | Size |
|---|---|---|---|---|
| MCP client — consume external MCP servers | `agent/multimodel_agent.py:43` `convert_mcp_servers_config` (name→cfg, `disabled`, default transport `stdio`), `:154` `get_mcp_tools` (`MultiServerMCPClient`, 45 s timeout, schema fixes `_fix_array_schemas` L134; failures swallowed → `[]`), `:100-151` schema fixers; wired in `tool_loader.py:~320-330 get_all_tools`. Java builds `mcp_servers` (CopilotUtils.java:1127) from `MCPUtils`/`MCPConfigNormalizer`/`MCPValidationHandler` | **missing** — guard 4 | LangChain4j `langchain4j-mcp`: `McpClient` + `McpToolProvider` (transports: stdio, streamable HTTP, SSE, docker-stdio). stdio servers spawn child processes inside Tomcat — needs an allow-list/sandbox policy. Port the schema sanitisers (array `items`, object `properties`) and the 45 s timeout | M |
| MCP server — dynamic per-agent server exposed by Python | Standalone FastMCP/uvicorn process thread started in `run.py:53` → `mcp/simplified_dynamic_manager.py` / `simplified_dynamic_server.py:366` `SimplifiedDynamicMCPServer` (default port 5007 internal; Java property `copilot.port.mcp` default 5006, `GetMCPConfiguration.java:526-528`; per-agent instances on ports ≥5008, TTL monitor L181, port pool). Routes: `GET /` L408, `GET /health` L426, `GET /instances` L440, `ANY /{identifier}/mcp[/path]` and `/{identifier}/direct/mcp[/path]` L498-511 (on-demand instance + reverse proxy `_proxy_request` L641). Tools per agent: prompt tool `_gen_prompt_tool` (`mcp/tools/agent_tools.py:647`), every Copilot tool converted `convert_langchain_tools_to_mcp` (L466), supervisor in direct mode → `_make_team_ask_agent_tools` L699, `ask_agent_<name>` L726 (posts to Etendo `/sws/copilot/question`), agent structure fetched from Etendo `GET /sws/copilot/structure?app_id=` (L88-120; Java route `RestService.java:107`). Auth: bearer = Etendo JWT, extracted `mcp/auth_utils.py:17`, stored as `extra_info.auth.ETENDO_TOKEN` (server L~535); OAuth 2.1 (`mcp/oauth_provider.py:83` `EtendoOAuthProvider`, `oauth_login.py:295-309` `GET/POST /oauth/login`, `/oauth/login/select`, SDK routes `/authorize /token /register /revoke /.well-known/*`) ; `mcp/auth_provider.py:42` `CopilotAuthProvider.verify_token`. Basic tool files `mcp/tools/basic_tools.py`, `session_tools.py` (no-op), `base.py` (abstract). Java produces client configs: `GetMCPConfiguration` process | **missing** (no Java MCP server at all) | Java MCP server: official MCP Java SDK (`io.modelcontextprotocol.sdk:mcp`, servlet transport) or LangChain4j-less hand-rolled JSON-RPC under a servlet (`/sws/copilot/mcp/{id}`) — no extra port or process needed; tool list built from Java tool registry, bearer → SWS JWT validated in-process (`OBContext` already available, no token re-fetch from `/structure`); OAuth 2.1 authorize/token/register + login page need re-implementation (or delegate to existing Etendo auth/SWS). Instance TTL/port pool disappear. `GetMCPConfiguration` URL building changes (no port 5006) | L |
| Tool registration conversion (`LangChain tool → MCP Tool`) | `mcp/tools/agent_tools.py:123-340,386-497` (exec-generated wrapper signatures, arg sanitiser L39) | **missing** | With a Java tool registry that already has JSON-schema + invoke, the conversion is direct | M |

## 6. OpenAPI / FLOW spec tools (`toolgen/`)

| Feature | Python implementation | Java engine status | Needs | Size |
|---|---|---|---|---|
| `specs[]` of type `FLOW` → tools | `tool_loader.py:273 _add_openapi_tools` (only `spec.type == "FLOW"`; `copilot.old.openapi.tools` env switches to legacy `generate_tools_from_openapi_old`); new generator `toolgen/openapi_tool_gen.py:543` `generate_tools_from_openapi` (operation→tool name `_generate_tool_name` L298, `sanitize_tool_name` L33; params `_process_openapi_parameters` L110; Etendo headless special cases `_is_etendo_headless_get/put/post` L72-93, `_process_etendo_headless_param` L93; request body models incl. oneOf/array L186-263; codegen via `exec` `_generate_function_code` L312; `replace_base64_filepaths` L19 (swap file paths for base64 in body)); legacy `toolgen/ApiTool.py:151` `ApiTool`, `generate_tools_from_openapi` L285; HTTP executor `toolgen/api_tool_util.py:43 do_request`, `do_post_put` L144, `token_not_none` L8 (replaces literal `ETENDO_TOKEN` bearer with real token). Java builds `specs` (CopilotUtils.java:1122), `OpenAPISpecFlowFile`/`OpenAPIDoc` hooks | **missing** — guard 3 | Parse OpenAPI (swagger-parser / `io.swagger.v3`, already used by `OpenAPIDoc.java`), build `ToolSpecification` (JsonObjectSchema) per operation, executor via `java.net.http.HttpClient`. **Bonus:** for calls to the same Etendo instance, short-circuit to in-process servlet/DAL instead of HTTP + token. Port: headless param rules, base64 file swap, summarise-large-response (`ApiTool.summarize` L100) | L |
| Auth for spec calls | `api_tool_util.py:8-40` converts `ETENDO_TOKEN` literal to `extra_info.auth.ETENDO_TOKEN` (`utils/etendo_utils.py:63 get_etendo_token`) | **missing** | Use OBContext / mint SWS token in-process (`CopilotUtils.getEtendoSWSToken` already exists, CopilotUtils.java:826) | S |

## 7. Tool runtime, loading, introspection

| Feature | Python implementation | Java engine status | Needs | Size |
|---|---|---|---|---|
| Tool loading / filtering | `tool_loader.py:46` `ToolLoader` (singleton), `load_configured_tools` L215 (all `ToolWrapper`/`CopilotTool` subclasses), `_get_filtered_base_tools` L253 (only tools named in request `tools[]`; none → no tools), `get_all_tools` L293 (base + KB + OpenAPI + MCP; sets `agent_id` on tools), dependency install `_install_enabled_tool_dependencies` L85, `tool_installer.py` (uv pip), `tool_dependencies.py`, `tools_deps.toml` per module, `_import_tools` L104 / `_generate_dynamic_tool_config` L122 | **missing** — guard 2 (see ToolInventory report for per-tool detail) | Java tool registry: CDI `@Named` beans (same pattern as `NeoHandler`) implementing a `CopilotTool` interface (name, JSON schema, `execute(args, ctx)`); filter by `tools[]` names from `ToolsUtil.getToolSet`. No pip install step | L |
| Tool base class | `tool_wrapper.py:127` `CopilotTool`, `:138` `ToolWrapper` (`run`, `_run/_arun`, input validation `arun` L153, error → `ToolOutputError` via `parse_response` L43, `accum_params` L13), `tool_input.py:4` `ToolInput`/`ToolField` | **missing** | Java interface + Jackson/`JsonObjectSchema` validation | M |
| `GET /tools` introspection | `routes.py:320` returns `{answer:{name:{description,parameters}}}`. Java caller: `SyncToolStructure.java:69-72` (process action "sync tool structure") and `CopilotSyncStartup` | **missing** (Java would implement natively; no HTTP) | Replace with a call to the Java registry; `SyncToolStructure` stops using HTTP | S |
| `POST /executeTool` | `routes.py:566` (run one tool directly, `agent_id`, `extra_info`); **no Java caller found** (grep over `src/`) — [INFERENCE] used by external clients/webhooks | **missing** | Direct registry invoke endpoint if still wanted | S |
| Provider schema fixes for Gemini | `utils/agent.py:~100-250` `fix_tools_for_provider` (`_fix_array_schemas`, bare `list`→`list[str]`, Union simplification, only for `google_genai`) | **missing** | Equivalent JSON-schema sanitiser in Java when building `ToolSpecification` for Gemini | S |

## 8. Files / attachments / multimodal

| Feature | Python implementation | Java engine status | Needs | Size |
|---|---|---|---|---|
| `POST /attachFile` | `routes.py:595` saves to `/copilotAttachedFiles/<uuid>/<name>` (docker) or `$CWD/...`; returns path in `answer`. Java: `RestService.java:195` (`handleFile(..., "attachFile")` → `RestServiceUtil.handleFile` L225-235), `AddBulkTasks.java:228` | **missing** — file bytes live inside the Python container FS | Save to Etendo attachments dir / temp dir managed by Java; return an id that the engine can resolve to bytes. Cleanup policy | S |
| Local file ids in prompt | Java sends `local_file_ids` (RestServiceUtil.java:853-865) + appends " Local files: ..." text to question (`appendLocalFileIds` L869). Python: `utils/agent.py:21 get_full_question` (adds absolute paths), `agent/agent_utils.py:8 process_local_files` (split L83, mime L66), graph: `langgraph_agent.py:32 fullfill_question` | **missing** — engine uses `question` verbatim; paths in text are meaningless to Java engine | Resolve ids → files in Java; images → content; others → text/tools (OCR) | M |
| Image / multimodal input | `agent_utils.py:43 get_payload` builds `image_url` data-URI blocks (JPEG/JPG/PNG/WEBP/GIF); added as extra content blocks of the HumanMessage (`multimodel_agent.py:421-451`, `langgraph_agent.py:~221 build_msg_input`); other files → "Attached files:" text block with paths | **missing** — `UserMessage.from(String)` only | LangChain4j `UserMessage.from(TextContent, ImageContent.from(base64, mime))`; supported by all 4 providers' models | S |
| `POST /transcription` | `routes.py:609` saves upload to `/copilotTranscripts/<uuid>/` then OpenAI `whisper-1` via `utils/models.py:40 get_openai_client`. Java: `RestService.java:198`, OpenAPIDoc path `/sws/copilot/transcription` | **missing** | `OpenAiAudioTranscriptionModel` is not in core LangChain4j [INFERENCE — verify]; simplest = direct `HttpClient` multipart POST to `{proxy or api.openai.com}/v1/audio/transcriptions` (proxy-aware) | S |
| Docker-vs-local path logic | `baseutils/logging_envvar.py is_docker`; `GET /runningCheck` `routes.py:561`; Java `CopilotUtils.isCopilotRunningInDocker` L572 → used to decide file paths | **n/a** | Disappears | – |

## 9. Structured outputs

| Feature | Python | Java status | Needs | Size |
|---|---|---|---|---|
| JSON-schema structured output | `utils/agent.py:289 get_structured_output` reads `output_schema` (string) → dict → `create_agent(response_format=...)` (`multimodel_agent.py:309`), supervisor `response_format` (`langsupervisor_pattern.py`); result `structured_response` serialised with `json.dumps` (multimodel_agent.py:373-380) | **missing** — and **field-name mismatch found**: Java sends `structured_output_json_schema` (RestServiceUtil.java:121, CopilotUtils.java:954,1096) but Python's `AssistantSchema` field is `output_schema` (`schemas.py:55`); Pydantic ignores unknown keys, so [INFERENCE] structured output never reaches Python today unless another hook renames it (grep of Java found none). Java engine does not read either | `ChatRequest.responseFormat(ResponseFormat.builder().type(JSON).jsonSchema(...))` (OpenAI/Gemini/Ollama; Anthropic via tool-forcing) — decide the single field name and port | S |

## 10. Provider support (`get_llm`)

`utils/agent.py:35-112` `get_llm`; `utils/models.py:11 get_proxy_url`, `:22 get_api_key`; Java `ChatModelFactory.java:79-150`.

| Provider / mode | Python | Java engine | Needs | Size |
|---|---|---|---|---|
| Proxy (`copilot.proxy.url`) | all providers via OpenAI client, model `"<provider>/<model>"`, key = `openai.api.key` or `"dummy"`, `stream_options.include_usage` | **supported** (ChatModelFactory L105-118) | – | – |
| OpenAI direct | `init_chat_model(model_provider=…)` | **supported** (L139) + `copilot.openai.base.url` override | – | – |
| Anthropic | via `init_chat_model("anthropic")` (needs `ANTHROPIC_API_KEY` env / `anthropic.api.key`) | **supported** (`AnthropicChatModel`, L120) | – | – |
| Google/Gemini | alias `gemini`→`google_genai` native SDK (`_PROVIDER_ALIAS_MAP` L10) | **supported** (`GoogleAiGeminiChatModel`, L129) | – | – |
| Ollama | `provider` containing "ollama": `copilot.ollama.host`/`port`, streaming | **supported** (L93-103, `ollama.base.url`) | host/port property names differ (`copilot.ollama.host|port` vs Java `ollama.base.url`) — align | S |
| Other providers | whatever `init_chat_model` supports (e.g. mistral, groq, azure, bedrock, deepseek, ...) — [INFERENCE] depends on installed langchain-* packages in `pyproject.toml` | **partial** — non-listed provider falls to OpenAI-compatible branch | Add LangChain4j modules as required (azure-open-ai, mistral, bedrock, ...) | S each |
| Model config `max_tokens` | `utils/agent.py:~255 get_model_config` from `extra_info.model_config[provider][model]` | **supported** (ChatModelFactory.java:165-185) | – | – |
| Temperature | param from request | **supported** | – | – |
| Timeouts / retries | none set (provider defaults) | Java sets 120 s (`copilot.engine.java.timeout.seconds`) | – | – |
| LangSmith tracing | `run.py:~40` sets `LANGSMITH_TRACING`, `LANGCHAIN_API_KEY` from `langsmith.tracing`, `langchain.api.key` | **missing** | LangChain4j `ChatModelListener` + OpenTelemetry/Langfuse, or drop | S |

## 11. Prompt assembly

| Feature | Python | Java | Needs | Size |
|---|---|---|---|---|
| System prompt | Built entirely in Java (`CopilotUtils.buildLangchainRequestForCopilot` L~1083-1106: `copilotApp.getPrompt()`, `getAppSourceContent` system/question behaviours, `#MEMORY#` tag etc.) and sent as `system_prompt`; Python passes it to `create_agent(system_prompt=…)` (`multimodel_agent.py:304`); fallback env `system.prompt` (`agent.py:28-35`, `_assert_system_prompt_is_set`) ; supervisor prompt `special_nodes/supervisor_node.py:7 build_system_prompt` / `:122 get_supervisor_system_prompt` (team: prompt + member names/descriptions); CodeAct prompt `agent/codeact/__init__.py:28 create_default_prompt` | **supported** for single agent (`LangChain4jEngine.buildMessages`: `SystemMessage`) | Supervisor prompt builder for teams; CodeAct prompt only if CodeAct kept | S |
| Question augmentation | Java appends question-behaviour sources + local-file text; Python adds file path text (`get_full_question`) | **partial** (engine takes Java's question; no file handling) | see §8 | – |
| `laststeps` alias | **Not found**: `grep -rni laststeps` returned no hits in Python `copilot/` nor in the Java worktree `src/`/`docs/`. Not a runtime feature of either side as inspected — [INFERENCE] it may live in the UI/chat module or another repo; flagging for confirmation | n/a | – | – |
| Agent description for teams | `members_util.py:125 get_assistant_supervisor_info`, `AssistantSchema.description` | **missing** | with teams | – |

## 12. Token / usage accounting

| Feature | Python | Java | Needs | Size |
|---|---|---|---|---|
| Usage totals into response metadata | `threadcontextutils.py:7 read_accum_usage_data`, `:33 read_accum_usage_data_from_msg_arr` (sums `usage_metadata` input/output tokens of messages after last human message; accumulates in `ThreadContext.usage_data`); `agent_utils.py:129 build_metadata` → `{input_tokens, output_tokens, total_tokens}` in `AssistantResponse.metadata` (`routes.py:70-100` serialises) ; used in `multimodel_agent.py:~358`, `langgraph_agent.py` `_handle_on_chain_end` | **missing** — `LangChain4jEngine` never reads `ChatResponse.tokenUsage()`; answer carries only `response`+`conversation_id`. Java consumer: `processResponseAndTrack` / tracking in `RestServiceUtil` (metadata parse [INFERENCE: not opened]) | `ChatResponse.metadata().tokenUsage()` → sum across tool rounds → add `metadata` object to answer | S |

## 13. Other Python HTTP routes (complete route table)

FastAPI app `copilot/app.py`, routers in `core/routes.py`, run by `run.py` (uvicorn on `copilot.port`, default 5005; MCP server in a thread, `copilot.port.mcp`).

| Route | File:line | Purpose | Java caller | Java engine status | Disappears with Python? |
|---|---|---|---|---|---|
| `GET /` | app.py:64 | redirect to docs; used as Docker/startup liveness | `CopilotSyncStartup.waitForCopilotService` (CopilotSyncStartup.java:~255-290) | n/a | yes |
| `POST /question` | routes.py:267 | sync agent answer | `PythonCopilotEngine` via `determineEndpoint` | supported (subset) | yes |
| `POST /aquestion` | routes.py:315 | SSE answer | `PythonCopilotEngine` | partial | yes |
| `POST /graph` | routes.py:171 | sync team graph | `PythonCopilotEngine`; `RestServiceUtil.getGraphImg` (L1005-1060) | missing | yes |
| `POST /agraph` | routes.py:219 | SSE team graph | `PythonCopilotEngine` | missing | yes |
| `GET /tools` | routes.py:320 | tool catalogue | `SyncToolStructure.java:69-72` | missing | yes |
| `GET /history` | routes.py:333 | CSV chat log (debug) | none | missing (drop) | yes |
| `GET /assistant` | routes.py:339 | raises "no longer supported" | none | drop | yes |
| `POST /ResetVectorDB` | routes.py:344 | mark docs for purge | `CopilotUtils.resetVectorDB` (L343) ← `SyncAssistant` | missing | yes |
| `POST /addToVectorDB` | routes.py:414 | index file into KB | `CopilotUtils.toVectorDB` (L18/112) ← `FileUtils.java:170` | missing | yes |
| `POST /purgeVectorDB` | routes.py:509 | delete flagged docs | `CopilotUtils.purgeVectorDB` (L736) ← `SyncAssistant` | missing | yes |
| `GET /runningCheck` | routes.py:561 | docker/pycharm | `CopilotUtils.isCopilotRunningInDocker` (L572) | n/a | yes |
| `POST /executeTool` | routes.py:566 | direct tool run | none found in Java src | missing | yes |
| `POST /attachFile` | routes.py:595 | store upload | `RestService.java:195`, `RestServiceUtil.handleFile` L225, `AddBulkTasks.java:228` | missing | yes |
| `POST /transcription` | routes.py:609 | Whisper | `RestService.java:198` | missing | yes |
| `POST /checkCopilotHost` | routes.py:628 | Python calls back Etendo `POST /sws/copilot/configcheck` with caller's token to validate `etendo.host.docker`; returns "success"/"failed" | `CheckHostsButton.java:110-127` | n/a — check meaningless without container | yes |
| MCP `GET /`, `/health`, `/instances` | mcp/simplified_dynamic_server.py:408,426,440 | MCP server meta | none | – | yes |
| MCP `ANY /{id}/mcp*`, `/{id}/direct/mcp*` | simplified_dynamic_server.py:498-511 | per-agent MCP endpoint | external MCP clients; URL built by `GetMCPConfiguration` | missing | yes (re-implement in Java) |
| MCP OAuth: `/oauth/login` (GET,POST), `/oauth/login/select`, SDK `/.well-known/oauth-*`, `/authorize`, `/token`, `/register`, `/revoke` | mcp/oauth_login.py:295-309; oauth_provider.py (`EtendoOAuthProvider`) | MCP client auth | external MCP clients | missing | yes |
| Handlers | `handlers.py` (validation→400, ApplicationError→400, other→500) | error mapping | – | n/a | yes |

## 14. Python → Etendo callbacks (Python currently calls Java)

| Callback | Python file:line | Java side | After removal |
|---|---|---|---|
| `POST /sws/copilot/configcheck` | routes.py:628-667 | `RestService.java:206` `checkEtendoHost` | disappears |
| `GET /sws/copilot/structure?app_id=` | mcp/tools/agent_tools.py:88-120 | `RestService.java:107` | disappears (in-process) |
| `POST /sws/copilot/question` (ask_agent tool) | mcp/tools/agent_tools.py:726-790 | `RestService` question handler | in-process call |
| Tool HTTP/Webhook calls to Etendo with `ETENDO_TOKEN` | `utils/etendo_utils.py:121 call_etendo`, `:209 call_webhook`, `:239 request_to_etendo`, `:281 simple_request_to_etendo`, `:179 login_etendo`; token from `extra_info.auth.ETENDO_TOKEN` (`:63 get_etendo_token`, `:88 get_extra_info`); Java mints it in `CopilotUtils.getAuthJson` (L805) / `RestServiceUtil.java:918-922` | token minting (`getEtendoSWSToken` L826) | replaced by OBContext/DAL in tools (see AuthInventory) |
| `tool_config` per agent/tool | Java `hook/ToolModelsConfig.java` → `extra_info.tool_config` | consumed by tools via `agent_id` | replaced by direct DAL read |

---

## Java ↔ Python touchpoints that disappear once Python is removed

| # | Touchpoint | Where (Java) | Replacement |
|---|---|---|---|
| 1 | `copilot.host` / `copilot.port` properties (constants `COPILOT_HOST/PORT`) | `CopilotUtils.java:81-82,163-164,235-236,627-639`; `PythonCopilotEngine.java:39-40`; `RestServiceUtil.java:1016-1017`; `SyncToolStructure.java:69-70`; `CopilotSyncStartup.java:257-258`; `CheckHostsButton.java:110-111` | none (delete) |
| 2 | `PythonCopilotEngine` and `CopilotEngineSelector` fallback/`copilot.engine=python|java|java-strict` switch, `unsupportedReason`, `warnIfClientToolsWillBeIgnored` | `engine/` | `LangChain4jEngine` becomes the only engine |
| 3 | `CopilotUtils.getResponseFromCopilot` / `doGetCopilot` / `createMultipartBody` (generic HTTP+multipart to Python) | CopilotUtils.java:158-260 | delete after KB/files are in-process |
| 4 | `POST /addToVectorDB` | `CopilotUtils.toVectorDB` (L18,112), `FileUtils.java:170` | in-process indexer |
| 5 | `POST /ResetVectorDB`, `POST /purgeVectorDB` | `CopilotUtils.java:343,736`; `SyncAssistant.java:483-489` | delete-by-filter on embedding store |
| 6 | `GET /runningCheck` / `isCopilotRunningInDocker` | `CopilotUtils.java:572` | delete |
| 7 | `GET /tools` | `SyncToolStructure.java:69-72` (process action) | read Java tool registry; also review `ETCOP_TOOL` sync design |
| 8 | `POST /checkCopilotHost` + `copilot.host` half of `CheckHostsButton` (and its `COPILOT_HOST` check/message `ETCOP_HOST_CHECK`); `etendo.host.docker` check (`POST /sws/copilot/configcheck`, `RestService.java:206`) loses its purpose | `CheckHostsButton.java:39,110-186` | button removed or reduced |
| 9 | Startup wait for Python `GET /` | `CopilotSyncStartup.waitForCopilotService` (L~255-290) | delete (sync can run immediately; but note it currently syncs KB files into Python) |
| 10 | `POST /attachFile`, `POST /transcription` (files stored in container FS) | `RestService.java:195,198`; `RestServiceUtil.handleFile` L225; `AddBulkTasks.java:228` | in-process file store + Java transcription call |
| 11 | `POST /graph` for graph images | `RestServiceUtil.getGraphImg` L1005-1060 | Java Mermaid generation |
| 12 | `POST /question|aquestion|graph|agraph` + SSE relay of Python frames | `RestServiceUtil.determineEndpoint` L550, `sendRequestToCopilot`, `serverSideEvents` L339 | in-process emit (same frame shapes) |
| 13 | Request payload fields consumed only by Python: `local_file_ids`, `kb_vectordb_id` (`KB_<id>`), `kb_search_k`, `code_execution`, `specs`, `mcp_servers`, `tools` (as names), `extra_info.auth.ETENDO_TOKEN`, `extra_info.tool_config`, `structured_output_json_schema` (mismatch with `output_schema`), `assistant_id`, `ad_user_id`, `ad_client_id` | `RestServiceUtil.buildRequestJson` (L~470-540), `CopilotUtils.buildLangchainRequestForCopilot/buildLangraphRequestForCopilot` (L~930-1130) | replace with direct Java objects (`CopilotApp`, OBContext) — no JSON serialisation of the agent definition |
| 14 | Python → Etendo callbacks: `/sws/copilot/configcheck`, `/sws/copilot/structure`, `/sws/copilot/question`, tool HTTP calls using SWS JWT | `RestService.java:107,206` etc. | in-process; endpoints `structure` and `configCheck` become unnecessary (keep `/question` for external MCP/clients) |
| 15 | Python MCP server on `copilot.port.mcp` (5006/5007+ per-agent ports) and URL building | `GetMCPConfiguration.java:526-528` (`context.url.copilot.mcp`, `copilot.port.mcp`) | Java MCP servlet endpoint |
| 16 | Docker artefacts: `Dockerfile`, `base.Dockerfile`, `full.dockerfile`, `compose/`, `run.py`, `requirements.txt`, `uv.lock`, `pyproject.toml`, `tools_deps.toml` (+ toolpack), `litellm/`, `logging.conf`, `/checkpoints`, `/vectordbs`, `/copilotAttachedFiles`, `/copilotTranscripts` volumes, `local_setup.py`, `doToolSymLinks.sh` | module root | removed |
| 17 | Gradle tasks that build/run the container (`tasks.gradle`, `config.gradle`, `build.gradle`) — [INFERENCE: not opened; verify] | module root | removed |

## Notable findings / risks

1. `structured_output_json_schema` (Java) vs `output_schema` (Python `AssistantSchema`): mismatch means structured output is likely dead on the Python path today; decide field name before porting (§9).
2. `code_execution` is silently ignored by the Java engine and is not in `unsupportedReason` — an agent with Code Interpreter enabled but no tools will be served by Java without code execution when `copilot.engine=java`.
3. `laststeps` alias: no occurrence found in either runtime (§11).
4. `/executeTool` has no Java caller — likely used by external clients; confirm before dropping.
5. The `CodeExecutor`/`OriginalExecutor` path runs unsandboxed `exec` inside the Python process with `ETENDO_TOKEN` injected — porting it 1:1 to Java is not possible or advisable.
6. Gemini tool-schema fixes (`utils/agent.py`) will be needed again when LangChain4j sends tool specs to `GoogleAiGeminiChatModel` — [INFERENCE].
7. Existing Chroma vector DBs (`/vectordbs/*.db`) are not readable from LangChain4j without a Chroma server; plan a full KB re-index via `SyncAssistant` on migration.
