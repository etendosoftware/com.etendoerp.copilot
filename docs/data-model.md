# Data Model (`ETCOP_*`)

Definitions live in `src-db/database/model/tables/*.xml`; the DAL entities are generated
into `build/classes/com/etendoerp/copilot/data/`.

## Core

### `ETCOP_APP` — the assistant
`NAME`, `DESCRIPTION`, `PROMPT` (system prompt), `APPTYPE`
(`openai` | `langchain` | `multimodel` | `langgraph`), `PROVIDER`,
`ETCOP_OPENAI_MODEL_ID`, `TEMPERATURE`, `SEARCH_K`, `CODE_INTERPRETER`, `RETRIEVAL`,
`OPENAI_ID_ASSISTANT`, `OPENAI_ID_VECTORDB`, `MCP_CONFIG`, `STRUCTURED_OUTPUT`,
`SYNC_ASSISTANT`, `SYNC_STARTUP`, `REFRESH_GRAPH_IMG`, `CHECK_HOSTS`, `AGENT_SCOPE`,
`FEATURED`.

### `ETCOP_TOOL` — the tool catalogue
`VALUE` (Python class name, the join key), `NAME`, `DESCRIPTION`, `JSON_STRUCTURE`
(optional full function schema), `USE_MODEL`, `SYNC_STRUCTURE`.

### `ETCOP_APP_TOOL` — assistant ↔ tool
Plus `MODEL` (per-tool model override).

### `ETCOP_TOOL_WBHK` — tool ↔ webhook grants
Used by `WebhookPermissionUtils` / `ToolWebhookAccess`.

## Knowledge base / sources

- `ETCOP_FILE`, `ETCOP_FILE_VARIANT` — KB files and their processed variants.
- `ETCOP_APP_SOURCE` — file attached to an assistant, with `BEHAVIOUR`
  (`question` content is appended to the question text, others are vectorised),
  `ALIAS`, `EXCL_CODE_INT`, `EXCL_RETRIEVAL`.

## Conversations

- `ETCOP_CONVERSATION` — `TITLE`, `AD_USER_ID`, `VISIBLE` (archive flag),
  `EXTERNAL_ID` (the id shared with Python / LangGraph `thread_id`), `LAST_MSG`,
  `ETCOP_APP_ID`.
- `ETCOP_MESSAGE` — `ROLE`, `MESSAGE`, `TIME`, `LINENO`, `METADATA` (token usage etc.),
  `EXTERNAL_ID`.

> `ETCOP_MESSAGE.METADATA` already stores the `metadata` object returned by Python. If UI
> actions are carried in `metadata`, they will land in this column for free — decide
> deliberately whether that is wanted (audit trail) or not (noise).

## Teams and graphs

- `ETCOP_TEAM_MEMBER` — members of a supervisor assistant, with stage grouping.

## Access

- `ETCOP_ROLE_APP` — which roles see which assistants (drives `/assistants`).
- `ETCOP_API_TOKEN` — tokens for `CopilotJwtServlet`.

## Ops

- `ETCOP_SCHEDULE`, `ETCOP_SCHED_EXEC` — scheduled assistant executions
  (`background/BulkTaskExec`, `process/ExecTask`).
- `ETCOP_MEMORY` — long-term memories (`MemoryUtils`, questions prefixed `#MEMORY#`).
- `ETCOP_APP_INFO`, `ETCOP_MCP`, `ETCOP_APP_MCP`, `ETCOP_OPENAI_MODEL`.
