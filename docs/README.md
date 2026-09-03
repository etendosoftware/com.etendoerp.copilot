# com.etendoerp.copilot — Documentation

Reverse-engineered reference for the Copilot module. Written 2026-09-03 by reading the
source; every statement below points at a real file and line so it can be re-verified
when the code moves.

## Index

| Document | What it covers |
|---|---|
| [architecture-overview.md](architecture-overview.md) | The three tiers (Chat UI / Java / Python) and how they are wired |
| [request-flow.md](request-flow.md) | End-to-end trace of a question, with the exact JSON payload at each hop |
| [java-layer.md](java-layer.md) | Servlets, endpoints, hooks, request/response shaping |
| [python-layer.md](python-layer.md) | FastAPI routes, agents, streaming events, thread context |
| [tools-system.md](tools-system.md) | How a tool is declared in AD, loaded in Python and exposed to the LLM |
| [frontend-chat.md](frontend-chat.md) | The React chat, the SmartClient toolbar button and the iframe bridge |
| [data-model.md](data-model.md) | `ETCOP_*` tables and what each one drives |
| [issues-and-improvements.md](issues-and-improvements.md) | Bugs, smells and inefficiencies found while writing these docs |
| [MCP_SETUP.md](MCP_SETUP.md) | (pre-existing) MCP server setup |
| [java-engine.md](java-engine.md) | **Java engine (LangChain4j)** — running a chat agent inside Tomcat with `copilot.engine=java` |
| [plans/2026-09-03-ui-tools.md](plans/2026-09-03-ui-tools.md) | **Plan: UI Tools** (navigate between Etendo Classic windows, draw SVG in the chat) |
| [plans/2026-09-03-langchain4j-java-engine.md](plans/2026-09-03-langchain4j-java-engine.md) | **Plan: Java-native engine with LangChain4j** (run the chat path inside Tomcat, no Python container) |

## Conventions

- All versioned content is in English.
- Paths are relative to the module root (`modules/com.etendoerp.copilot/`).
- `Class.java:123` references are accurate as of commit `8402f0b4` (version 3.3.0).
