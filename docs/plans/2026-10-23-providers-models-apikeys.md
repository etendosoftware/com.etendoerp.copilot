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

## 2. Target design

### 2.1 Data model

- **`ETCOP_PROVIDER` (new)**: one row per provider configuration.
  `SEARCHKEY` (openai, anthropic, gemini, ollama, ...), `NAME`, `TYPE` (list: `openai`, `anthropic`, `gemini`,
  `ollama`, `openai_compatible`), `BASE_URL`, `API_KEY` (encrypted, masked in the UI), `TIMEOUT_SECONDS`,
  `GATEWAY_MODE` (when true, every model of the providers routed through it is sent to this OpenAI-compatible
  endpoint with the `provider/model` prefix: replaces `copilot.proxy.url`), `ISDEFAULT`, standard client/org/active
  columns. Resolution is org -> client -> system (client 0), which gives per-tenant keys for Etendo GO without a
  new mechanism.
- **`ETCOP_OPENAI_MODEL`**: keep the table (renaming AD tables is costly and breaks references); add an FK
  `ETCOP_PROVIDER_ID`. The `PROVIDER` string stays during the migration as the compatibility key and is derived
  from the FK afterwards.
- **Tool credentials**: one small table `ETCOP_TOOL_CREDENTIAL` (`TOOL`/`NAME`, encrypted `VALUE`, client/org)
  for tools that need a secret (Tavily, Resend, Google OAuth client, ...). Both tables are read through the same
  Java service, so there is one secret path, not two.

### 2.2 Java service

`com.etendoerp.copilot.config.ProviderCredentials` (one class, replaces all scattered property reads):

```java
ProviderConfig resolve(String providerKey);           // org -> client -> system, then deprecated properties
String toolSecret(String tool, String name);           // same order
record ProviderConfig(String type, String baseUrl, String apiKey, Duration timeout, boolean gatewayMode) {}
```

- Resolution never throws on a missing optional key; it returns a typed "missing" result so the engine reports
  `no API key for provider X configured under Copilot > Providers`, not a stack trace.
- Short-lived cache keyed by `(client, org, provider)`, invalidated by an entity event handler on save, so an edit
  applies on the next request with no restart.
- The decrypted key lives only inside `ProviderConfig` for the duration of the call: never logged, never put in
  prompts, SSE frames, `ETCOP_MESSAGE`, exceptions or the request JSON the user can see. `toString()` masks it.
- Encryption: the reversible encrypted-column mechanism already used by Etendo core for secrets (SMTP password
  handling in `Utility`/`EmailManager` uses `FormatUtilities.encryptDecrypt`). Phase 0 confirms the exact API and
  the AD reference/`ISENCRYPTED` handling and the UI masking.

### 2.3 Where it is consumed (every call site that changes)

| Call site | Change |
|---|---|
| `ChatModelFactory` | Build the model from `ProviderConfig` (type, base URL, key, timeout, gateway mode). Remove the property reads and the `"dummy"` special case; gateway mode keeps the placeholder key only when the provider row has none. |
| `OpenAIUtils.getOpenaiApiKey` and its callers (Assistants API, vector/embeddings, transcription, model sync) | Resolve through `ProviderCredentials`. |
| `CopilotUtils.validateOpenAIKey` / `RestServiceUtil` ~307-310 | Provider-aware validation (type-specific cheap call, or skip for gateway mode); results never include the key. |
| `ModelHandler`, `SyncAssistant`, `SYNCMODELS` | Model sync uses the provider row for the list-models call. |
| Python (while it still exists) | Java sends the resolved credentials to the container inside the request (`extra_info.llm`, not logged); `get_api_key`/`get_proxy_url`/`agent.py:69`/`core/agent/agent.py:42` read the request first, the environment second. Removed with Python. |
| Toolpack/devassistant tools | Java tools read secrets through `ToolExecutionContext` -> `ProviderCredentials.toolSecret`, never `System.getenv`. |
| Docs | `docs/java-engine.md` (properties block), module README, `docs/request-flow.md`, release notes. |

### 2.4 Security and access

- Only a dedicated role (System Administrator by default) can create/edit providers and credentials; the window is
  not readable by normal roles. The API never returns the key (field masked; "set / not set" indicator only).
- Exclude the secret columns from audit trail and from any DAL JSON export; redact them in `ad_audit`/logs.
- Tools run as the asking user, so a user cannot read a secret by asking a tool: secrets are resolved server-side
  inside the tool, and tool results are not allowed to echo them (covered by a test with a canary key).
- Per-tenant keys: a client's key is only visible to that client's resolution path; system-level (client 0) rows
  are the fallback.

## 3. Deprecation of the properties

1. **Release N (this ticket)**: DB first, properties as fallback. Every property read emits a single WARN per key
   per JVM start: `openai.api.key in Openbravo.properties is deprecated; configure it under Copilot > Providers`.
   A new process/button (and a module script on `update.database`) imports existing keys, base URLs and the proxy
   URL from the properties into `ETCOP_PROVIDER` at system level when the table is empty, so an upgrade keeps
   working with no manual step and the warning disappears once imported.
2. **Release N+1**: properties ignored for keys (still read for non-secret bootstrap like the Python host while
   Python exists); startup logs an ERROR if a deprecated key is still present.
3. **Release N+2**: remove the fallback code and the property documentation. Clean cutover: no alias left.

## 4. Implementation order

1. Phase 0 (research, no behaviour change): confirm the encryption/masking API in core, complete the tool-secret
   inventory (toolpack, devassistant), confirm how SyncModels uses the key.
2. AD: `ETCOP_PROVIDER`, FK on the model table, `ETCOP_TOOL_CREDENTIAL`, windows/tabs, role access, seed rows
   (system-level providers matching today's catalogue).
3. `ProviderCredentials` + event-handler cache invalidation + masking/redaction.
4. Switch `ChatModelFactory`, `OpenAIUtils`, validation and model sync; properties as logged fallback.
5. Import process + module script; deprecation warnings.
6. Python request carries credentials (compat) - dropped with Python.
7. Tool secrets through `ToolExecutionContext`.
8. Docs and release notes; schedule N+1 / N+2 removal.

## 5. Tests

- Resolution order org -> client -> system -> properties, including inactive rows and a missing provider.
- The key never appears in: logs captured during a failing call, exception messages, SSE frames, `ETCOP_MESSAGE`,
  tool results (canary-key test).
- Cache: edit a key and the next request uses it; another tenant never sees it.
- Import process: idempotent, does not overwrite existing rows, handles partial properties.
- Gateway mode vs native providers produce the same `ChatModel` configuration as today (same base URL, model
  prefix, placeholder behaviour) - behavioural parity test against the current `ChatModelFactory` outputs.

## 6. Improvements this enables

- One credential path for Java engine, validation, model sync, embeddings and tools (instead of one reader per
  call site); one "is this provider usable" check shown in the UI.
- Per-client keys for Etendo GO and usage attribution per provider row.
- No restart to rotate a key; keys leave the properties file (and support bundles).

## 7. Open questions

1. Per-tenant keys in this ticket, or system-level only first (schema supports both)?
2. Gateway (`llm.etendo.software`) as a provider row of type `openai_compatible` with `GATEWAY_MODE`, as proposed,
   or a separate "Gateway" entity?
3. Rename `ETCOP_OPENAI_MODEL`? Proposed: no (cost, references), only add the FK.
4. Which release numbers are N, N+1, N+2 for the deprecation window?
