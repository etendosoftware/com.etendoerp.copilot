# Java engine (LangChain4j) — running an agent without the Python container

Status: the Java runtime supports plain chat, client-executed WebMCP tools, and a CDI-contributed
server-tool foundation; individual ETCOP tools must still be implemented as Java beans.

Verified run: agent *Pepin puercoespin* (`multimodel`, no server tools / KB / MCP), `copilot.engine=java`,
Python container stopped. Asked "Buscá las ventanas de ventas y abrime la de Sales Order" from the
Copilot widget in Etendo Classic; the model ran three tool rounds through the browser and Sales Order
(`windowId` 143) opened as a real SmartClient tab. `ETCOP_MESSAGE` recorded exactly one USER and one
ASSISTANT row for the turn, with no tool internals. `PythonCopilotEngine` never appeared in the log.
Related plan: [plans/2026-09-03-langchain4j-java-engine.md](plans/2026-09-03-langchain4j-java-engine.md)

---

## 1. What it is

A second implementation of the agent runtime that executes inside Tomcat with
[LangChain4j](https://docs.langchain4j.dev/), so a plain conversational agent answers without
the Python/Docker container running.

It is not a fork of Copilot. Java already assembles the **entire** agent definition before
talking to Python — system prompt, provider, model, temperature, tools, history, `extra_info` —
in `CopilotUtils.buildLangchainRequestForCopilot`. The conversation memory is Etendo's own
`ETCOP_MESSAGE`, shipped as the `history` array. Python is a stateless executor of that payload.
The Java engine is a second executor of the very same payload.

## 2. Configuration

One property in `Openbravo.properties`:

```properties
# python (default) | java | java-strict
copilot.engine=java
```

| Value | Behaviour |
|---|---|
| `python` | **Deprecated.** Always call the Python container. Identical to the behaviour before this feature existed; a one-time WARN is logged on first use. It will be removed once the Java engine is the default. |
| `java` | Run in-process when the request is supported; otherwise log a warning and fall back to Python. |
| `java-strict` | Run in-process always; raise an error instead of falling back. Use it to prove no request is silently reaching the container. |

Model credentials and endpoints. Provider resolution **mirrors the Python runtime**
(`copilot/core/utils/agent.py::get_llm`) so both engines reach the same endpoint with the same
model name:

1. **Ollama** is handled natively, before anything else.
2. **Proxy mode** — if `copilot.proxy.url` is set, it applies to *every* provider: the
   OpenAI-compatible client is pointed at that gateway and the model name is prefixed with its
   original provider (`openai/gpt-4.1`), which is how the gateway resolves the real credentials.
   The API key falls back to `"dummy"` when `openai.api.key` is unset, because the OpenAI client
   refuses to build without one.
3. Otherwise each provider uses its **native** client.

Knowledge-base embeddings use the same OpenAI-compatible endpoint and proxy key fallback as the
chat model. Set `copilot.embedding.model` to override the default `text-embedding-3-small`.
When a proxy is configured, the model is sent as `openai/<model>`. Run `update.database` after
installing pgvector to provision the KB table/indexes; runtime requests never perform DDL and
report a clear error when pgvector or the provisioned table is missing.

```properties
copilot.proxy.url=https://llm.etendo.software   # gateway; enables proxy mode for every provider
openai.api.key=sk-...
anthropic.api.key=sk-ant-...
gemini.api.key=...
ollama.base.url=http://localhost:11434

# Direct mode only: override the OpenAI endpoint without prefixing the model name.
copilot.openai.base.url=http://localhost:4000

# Model call timeout, seconds. Default 120.
copilot.engine.java.timeout.seconds=120

# Client and server tool rounds allowed per question. Default 25.
copilot.engine.java.max.tool.rounds=25

# Stream team orchestration debug events (default false).
copilot.stream.debug=false
```

The provider and model come from `ETCOP_MODEL`, resolved by `CopilotModelUtils`, and the
per-model `max_tokens` is read from `extra_info.model_config` — the same structure Python
consults in `get_model_config`. So the Java engine talks to exactly the model, endpoint and
token limit the Python runtime would have used.

## 3. Where it plugs in

```
RestService                        (unchanged)
  └─ RestServiceUtil.handleQuestion (unchanged)
       ├─ buildRequestJson          (unchanged — one payload, both engines)
       ├─ sendRequestToCopilot  →  CopilotEngineSelector.get(request, app).execute(...)
       │        ├─ PythonCopilotEngine    HTTP POST to the container
       │        └─ LangChain4jEngine      in-process LangChain4j call
       └─ processResponseAndTrack   (unchanged — tracking, ETCOP_MESSAGE, metadata)
```

| Class | Role |
|---|---|
| `engine/CopilotEngine` | The contract. Input is the payload `buildRequestJson` produced; output is what `processResponseAndTrack` expects. |
| `engine/CopilotEngineSelector` | Reads `copilot.engine`, decides, and explains a fallback. |
| `engine/PythonCopilotEngine` | The previous `sendRequestToCopilot` body, moved unchanged. |
| `engine/LangChain4jEngine` | Builds the message list, calls the model, emits the SSE frame. |
| `engine/ChatModelFactory` | Maps provider + model + temperature + timeout to a LangChain4j `ChatModel`. |

For agents without server tools, the existing chat interaction and answer shapes are unchanged.
Mixed server/client tool rounds add only the `answer.server_results` echo described in §6.

```
data: {"answer": {"response": "...", "conversation_id": "..."}}
```

Return values also mirror Python exactly: the synchronous path returns the full
`{"answer": {...}}` body, the streaming path returns the bare `answer` object — which is what
`RestServiceUtil.serverSideEvents` returns, and what `extractResponse` reads through its flat
`response` branch.

### Knowing which engine answered

Every request logs its decision. The line is INFO whenever `copilot.engine` is not the default,
so a standard installation stays quiet while anyone running the Java engine sees the decision per
request without enabling debug logging:

```
Copilot engine: java (LangChain4j) for agent 'Demo' (no Python container involved)
Copilot engine: python for agent 'Bastian' (falling back, the agent has a knowledge base, ...)
```

A fallback is always logged at WARN, because it is a degradation rather than routine.

## 4. Supported and unsupported requests

`CopilotEngineSelector.unsupportedReason` is the single place that decides. A request is served
in Java only when **all** of these hold:

- the agent type is `langchain`, `multimodel`, or `langgraph`; `openai-assistant` is not
  implemented as a Java agent type. The Python `/assistant` route also rejects the legacy
  Assistants flow, and Python's team graph explicitly rejects `openai-assistant` members.
- each `langgraph` team member must be `langchain` or `multimodel`; other member types have no
  Java member runner.
- every server-side tool in `tools` has a registered Java `ServerTool` implementation. A missing
  implementation falls back to Python (or fails in `java-strict`) rather than silently dropping
  a declared capability. Browser WebMCP tools are separate and supported, see §6.
- `specs[]` are parsed per question into immutable OpenAPI operation descriptors and request-scoped
  `ServerTool` instances. The registry appends these supplementary tools directly; they do not need
  CDI beans or durable registrations. Operation names, descriptions and argument schemas follow the
  Python generator's FLOW naming/parameter rules.
  Only a fixed-entity RX read with descriptor-declared entity/field allowlists, local Etendo server,
  and simple projection/sort/paging arguments uses core `DefaultJsonDataService.fetch` under the
  caller's non-admin `OBContext`. RX-specific reads and writes use `EtendoHttp` loopback. Generic
  JSON add/update currently also enters cross-org-reference admin mode, while remove commits
  internally, so writes remain loopback to preserve the caller-context/security contract. Each
  operation logs its classification and reason; unknown operations remain loopback.
- the payload and each team member may declare `mcp_servers`. These become request-scoped
  LangChain4j MCP tools over stdio or Streamable HTTP.
- the payload does not request code execution (`code_execution`, CodeAct). CodeAct runs Python
  code and is deliberately not executed inside the Etendo JVM.
- agents with KB sources receive the implicit `KnowledgeBaseSearch` tool. It uses the Java
  pgvector index; missing pgvector or an unprovisioned table produces a clear runtime error.

MCP clients are opened for each question and closed when the engine finishes. Disabled servers
are ignored; missing transport defaults to stdio as in Python. Unsupported transports or
configurations produce no MCP tools for that question and are logged. Remote tool-name collisions
are qualified with the configured server name. Configured remote credentials are used only for the
MCP connection; the Etendo SWS token is not forwarded. The inbound MCP servlet is a separate
surface.

An agent with missing Java tool implementations falls back to Python (or fails, in `java-strict`),
and the warning names the missing `ETCOP_TOOL.VALUE` values. This keeps capability loss explicit.
Agents with Java implementations execute their tools in the server loop described in §6.

Team graphs run an in-process, bounded supervisor loop. The supervisor and each member can use
their own declared Java server tools and request-scoped MCP tools; member tools execute through
the same user-context boundary. The supervisor is capped at 100 iterations and each member uses
the configured tool-round cap (default 25; `copilot.engine.java.max.tool.rounds`).
Team task state is attached to the current `ToolExecutionContext` under a private versioned key, so it is
isolated to that user's request and is not persisted across requests.
Member histories replay ETCOP_MESSAGE `USER`/`ASSISTANT` entries and retain member-specific messages
in a bounded in-process cache (up to 64 conversations, 16 members per team, and 16 KiB/member).
These checkpoints are not compatible with Python's SQLite files and are lost on Tomcat restart.
`openai-assistant` team members remain unsupported because the Python graph runtime explicitly rejects them.

The Java selector falls back to Python for unsupported agent/member types, missing Java
implementations for declared server tools, or CodeAct. OpenAPI operations are generated at request
time; ordinary fixed-entity contracts execute in-process and RX-specific/other operations use
authenticated `EtendoHttp` loopback, as described above. CodeAct's Python execution is intentionally
not run in the Etendo JVM. An unavailable MCP server does not trigger a whole-request fallback: its
loader logs the failure and exposes no tools from that server. Explicit Java mode stores question
uploads in private Java temp files, resolves local image paths as multimodal content, and transcribes
audio in-process with the shared Whisper helper. `python` mode retains the legacy upload and
transcription routes. **Docker disappears only as the runtime for supported Java-engine requests,
not for the product.**

## 5. Deliberate limitations of this version

- **No token-level streaming.** Python emits event frames, not token deltas, and the chat front-end
  renders each SSE frame as its own message bubble (`App.tsx` → `handleNewMessage`). Java retains
  that granularity; single-agent server-tool invocations emit `role:"tool"` frames, then one final
  answer frame. Client tool-call turns retain their existing `tool_call` frame.
- Server-side tools run only when `ETCOP_TOOL.VALUE` has a CDI-discovered Java implementation;
  missing implementations still fall back to Python. Client-executed tools are also supported,
  see §6 below.
- Single-agent Java requests pass `output_schema` as a LangChain4j JSON response format and attach
  aggregate `input_tokens`, `output_tokens` and `total_tokens` usage metadata when providers report
  token counts. Team supervisor and member calls contribute to the same usage totals, and the team
  supervisor receives `output_schema` when configured. Provider-specific support depends on the
  model implementation's JSON response-format support.
- Java attachment paths are JVM-temp files and currently rely on `deleteOnExit`; no durable
  attachment retention/cleanup policy is provided.
- Tool implementations can read the immutable identity and defensive `extra_info` snapshot from
  `ToolExecutionContext`; no tool-specific context is exposed when the agent has no server tools.

## 6. Client-executed tools (WebMCP)

The engine also supports tools that the **browser** executes while the model chooses them. This is
the WebMCP capability registered by Etendo Classic; these client tools need no server implementation
and remain independent of Java `ServerTool` beans.

### The loop

```
client  POST /copilot/question  { question, app_id, conversation_id, client_tools:[...] }
server                          -> model chooses a tool
client  <- { "answer": { "role":"tool_call", "tool_calls":[{id,name,arguments}] } }
client  executes it in its own realm (document.modelContext.executeTool)
client  POST /copilot/question  { question, ..., tool_exchange:[ {calls:[...], results:[...]} ] }
server                          -> model continues, possibly asking for more tools
client  <- { "answer": { "response": "..." } }
```

The exchange is **stateless**: the client echoes every round back and the engine rebuilds the
message list from the stored conversation history plus those rounds. Nothing is kept between
requests, which is why this fits the existing design — the conversation memory already lives in
`ETCOP_MESSAGE`, not in the runtime. Echoing the calls back adds no trust boundary: the client
controls the tool results by construction anyway.

`ClientToolSupport.DEFAULT_MAX_TOOL_ROUNDS` (25) caps client-exchanged rounds and internal
server-tool loops, overridable with `copilot.engine.java.max.tool.rounds`. It is a backstop against a
tools, not a flow-control knob, so it sits well above what real work needs: creating a single
record already spends most of a dozen rounds — read the screen, find the window, open it, start the
record, list the fields, write them, save, read the screen back to confirm — and more when a
validation error sends it round again. The first version capped it at 5, which was fine for four
read-only tools and cut real work short as soon as the tool set grew.

### Wire fields

| Field | Direction | Meaning |
|---|---|---|
| `client_tools` | client → server | Tool descriptors, as `document.modelContext.getTools()` reports them. `inputSchema` is accepted **both** as a JSON string (what WebMCP actually sends) and as an object. |
| `tool_exchange` | client → server | Ordered rounds of `{calls, results}` already played out for this question. |
| `answer.tool_calls` | server → client | All calls requested by the model, with `id`, `name` and `arguments` (a JSON string). |
| `answer.server_results` | server → client | In a mixed batch, `{id, name, result}` records for server calls already executed. The client skips those call IDs and echoes these results alongside browser results in the next `tool_exchange.results`. |
| `answer.role` | server → client | `tool_call` on a turn with calls to execute or echo. |

Each top-level property of `inputSchema` is passed to LangChain4j as a **raw** schema rather than
translated field by field, so arbitrary nesting survives untouched.

### Server-executed tools

Server tools are selected by the `tools` request field, whose names are the exact
`ETCOP_TOOL.VALUE` values. The Java registry resolves each name to a CDI bean. A tool from this or
another module registers as an `@ApplicationScoped @Named("<ETCOP_TOOL.VALUE>")` bean implementing
`ServerTool`; it supplies its `ToolSpecification` and `execute(JSONObject, ToolExecutionContext)`.
The returned `name()` must match the `@Named` value. A non-empty `ETCOP_TOOL.JSON_STRUCTURE` supplies
the OpenAI function schema and overrides the implementation's parameter schema.

Each server invocation runs on the bounded `copilot-tool-N` daemon executor with a new,
non-administrator `OBContext`, its own DAL transaction and audit `SessionInfo`. Success commits;
any failure rolls back and cleans the worker before reuse. `ToolExecutionContext.admin(...)` is the
explicit balanced escape hatch for narrow privileged reads. Only tools declaring
`needsWebService() == true` require a web-service-enabled role. A tool timeout cancels the worker.
Failures are returned to the model as a safe tool-result error; no exception details or token enter
the model response. The server loop and client exchange use the same configured round limit.

A mixed batch still returns the original set of model calls. Results already produced on the server
are included in `answer.server_results`; `toolLoop.ts` executes only the remaining browser calls and
copies both result sets into the ordinary `tool_exchange.results`. The next request therefore
reconstructs one assistant tool-call message and one result for every call without adding a
conversation turn. Server-only rounds stay inside the same engine invocation.

### The client side

Two modules in the chat app implement the browser half:

| File | Role |
|---|---|
| `com.etendoerp.copilot.chat/src/utils/webmcp.ts` | Discovery and execution. Reads `document.modelContext` **of the iframe's own realm** — WebMCP scopes tools by origin, so the same-origin chat inherits the tools Classic registered without touching `window.parent`. Degrades to "no tools" on any browser without the API. |
| `com.etendoerp.copilot.chat/src/utils/toolLoop.ts` | The POST loop, capped at the same configured 25 rounds as the backend; it also skips server calls already listed in `answer.server_results` and echoes their results. |

`App.tsx` branches in `handleSendMessage`: when the page registered tools, the turn goes through
the POST loop; otherwise the existing GET/SSE path is untouched. Two send paths coexist on
purpose — the streaming path keeps working exactly as before for every agent that does not use
tools.

While a round runs the user sees a `tool` bubble (🛠️) naming the tools being executed, reusing
the message-replacement behaviour that already existed for the Python runtime's `tool` frames.

A note on response shapes: a tool-call turn returns `{ "answer": {...} }` while a completed turn
returns the flat tracked response from `processResponseAndTrack`. The client normalises both
(`data?.answer ?? data`); the server shapes were left alone because other callers
(`ConversationUtils`, `EvalTask`, `ExecTask`) depend on the flat one.

Remember the bundle in `web/com.etendoerp.copilot.dist` is committed: changing the chat requires
`npm run build` in `com.etendoerp.copilot.chat`.

### Two constraints

- **POST only.** The streaming GET whitelists four parameters
  (`RequestUtils.retrieveParametersAsJson`), so descriptors and results do not survive it and the
  turn degrades to a plain chat. Tool results are also large — an unfiltered window listing is
  several KB — which a query string could not carry anyway.
- **Java engine only.** With `copilot.engine=python` the descriptors are dropped; the selector
  logs a warning rather than letting the agent look like it is ignoring its own tools.

### Tool round trips are not conversation turns

An answer carrying `tool_calls` short-circuits `processResponseAndTrack`, so `ETCOP_MESSAGE` never
stores tool internals: one user turn produces one stored question and one stored answer, whatever
happens in between.

### Confirmation and refusal

Tools that mutate data ask the user to confirm before running, in the tool itself on the Classic
side (`OB.WebMCP.requireConfirmation`, on by default). Two consequences shape this side:

- **A tool call can take as long as a human takes to decide.** That costs nothing here: the tool
  runs *between* two requests, so no server request is held open while the dialog is up, and there
  is no per-round timeout to blow. The model call timeout
  (`copilot.engine.java.timeout.seconds`) only bounds the call to the provider.
- **A refusal is reported through `isError`, like a crash — and must not read like one.** A crash
  invites a retry; retrying a refusal would pop the dialog the user just dismissed. `webmcp.ts`
  therefore reports a refusal as an outcome with an explicit "do not retry", and only a real
  failure as a failure. It recognises a refusal by the `declined` flag when the tool sets it, and
  falls back to the dialog's wording.

### Security

The tool runs in the browser under the user's own session, so it inherits the user's permissions
and the backend cannot be bypassed. That is precisely why **prompt injection matters more than
authorization here**: an argument dictated by the content of a record the model read executes with
the user's rights. Anything that mutates data needs confirm-by-click, and navigation should be
re-checked against the real `OBContext`. See `docs/plans/2026-09-03-ui-tools.md` §11.

## 6.1. Inbound MCP servlet

`CopilotMcpServlet` is registered at `/sws/copilot/mcp/*`. Generated URLs now use
`/sws/copilot/mcp/{agentId}/mcp` and `/sws/copilot/mcp/{agentId}/direct/mcp`; this replaces the
Python listener's separate-port URLs. The servlet is synchronous, stateless Streamable HTTP at
protocol version `2025-03-26`: it handles initialize, ping, tool listing/calls and notifications,
does not assign session IDs or provide SSE, and authenticates the SWS identity for each request.
Role-to-agent assignment is checked on every request; tool calls run through the Java tool context.
See [MCP_SETUP.md](MCP_SETUP.md) for supported modes, auth and the exact external URL migration.

## 7. Dependency compatibility

The Java runtime uses LangChain4j **1.19.3** and the published MCP integration
**1.19.3-beta29** artifact (whose POM depends on LangChain4j 1.19.3). MCP depends on
Jackson 2.22.1. The integrated build deployed the matching Jackson 2.22.1 jars; no older
Jackson copies or duplicate Log4j versions were present in `WebContent/WEB-INF/lib`.
Newly added dependencies exclude Log4j because Etendo supplies the runtime Log4j jars.
Recheck the deployed library directory if these dependency versions change.

## 8. Testing

- `CopilotEngineSelectorTest` covers the supported/unsupported matrix, the rule most likely to
  regress: adding a capability to the Java engine means relaxing exactly one branch there.
- `ClientToolSupportTest` covers the WebMCP translation layer — the JSON-string `inputSchema`, the
  bare and wrapped tool-call envelopes, the exchange rebuild, a missing client result and the round
  cap.
- `LangChain4jEngineServerToolTest` covers server-loop continuation, safe failure results, the
  round cap, and the mixed server/client `server_results` envelope.
- `ServerToolRegistryTest` covers name resolution through an in-memory fake lookup,
  `JSON_STRUCTURE` overrides and missing implementation names.
- `ToolExecutionContextTest` exercises worker identity binding, timeout cancellation and cleanup via a
  lifecycle seam without a database fixture; it does not replace an integration test against DAL.

## 9. Tool runtime configuration

`copilot.tools.fs.read.roots` and `copilot.tools.fs.write.roots` are optional, comma-separated
lists of existing absolute directories. Configure them independently in `Openbravo.properties`:

```properties
copilot.tools.fs.read.roots=/Users/etendo/Workspace/erp,/Users/etendo/Workspace/tests
copilot.tools.fs.write.roots=/Users/etendo/Workspace/erp
```

When unset, both access lists contain only the conversation workspace
(`/tmp/etendo-copilot/<conversation>` by default). The legacy `copilot.tools.fs.roots`
property remains a fallback for either list that is not separately set. `COPILOT_WORKSPACE`
or `COPILOT_WORKING_DIR` system properties can select a different workspace root; the resolved
conversation directory must be outside protected application and system paths.

The hard deny-list cannot be overridden by configured roots: the deployed webapp, Tomcat
`webapps` and `conf`, the Etendo `config` directory, `Openbravo.properties`, `gradle.properties`,
`.git` internals, and dotfiles below the JVM user home. Path traversal, symlink escapes, and
hard-linked files are rejected. Writes are non-executable. File tools do not execute files.

The default quotas are 100 MiB and 1,000 files per conversation, and 25 MiB per file.
Override with `copilot.tools.fs.max.conversation.bytes`, `.max.conversation.files`, and
`.max.file.bytes`. Archive extraction defaults to 1,000 entries, 100 MiB expanded data, and
a 100:1 expansion ratio; configure `copilot.tools.fs.archive.max.entries`,
`.max.bytes`, and `.max.ratio`.

Docker containers use the isolated `none` network by default. To enable networking only for
one agent's DockerTool configuration, set `network_enabled` to `true` in that tool's
`tool_config` entry. The server property `copilot.tools.docker.network.enabled=true` enables
networking globally and overrides per-tool settings. Reused containers must match the selected
policy; a mismatch is rejected rather than silently changing a running container.

The `APICallTool` has an argument-dependent web-service gate: external URLs may be called
without a web-service-enabled role, while Etendo-origin requests are rejected with a clear
permission error. Redirects cannot forward Etendo credentials to another origin.
