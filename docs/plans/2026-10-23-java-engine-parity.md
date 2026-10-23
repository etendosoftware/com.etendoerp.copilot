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
| 1 | RX datasource endpoints (`/sws/com.etendoerp.etendorx.datasource/*`) and OpenAPI/FLOW tools | Loopback HTTP with a token minted per call, inside Java only. May move to DAL later, tool by tool. |
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

1. Fix the two bugs above.
2. Server-side tool foundation:
   - [x] Add `ToolExecutionContext`, the CDI-backed Java registry, and the bounded server-tool loop
     with shared client/server round limits and context isolation.
   - [ ] Add initial concrete tool implementations (`ReadFileTool`, `WriteFileTool`, `APICallTool`).
3. Rest of the toolpack, by priority (see the tools inventory).
4. OpenAPI/FLOW tools and MCP client.
5. Knowledge base and indexing (replaces `/addToVectorDB` and the other vector routes).
6. Teams / supervisor (`langgraph`).
7. MCP server in Java.
8. Intermediate streaming, attachments/images, usage tokens.
9. Remove `copilot.host`, `copilot.port`, `PythonCopilotEngine` and the Python routes.

## Risks to keep in sight

- `OBContext` is thread-bound: any thread hop (LangChain4j executors, streaming callbacks) must
  propagate and clean it, or a tool runs as the wrong user.
- Tools must run with the asking user's role, never in admin mode by default.
- A long agent run holds the request-thread Hibernate session; decide when it is closed.
