# Python Layer

Package root: `copilot/`. Entry point `run.py`.

## Boot

`run.py` reads config through `copilot.baseutils.logging_envvar` (which maps
`copilot.port`-style property names onto env vars), optionally starts `debugpy`, starts
the **simplified dynamic MCP server**, then `uvicorn.run(app.app, 0.0.0.0, 5005)`.

## Routes (`copilot/core/routes.py`)

| Route | Schema | Mode |
|---|---|---|
| `POST /question` | `QuestionSchema` | sync |
| `POST /aquestion` | `QuestionSchema` | SSE (`StreamingResponse`) |
| `POST /graph` | `GraphQuestionSchema` | sync, forces `LanggraphAgent` |
| `POST /agraph` | `GraphQuestionSchema` | SSE, forces `LanggraphAgent` |
| `GET /tools` | — | introspection: name → description + args |
| vector-db routes | `VectorDBInputSchema`, `TextToVectorDBSchema` | KB indexing |

`load_thread_context(question)` (`routes.py:127`) is called by every question route and
is the only place request-scoped data enters `ThreadContext`:

```python
ThreadContext.set_data("extra_info", question.extra_info)
ThreadContext.set_data("assistant_id", question.assistant_id)
ThreadContext.set_data("conversation_id", conversation_id)
ThreadContext.set_data("ad_user_id", question.ad_user_id)
ThreadContext.set_data("ad_client_id", question.ad_client_id or "0")
```

`_response(response)` (`routes.py:68`) is the SSE serialiser. Note it only forwards
`metadata` on the `AssistantResponse` branch — the fallback branch silently drops it
(issues §B4).

## Schemas (`copilot/core/schemas.py`)

`AssistantSchema` is the assistant configuration; `QuestionSchema` extends it with
`question`, `conversation_id`, `history`, `extra_info`. `GraphQuestionSchema` is a
parallel (not inheriting) model for supervisor graphs — the duplication between the two
is a known maintenance hazard (issues §Q2).

`extra_info: Optional[dict]` — completely untyped, which is why it is such a convenient
carrier, and why anything put there needs its own validation.

## Agents

### `MultimodelAgent` (`copilot/core/agent/multimodel_agent.py`)

- `get_agent()` builds the LLM (`get_llm`), resolves tools via `ToolLoader.get_all_tools`,
  applies `fix_tools_for_provider`, and returns either a CodeAct agent
  (`code_execution=True`) or `langchain.agents.create_agent(...)`.
- `execute()` — `agent.ainvoke`, returns the last message.
- `aexecute()` — `agent.astream_events(version="v2")`, each event through
  `handle_events`.
- Memory: `AsyncSqliteSaver.from_conn_string(get_checkpoint_file(assistant_id))`, thread
  id = `conversation_id`. If the thread already exists, the `history` array from Etendo
  is **not** replayed (`get_messages_array`).

### `LanggraphAgent` (`copilot/core/agent/langgraph_agent.py`)

Supervisor pattern (`LangSupervisorPattern`) over `GraphMember`s built by `MembersUtil`.
Same streaming machinery.

### Event → response mapping

`handle_events(copilot_stream_debug, event, thread_id)` (`langgraph_agent.py:193`) is the
**only** place where LangGraph internals become something the UI can see. It handles
`on_chain_start`, `on_tool_start`, `on_chain_end` and drops everything else. It wraps the
whole body in a bare `except Exception` that only logs at debug level (issues §B5).

Roles produced today: `node`, `tool`, `error`, `debug`, and *no role* for the final
answer (the UI defaults it to `bot`).

## ThreadContext (`copilot/core/threadcontext.py`)

A `ContextVar[dict]` for the current request plus a **module-level
`_conversation_contexts: Dict[str, dict]`** that is never evicted (issues §B6). Tools read
request data from here — e.g. the Etendo auth token in `extra_info["auth"]`.

## Tests

`tests/` (pytest, `tox.ini`, `conftest.py`) — ~40 test modules, heavily weighted toward
MCP and tool loading. `pipelines/run-tests/` holds the CI wiring.
