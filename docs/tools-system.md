# Tools System

A "tool" is a Python class the LLM may call. Its *availability* is decided in Etendo, its
*implementation* lives in Python. Understanding both halves is a prerequisite for the UI
Tools plan.

## 1. Declaration in the Application Dictionary

| Table | Meaning |
|---|---|
| `ETCOP_TOOL` | the catalogue. Columns: `VALUE` (the Python class name — this is the join key), `NAME`, `DESCRIPTION`, `JSON_STRUCTURE`, `USE_MODEL`, `SYNC_STRUCTURE` |
| `ETCOP_APP_TOOL` | many-to-many: which assistant may use which tool |
| `ETCOP_TOOL_WBHK` | webhooks a tool is allowed to call |

`ToolsUtil.getToolSet(app)` (`src/com/etendoerp/copilot/util/ToolsUtil.java:35`) turns the
`ETCOP_APP_TOOL` rows into the `tools` array of the request:

```json
[ { "type": "function", "function": { "name": "HelloWorldTool" } } ]
```

If `JSON_STRUCTURE` is filled and is not `{}`, that raw JSON is sent instead — so a tool
can ship a full OpenAI function schema from the database.

## 2. Implementation in Python

Base classes in `copilot/core/tool_wrapper.py`:

- `CopilotTool(BaseTool)` — marker.
- `ToolWrapper(CopilotTool)` — the one real tools extend. Implements `_run` / `_arun`,
  wraps exceptions into `ToolOutputError`, and validates the input against
  `args_schema`.
- Return types: `ToolOutputMessage {message}`, `ToolOutputError {error}`,
  `ToolOutputContent {content}`; `parse_response()` flattens them to a string.

Reference implementations live in `tools/` (`HelloWorldTool.py`, `ExampleTool.py`,
`CustomHelloWorldMultiArgsTool.py`) plus `copilot/core/langgraph/tool_utils/TaskManagementTool.py`.

## 3. Loading (`copilot/core/tool_loader.py`)

`ToolLoader` is a singleton with a class-level cache:

1. `load_configured_tools()` — imports `tools/`, instantiates every concrete
   `ToolWrapper`/`CopilotTool` subclass, caches the list **process-wide**.
2. `_get_filtered_base_tools(enabled_tools)` — keeps only tools whose `name` appears in
   the `tools` array sent by Java. **An empty/absent array means zero base tools.**
3. `get_all_tools(...)` additionally appends:
   - the knowledge-base retrieval tool (`_add_kb_tool`),
   - tools generated from each `specs[].spec` of type `FLOW`
     (`toolgen/openapi_tool_gen.py`),
   - MCP tools from `agent_configuration.mcp_servers`
     (`MultiServerMCPClient`, 45 s timeout).
4. Sets `tool.agent_id` on every tool that has the attribute.

The important consequence: **the tool list is rebuilt per request** from data that
arrived in the request body. A tool does *not* have to exist as a file in `tools/` —
OpenAPI and MCP tools are proof that tools can be synthesised at request time from
request data. That is precisely the mechanism the UI Tools plan reuses.

## 4. How a tool reads request context

Anything a tool needs beyond its own arguments comes from `ThreadContext`:

```python
from copilot.core.threadcontext import ThreadContext
extra = ThreadContext.get_data("extra_info") or {}
token = extra.get("auth", {}).get("ETENDO_TOKEN")
```

## 5. What is missing for UI tools

Every existing tool is **server-executed**: the LLM calls it, Python runs it, a string
comes back. There is no notion of a tool whose execution happens **in the browser**.
Adding one requires:

- a way for the UI to declare, per request, which client-side capabilities exist;
- a synthetic Python tool per declared capability, whose "execution" is *emitting an
  action* rather than doing work;
- a transport for that action back to the browser (an SSE role, or `metadata`);
- a dispatcher in the chat UI.

See [plans/2026-09-03-ui-tools.md](plans/2026-09-03-ui-tools.md).
