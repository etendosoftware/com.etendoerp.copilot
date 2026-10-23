# Python tools inventory (ETP-5136 parity)

Legend. **Callback** = HTTP to Etendo with ETENDO_TOKEN / `etendo_utils` (`get_etendo_host`, `get_etendo_token`, `call_etendo`, `call_webhook`), or ThreadContext / extra_info reads. **Diff** = Java rewrite difficulty. **Shipped use** = linked in `ETCOP_APP_TOOL` (module `src-db/database/sourcedata`) to a shipped `ETCOP_APP` (27 apps found).

## Counts
- `.py` files: toolpack `tools/` = 26 (25 `ToolWrapper` tools + `GoogleServiceUtil.py` helper); devassistant `tools/` = 5; copilot `tools/` = 4 (+ `__init__.py`, `schemas/`); core-embedded = 3 (`TaskManagementTool`, `ApiTool` generator, MCP `agent_tools`). modules_core and all other modules: 0 Python tools, 0 `tools_deps.toml`.
- **Python tool classes: 25 + 5 + 4 = 34**, plus 1 langgraph `@tool` (TaskManagementTool) and 1 generic OpenAPI-generated `ApiTool` (BaseTool, runtime-generated).
- `ETCOP_TOOL` rows: copilot 4, toolpack 27, devassistant 5 = 36. `ETCOP_APP_TOOL` rows: copilot 1, toolpack 5, devassistant 37.
- `tools_deps.toml` files (4): copilot (HelloWorldTool, SimpleOcrTool), toolpack, devassistant, plus `copilot/tests/tools_deps.toml` (test fixture). Stale sections naming tools with no .py: DDLTool, CreateReferencesTool, OpenAPIPetStoreTool, DBQueryGenerator.
- Tools linked to a shipped agent: **16 of 36** (rows below marked YES).

## A. Toolpack (`com.etendoerp.copilot.toolpack/tools/`)
| Tool | What it does | Py libs / services | Callback Etendo / ctx | Diff (Java lib) | Shipped use |
|---|---|---|---|---|---|
| APICallTool | Generic GET/POST/PUT to an Etendo/any URL with auth header; swaps base64 filepaths | requests, curlify, `toolgen.openapi_tool_gen` | Yes: uses ETENDO token via `api_tool_util.token_not_none` | S (java.net.http / OkHttp; in-process can call DAL for local Etendo URLs) | YES: 9 agents (Background/Button Process, Message, Module, Reference Creators, SQL Expert, Table&Column, Webhook Creator, Windows/Tabs/Fields) |
| AttachFileTool | Attaches a local file to an Etendo record via `AttachFile` webhook | base64 | Yes: `get_etendo_host/token`, webhook | S (replace with direct `TabAttachments`/`AttachImplementationManager` DAL call) | no |
| AudioTool | Audio transcription (OpenAI Whisper) | openai client via `utils.models.get_openai_client`, langsmith | No (reads API key/proxy config) | S (LangChain4j `OpenAiAudioTranscription` / plain HTTP) | no |
| CodbarTool | Decodes barcodes/QR from image | pyzbar (native zbar) | No | M (ZXing `com.google.zxing`) | no |
| DockerTool | Runs python/bash code in a Docker container, copies files in/out, injects extra_info as env vars | docker SDK | Yes: ThreadContext `extra_info`, `conversation_id` | M (docker-java; security review needed) | YES: Code Run |
| EtendoSQLToCSVTool | Runs SQL through Etendo webhook and writes CSV | csv, requests | Yes: `get_etendo_host/token` webhook | S (JDBC/DAL `OBDal.getSession().createNativeQuery` + commons-csv) | YES: SQL Expert |
| ExcelOCRTool | Reads XLS/CSV and uses an LLM to extract structured data/plots | pandas, matplotlib, langchain `init_chat_model` | No (api key/proxy utils) | L (pandas + matplotlib; Apache POI/Tablesaw + LangChain4j chat model; plot part droppable) | no |
| FileCopyTool | Copy file between paths | stdlib | No | S (java.nio.file) | no |
| FileDownloaderTool | Downloads a URL (or local path) to the working dir | requests | No | S (java.net.http) | no |
| GoogleDriveTool | List/download/upload Drive files using OAuth token | `GoogleServiceUtil` (requests); deps google-api-python-client | Yes: ThreadContext `oauth_tokens` | M (Google API Java client or plain REST) | YES: SQL Expert |
| GoogleSpreadsheetsTool | Read/write Google Sheets via `GoogleServiceUtil` | requests | Yes: ThreadContext `oauth_tokens` | M (same as above; shares util) | no |
| GoogleServiceUtil.py | Helper (not a tool): Drive/Sheets REST, csv conversion | requests | ThreadContext `oauth_tokens` | M (shared with the 2 tools above) | n/a |
| LoadOAuthTokenTool | Fetches the user's Google OAuth tokens from Etendo and stores them in ThreadContext | requests | Yes: `call_etendo`, writes ThreadContext `oauth_tokens`, `save_conversation` | S (direct DAL read of the OAuth token record) | YES: SQL Expert |
| MemoryTool | Per-agent/user long-term memory (store/recall/delete) in vector DB | langchain_chroma, Chroma, `vectordb_utils` | Yes: ThreadContext `assistant_id`, `ad_user_id` | L (needs Java vector store/embeddings: LangChain4j `EmbeddingStore` Chroma/pgvector, must match existing KB) | YES: SQL Expert |
| OCRExampleCreatorTool | Draws red-box markers on a reference image, saves as OCR example | PIL (via pathlib/base64) | No | M (java.awt/ImageIO) | no |
| OcrTool (row `OcrTool`) | Vision-LLM structured extraction from image/PDF, with reference templates and pydantic schemas | pypdfium2, PIL, filetype, pydantic, `utils.agent.get_llm`, vectordb | Yes: extra_info `tool_config[agent][tool_id]` (model), accum usage | L (PDFBox render + LangChain4j vision chat + structured output + vector store; 759 lines) | YES: Jasper Report Creator |
| OCRAdvancedTool (AD row only) | Row in `ETCOP_TOOL` with no Python class of that name; json_structure matches `OcrTool` params | n/a | n/a | n/a [INFERENCE: stale/duplicate of OcrTool; verify loader mapping] | no |
| PdfToImagesTool | Renders PDF pages to images | pypdfium2 | No | S (Apache PDFBox `PDFRenderer`) | no |
| PrintDirectoryTool | Lists directory tree | stdlib | No | S (java.nio.file) | YES: Java Test Checker, Unique Java Test Agent |
| ReadFileTool | Reads a text file | stdlib | No | S | YES: 10 agents (Background/Button Process, Etendo Code Expert, Event Handler, Jasper, Java Test Generator, React Test, Refactor, Unique Java Test, Webhook) |
| SendEmailTool | Sends email through Resend | resend | No (env var key) | S (Resend Java SDK or Etendo mail via DAL/`EmailUtils`) | no |
| TaskCreatorTool | Parses zip/csv/xls files into task items and creates Etendo tasks (taskapi) in groups | pandas, requests, zipfile, uuid | Yes: `get_etendo_host/token` + ThreadContext `conversation_id`, `assistant_id` | L (pandas parsing + task API; DAL direct possible; 529 lines) | no |
| TavilySearchTool | Web search | langchain_community Tavily, rapidfuzz | No (env API key) | S (Tavily REST / LangChain4j `TavilyWebSearchEngine`) | no |
| TemplateTool | Template example tool | stdlib | No | S | no |
| UncompressTool | Extracts zip/rar/bz2/tar | rarfile, bz2file | No | M (commons-compress; rar needs junrar) | no |
| WriteFileTool | Writes a text file | stdlib | No (env var base dir) | S | YES: 9 agents (see ReadFileTool minus Etendo Code Expert) |
| XLSTool | Reads XLS/XLSX/CSV rows | pandas, openpyxl | No | M (Apache POI + commons-csv) | no |
| OpenAPIPetStoreTool (AD row only) | Demo tool generated from OpenAPI spec (`toolgen`), no .py | requests | n/a | n/a, covered by ApiTool rewrite | no |

## B. Copilot module (`com.etendoerp.copilot`)
| Tool | What it does | Py libs / services | Callback / ctx | Diff | Shipped use |
|---|---|---|---|---|---|
| HelloWorldTool | Demo: figlet greeting | pyfiglet | No | S (drop or trivial) | no |
| CustomHelloWorldMultiArgsTool | Demo calculator, multi-arg schema | pydantic | No | S (drop; useful as test fixture) | no |
| SimpleOcrTool | Vision-LLM OCR with schema, model from tool_config | filetype, base64, `schemas/testdocument.py` | Yes: extra_info `tool_config` via `get_extra_info` | M (LangChain4j vision model + structured output) | YES: OCR Expert |
| ExampleTool | `CopilotTool` (new-style, `_run`) example with nested pydantic; no AD row | pydantic | No | S (drop; good template for Java tool API) | no |
| TaskManagementTool (`copilot/core/langgraph/tool_utils/`) | Supervisor task queue: get_next/add_tasks/status/mark_done, mutates LangGraph state | langgraph (`InjectedState`, `Command`), langchain_core | Reads/writes graph state only | M (state is LangGraph-specific; must be mapped to Java supervisor/graph state) | AD row exists; linked to no agent but injected by name in `langsupervisor_pattern.py:42` for supervisors (Dev Assistant Supervisor, Test Supervisor) |
| ApiTool + `openapi_tool_gen` (`core/toolgen/`) | Generates tools from OpenAPI spec at runtime (e.g. OpenAPIPetStoreTool) | requests, aiohttp, pydantic | Yes: `get_etendo_token`, `get_etendo_host` | M (OpenAPI parser swagger-parser + java.net.http) | no shipped agent |
| MCP `agent_tools.py` (`core/mcp/tools/`) | Exposes agents/tools through FastMCP (`get_agent_prompt`, wrapped tools) | fastmcp, httpx, langchain BaseTool | Yes: token from MCP context, `call_etendo` | L (MCP server side; see Runtime inventory) | n/a (infrastructure, not an agent tool) |

## C. Devassistant (`com.etendoerp.copilot.devassistant/tools/`)
| Tool | What it does | Py libs / services | Callback / ctx | Diff | Shipped use |
|---|---|---|---|---|---|
| ClientInitTool | Creates a client (initial client setup) via Etendo servlet | requests, requests-toolbelt (multipart) | Yes: `get_etendo_host/token`, `login_etendo` | M (call `InitialClientSetup` Java class directly in-process) | YES: Client/Organization Initializer |
| OrgInitTool | Creates organization (reuses `do_init_request`) | requests | Yes: `get_etendo_host/token` | M (call `InitialOrgSetup` directly) | YES: same agent |
| ProcessDefinitionJasperTool | Creates a Jasper process definition via `ProcessDefinitionJasper` webhook | requests | Yes: webhook with token | S (direct DAL / call webhook class) | YES: Jasper Report Creator |
| TestRunTool | Runs gradle/Java tests via subprocess | subprocess | No | S (ProcessBuilder) | YES: Java Test Executor, Java Test Generator |
| XMLTranslationTool | Translates Etendo XML translation files with an LLM, per language | xml.etree, pycountry, openai client | No (openai key) | M (dom4j/JAXB + LangChain4j chat; pycountry→`java.util.Locale`) | YES: Module Translation Creator |

## Priority order
- **P0 (shipped or trivial):** ReadFileTool, WriteFileTool, PrintDirectoryTool, FileCopyTool, APICallTool, TestRunTool, ProcessDefinitionJasperTool, ClientInitTool, OrgInitTool, EtendoSQLToCSVTool, DockerTool, TaskManagementTool (supervisor), SimpleOcrTool, XMLTranslationTool.
- **P0b (shipped, bigger):** OcrTool (+ resolve OCRAdvancedTool row), MemoryTool, LoadOAuthTokenTool + GoogleDriveTool (SQL Expert).
- **P1 (not shipped, general value, small):** AttachFileTool, FileDownloaderTool, SendEmailTool, TavilySearchTool, PdfToImagesTool, XLSTool, UncompressTool, AudioTool, GoogleSpreadsheetsTool (+ GoogleServiceUtil), ApiTool/OpenAPI generator, TaskCreatorTool.
- **P2 (niche/drop candidates):** CodbarTool, ExcelOCRTool, OCRExampleCreatorTool, TemplateTool, HelloWorldTool, CustomHelloWorldMultiArgsTool, ExampleTool (keep as Java test fixtures), OpenAPIPetStoreTool, OCRAdvancedTool duplicate row, stale deps sections (DDLTool, CreateReferencesTool, DBQueryGenerator).

## Java status

`done` means a Java implementation is present; `partial` means the port still lacks Python
behavior; `deferred` names the Java package that owns the future migration.

| Python tool | Java status |
|---|---|
| APICallTool | done |
| AttachFileTool | done |
| AudioTool | done |
| CodbarTool | done |
| DockerTool | done |
| EtendoSQLToCSVTool | done |
| ExcelOCRTool | done |
| FileCopyTool | done |
| FileDownloaderTool | done |
| GoogleDriveTool | done |
| GoogleSpreadsheetsTool | done |
| LoadOAuthTokenTool | done |
| MemoryTool | deferred: KB package |
| OCRExampleCreatorTool | dropped by decision |
| OcrTool | done |
| OCRAdvancedTool | dropped by decision: duplicate of OcrTool |
| PdfToImagesTool | done |
| PrintDirectoryTool | done |
| ReadFileTool | done |
| SendEmailTool | done |
| TaskCreatorTool | done |
| TavilySearchTool | done |
| TemplateTool | dropped by decision |
| UncompressTool | done |
| WriteFileTool | done |
| XLSTool | done |
| OpenAPIPetStoreTool | deferred: OpenAPI package |
| HelloWorldTool | dropped by decision |
| CustomHelloWorldMultiArgsTool | dropped by decision |
| SimpleOcrTool | done |
| ExampleTool | dropped by decision |
| TaskManagementTool | deferred: teams package |
| ApiTool (OpenAPI generator) | dropped by decision |
| MCP `agent_tools.py` | deferred: MCP package |
| ClientInitTool | done |
| OrgInitTool | done |
| ProcessDefinitionJasperTool | done |
| TestRunTool | done |
| XMLTranslationTool | done |

Caveats: usage derived only from `src-db/database/sourcedata` and `referencedata` XML in /modules; customer/AD data not seen. Per-tool library lists came from import/regex scanning, not full reads.
