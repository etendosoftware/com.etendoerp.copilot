# Java engine (LangChain4j) — running an agent without the Python container

Status: implemented and **verified end to end on 2026-09-03**. Chat plus client-executed tools.

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
| `python` | Always call the Python container. Identical to the behaviour before this feature existed. |
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

# Rounds of client-executed tools allowed per question. Default 25.
copilot.engine.java.max.tool.rounds=25
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

**The chat front-end is not modified.** The engine emits the same server-sent event shape the
Python runtime emits:

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

- the agent type is `langchain` or `multimodel`
- the payload declares no server-side `tools` (client-executed tools are a different field and
  are supported, see §6)
- the payload declares no OpenAPI `specs`
- the payload declares no `mcp_servers`
- the agent has no knowledge-base app source

Everything else falls back to Python (or fails, in `java-strict`). This guard exists so that
capability loss is never silent: an agent with tools must not be answered by an engine that
ignores them.

Consequently the Python container is still required for: team graphs (`langgraph`), the OpenAI
Assistants app type, tools, knowledge bases / RAG, MCP servers, transcription, file processing
and the code interpreter. **Docker disappears for chat-only agents, not for the product.**

## 5. Deliberate limitations of this version

- **No token-level streaming.** The chat front-end renders every SSE frame as its own message
  bubble (`App.tsx` → `handleNewMessage`) and does not accumulate deltas — which is why the
  Python runtime streams *events*, not tokens. Emitting token deltas would create one bubble per
  token. Real streaming therefore needs a front-end change and was left out on purpose.
- **No server-side tools.** `ETCOP_TOOL` implementations live in Python and are not executed
  here; an agent that declares them still falls back. Client-executed tools *are* supported, see
  §6 below.
- **No `extra_info` consumption.** The block is still assembled and would be available to a
  future Java tool, but nothing reads it yet.

## 6. Client-executed tools (WebMCP)

The engine supports tools that the **browser** executes and the model merely chooses. This is what
makes the WebMCP tools registered by Etendo Classic usable from an agent, and it needs **no tool
implementations in Java at all**: adding a tool in the browser adds it to the agent with no server
change.

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

`ClientToolSupport.DEFAULT_MAX_TOOL_ROUNDS` (25) caps the rounds per question, overridable with
`copilot.engine.java.max.tool.rounds`. It is a backstop against a model that never stops calling
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
| `answer.tool_calls` | server → client | The calls to execute, each with `id`, `name` and `arguments` (a JSON string). |
| `answer.role` | server → client | `tool_call` on such a turn. |

Each top-level property of `inputSchema` is passed to LangChain4j as a **raw** schema rather than
translated field by field, so arbitrary nesting survives untouched.

### The client side

Two modules in the chat app implement the browser half:

| File | Role |
|---|---|
| `com.etendoerp.copilot.chat/src/utils/webmcp.ts` | Discovery and execution. Reads `document.modelContext` **of the iframe's own realm** — WebMCP scopes tools by origin, so the same-origin chat inherits the tools Classic registered without touching `window.parent`. Degrades to "no tools" on any browser without the API. |
| `com.etendoerp.copilot.chat/src/utils/toolLoop.ts` | The POST loop, capped at the same 5 rounds as the backend. |

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

## 7. Known classpath conflict — read before the first build

LangChain4j 1.19.0 requires `jackson-databind` **2.22.1**. Etendo already deploys
`jackson-databind` **2.13.2.2** in `WebContent/WEB-INF/lib`. Both jars land there and Tomcat
loads them in filename order, so 2.13 wins and LangChain4j fails at runtime with
`NoSuchMethodError` / `NoClassDefFoundError` under `com.fasterxml.jackson`.

The real fix — bumping the platform's Jackson — is outside this module. Until then either
remove the stale 2.13.x jars from `WebContent/WEB-INF/lib` after a build, or enable the
`resolutionStrategy.force` block already written (commented) in `build.gradle`.

`slf4j-api` 2.0.18 vs the deployed 2.0.17 and `jspecify` 1.0.0 are compatible and need nothing.

## 8. Testing

- `CopilotEngineSelectorTest` covers the supported/unsupported matrix, the rule most likely to
  regress: adding a capability to the Java engine means relaxing exactly one branch there.
- `ClientToolSupportTest` covers the WebMCP translation layer — the JSON-string `inputSchema`, the
  bare and wrapped tool-call envelopes, the exchange rebuild, a missing client result and the round
  cap.
