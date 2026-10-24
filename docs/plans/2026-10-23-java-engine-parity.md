# Java engine parity with the Python runtime (ETP-5136)

Goal: the Java engine (`copilot.engine=java`) does everything the Python runtime does, so the Python
container can be deprecated. Everything is delivered under ETP-5136.

## Guiding principle

The target is capabilities and behaviour, not a line-by-line port. Whenever the migration shows a way
to improve, optimise or unify code (for example, one HTTP client instead of several near-identical
Python ones, dropping the token round trip, one request builder instead of the langchain/langgraph
duplicates), take it, as long as the user-visible behaviour of Python mode is preserved. Record each
such improvement in the PR description.

## Constraints

- `com.etendoerp.copilot` MUST NOT depend on `com.etendoerp.go`. Ideas from GO (authenticated
  in-process router, `executeInContext`) may be reimplemented inside this module, not imported.
- Tools run in-process in Tomcat, so the SWS token and the HTTP callbacks to Etendo that Python needs
  are replaced by direct `OBContext` / DAL access wherever possible.

## Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | RX datasource endpoints (`/sws/com.etendoerp.etendorx.datasource/*`) and OpenAPI/FLOW tools | Parse each FLOW operation at question time; run only fixed-entity plain reads through core JSON service in the caller's non-admin `OBContext`. Keep RX-specific, all writes, and non-entity operations on explicit, classified `EtendoHttp` loopback because core add/update elevates cross-org reference mode and remove commits internally. Never depend on `com.etendoerp.go`. |
| 2 | Python dynamic MCP server | Rewrite in Java inside this module, using GO as a design reference only. |
| 3 | `isWebServiceEnabled` gate | Kept only for tools that go over HTTP. In-process tools run with the asking user's role and no such gate. |
| 4 | Tools acting on another Etendo instance (login with credentials, `remote_host`) | Rewrite in Java as HTTP tools. |
| 5 | Tool loop style | Keep the hand-written loop: one code path for client tools (WebMCP) and server tools. |

## Out of scope: CodeAct

`code_execution` (CodeAct agents, `code_evaluators.py`, the Riza/evaluator path) is NOT migrated. It was a
proof of concept that did not take off. Consequences to implement:

- Add `code_execution` to `CopilotEngineSelector.unsupportedReason`, so a CodeAct agent falls back to
  Python (or fails in `java-strict`) instead of being silently served as a plain agent.
- Drop it from the "to migrate" list; when Python is removed, CodeAct is removed with it.

## Known bugs in the current branch

- Java sends `structured_output_json_schema`, Python reads `output_schema`: structured output
  probably never reaches Python.
- `code_execution` is ignored silently by the Java engine (see above).

## Implementation order

1. Bugs and selector behavior:
   - [ ] Align the structured-output payload field with Python's `output_schema`.
   - [x] Reject `code_execution` in the Java selector instead of silently ignoring CodeAct.
2. Server-side tool foundation:
   - [x] Add `ToolExecutionContext`, the CDI-backed Java registry, and the bounded server-tool loop
     with shared client/server round limits and context isolation.
   - [x] Add initial concrete tool implementations (`ReadFileTool`, `WriteFileTool`, `APICallTool`).
3. [x] Port the selected toolpack/devassistant tools; intentionally dropped tools remain listed in
   the tools inventory.
4. [x] OpenAPI/FLOW operation descriptors and request-scoped tools; [x] request-scoped MCP client over supported transports.
5. [x] Add PostgreSQL/pgvector knowledge-base memory, retrieval and text indexing with optional
   provisioning when the database lacks pgvector. [ ] Image-similarity/CLIP indexing.
6. [x] Add bounded Teams / supervisor (`langgraph`) execution with server and MCP tools.
7. [x] Add the authenticated Java MCP servlet and application-dictionary registration.
8. [ ] Intermediate streaming, attachments/images, transcription and usage tokens.
9. [ ] Remove `copilot.host`, `copilot.port`, `PythonCopilotEngine` and the Python routes.

## Risks to keep in sight

- `OBContext` is thread-bound: any thread hop (LangChain4j executors, streaming callbacks) must
  propagate and clean it, or a tool runs as the wrong user.
- Tools must run with the asking user's role, never in admin mode by default.
- A long agent run holds the request-thread Hibernate session; decide when it is closed.
