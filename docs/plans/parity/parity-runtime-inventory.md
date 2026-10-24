# Python runtime vs Java engine — runtime feature parity (ETP-5136)

Scope: `modules/com.etendoerp.copilot/copilot/` (Python) vs worktree `worktrees/copilot-ETP-5136/src/com/etendoerp/copilot/engine/` (Java).
Paths below are relative to the copilot module's `copilot/core/` unless stated. `routes.py`, `app.py` are in `core/` and `copilot/` respectively.
Java engine = `CopilotEngineSelector`, `LangChain4jEngine`, `ChatModelFactory`, `ClientToolSupport`, `ServerToolRegistry`, `ToolExecutionContext`, `PythonCopilotEngine`. It builds system/history/question messages, offers server-side Java tools and client-executed WebMCP tools in a bounded hand-written loop, and retains stateless history replay.

Size key: S ≤ 1–2 days, M ≈ 3–7 days, L > 1 week.

Selector fallbacks in `CopilotEngineSelector.unsupportedReason` (engine/CopilotEngineSelector.java):
1. Agent type outside {`langchain`,`multimodel`,`langgraph`} is Python-only; `openai-assistant`
   is not actually served by the Python `/assistant` route.
2. `openai-assistant` team members and unsupported member types are rejected; Python's graph
   explicitly rejects its OpenAI Assistant member type too.
3. `tools[]` with a missing Java implementation falls back to Python; registered tools execute
   through the Java server-tool loop.
4. FLOW `specs[]` are supported as request-scoped operation tools; route classification selects
   core JSON data service only for fixed-entity simple reads; writes and RX-specific/non-entity
   operations use explicit EtendoHttp loopback.
5. `code_execution`/CodeAct remains Python-only and now has an explicit guard.
6. `mcp_servers[]` and KB sources are supported in Java; unsupported MCP configurations expose
   no tools, and KB requests require the optional pgvector table to have been provisioned.

---

## 1. Agent types

Python type dispatch: `agent/__init__.py:5-20` (`_get_agent_executors`) maps `openai-assistant`, `langchain`, `multimodel` → all to `MultimodelAgent`. `AgentEnum` at `agent/agent.py:14-17`. Default type from env `agent.type` (`routes.py:123-131`). Java constants: `CopilotConstants.java:9-12` (`langchain`, `langgraph`, `multimodel`, `openai-assistant`). Java routes to graph endpoints via `CopilotUtils.checkIfGraphQuestion` (CopilotUtils.java:1139) → `RestServiceUtil.determineEndpoint` (RestServiceUtil.java:550).

| Feature | Python implementation (file:line) | Java engine status | What Java needs | Size |
|---|---|---|---|---|
| `multimodel` / `langchain` single agent | Python `MultimodelAgent` uses the configured model, prompt, history, and tools. | **partial** — Java supports CDI tools, MCP, WebMCP, pgvector KB, OpenAPI specs, Java-managed uploads, local image content, shared Whisper transcription, structured JSON responses, and usage totals. Remaining limits include some Python-only tools, local-only file resolution, and event/debug differences. | Continue removing unsupported Python-only capabilities before Python cutover. | M |
| `openai-assistant` | Python maps the old type to `MultimodelAgent`, but `/assistant` rejects the Assistants flow and team members explicitly raise `NotImplementedError`. | **unsupported intentionally** — selector routes this type to Python; the Python route itself reports that OpenAI Assistants is no longer supported. | No Assistants API client is planned; remove the legacy type with Python cutover. | S |
| `langgraph` supervisor / team | Python team graph gives the supervisor and each member its own model and tools. | **supported with limits** — Java runs a bounded supervisor loop with member delegation, tools, request-scoped MCP, team memory, JSON response format and usage aggregation. OpenAI-assistant members and graph-image output remain unsupported. | Preserve member guards; implement graph rendering only if still required. | L |
| Legacy graph patterns (dead code) | `patterns/supervisor_pattern.py:12` `SupervisorPattern`, `patterns/loop_pattern.py:10` `LoopPattern`, `patterns/base_pattern.py:14`, `special_nodes/output_node.py:8`, `OutputNode`, `SupervisorNode.build` (supervisor_node.py:69). `setup_graph` (langgraph_agent.py:60-72) only instantiates `LangSupervisorPattern`; `AssistantGraph/stages` from request (`schemas.py:20-27`) is passed but not used by it | n/a — [INFERENCE] unreachable from routes; verify with grep before dropping | Do not port. Delete with Python. | – |
| Graph diagram | `generate_image` flag → `cgraph.get_graph().draw_mermaid_png()` (`langgraph_agent.py:~290`); Java caller `RestServiceUtil.getGraphImg` (RestServiceUtil.java:~1005) posts to `/graph` with `generate_image=true` | **missing** | No LangChain4j equivalent; build Mermaid text from members/supervisor in Java and render (mermaid-cli/kroki) or return Mermaid text and change the UI | M |
| CodeAct / `code_execution` | Python can execute generated Python code through the CodeAct evaluator path. | **unsupported intentionally** — `code_execution` is now an explicit selector fallback; Java does not run Python code in the Etendo JVM. | Keep the guard; do not execute untrusted Python in-process. | L |
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
| `POST /aquestion` (SSE) | `routes.py:315`, `_serve_question_async` L272 (queue pump `gather_responses` L102), `_response` L70 serialises `AssistantResponse`; SSE `data: {"answer": {...}}` | **partial** — Java emits handshake + final answer; Java server-tool loops now emit ordered `role:"tool"` frames before one final frame. No token deltas. | Add node/debug parity for single-agent events; graph team retains its own event loop | M |
| `POST /graph` / `POST /agraph` | `routes.py:171,219,224` (`_serve_agraph`), `LanggraphAgent` | **missing** (see §1) | – | counted in §1 |
| Event handling → roles | Python `langgraph_agent.handle_events`: `node`, `tool`, final answer, optional `debug`; route exceptions → `error` | **partial** — Java single-agent server tool starts emit `{response:<tool name>,role:"tool"}`, final answer remains one frame, and runtime exceptions in async mode emit `role:"error"`. No single-agent `node` or `debug` event generation yet; team frames remain handled by `TeamEngine`. | Match single-agent node/debug events where Python emits them | M |
| Error shaping | `routes.py:151-163` `_handle_exception` (extracts provider error content), `routes.py:288-306` yields `role=error` frame | **partial** — async Java catches runtime failures and emits `role:"error"` with the Java exception message before rethrowing; provider-specific message extraction differs | Match provider-specific Python error-message extraction | S |
| Empty-response check | `multimodel_agent.py:~361`, `langgraph_agent.py:~323`: raise if empty content and 0 output tokens | **partial** — Java throws on empty text (L~65) but doesn't consider output tokens | trivial | S |
| Content normalisation (Gemini list blocks) | `agent/agent_utils.py:153` `normalize_content` | **supported by LangChain4j** (`AiMessage.text()`) | – | – |

## 4. Knowledge base / vector DB

| Feature | Python implementation | Java engine status | Needs | Size |
|---|---|---|---|---|
| KB retrieval tool `KnowledgeBaseSearch` | Python uses Chroma with client/organization visibility and configurable result count. | **supported** — Java registers an implicit `KnowledgeBaseSearch` tool backed by the provisioned pgvector table and preserves client scope. Missing pgvector/table produces a clear runtime error. | None for text retrieval; provision pgvector in environments that enable KB. | L |
| Embeddings | Python uses proxy-aware OpenAI embeddings. | **supported** — Java OpenAI-compatible embeddings use the configured proxy/key and `copilot.embedding.model` (default `text-embedding-3-small`). Existing Chroma vectors require re-indexing. | Re-index data when migrating from Chroma or changing embedding dimensions. | S |
| Indexing route `POST /addToVectorDB` | Python accepts PDF, text, markup, code, JSON and ZIP sources, with overwrite and chunk controls. | **supported for textual sources** — Java ports PDFBox extraction, ZIP expansion, text/code/JSON parsing, SHA-256 chunk IDs, and purge/overwrite-compatible persistence. Images are skipped because CLIP is unavailable. | No additional text parser; preserve the documented image limitation. | L |
| Splitters | Python has Markdown, JSON and language-aware recursive splitters. | **supported with limits** — Java uses deterministic extension-aware chunk boundaries for Markdown, JSON, code and plain text; it is not a token-aware splitter. | Consider token-aware splitting only if observed quality requires it. | M |
| Reset / purge routes | Python marks documents stale and then deletes purged Chroma entries. | **supported** — Java reset/purge operations mark and delete scoped pgvector chunks; `SyncAssistant` retains the paired route semantics. | None for text sources. | S |
| Image reference index (visual similarity) | Python uses fastembed/CLIP over the `IMAGES` collection; toolpack OCR consumes the matches. | **unsupported intentionally** — Java ingestion skips images; no CLIP embedding model/preprocessor is included. | A real CLIP-compatible model and its image-preprocessing/runtime dependency are prerequisites. | L |
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
| `specs[]` of type `FLOW` → tools | Python `tool_loader.py:273 _add_openapi_tools` and `toolgen/openapi_tool_gen.py:543`: GET/POST/PUT/DELETE operations; Python-compatible operation/path naming, parameter schemas and RX pagination argument mapping; body/base64 handling; `api_tool_util.py` returns `{status_code,content}` and recognizes the literal `ETENDO_TOKEN`. Java parses each request's `{name,type,spec}` into immutable operation descriptors and request-scoped `ServerTool`s; only fixed-entity/field-allowlisted local RX reads with simple projection/sort/paging invoke core `DefaultJsonDataService.fetch` in caller non-admin context. Writes and RX-specific, non-entity, or unsupported operations use explicit logged `EtendoHttp` loopback; add/update otherwise enter cross-org-reference admin mode and remove commits internally. Exact installed FLOW operation paths are not available because the only installed `spec` file's `ETCOP_FILE.TEXT` is empty; see [operation matrix](openapi-operation-matrix.md). | Implemented; no spec guard | Exact per-operation matrix requires an emitted spec attachment; inventory is documented without inventing paths | M |

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
| `POST /attachFile` | `routes.py:595` saves to `/copilotAttachedFiles/<uuid>/<name>` (docker) or `$CWD/...`; returns path in `answer`. Java: `RestService.java:195` (`handleFile(..., "attachFile")` → `RestServiceUtil.handleFile`), `AddBulkTasks.java:228` | **partial** — when `copilot.engine=java|java-strict`, uploads are retained in a private Java temp file and the absolute path is returned; `python` mode retains the legacy route | Java temp-file lifecycle still uses JVM `deleteOnExit`; durable cleanup policy remains | S |
| Local file ids in prompt | Java sends `local_file_ids` + appends local-file text; Python `get_full_question` appends absolute paths; `process_local_files` splits ids | **partial** — existing local file ids resolve to readable local Java paths; missing/unreadable paths are skipped | Files retained by container-only legacy callers cannot be read by Java; use Java-mode uploads | M |
| Image / multimodal input | `agent_utils.py:43 get_payload` builds image data-URI blocks (JPEG/JPG/PNG/WEBP/GIF); other files become an `Attached files:` text block with paths | **partial** — Java makes local JPEG/JPG/PNG/WEBP/GIF paths `ImageContent` and sends non-image paths in the Python-compatible text block | Unsupported MIME/image encoding variants and remote/container-only paths remain unavailable | S |
| `POST /transcription` | `routes.py:609` saves upload then calls OpenAI `whisper-1`; Java: `RestService.java:198` | **supported** for explicit Java engine mode — in-process shared helper calls OpenAI Whisper through configured proxy/direct endpoint and key | Live provider call requires a configured key; no LLM/network verification performed | S |
| Docker-vs-local path logic | `baseutils/logging_envvar.py is_docker`; `GET /runningCheck` `routes.py:561`; Java `CopilotUtils.isCopilotRunningInDocker` | **n/a** | Disappears | – |

## 9. Structured outputs

| Feature | Python | Java status | Needs | Size |
|---|---|---|---|---|
| JSON-schema structured output | Python reads `output_schema` and applies `response_format`; team supervisor also has a response format | **supported with provider limits** — single-agent Java and team supervisor pass `output_schema` to LangChain4j JSON response format; response body is emitted as JSON text | Provider-specific schema enforcement varies | S |
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
| System prompt | Java builds the single-agent prompt in `CopilotUtils`; Python consumes `system_prompt`; Java teams add member names/descriptions to the supervisor prompt | **supported** — single-agent uses `SystemMessage`; team supervisor prompt includes the member tools/descriptions | Exact Python/LangGraph event/prompt internals differ; CodeAct remains unsupported | S |
| Question augmentation | Java appends question-behaviour and local-file text; Python adds local-file path text and image/file blocks | **partial** — Java now resolves existing local image paths to multimodal content and non-image paths to the matching `Attached files:` block; only locally accessible files resolve | Container-only paths and image context forwarded to individual delegated members are not reproduced | – |
| `laststeps` alias | No occurrence found in the inspected Python `copilot/` or Java `src/` and docs: not a runtime feature and therefore dead in these runtimes; UI/other repositories were not inspected | n/a | – | – |
| Agent description for teams | `members_util.py:125 get_assistant_supervisor_info`, `AssistantSchema.description` | **missing** | with teams | – |

## 12. Token / usage accounting

| Feature | Python | Java | Needs | Size |
|---|---|---|---|---|
| Usage totals into response metadata | Python sums input/output token usage across messages after the current human turn and returns `{input_tokens, output_tokens, total_tokens}` | **supported with provider limits** — Java aggregates `ChatResponse.tokenUsage()` across single-agent server-tool rounds and across team supervisor/member calls, exposing the same metadata keys; absent token counts cannot be inferred | Providers may omit usage counts | S |

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

1. The Java payload field is now `output_schema`, the key Python `AssistantSchema` reads (the epic branch already sends this key). The Java engine consumes the same field, and structured output now reaches the Python runtime too; this is a deliberate bug fix in `engine=python` mode.
2. `code_execution` is now an explicit Java selector fallback (guard 5); CodeAct remains intentionally unsupported.
3. `laststeps` alias: no occurrence found in either runtime (§11).
4. `/executeTool` has no Java caller — likely used by external clients; confirm before dropping.
5. The `CodeExecutor`/`OriginalExecutor` path runs unsandboxed `exec` inside the Python process with `ETENDO_TOKEN` injected — porting it 1:1 to Java is not possible or advisable.
6. Gemini tool-schema fixes (`utils/agent.py`) will be needed again when LangChain4j sends tool specs to `GoogleAiGeminiChatModel` — [INFERENCE].
7. Existing Chroma vector DBs (`/vectordbs/*.db`) are not readable from LangChain4j without a Chroma server; plan a full KB re-index via `SyncAssistant` on migration.
