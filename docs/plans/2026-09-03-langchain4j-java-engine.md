# Plan — Java-native Copilot engine with LangChain4j (drop Docker/Python for the chat path)

Status: F0-F3 implemented (see docs/java-engine.md); F4-F5 pending
Date: 2026-09-03
Scope: `com.etendoerp.copilot` only.

---

## 1. Verdict

**Yes, it is feasible, and it is much smaller than it looks.** The chat path can run
entirely inside Tomcat with LangChain4j, with **no changes to the React chat** and no
new endpoint.

The reason is a fact that is easy to miss until you read `RestServiceUtil`: for
`langchain` / `multimodel` agents, **Java already builds the complete agent definition**
and Python is only a stateless executor of it. Everything the LLM call needs is assembled
on the Java side in `CopilotUtils.buildLangchainRequestForCopilot` before a single byte
crosses the wire:

| Field sent to Python | Source (already Java) |
|---|---|
| `system_prompt` | `CopilotUtils.getAssistantPrompt(app)` (`ETCOP_APP.PROMPT` + app sources + var replacement) |
| `provider`, `model` | `CopilotModelUtils.getModelProviderResult(app)` |
| `temperature` | `ETCOP_APP.TEMPERATURE` |
| `history` | `TrackingUtil.getHistory(conversationId)` — **from `ETCOP_MESSAGE`, not from Python** |
| `tools` | `ToolsUtil.getToolSet(app)` (OpenAI function-schema JSON) |
| `extra_info` (auth token, model config, hook output) | `RestServiceUtil.addExtraContextWithHooks` |
| `specs`, `mcp_servers`, `kb_search_k`, `structured_output` | app sources / `MCPUtils` / `ETCOP_APP` |

Python owns almost no state for this app type. **Conversation memory is already
Java-side.** So a Java engine is not a rewrite of Copilot — it is a second implementation
of a contract Java already produces.

The API key is likewise already reachable from Java: `openai.api.key` in
`Openbravo.properties` (`OpenAIUtils.getOpenaiApiKey()`).

### The single seam

`RestServiceUtil.handleQuestion(...)` (line ~426) does exactly three things:

```
buildRequestJson(...)                                   ← stays identical
sendRequestToCopilot(async, queue, json, copilotApp)    ← THE ONLY THING TO REPLACE
processResponseAndTrack(...)                            ← stays identical
```

`sendRequestToCopilot` (line ~540) is the whole Python coupling for the chat path: it
opens `http://copilot.host:copilot.port/{question|aquestion|graph|agraph}`, POSTs the
JSON, and either parses the body or relays SSE via `serverSideEvents`.

Replace that one method with a dispatcher and the rest of the stack — tracking,
`ETCOP_MESSAGE` persistence, the SSE frame format, the React client — is untouched.

---

## 2. Routing: **decided — a property, not an endpoint**

The user's first instinct was a separate `/copilotJ` endpoint. That was dropped: it forces a
front-end change and doubles an already hand-rolled routing if-chain in `RestService`.

**Decision: `copilot.engine` in `Openbravo.properties`.**

```properties
copilot.engine=python      # default, unchanged behaviour
copilot.engine=java        # in-process when supported, fall back to Python otherwise
copilot.engine=java-strict # in-process always, fail instead of falling back
```

A per-agent AD column was considered and rejected for this iteration: a property needs no
`ETCOP_APP` model change, no AD field, no `update.database`, and is enough to run and compare the
engine. If per-agent selection is ever needed, `CopilotEngineSelector` is the only place to change.

## 3. Architecture

```
RestService (unchanged)
   │
RestServiceUtil.handleQuestion (unchanged)
   │   buildRequestJson  →  the SAME JSONObject as today
   ▼
CopilotEngineSelector.get(copilotApp)          ← NEW
   ├── PythonCopilotEngine   (today's sendRequestToCopilot, moved verbatim)
   └── LangChain4jEngine     ← NEW
   ▼
same JSONObject back  →  processResponseAndTrack (unchanged)  →  same SSE  →  same React
```

New interface (in `com.etendoerp.copilot.engine`):

```java
public interface CopilotEngine {
  /**
   * @param request the payload built by buildRequestJson - identical for both engines
   * @param queue   servlet response, used to write SSE frames when async
   * @return the final answer JSON, in the shape processResponseAndTrack expects
   */
  JSONObject execute(boolean asyncRequest, HttpServletResponse queue,
                     JSONObject request, CopilotApp app) throws IOException, JSONException;
}
```

Keeping `JSONObject` as the input type (rather than inventing a typed request object) is
deliberate: it guarantees the two engines stay contract-compatible and makes the
Python engine a pure move with zero edits.

### SSE frame parity is the acceptance criterion

The React client parses `data: {"answer": {...}}` lines. The Java engine MUST emit the
same frames as Python does, using the existing `RestServiceUtil.sendEventToFront`. From
`docs/python-layer.md`, the roles the client understands are `node`, `tool`, `error`,
`debug`, and absent → bot. For v1 the Java engine emits:

- token deltas → frames with no role (bot bubble), same as today
- an `error` frame on failure
- the final frame carrying `conversation_id` + `metadata`

If the frames match byte-for-byte in shape, the front-end change set is literally empty.

---

## 4. What can and cannot move to Java

### Moves cleanly (v1 target)

| Capability | LangChain4j equivalent |
|---|---|
| Single-agent chat with system prompt | `ChatModel` / `StreamingChatModel` + `SystemMessage` |
| Token streaming | `StreamingChatResponseHandler.onPartialResponse` → `sendEventToFront` |
| Conversation history | Feed `TrackingUtil.getHistory` into the message list. **No memory store needed** — history already arrives in the request. |
| Temperature, max tokens | `OpenAiChatModel.builder().temperature(...)` |
| OpenAI provider | `langchain4j-open-ai` |
| Anthropic / Gemini / Ollama | `langchain4j-anthropic`, `-google-ai-gemini`, `-ollama` |
| LiteLLM gateway | `langchain4j-open-ai` with a custom `baseUrl` (LiteLLM is OpenAI-compatible) — this is how the current LiteLLM setup keeps working |
| Structured output | `responseFormat` JSON schema (`ETCOP_APP.STRUCTURED_OUTPUT`) |

### Does **not** move in v1 (agent stays on Python)

| Capability | Why |
|---|---|
| `openai-assistant` app type | Assistants API + vector store lifecycle lives in `OpenAIUtils`/Python; out of scope |
| `langgraph` teams (graph questions) | Multi-agent supervisor graph; LangChain4j has no LangGraph equivalent |
| Knowledge base / RAG (`kb_vectordb_id`, `search_k`) | ChromaDB ingestion is Python. Deferrable — `langchain4j-embeddings` + pgvector is a later, separate project |
| Python tools (`ETCOP_TOOL` implementations) | Must be reimplemented in Java. **User has accepted this cost.** Deferred to a later phase |
| MCP servers | Doable — the MCP Java SDK is *already on the classpath* (`mcp-json-jackson3-1.1.1.jar`) and `langchain4j-mcp` exists — but deferred with tools |
| Whisper transcription, file processing | `/transcription`, `/file` endpoints stay Python |
| Code interpreter | Sandbox is Python-side |

So **Docker is not eliminated yet** — it is eliminated *for agents that only chat*. That
is the honest framing, and it is still worth a lot: it is the demo/edge/on-prem case where
"install Etendo, get an assistant" beats "install Etendo + Docker + a Python container".
Full Docker removal only arrives after tools + KB are ported, and `openai-assistant` /
`langgraph` agents are either dropped or reimplemented.

---

## 5. Phases

### F0 — Extract the seam (no behaviour change) — DONE
- Create `com.etendoerp.copilot.engine.CopilotEngine`.
- Create `PythonCopilotEngine` by **moving** the body of `sendRequestToCopilot` verbatim.
- `sendRequestToCopilot` becomes `CopilotEngineSelector.get(app).execute(...)`, with the
  selector returning `PythonCopilotEngine` unconditionally.
- Ship and verify nothing changed. This phase is independently valuable and reviewable.

### F1 — Dependencies and a smoke test — DONE (dependencies added; the Jackson conflict is confirmed and documented, see docs/java-engine.md §6)
- Add to `build.gradle` `dependencies`:
  ```groovy
  implementation('dev.langchain4j:langchain4j:1.x')
  implementation('dev.langchain4j:langchain4j-open-ai:1.x')
  ```
  (pin the exact version at implementation time; Java 17 is required and Etendo's root
  `build.gradle` already sets `sourceCompatibility = JavaVersion.VERSION_17`).
- Verify the jars land in `WebContent/WEB-INF/lib` — the module's existing
  `com.konghq:unirest-java:3.14.5` proves the mechanism works (`unirest-java-3.14.5.jar`
  is there today).
- **Resolve the Jackson conflict first — this is the top technical risk.** See §7.
- Smoke test: a `src-test` case that calls OpenAI and asserts a non-empty completion.

### F2 — `LangChain4jEngine`, chat only — DONE (no token streaming: the front-end renders one bubble per frame, so token deltas would need a front-end change)
- Read `system_prompt`, `history`, `question`, `temperature`, `model`, `provider` out of
  the request JSON (do **not** re-query the DB — consume the same contract Python does,
  so the engines cannot drift).
- Build the message list: `SystemMessage` + history + `UserMessage`.
- Async path: `StreamingChatModel.chat(messages, handler)`; on each partial token, write
  an SSE frame with `sendEventToFront`. On complete, write the final frame with
  `conversation_id` and `metadata`, and return the JSON `processResponseAndTrack` expects.
- Sync path: `ChatModel.chat(...)`, return the same shape.
- **Set explicit timeouts** — the Python engine has none (issue B2 in
  `docs/issues-and-improvements.md`); do not reproduce that bug in the new engine.

### F3 — Provider mapping — DONE (OpenAI / OpenAI-compatible, Anthropic, Gemini, Ollama)
- `ChatModelFactory` mapping `provider` → a LangChain4j model builder:
  `openai`, `anthropic`, `gemini`, `ollama`, plus **openai-compatible with custom
  `baseUrl`** for LiteLLM.
- API keys: reuse `OpenAIUtils.getOpenaiApiKey()` and add sibling properties per provider,
  following the existing `readPropertyWithLegacyCompatibility` convention.
- Honour `max_tokens` from `ETCOP_MODEL` via the `model_config` already in `extra_info`.

### F4 — Parity harness + docs — PARTIAL (docs and the supported/unsupported unit test are in; the two-engine parity harness is not)
- A test that runs the same prompt through both engines and diffs the SSE frame *shapes*
  (not the text — LLM output is non-deterministic).
- A "Java engine limitations" table in `docs/` listing what silently degrades, and a
  **validation on save**: an agent with `RUN_IN_JAVA = true` plus tools / a KB / a
  `langgraph` type must be rejected by an event handler, not fail at runtime.

### F5 — Tools (separate project, user's stated "later")
- `ETCOP_TOOL` gains a Java implementation class column; `ToolsUtil.getToolSet` already
  emits OpenAI function schemas that LangChain4j `ToolSpecification` can consume.
- Webhook-backed tools are the cheapest win: the tool body is just an authenticated HTTP
  call to `/webhooks/*`, which Java can do natively — a *generic* `WebhookTool` would
  port a large share of the existing catalogue without per-tool Java code.
- MCP via `langchain4j-mcp` reuses `MCPUtils.getMCPConfigurations`.

**F0–F4 ≈ 6 days** for "an Etendo agent chats with an LLM with no Python container".

---

## 6. Files touched

**New** (`src/com/etendoerp/copilot/engine/`):
`CopilotEngine.java`, `CopilotEngineSelector.java`, `PythonCopilotEngine.java`,
`LangChain4jEngine.java`, `ChatModelFactory.java`, `SseEmitter.java` (thin wrapper over
`sendEventToFront`).

**Modified:**
`RestServiceUtil.java` (one method body → delegation), `build.gradle` (dependencies),
`src-db/database/model/tables/ETCOP_APP.xml` + sourcedata (the `RUN_IN_JAVA` column and
its field), a new `AssistantValidations` rule, `docs/`.

**Untouched:** the React chat, `RestService` routing, `TrackingUtil`, all hooks, the
Python tree.

---

## 7. Risks

| # | Risk | Assessment |
|---|---|---|
| R1 | **Jackson classpath collision.** `WEB-INF/lib` currently holds `jackson-databind 2.13.2.2` *and* `jackson-core 3.0.3` *and* `jackson-annotations 2.20` — already an inconsistent set. LangChain4j needs a modern Jackson 2.x. | **Highest risk; resolve in F1 before anything else.** Options: align the core Jackson to a 2.17+ set, or shade LangChain4j's Jackson. If neither is acceptable this plan stalls, so spike it first. |
| R2 | Streaming from a servlet thread | Tomcat blocking I/O + a `StreamingChatResponseHandler` on the LangChain4j thread means cross-thread writes to one `PrintWriter`. Needs a `TransferQueue` hand-off — the pattern already exists in `RestServiceUtil.sendData`. Manageable, but do not skip it. |
| R3 | Feature drift between engines | Mitigated by consuming the *same* request JSON and by the F4 save-time validation. |
| R4 | Provider coverage smaller than LiteLLM's | LiteLLM's value is normalising ~100 providers. Mitigate by keeping the openai-compatible `baseUrl` path — a Java agent can still point at LiteLLM if the container is running. |
| R5 | Two agent runtimes to maintain | Real and permanent. Only justified if the Java engine eventually becomes the default for simple agents. Decide the intent up front: **is this the escape hatch, or the future default?** |

---

## 8. Open questions

1. **Endpoint vs agent flag** — plan assumes the flag. Confirm.
2. **Is the goal "no Docker for simple agents" or "no Docker at all"?** The first is ~6
   days. The second requires porting tools, KB/RAG and dropping `openai-assistant` /
   `langgraph` — a quarter, not a sprint.
3. **Jackson** — is aligning core's Jackson acceptable, or must LangChain4j be shaded?
   This decision gates F1.
4. **Provider scope for v1** — OpenAI only, or OpenAI + openai-compatible (LiteLLM/Ollama)?
   Recommendation: both, since the second is nearly free.
5. **Relationship to the UI-tools plan** (`2026-09-03-ui-tools.md`) — UI tools were
   designed against the Python return path. If the Java engine is going to be the target,
   sequence this plan's F0–F2 first so the UI-tool return path is written once.
