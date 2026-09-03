# Java Layer

Package root: `src/com/etendoerp/copilot/`. 70 classes.

## Servlets and routing

| Class | Role |
|---|---|
| `rest/CopilotSecureServlet` | Extends `HttpSecureAppServlet` (session or basic auth). Delegates to a **static singleton** `RestService`. |
| `rest/CopilotJwtServlet` | Token-based variant for external callers. |
| `rest/RestService` | Hand-rolled router on `request.getPathInfo()`. |
| `rest/RestServiceUtil` | 1129 lines. Payload assembly, HTTP call to Python, SSE relay, response extraction. |
| `rest/RequestUtils` | Body/param → `JSONObject` normalisation. |

### Endpoints

GET (`RestService.java:74`):

| Path | Handler |
|---|---|
| `/assistants` | `RestServiceUtil.handleAssistants()` — assistants visible to the current role |
| `/conversations`, `/conversationMessages`, `/archivedConversations` | `ConversationUtils` |
| `/aquestion` | `handleQuestion` in **async/SSE** mode |
| `/labels` | i18n labels for the chat |
| `/structure` | assistant configuration dump (debug/introspection) |

POST (`RestService.java:183`):

| Path | Handler |
|---|---|
| `/question` | synchronous question |
| `/file`, `/transcription` | multipart upload → Python |
| `/cacheQuestion` | park a long question in the `HttpSession` |
| `/configCheck` | liveness (always returns 200 — see issues §B7) |
| `/generateTitleConversation`, `/renameConversation`, `/deleteConversation`, `/restoreConversation`, `/permanentDeleteConversation` | `ConversationUtils` |

## Payload assembly

`RestServiceUtil.buildRequestJson` (`:474`) is the funnel. Order matters:

1. `generateAssistantStructure(...)` → dispatches by `app_type`:
   - `langchain` / `multimodel` → `CopilotUtils.buildLangchainRequestForCopilot`
   - graph assistants → `CopilotUtils.buildLangraphRequestForCopilot` (adds `assistants`
     and `graph.stages` for the supervisor pattern)
   - `openai` → `buildOpenAIrequestForCopilot`
2. `conversation_id` (generated as a UUID for langchain-derived assistants if absent)
3. `ad_user_id` / `ad_client_id` from `OBContext`
4. app-source content appended **into the question string**
   (`getAppSourceContent(..., FILE_BEHAVIOUR_QUESTION)`)
5. `CopilotUtils.checkQuestionPrompt(question)` — hard length limit
6. file ids
7. `addExtraContextWithHooks(...)`

## Hooks (the sanctioned extension points)

| Interface | Manager | When it runs |
|---|---|---|
| `hook/CopilotQuestionHook` | `CopilotQuestionHookManager` | last step of `buildRequestJson`; may mutate the whole request JSON |
| `hook/OpenAIPromptHook` | `OpenAIPromptHookManager` | while composing the system prompt |
| `hook/CopilotFileHook` | `CopilotFileHookManager` | when a knowledge-base file is synced |

All three are CDI `Instance<T>` injections sorted by `getPriority()` (lower first) and
filtered by `typeCheck(app)`. Adding a hook needs no core change — just a bean in this
module.

## Talking to Python

`sendRequestToCopilot` (`:540`) — a bare `HttpURLConnection`:

- host/port from `copilot.host` / `copilot.port` (`Openbravo.properties`), defaults
  `localhost:5005`
- `POST`, `Content-Type: application/json`
- **no connect/read timeout is set**
- on any exception it calls `Thread.currentThread().interrupt()` and rethrows a generic
  `ETCOP_ConnError`, discarding the real cause (issues §B1)

## SSE relay

`serverSideEvents` (`:338`):

- sets `text/event-stream` (`setEventStreamMode`)
- async: emits one priming `data: {}` then forwards every incoming line starting with
  `data:` **verbatim**
- keeps only `lastLine`; parses it (stripping the 5-char `data:` prefix in async mode)
- if the parsed answer has `role` = `"null"` or `"error"`, it is returned as the result
  so the caller can surface it

## Tracking

`TrackingUtil` writes `ETCOP_CONVERSATION` / `ETCOP_MESSAGE` after every answer and
`TelemetryUsageInfo` records `GET /aquestion` and `POST /question` only
(`RestService.java:53-58`).

## Data model access

Generated DAL entities live in `build/classes/com/etendoerp/copilot/data/` (not in
`src/`) — they are produced from `src-db/database/model/tables/*.xml` at build time.
`CopilotApp`, `CopilotAppTool`, `CopilotTool`, `CopilotAppSource`, `Conversation`,
`Message`, `TeamMember`, `CopilotOpenAIModel`, `CopilotAppMcp`, …
