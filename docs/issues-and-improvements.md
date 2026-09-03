# Issues, Smells and Improvement Opportunities

Found while reverse-engineering the module on 2026-09-03 (v3.3.0, commit `8402f0b4`).
Nothing here has been fixed — this is a backlog, ordered by tier. Severity:
**B** = bug, **S** = security, **P** = performance, **Q** = quality/maintainability.

---

## Java

### B1 — `sendRequestToCopilot` swallows the cause and fakes an interrupt
`rest/RestServiceUtil.java:575-580`

```java
} catch (Exception e) {
  log.error(e);
  Thread.currentThread().interrupt();          // ← not an InterruptedException
  throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
}
```

Two problems: (a) the request thread's interrupt flag is set on *any* failure, including
a JSON parse error, which can make unrelated blocking calls later in the same thread
throw; (b) the real message (connection refused, 500 from Python, malformed JSON) never
reaches the user or the log message — only the stack trace. Every Python-side failure
looks identical to the user.

**Fix:** catch `IOException` specifically, keep the cause
(`new OBException(msg, e)`), and only `interrupt()` inside a `catch (InterruptedException)`.

### B2 — No timeouts on the connection to Python
`rest/RestServiceUtil.java:552`

`HttpURLConnection` is used without `setConnectTimeout` / `setReadTimeout`. If the Python
container hangs, the Tomcat request thread hangs with it, indefinitely. Under load this
is how the connector's thread pool gets exhausted.

**Fix:** configurable timeouts (long read timeout for SSE, short connect timeout).

### B3 — `serverSideEvents` assumes the last line is a `data:` line
`rest/RestServiceUtil.java:359-362`

```java
String payload = asyncRequest ? lastLine.substring(5) : lastLine;
jsonLastLine = new JSONObject(payload);
```

If the stream ends with a heartbeat, a comment (`: keep-alive`) or a blank-ish line, this
either throws `StringIndexOutOfBoundsException` or `JSONException`, which becomes a
generic `ETCOP_SSEError` *after* the user already saw the correct answer streamed.

**Fix:** track the last line that actually starts with `data:`.

### B7 — `/configCheck` always returns 200
`rest/RestService.java:283-289`

The method sets `SC_OK` unconditionally inside a `try` that can't fail, so the
"is Copilot reachable?" check never reports a problem. It should actually probe the
Python host/port.

### P1 — `parseJsonFromRequest` builds the body with `reduce("", String::concat)`
`rest/RequestUtils.java:64`

Quadratic string concatenation for every POST body. Use
`Collectors.joining()` or read the stream directly.

### Q1 — `RestService` is a hand-rolled `if/else` router
`rest/RestService.java:74-113` and `:183-231` — 17 endpoints matched with
`StringUtils.equalsIgnoreCase` chains. Adding an endpoint means editing two long methods
and it is easy to place a new branch after the fallthrough. A `Map<String, Handler>`
would remove the whole class of mistake.

### Q6 — `CopilotSecureServlet` holds a mutable static singleton
`rest/CopilotSecureServlet.java:19-30` — lazy, non-synchronised `getInstance()`. Harmless
today (`RestService` is stateless apart from the static `asyncRequests` map) but it is a
race waiting to matter.

### Q7 — `RestService.asyncRequests` is dead state
`rest/RestService.java:48` — a `static Map<String, TransferQueue<String>>` that is never
written to. Leftover from an earlier async design.

---

## Python

### B4 — `_response()` drops `metadata` on the fallback branch
`copilot/core/routes.py:68-94`

```python
if type(response) == AssistantResponse:      # ← also: use isinstance()
    … includes "metadata" …
else:
    … no "metadata" key …
```

Any response object that is a *subclass* of `AssistantResponse`, or the
`AgentResponse`-wrapped shape, loses its metadata silently. `type(x) == C` should be
`isinstance(x, C)`.

**This matters for UI Tools**: if actions travel in `metadata`, this branch is a silent
drop.

### B5 — `handle_events` swallows every exception
`copilot/core/agent/langgraph_agent.py:220-221`

```python
except Exception as e:
    copilot_debug(f"Error in event processing: {str(e)}")
```

A bug in an event handler produces no user-visible error and no `logger.exception` — the
stream just silently stops emitting that kind of event. Debug-level only, so in a normal
deployment it is invisible.

### B6 — `ThreadContext._conversation_contexts` never evicts
`copilot/core/threadcontext.py:9`

A module-level `Dict[str, dict]` keyed by `conversation_id`, written by
`save_conversation()` and never cleaned. Every conversation the process ever served stays
resident, including whatever `extra_info` carried (auth tokens, record payloads). This is
both a memory leak and a data-retention issue.

**Fix:** an LRU with a bounded size / TTL.

### B8 — `parse_response()` never marks errors
`copilot/core/tool_wrapper.py:57-73`

```python
is_error = False
…
return ("ERROR: " if is_error else "") + str(response)
```

`is_error` is assigned once and never set to `True`, so the `"ERROR: "` prefix is dead
code. When a tool raises, `_run` wraps it in `ToolOutputError` and this function returns
the bare error string — the LLM receives a plain sentence indistinguishable from a
successful result and will often report it to the user as fact.

**Fix:** set `is_error = True` in the `"error" in tool_response` branch.

### P2 — `tools_loaded` global grows without bound
`copilot/core/agent/multimodel_agent.py:39, 286`

`tools_loaded[assistant_id] = _enabled_tools` is written on every request and **never
read anywhere in the codebase**. Dead code that pins tool instances in memory.

### P3 — MCP client rebuilt on every request
`copilot/core/agent/multimodel_agent.py:151-176` — a new `MultiServerMCPClient` is
constructed and `get_tools()` awaited (up to 45 s) per question, with no caching. For an
assistant with MCP servers this is added latency on every single turn.

### Q2 — `QuestionSchema` and `GraphQuestionSchema` duplicate ~15 fields
`copilot/core/schemas.py` — the two request models were copy-pasted rather than sharing a
base. Any new request field (such as a UI-tools block) has to be added twice or it will
work on `/aquestion` and silently vanish on `/agraph`.

### Q3 — `extra_info` is an untyped `dict`
Same file. Everything crossing the Java↔Python boundary outside the declared fields is
unvalidated. Convenient, but it means a typo in a key produces "the feature does nothing"
rather than a 422.

---

## Front-end

### S1 — `postMessage` with `'*'` target origin, and no origin check on receipt
`web/com.etendoerp.copilot/js/copilotToolbarButton.js:387` sends the full window context
(including form values of the record being edited) with `targetOrigin: '*'`.
`com.etendoerp.copilot.chat/src/App.tsx:673` accepts any `message` event without checking
`event.origin` or `event.source`.

Today both ends are same-origin so the practical exposure is limited, but the pattern is
exactly the one that leaks business data the day the chat is served from another host,
and it lets any embedded third-party frame inject a fake `COPILOT_CONTEXT`.

**Fix:** send with an explicit origin, and validate `event.origin === window.location.origin`
on receipt.

### S2 — Business data is smuggled into the prompt as text
`App.tsx:353-358` — the selected records and live form values are `JSON.stringify`-ed
into the question inside `<Context>…</Context>`. Consequences:
- it counts against the model's context window and against `LANGCHAIN_MAX_LENGTH_QUESTION`;
- it is stored verbatim in `ETCOP_MESSAGE.MESSAGE`;
- record content becomes indistinguishable from user instructions — a **prompt-injection**
  surface (a `_identifier` containing "ignore previous instructions…" is just text to the
  model).

**Fix:** carry context as structured data (`extra_info`) and let the system prompt frame
it explicitly as untrusted data.

### B9 — The streaming request is a GET with the whole prompt in the URL
`App.tsx:474-490`

`EventSourcePolyfill` forces GET, so the question (with the embedded context) goes in the
query string. This produces:
- the 7000-character `cacheQuestion` workaround (`App.tsx:385`), which parks the prompt in
  the `HttpSession` — a second, stateful code path with its own failure modes;
- prompts written into Tomcat/proxy access logs;
- a hard ceiling on anything else the UI ever wants to send.

**Fix (also a prerequisite for UI Tools):** POST + `fetch` with a streaming reader
(`ReadableStream`), or `@microsoft/fetch-event-source`.

### B10 — `addDataUpdatedHandler` is re-registered on every click
`copilotToolbarButton.js:398-427` — each press of the toolbar button attaches another
grid handler; each one calls `openCopilotWindow` again. After N clicks, one selection
change fires N context updates.

**Fix:** register once, guard with a flag on the view.

### B11 — Unknown SSE roles render as user messages
`App.tsx:598-607` — the `default` branch of `renderMessage` uses `type="right-user"`.
Any role the backend adds and the UI does not know about is displayed as if the user had
said it. New roles must always be added here.

### Q4 — `App.tsx` is an 861-line component
State, SSE lifecycle, message reducing, context handling and rendering all live in one
function component with ~15 `useState`s. Any new feature (UI tools included) makes it
worse unless extracted first — at minimum a `useCopilotStream` hook and a
`MessageRenderer` component.

### Q5 — Hardcoded dev credentials
`src/utils/environment.ts:14` — `Authorization: 'Basic ' + btoa('admin:admin')` when
`NODE_ENV !== 'production'`. Guarded by the build mode, but it is a credential in the
source tree; it belongs in a `.env` file.

### Q8 — `console.log` left in the production path
`App.tsx:407, 431, 437, 494` — emoji-prefixed debug logs on every streamed message.

---

## Cross-cutting

### Q9 — No documentation existed before this folder
`docs/` contained only `MCP_SETUP.md`. Nothing described the request contract between the
three tiers, which is why the contract has drifted (context-in-the-prompt, GET-only
streaming, whitelisted GET params).

### Q10 — The React bundle is committed
`web/com.etendoerp.copilot.dist/` holds build output in git. Source and bundle can (and
do) drift; there is no CI check that the committed dist matches
`com.etendoerp.copilot.chat/`.

### Q11 — Two sources of conversation truth
`ETCOP_CONVERSATION`/`ETCOP_MESSAGE` vs the LangGraph SQLite checkpoint per assistant.
Deleting a conversation in Etendo leaves the LLM thread alive; a restored conversation may
"remember" things the UI no longer shows.
