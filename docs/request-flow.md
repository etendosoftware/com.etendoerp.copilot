# Request Flow — end to end

This is the single most important document for anyone adding a new channel between the
UI and the LLM (for example **UI Tools**). It traces one question through all four hops
and shows the exact JSON at each one.

## 0. Context injection (before the question is even typed)

`copilotToolbarButton.js` builds an `activeWindowInfo` object from the SmartClient view:

```js
{
  windowId, tabId, tabTitle,
  selectedRecords: [ { …only string|number|boolean fields… } ],
  isFormEditing: bool,
  formValues?: { …only string|number fields… },
  contextTitle: "Sales Order - 1000123"   // computed in openCopilotWindow()
}
```

and posts it to the iframe:

```js
iframe.contentWindow.postMessage({ type: 'COPILOT_CONTEXT', data: messageData }, '*');
```

`App.tsx:675` stores it in `contextValue`. **There is no schema and no versioning on this
message.** Also note the `'*'` target origin — see `issues-and-improvements.md` §S1.

## 1. UI → Java

When the user sends a message, `App.tsx` does **not** send the context as structured
data. It string-concatenates it into the question (`App.tsx:355`):

```
<Context>{ …JSON.stringify(contextValue, null, 2)… }</Context>
<Question>the user text</Question>
```

Then `handleSendMessage` (`App.tsx:445`) opens an **`EventSourcePolyfill` — i.e. an HTTP
GET** against:

```
../../copilot/aquestion?question=<urlencoded>&app_id=<id>&conversation_id=<id>&file=<id>
```

If the encoded question exceeds 7000 characters it is instead POSTed to
`/copilot/cacheQuestion`, parked in the `HttpSession` under `cachedQuestion`, and the
`question` param is dropped from the URL (`App.tsx:385-396`,
`RestService.java:302-360`).

> **Hard constraint for any new payload.** The streaming path is a GET. On the GET path
> `RequestUtils.retrieveParametersAsJson` (`RequestUtils.java:33`) whitelists **only**
> `question`, `app_id`, `conversation_id` and `file`. Any new field must either be added
> to that whitelist *and* stay small enough for a URL, or ride the `cacheQuestion`
> mechanism, or the transport must move to POST.

## 2. Java: building the payload for Python

`RestService.handleQuestion` → `RestServiceUtil.handleQuestion` →
`RestServiceUtil.buildRequestJson` (`RestServiceUtil.java:474`).

The resulting object (langchain/multimodel case) is roughly:

```jsonc
{
  "name": "My Assistant",
  "type": "multimodel",
  "assistant_id": "…ETCOP_APP.id…",
  "system_prompt": "…",
  "provider": "openai", "model": "gpt-4o", "temperature": 1,
  "tools": [ { "type": "function", "function": { "name": "HelloWorldTool" } } ],
  "specs": [ { "name": "…", "type": "FLOW", "spec": "{…openapi…}" } ],
  "kb_vectordb_id": "…", "kb_search_k": 4,
  "mcp_servers": [ … ],
  "structured_output_json_schema": "…",
  "conversation_id": "uuid",
  "ad_user_id": "…", "ad_client_id": "…",
  "question": "<Context>…</Context><Question>…</Question>",
  "local_file_ids": [ … ],
  "extra_info": {
     "auth":         { "ETENDO_TOKEN": "…", "ETENDO_HOST": "…" },
     "model_config": { … }
  }
}
```

`extra_info` is assembled in `RestServiceUtil.addExtraContextWithHooks`
(`RestServiceUtil.java:904`) and is then handed to every registered
`CopilotQuestionHook` (`hook/CopilotQuestionHookManager.java:39`), which may add or
mutate anything in the request.

> **`extra_info` is the natural carrier for UI Tools.** It is an untyped `dict` on the
> Python side (`schemas.py: QuestionSchema.extra_info: Optional[dict]`), it survives the
> whole chain untouched, and it is already reachable from any tool through
> `ThreadContext.get_data("extra_info")`.

The POST target is chosen by `determineEndpoint` (`RestServiceUtil.java:512`):

| graph? | async? | endpoint |
|---|---|---|
| no | no | `/question` |
| no | yes | `/aquestion` |
| yes | no | `/graph` |
| yes | yes | `/agraph` |

## 3. Python: agent execution

`routes.py` validates the body into `QuestionSchema` / `GraphQuestionSchema`, then
`load_thread_context(question)` (`routes.py:127`) copies `extra_info`, `assistant_id`,
`conversation_id`, `ad_user_id`, `ad_client_id` into the per-request `ThreadContext`.

`MultimodelAgent.get_agent` (`multimodel_agent.py:243`) resolves the tool list through
`ToolLoader.get_all_tools(...)`: base Python tools filtered by the `tools` array, plus
the KB tool, plus OpenAPI-generated tools, plus MCP tools. Then `create_agent(...)`
binds them to the LLM.

Streaming (`aexecute`, `multimodel_agent.py:391`) iterates
`agent.astream_events(..., version="v2")` and passes each event to
`handle_events` (`langgraph_agent.py:193`), which converts a *few* event kinds into an
`AssistantResponse`:

| LangGraph event | becomes | `role` |
|---|---|---|
| `on_chain_start` (graph step) | "X is thinking…" | `node` |
| `on_tool_start` (depth 1) | the tool name | `tool` |
| `on_chain_end` (root) | the final answer | *(none → bot)* |

Everything else is dropped. **There is currently no event kind that carries structured
data to the UI** — that is exactly the gap the UI Tools plan fills.

## 4. Python → Java → UI (the return path)

`routes._response()` (`routes.py:68`) serialises each `AssistantResponse` as one SSE
line:

```
data: {"answer":{"response":"…","conversation_id":"…","role":"…","metadata":{…}}}
```

`RestServiceUtil.serverSideEvents` (`RestServiceUtil.java:338`) **relays every `data:`
line verbatim** to the browser and only parses the *last* line. So:

> **Any new SSE `role` passes through Java without a single Java change.** Java only
> needs to change if the new role must be validated or enriched server-side.

In the non-streaming path, only the last line survives, and
`processResponseAndTrack` → `extractResponse` (`RestServiceUtil.java:658`) copies
`answer.metadata` into the response returned to the browser. So `metadata` is the
sync-path equivalent of a custom role.

Finally `App.tsx:492` `eventSource.onmessage` ignores anything without
`answer.response`, routes by `conversation_id`, and renders by `role` in
`renderMessage` (`App.tsx:530`). Unknown roles fall into the `default` branch and are
rendered as a right-aligned user bubble — a small trap for new roles.

## Summary of extension seams

| Seam | Direction | Cost of use |
|---|---|---|
| `extra_info` in the request JSON | UI → LLM | free on Java, free on Python (untyped dict) |
| `CopilotQuestionHook` | Java → request JSON | new CDI bean, no core change |
| SSE `role` | LLM → UI | free on Java (verbatim relay), needs a UI renderer |
| `answer.metadata` | LLM → UI | already relayed in both sync and async paths |
| GET query string | UI → Java | **blocked**: 4-param whitelist + URL length |
