# Architecture Overview

Copilot is a **three-tier** system living in a single Etendo module. The tiers do not
share a process, a language or a deployment unit.

```
┌──────────────────────────────────────────────────────────────────────────┐
│ TIER 1 — Chat UI (TypeScript / React / Vite)                             │
│ com.etendoerp.copilot.chat/  → built into web/com.etendoerp.copilot.dist │
│ Runs inside an <iframe> hosted by an isc.Window in Etendo Classic.       │
└───────────────┬──────────────────────────────────────────────────────────┘
                │ HTTP (same origin)  ../../copilot/*
                │ postMessage (COPILOT_CONTEXT, parent → iframe)
┌───────────────▼──────────────────────────────────────────────────────────┐
│ TIER 2 — Java (Etendo / Tomcat)                                          │
│ src/com/etendoerp/copilot/**                                             │
│ Auth + OBContext, assistant configuration assembly, tracking, hooks,     │
│ SSE relay.  Owns the ETCOP_* tables.                                     │
└───────────────┬──────────────────────────────────────────────────────────┘
                │ plain HTTP POST to http://{copilot.host}:{copilot.port}
┌───────────────▼──────────────────────────────────────────────────────────┐
│ TIER 3 — Python (FastAPI + LangChain / LangGraph)                        │
│ copilot/**, tools/**, run.py                                             │
│ Builds the agent, loads tools, calls the LLM, streams events back.       │
└──────────────────────────────────────────────────────────────────────────┘
```

## Tier 1 — Chat UI

Two separate pieces of front-end code:

- **`web/com.etendoerp.copilot/js/copilotToolbarButton.js`** — plain SmartClient JS,
  registered globally by `CopilotComponentProvider.getGlobalComponentResources()`
  (`src/com/etendoerp/copilot/CopilotComponentProvider.java:25`). It creates
  `window.copilotWindow` (an `isc.Window`) containing an `<iframe>` pointed at the
  React bundle, and pushes the current window/tab/selected-records context into the
  iframe with `postMessage({type:'COPILOT_CONTEXT', data})`.
- **`com.etendoerp.copilot.chat/`** — the React app (Vite + `etendo-ui-library`),
  built into `web/com.etendoerp.copilot.dist/`. It talks to Tier 2 over
  `../../copilot/*` (see `src/utils/references.ts`).

The iframe is **same-origin** with the Etendo Classic page, which matters a lot for the
UI Tools plan: the chat can reach `window.parent.OB.*` directly, not only via
`postMessage`.

## Tier 2 — Java

Entry points (`web.xml` of the Etendo webapp maps them; the servlet classes are):

- `CopilotSecureServlet` → session/basic auth, delegates to `RestService`
- `CopilotJwtServlet` → token auth variant

`RestService` is a hand-rolled router over `request.getPathInfo()`
(`RestService.java:74` for GET, `:183` for POST). `RestServiceUtil` does the real work:
assembles the assistant configuration from the `ETCOP_*` tables, POSTs it to Python, and
relays the response.

## Tier 3 — Python

`run.py` boots two servers: the main FastAPI app (`copilot.app`, default port 5005) and
a "simplified dynamic MCP" server. `copilot/core/routes.py` exposes
`/question`, `/aquestion`, `/graph`, `/agraph`, `/tools` and the vector-DB endpoints.

Agents:

| Agent | File | Used when |
|---|---|---|
| `MultimodelAgent` | `copilot/core/agent/multimodel_agent.py` | `app_type` = `multimodel` / `langchain` |
| `LanggraphAgent` | `copilot/core/agent/langgraph_agent.py` | assistant is a supervisor (graph) |

## Deployment shape

- Java runs in Etendo's Tomcat.
- Python runs in a container (`Dockerfile`, `compose/`), reachable at
  `copilot.host` / `copilot.port` from `Openbravo.properties`
  (`RestServiceUtil.java:541-543`).
- The React bundle is committed pre-built under `web/com.etendoerp.copilot.dist/`.

## Two conversation "memories"

State is duplicated on purpose and it is easy to get confused:

1. **Etendo side** — `ETCOP_CONVERSATION` + `ETCOP_MESSAGE`, written by `TrackingUtil`
   after each answer. This is what the conversations sidebar reads.
2. **Python side** — a LangGraph `AsyncSqliteSaver` checkpoint file per assistant
   (`get_checkpoint_file(assistant_id)`), keyed by `conversation_id` as `thread_id`.
   This is the actual LLM memory.

They can drift: deleting a conversation in Etendo does not delete the LangGraph thread.
