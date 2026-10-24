# MCP Server Configuration

Etendo Copilot exposes MCP endpoints from its Java servlet in the Etendo web application. The
servlet does not open a separate Python/Uvicorn listener and does not use `copilot.port.mcp`.
The MCP endpoint is available only when the Copilot module is deployed and Etendo is running.

## Endpoints

Generated configurations use the servlet mapping below:

- Standard/conversational: `/sws/copilot/mcp/{AGENT_ID}/mcp`
- Direct tools: `/sws/copilot/mcp/{AGENT_ID}/direct/mcp`

The former Python paths (`/{AGENT_ID}/mcp` and `/{AGENT_ID}/direct/mcp` on the separate MCP
port) are replaced. `GetMCPConfiguration` now emits the servlet URL while retaining the existing
`token_header`, `token_url`, and `npx mcp-remote` configuration shapes. Existing manually authored
URLs must be updated to the paths above. `context.url.copilot.mcp` remains an optional public
origin override; the servlet suffix `/sws/copilot` is added by the configuration process.

## Authentication and transport

MCP requests are authenticated with an Etendo Secure Web Services bearer token. Generated clients
use `Authorization: Bearer <token>`; compatibility extraction also accepts `etendo-token`,
`x-etendo-token`, and the `token` query parameter. The token's role must be assigned to the target
Copilot agent. Unauthorized requests return 401 and a protected-resource metadata challenge.

The servlet supports synchronous Streamable HTTP POST JSON-RPC for the `2025-03-26` protocol
revision. It responds with JSON, accepts notifications with HTTP 202, implements `initialize`,
`ping`, `tools/list`, and `tools/call`, and does not open SSE GET streams or allocate session IDs.
Requests are stateless; clients may initialize each new connection. `GET` is reserved for the
protected-resource metadata document, and `OPTIONS` provides CORS preflight handling.

## Modes

- **Standard** publishes `ask_agent_{normalized-agent-name}`. Calls are handled in-process by the
  Java engine and accept `question` plus an optional `conversation_id`.
- **Direct** publishes the configured Java server tools available to the agent and
  `get_agent_prompt`. Tools execute through `ToolExecutionContext` using the caller's SWS identity
  and role. Tools without Java implementations are not exposed.

The servlet intentionally does not expose Python-only utilities, OAuth login/PKCE endpoints,
resources, or dynamically generated tool stubs. Configure `token_header` or `token_url` for
external clients; legacy `oauth` configurations do not gain OAuth support from this servlet.

## Generate a client configuration

Use the existing Copilot `GetMCPConfiguration` process to generate endpoint URLs and authentication
headers. For clients requiring a subprocess bridge, the existing `mcp-remote` compatibility mode
continues to wrap the same servlet URL.
