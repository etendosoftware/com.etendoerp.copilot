# Providers, models and API keys configurable from Etendo (ETP-5136)

Goal: providers, models and their credentials are configured from Java/Etendo (Application Dictionary, window,
DAL) instead of `Openbravo.properties` / environment variables. Keys in properties become **deprecated**: still
read as a fallback during a deprecation window, with a warning, then removed. Same ticket as the Java engine
(see [2026-10-23-java-engine-parity.md](2026-10-23-java-engine-parity.md)); when Python is removed, this is the
only place credentials live.

## 1. Current state (verified in code)

| Piece | Where | Today |
|---|---|---|
| Model catalogue | table `ETCOP_OPENAI_MODEL` (`src-db/.../tables/ETCOP_OPENAI_MODEL.xml`) | columns `SEARCHKEY`, `NAME`, `PROVIDER` (free string), `MAX_TOKENS`, `ISDEFAULT`, `SYNCMODELS`, `ISDEFAULTOVERRIDE`, `ETENDO_MAINTAINED`, plus client/org. The name is historical: it holds every provider. |
| Model per agent | `CopilotApp.getModel()`; `CopilotModelUtils.getProvider/getAppModel/getDefaultModel` | Resolves provider + model; a preference can override the default (`provider/modelId`). |
| Java keys | `ChatModelFactory` (`openai.api.key`, `anthropic.api.key`, `gemini.api.key`, `ollama.base.url`, `copilot.proxy.url`, `copilot.openai.base.url`, `copilot.engine.java.timeout.seconds`); `OpenAIUtils.getOpenaiApiKey` (`OPENAI_API_KEY`, line ~528) | Read from `Openbravo.properties` through `readPropertyWithLegacyCompatibility`. Proxy mode falls back to the literal key `"dummy"`. |
| Python keys | `copilot/core/utils/models.py::get_api_key` (`<provider>.api.key`), `get_proxy_url`; `agent.py:69`; `core/agent/agent.py:42`; `multimodel_agent.py:267` | Read from the container environment. The key never travels in the request, so Python needs it configured separately from Java. |
| Validation | `CopilotUtils.validateOpenAIKey` (called from `RestServiceUtil` ~307-310) | OpenAI-specific; not meaningful for proxy or other providers. |
| Tool secrets | toolpack/devassistant Python tools (Tavily, Resend, XMLTranslation `OPENAI_MODEL`, ...) | Environment variables; exact list to be completed in phase 0. |
| Scope | Models carry client/org; keys are instance-wide | One key per provider per Etendo instance. |

Consequences: a key change needs a Tomcat/container restart; Etendo GO (multi-tenant) cannot give a tenant its own
key; keys sit in a file that is easy to leak (backups, support bundles, repo configs); Java and Python can
disagree on the key.

## 2. Final target design (user-approved)

### 2.1 Data model

- **`ETCOP_API_KEY` (new)** is the credential record and window `AI API Keys`. Each row has a
  name, provider type, encrypted API key, optional base URL, active flag, and standard client/org
  scope. The window is restricted to the System Administrator role. The key is masked on screen,
  excluded from audit, and never returned in clear by APIs, DAL/JSON exports, logs, or errors.
  Reuse Etendo's reversible encryption API, `org.openbravo.utils.FormatUtilities.encryptDecrypt`
  (`true` encrypts, `false` decrypts), and the `Password (decryptable)` AD reference
  (`16EC6DF4A59747749FDF256B7FBBB058`) to mask the value in the UI.
- Keep **`ETCOP_OPENAI_MODEL`**. Add a nullable API-key FK. No validation blocks saving an
  unlinked model: it resolves like today using deprecated properties/environment as fallback and
  emits the one-time property warning. Only new `etendo-*` aliases require a linked key at use
  time, with a clear error directing the administrator to AI API Keys.
- Provider keys are required at runtime for `openai`, `anthropic`, `gemini`, and `etendo`;
  `ollama` needs no API key. Other API-key providers (`tavily`, `resend`) are available to tool
  consumers but are not model providers.
- Model/key resolution uses most-specific applicable scope in order: organization, client,
  system. Active status is required. Existing property configuration is only a deprecated fallback.
- Add seeded Etendo-maintained aliases `etendo-fast`, `etendo-smart`, and `etendo-embeddings`,
  provider `etendo`, endpoint `https://llm.etendo.software`, and no model-name prefix. Mark the
  embedding alias as an embedding model. A system-level embedding-model preference wins when
  configured; otherwise resolve the active `etendo-embeddings` alias and its configured key.
- Tool credentials use `ETCOP_API_KEY` where the credential is an API key (e.g. `tavily`,
  `resend`), resolved by the same Java service. Do not add a parallel tool-secret table.

### 2.2 Resolution and consumers

One Java `ProviderCredentials` service resolves from a model row, including provider, model name,
API key, base URL, and timeout. All Java consumers use that service: `ChatModelFactory`,
`OpenAIUtils.getOpenaiApiKey` and its callers, embeddings/KnowledgeBase, transcription,
`SYNCMODELS`/`ModelHandler`/`SyncAssistant`, and provider-aware `CopilotUtils.validateOpenAIKey`.
Remove scattered credential/endpoint property reads. Ollama remains keyless. The Java service
provides a tool-key lookup for `ToolSecrets` in toolpack and related tool consumers.

While Python mode exists, Java sends the resolved credentials in request-only `extra_info` so the
container does not need separately configured credentials. This payload must never be logged or
persisted. Existing installs in Python mode continue to work through the Java-resolved data or
the deprecated property fallback.

The service has a short-lived cache keyed by scope/model and an entity event handler invalidates
affected entries on API-key/model edits. Secrets remain transient, `toString()` is masked, and
missing credentials produce safe errors without exposing values.

### 2.3 Legacy properties and import

`openai.api.key`, `anthropic.api.key`, `gemini.api.key`, `ollama.base.url`,
`copilot.proxy.url`, `copilot.openai.base.url`, and
`copilot.engine.java.timeout.seconds` are deprecated fallbacks. A read emits exactly one WARN per
property per JVM start and names its replacement in AI API Keys/model configuration. DB records
win. An idempotent module script on `update.database` and an explicit import process create API-key
records from properties present at upgrade time and link existing models without overwriting
configured records. Proxy/gateway configuration is represented by model key/base-URL records.
Python continues honoring these properties as fallback until its code is removed.

Deprecation schedule: Release N (this ticket) imports and warns while preserving fallback;
Release N+1 ignores these values for model resolution and logs an error when a deprecated property
is present; Release N+2 removes fallback reads and property documentation.

### 2.4 Security and access

- Only the dedicated administrator role can read/edit AI API Keys; no normal user/API response can
  retrieve decrypted keys. Exclude secret columns from audit and JSON/DAL exports.
- Canary-key coverage proves no key appears in logs, exception messages, SSE frames,
  `ETCOP_MESSAGE`, MCP responses, or tool results.
- Provider/key changes take effect without restart through entity-event cache invalidation.

## 3. Implementation order

1. Confirm Etendo reversible-encryption API and AD reference/column settings.
2. Create `ETCOP_API_KEY`, AI API Keys window/access, model FK, aliases, and sourcedata.
3. Implement `ProviderCredentials`, safe serialization, scope resolution, and cache invalidation.
4. Migrate every Java caller, model sync, tools, and Python request credential handoff.
5. Add idempotent import process and update.database module script; add per-property one-time warnings.
6. Update docs and tests, including provider resolution, import, key redaction, and embedding selection.

## 4. Tests and acceptance evidence

- Unit tests cover organization -> client -> system -> properties resolution; active/inactive and
  missing records; provider-specific validation; embedding preference/fallback; cache invalidation.
- Canary-key tests cover logs, errors, SSE, ETCOP_MESSAGE, MCP, and tool outputs.
- Import tests cover idempotency, partial properties, existing records, model links, and gateway mapping.
- Live environment evidence: create key/model records and resolve without an external LLM; run
  update.database/import against legacy properties; inspect warning log; fetch/render the AD window.
- Report per-class XML test results for each changed/added class. Real provider calls may be reported
  as unverified if credentials/network are unavailable.

## 5. Improvements enabled

- One tenant-aware credential path shared across Java, Python compatibility, validation, embeddings,
  model sync and applicable tools.
- Tenant-owned credentials with organization/client/system fallback and key rotation without restart.
- Etendo-managed model aliases whose concrete provider fallbacks are maintained by Etendo.

## 6. Remaining implementation decisions

- Release numbers are intentionally expressed as N, N+1, and N+2 until product release planning assigns
  version numbers.
