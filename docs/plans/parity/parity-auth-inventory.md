# ETP-5136 — Identity / authentication / context reaching tools: inventory and in-process design

Scope: how identity (user / role / org / warehouse / client) and the SWS token reach tools **today** (Python runtime), and what replaces them when tools run in-process in Tomcat (Java engine).

Sources and checkouts (all read-only):

| Tag | Path | Note |
|---|---|---|
| `MOD` | `/Users/futit/Workspace/etendo_develop/modules/com.etendoerp.copilot` | branch `epic/ETP-3504`. Python runtime in `copilot/`, `tools/`. |
| `WT` | `/Users/futit/Workspace/etendo_develop/worktrees/copilot-ETP-5136` | branch `feature/ETP-5136-develop` (+5 commits). Java engine in `src/com/etendoerp/copilot/engine/`. Java line numbers below are `WT` unless tagged `MOD`. |
| `TP` | `.../modules/com.etendoerp.copilot.toolpack` | 26 Python tools + 5 Java webhooks. |
| `DA` | `.../modules/com.etendoerp.copilot.devassistant` | 3 Python tools use the token. |
| `SWS` | `.../modules_core/com.smf.securewebservices` | JWT mint/verify. |
| `WH` | `.../modules/com.etendoerp.webhookevents` | `/webhooks/` servlet + `BaseWebhookService`. |
| `CORE` | `.../src`, `.../src-core` | `OBContext`, `DalRequestFilter`, `DalThreadHandler`, `SessionInfo`. |
| `GO` | `.../modules/com.etendoerp.go` | Prior art for in-process tool execution under an explicit `OBContext` (`McpSessionManager`). |

`MOD` and `WT` carry identical copies of `CopilotUtils` (lines 710/717/801/826), `RestService` and `CopilotJwtServlet`. `RestServiceUtil` differs (WT adds the engine selector and client-tool plumbing); WT line numbers are quoted.

---

## 1. Java side: how identity is minted and shipped

### 1.1 Token and `extra_info` construction

| Step | Location | What happens |
|---|---|---|
| Entry (JWT) | `AD_MODEL_OBJECT` `CopilotJwtServlet`, mapping `/sws/copilot/*` (`MOD src-db/.../AD_MODEL_OBJECT.xml:15-26`, `AD_MODEL_OBJECT_MAPPING.xml:9`) | Bearer SWS JWT. `CopilotJwtServlet.checkJwt` (`CopilotJwtServlet.java:65-81`) decodes with `SecureWebServicesUtils.decodeToken`, reads claims `ad_user_id`\|`user`, `ad_role_id`\|`role`, `ad_org_id`\|`organization`, `m_warehouse_id`\|`warehouse`, `ad_client_id`\|`client` (`:68-72`, `getRequiredClaim :83-92`), then `SecureWebServicesUtils.createContext` + `OBContext.setOBContext` + `setOBContextInSession` (`:74-76`). Any missing claim → 401. No `isWebServiceEnabled` check here (the check lives in `BaseSecureWebServiceServlet`, which this servlet does NOT extend; it extends `HttpBaseServlet`, `:29`). |
| Entry (session) | `CopilotSecureServlet` (`/copilot/*`, `AD_MODEL_OBJECT_MAPPING.xml:19`) | Classic session auth. `DalRequestFilter` (`CORE DalRequestFilter.java:77-119`, `web.xml:92`) sets `OBContext` from the HTTP session. |
| Both entries converge | `RestService.doGet` / `doPost` (`RestService.java:62-73`, `165-183`) | **Both wrap the whole request in `OBContext.setAdminMode()`** (`:65`, `:168`; `:72`, `:180` restore). Added by `9bd4f383` / `62b967c7` (2024-03-06) and `57462240` ("Issue #12: Prevent Access errors on Entities with SetAdminMode", ETP-256). This is the existing workaround: ETCOP_* entities are not granted to ordinary roles, so the endpoint runs privileged. |
| Payload assembly | `RestServiceUtil.buildRequestJson` (`RestServiceUtil.java:512-539`) | Puts `ad_user_id` and `ad_client_id` (`:528-529`, from `OBContext.getUser()` / `getCurrentClient()`), then calls `addExtraContextWithHooks` (`:536`). |
| `extra_info` | `RestServiceUtil.addExtraContextWithHooks` (`:915-932`) | `extra_info = { auth: CopilotUtils.getAuthJson(role, ctx), model_config: CopilotUtils.getModelsConfigJSON() }` (`:920-922`), then `CopilotQuestionHookManager.executeHooks` (`:927`), which can mutate anything (`ToolModelsConfig.java:101` adds `extra_info.tool_config`). Failure of the token step is **swallowed** (`catch (Exception e) { log.error(...) }`, `:923-925`): the request goes out with no `auth` and the tools fail later with "No access token provided". Role is re-fetched by id `OBDal.getInstance().get(Role.class, context.getRole().getId())` (`:918`). Same call also in `getGraphImg` (`:1029`). |
| Gate | `CopilotUtils.getAuthJson` (`CopilotUtils.java:801-808`) | `authJson = {}`; adds `ETENDO_TOKEN` **only if `role.isWebServiceEnabled()`** (`:804-805`). A non-WS role gets `auth: {}` (no failure). |
| Mint | `CopilotUtils.getEtendoSWSToken` (`:826-837`, package-private) | Re-fetches `User`, `Organization` (`context.getCurrentOrganization()`), `Warehouse` (`context.getWarehouse()`, nullable) by id "to avoid LazyInitializationException" (`:830` comment) and calls `SecureWebServicesUtils.generateToken(user, role, org, warehouse)` (`SWS SecureWebServicesUtils.java:478-518`). |
| Other mint callers | `CopilotUtils.generateEtendoToken()` (`:716-718`, role=null → role from context) | Used by `CheckHostsButton.java:60` and `GetMCPConfiguration.java:132` (MOD) to call `/sws/copilot/configcheck` and to render MCP client configs. Not part of tool execution. |

Python never receives host or user/role/org/warehouse/client separately. `docs/request-flow.md:82` documents `auth: {ETENDO_TOKEN, ETENDO_HOST}`, but `getAuthJson` only ever puts `ETENDO_TOKEN` [verified by `CopilotUtils.java:801-808`]; the host comes from the Python env `etendo.host.docker` (`etendo_utils.py:169-176`). The doc is inaccurate here.

### 1.2 What identity the token really carries

`generateToken` (`SWS SecureWebServicesUtils.java:478-518`) does NOT blindly serialize the `OBContext`; it re-resolves each element and falls back to defaults:

| Element | Source | Fallback |
|---|---|---|
| user | `context.getUser()` | n/a |
| role | `context.getRole()` (re-fetched) | `getRole` (`:706-727`): must be in the user's `UserRoles` else `Exception(SMFSWS_UserHasNoRole)`. With role null (`generateEtendoToken`): `smfswsDefaultWsRole`, then `defaultRole`, then first user role. |
| client claim | **`selectedRole.getClient()`** (`getJwtBuilder :764-772`), NOT `OBContext.getCurrentClient()` | none |
| org | `context.getCurrentOrganization()` | `getOrganization(org, role, defaultRole, defaultOrg)` (default org if not valid for the role) |
| warehouse | `context.getWarehouse()` | `getWarehouse(...)` (default/first warehouse of the org). `.withClaim("warehouse", selectedWarehouse.getId())` (`:771`) NPEs if nothing resolves [INFERENCE: `getWarehouse` body not read]. |
| claims | `user, client, role, organization, warehouse, iat` (`:764-772`); issuer/audience `sws` | `exp` only if `SWSConfig.expirationTime > 0` (`:508-513`, minutes) |

Consequence: today's tool identity = "the request's user and role, org/warehouse possibly defaulted, client = the role's client". That is the parity target for the in-process context (see §4 `capture`).

### 1.3 Expiry / refresh

* Token is minted **once per question** in `buildRequestJson`; there is **no refresh, no re-mint, no 401 retry** anywhere (grep in `copilot/` for refresh/expir/401 on tokens returns only `routes.py:635` and `oauth_provider.py`; `AccessToken(expires_at=None)` at `auth_provider.py:83`). TTL is `SWSConfig.getExpirationTime()` minutes (`SWSConfig.java:30-38`); `<=0` = never expires.
* A run longer than the TTL (supervisor teams, `TaskCreatorTool` bulk, long `ask_agent`) gets 401 mid-run [INFERENCE: no test; follows from the code above].
* `ThreadContext._conversation_contexts` (`threadcontext.py:8,47-50`) keeps `extra_info` (including the token) in a module-level dict keyed by conversation and never evicts it (docs/issues-and-improvements.md B6). The saved token is overwritten by `load_thread_context` on the next question (`routes.py:137-138`).
* In-process there is no token to expire for DAL access; only §4 `mintToken()` has a TTL.

### 1.4 History / workarounds around auth (module git log)

| Commit | Date | Effect |
|---|---|---|
| `31045e4f` ETP/EML-479 | 2024-03-04 | Introduced `extra_info` + Python `ThreadContext` + question hooks. |
| `9bd4f383`, `62b967c7` | 2024-03-06 | `setAdminMode` in question and in `/assistants`. |
| `63ac7aef` KNQA-67 | 2024-04-16 | Scheduled apps: `ProcessScheduleApps` (a background `DalBaseProcess`) calls `RestServiceUtil.handleQuestion(...)` (MOD `ProcessScheduleApps.java:120` today); the `OBContext` is the scheduler's, there is no `HttpServletRequest`. |
| `3c619165` EML-500 | 2024-05-18 | Python "read the Etendo token more safely" (`get_etendo_token` raising a descriptive exception). |
| `57462240` Issue #12 / ETP-256 | 2024-08-28 | **Admin mode around the whole `RestService.doGet/doPost`** (see §1.1). |
| `02576992` ETP-230 | 2024-09-02 | `generateEtendoToken()` util (host checks). |
| `1773ee4d` ETP-784 | 2024-11-26 | "Prevent model config setting when enabled sws". Before it, `auth`, `model_config` and `extra_info` were all written **only inside `if (role.isWebServiceEnabled())`**; after it `model_config` is always sent and `auth` is `{}` for non-WS roles. Extracted `getAuthJson` / `getEtendoSWSToken` (the "Refresh to avoid LazyInitializationException" re-fetch-by-id comes from here). |
| `07a2994c` ETP-921 | 2025-01-09 | Doc: KB file explaining the literal `ETENDO_TOKEN` convention (`OpenAPISpecFlowFile.java:112-131` appends "use the literal string 'ETENDO_TOKEN' as Bearer token" to every generated OpenAPI spec). |
| `0becd5ec` ETP-3630 | recent | `AgentAccessUtils.ensureSharedAgentsGrantedSafely(role)` on assistants load (MOD `RestServiceUtil`), grants shared agents to client-admin roles. Not in WT's copy. |
| ETP-3439 series (`1d1d740a`, `07d17f53`, …) | recent | MCP `auth_type` (oauth / token_header / token_url) for MCP client configs and `oauth_login.py`. External-client surface, not tool identity. |

`git log -S ETENDO_TOKEN` also lists ETP-2919/1953/1952/1625/1360 (tool loading, CodeAct): all Python-side consumers listed in §2.

The literal string `ETENDO_TOKEN` is a **cross-layer contract**: it is embedded in agent prompts (`DA src-db/.../ETCOP_APP.xml:10,1526,1694`), OpenAPI request descriptions (`DA ETAPI_OPENAPI_REQ.xml:348`: `'token': 'ETENDO_TOKEN'`), generated OpenAPI specs (`OpenAPISpecFlowFile.java:125`), `openapi_tool_gen.py:358`, `api_tool_util.py:30`. The LLM passes it as the `token` argument; Python substitutes the real JWT. The Java replacement must keep accepting it.

---

## 2. Python side: every consumer of identity (file:line)

`ThreadContext` = `ContextVar` dict (`threadcontext.py:5,21-64`). Only producer: `load_thread_context` (`routes.py:135-142`) sets `extra_info`, `assistant_id`, `conversation_id`, `ad_user_id`, `ad_client_id` (default `"0"`); called from the question routes (`routes.py:131,177,230`). The only other writers are `simplified_dynamic_server.py:535` (MCP surface), `LoadOAuthTokenTool.py:59` (`oauth_tokens`) and `threadcontextutils.py:12-61` (`usage_data`). `ThreadContext` is not propagated across threads by anyone: no `copy_context`, `to_thread`, `run_in_executor` in `copilot/` (grep). Tools that fan out read the token first (see P16).

Legend, "Java in-process equivalent": **DAL** = direct `OBDal`/`OBCriteria` under the user's `OBContext`; **WH** = invoke the `BaseWebhookService` bean directly (skip the servlet, re-apply the access check); **NEO** = Etendo GO NEO/DAL service layer [INFERENCE: see O3]; **SWS-loop** = loopback HTTP with a freshly minted JWT; **none** = nothing to replace.

### 2.1 Shared infrastructure (`MOD copilot/core/utils/etendo_utils.py`)

| ID | file:line | Token / context use | Etendo endpoint | Java in-process equivalent |
|---|---|---|---|---|
| P1 | `etendo_utils.py:63-85` `get_etendo_token` | Reads `ThreadContext["extra_info"]["auth"]["ETENDO_TOKEN"]`; raises `Exception("No access token provided…")` if absent (non-WS role, mint failure, or wrong thread/context). | – | `ToolExecutionContext` (§4). The "no token" failure mode disappears; a WS-enabled gate is re-introduced only where wanted (O2). |
| P2 | `:88-101` `get_extra_info` | `ThreadContext.get_data("extra_info")`; `{}` on error. Used by `agent.py:279` (model_config), `SimpleOcrTool.py:103`, `OcrTool.py:508` (tool_config). | – | `ToolExecutionContext.extraInfo()` read-only view of the already-built JSON, or typed getters. |
| P3 | `:15-44`, `:47-60`, `:104-118` `normalize_etendo_token` / `validate_etendo_token` / `build_headers` | Prefixes `Bearer `. | – | none (no HTTP). |
| P4 | `:121-162` `call_etendo` | `requests.{get,post,put,delete}` to `{host}{endpoint}` with Bearer; returns JSON or `{"error": text}`. Callers: `agent_tools.py:105`, `LoadOAuthTokenTool.py:43`, `evaluation/utils.py:80`. | any (`/sws/copilot/structure`, `/webhooks/ReadOAuthToken`) | per caller, see P17, P28. |
| P5 | `:209-236` `call_webhook` | `POST {host}/webhooks/?name=<webhook>` Bearer JSON body. Callers: `EtendoSQLToCSVTool.py:106`, `AttachFileTool.py:78`, `ProcessDefinitionJasperTool.py:108`. | `/webhooks/` → `WebhookServiceHandler` (`WH …/services/WebhookServiceHandler.java:231-278`) | **WH**: see §3.2. |
| P6 | `:239-299` `request_to_etendo`, `simple_request_to_etendo` | `Bearer {get_etendo_token()}` + `get_etendo_host()`. Callers: `TaskCreatorTool.py:104,364,382,399,411`. | `/sws/com.etendoerp.etendorx.datasource/*` | **DAL** / **NEO**, see P16. |
| P7 | `:179-206` `login_etendo` | `POST {server}/sws/login` with **username/password** (not extra_info). Callers: `OrgInitTool.py:113`, `ClientInitTool.py:132`, `evaluation/utils.py:78`. | `/sws/login` (`SWS SecureLoginServlet`) | Remote-host login for a *different* Etendo instance: stays HTTP, cannot be in-process (see O6). |
| P8 | `:169-176` `get_etendo_host` | env `etendo.host.docker` (default `http://host.docker.internal:8080/etendo`). Used by P4–P7, `openapi_tool_gen.py:474`, `ApiTool.py:290`, `api_tool_util.py:36`, `routes.py:631`, MCP auth/login modules. | – | Java: `CopilotUtils.getEtendoHost()` (MOD, used by `CheckHostsButton`). Needed only for **SWS-loop** and external-URL matching. |
| P9 | `routes.py:135-142` (producer), `:177-183` (debug log of the full `extra_info` incl. token), `:631-645` `/checkCopilotHost` | `checkCopilotHost` forwards the caller's `Authorization` header to `{host}/sws/copilot/configcheck` (not extra_info). | `/sws/copilot/configcheck` | none (host check; Java side is `CheckHostsButton`). Note the token is written to Python debug logs (`:177-183`). |

### 2.2 Generic tool generators (token substitution)

| ID | file:line | Token / context use | Endpoint | Java in-process equivalent |
|---|---|---|---|---|
| P10 | `MOD copilot/core/toolgen/api_tool_util.py:28-40` `token_not_none` | (a) literal `token == "ETENDO_TOKEN"` → real token (`:30-33`); (b) no token **and** `url` starts with Etendo host **and** `endpoint` starts with `/sws/` → real token implicitly (`:35-40`). | any Etendo `/sws/*` | Same two rules, evaluated against `ToolExecutionContext`; destination resolved in-process or via **SWS-loop** (O1). |
| P11 | `:43-141` `do_request` (call `:80`) | HTTP method on arbitrary `url + endpoint`, headers from P10. Used by P12 and P14. | arbitrary (Etendo or external) | Etendo URLs: **DAL/NEO/WH** router; external URLs: plain HTTP client, no identity. |
| P12 | `openapi_tool_gen.py:357-358`, `:474-475`, `:398-406`, `:502` | For OpenAPI-spec tools: `is_etendo_classic = normalize(url) == normalize(get_etendo_host())` → generated body does `auth_token = "ETENDO_TOKEN"` and the tool has **no `token` parameter**; external APIs get an optional `token` parameter. | `ETENDO_HEADLESS_PATH_PREFIX = /sws/com.etendoerp.etendorx.datasource` (`:15`), `/sws/copilot/*`, any OpenAPI flow | Spec-tool generator in Java: `url` host equals Etendo host → in-process dispatch with the context; else HTTP with optional token param. |
| P13 | `copilot/core/toolgen/ApiTool.py:217` (`_run`), `:252` (`_arun`), `:290` | Always `Authorization: Bearer get_etendo_token()`; `base_url = get_etendo_host()` (`generate_tools_from_openapi`). Path names are rewritten `_sws_com_etendoerp_etendorx_datasource` → `_etendo` (`:301`). | `/sws/com.etendoerp.etendorx.datasource/*` | **NEO/DAL** (O3). |
| P14 | `TP tools/APICallTool.py:5,58,180` | Imports `token_not_none` (P10): the LLM supplies `url`, `endpoint`, `method`, `token`. Prompts tell it to pass `'ETENDO_TOKEN'` (§1.4). | arbitrary | As P11. |
| P15 | `copilot/core/agent/eval/code_evaluators.py:64-70` `CodeExecutor.execute`; `:124-125` Riza | Injects `variables["ETENDO_TOKEN"] = get_etendo_token()` into the namespace of **LLM-written code**. `OriginalExecutor` `exec`s it in the Python process; `RizaExecutor` sends `processed_locals` (incl. the token) to the Riza cloud API via `exec_func(input=...)`. | whatever the code calls | The only consumer that genuinely needs a *real, portable credential*: `mintToken()` (§4), short TTL, role-scoped (O4). Do not hand it an `OBContext`. |

### 2.3 Toolpack tools (`TP tools/`)

| ID | file:line | Token / context use | Endpoint | Java in-process equivalent |
|---|---|---|---|---|
| P16 | `TaskCreatorTool.py:104` (POST Task), `:364` (GET TaskType), `:382` (POST TaskType), `:399` (GET TaskStatus), `:411` (POST TaskStatus), `:481-485` (reads `ThreadContext["conversation_id"]`, `["assistant_id"]`), `:502-503` (`host`, `token`), `:517` (`ThreadPoolExecutor(max_workers=10)`) | Reads the token **before** spawning the pool (worker threads would not see the `ContextVar`). | `/sws/com.etendoerp.etendorx.datasource/{Task,TaskType,TaskStatus}` | **DAL** (`OBProvider.get(Task.class)`; entity names from the RX projection, O3). **Gotcha:** fan-out of 10 threads each needs its own context/session (§3.4). |
| P17 | `LoadOAuthTokenTool.py:43-47` (`POST /webhooks/ReadOAuthToken`), `:55-60` (writes `ThreadContext["oauth_tokens"]` + `save_conversation()`) | Token only to authorize the webhook. | `/webhooks/ReadOAuthToken` → `TP ReadOAuthToken.java:33-47` (queries `ETRXTokenInfo` where `validUntil >= now` and provider like `%drive%`, `setMaxResults(1)`, **no user filter**) | **WH**/**DAL**: call `ReadOAuthToken.get` (or the query) in-process; the oauth-token store (`ThreadContext["oauth_tokens"]`) becomes `ToolExecutionContext.attributes()` scoped by conversation. |
| P18 | `EtendoSQLToCSVTool.py:99-106` | `call_webhook(token, {"Query": sql, "Mode": "EXECUTE_QUERY"}, host, "DBQueryExec")`. | `/webhooks/DBQueryExec` → `TP ExecSQL.java:69-102,471-492` | **WH**: `ExecSQL.get(params, responseVars)`. Entity access is `OBContext.getOBContext().getEntityAccessChecker().getReadableEntities()` (`:487-491`) and security filters `getReadableClients/Organizations`; both depend on the **role** of the thread's `OBContext` (an admin-context thread would see every table). |
| P19 | `AttachFileTool.py:61-62`, `:78` | `call_webhook(.., "AttachFile")`. Reads a **local file path** from the Python container (`/app` + path), base64-encodes it. | `/webhooks/AttachFile` → `TP AttachFileWebhook.java:23-60` | **WH**/**DAL**: `AttachFileWebhook.createAttachment` directly from the file in Tomcat's filesystem; the base64 round-trip disappears. |
| P20 | `DockerTool.py:81` (`conversation_id` → container name), `:118-136` (`add_extra_info` flattens **all** string values of `extra_info`, including `ETENDO_TOKEN` and model config, into the container's environment variables) | Exposes the token to code the LLM runs in `python:3.10-slim`. | arbitrary from inside the container | `mintToken()` into container env (same as P15), or drop auth from the container. Python-runtime-only tool (needs the Docker socket): outside the in-process scope [INFERENCE]. |
| P21 | `MemoryTool.py:304-305`, `GoogleServiceUtil.py:19` | `assistant_id`, `ad_user_id` partition memory (`MemoryTool`); `oauth_tokens` alias lookup. | none (local Chroma / Google APIs) | `ToolExecutionContext.userId()/agentId()/attributes()`. |
| P22 | `OcrTool.py:508-510`, `MOD tools/SimpleOcrTool.py:103`, `MOD copilot/core/utils/agent.py:279` | `extra_info.tool_config[agent_id][tool_id]`, `extra_info.model_config[provider][model]`: config, not credentials. | none | Already in the request JSON the Java engine reads (`java-engine.md:71`); reuse the same payload (`ToolModelsConfig.java:101`). |
| P23 | `MOD copilot/core/kb_utils.py:42` | `ad_client_id` from `ThreadContext` (default `"0"`) → Chroma filter `{"ad_client_id": {"$in": ["0", ad_client_id]}}`. | none | `ToolExecutionContext.clientId()`; note this is the request's `OBContext.getCurrentClient()` (`RestServiceUtil.java:529`), **not** the token's client claim (role's client, §1.2). They differ when the context client != role client (e.g. `0`). |

### 2.4 Dev-assistant tools (`DA tools/`)

| ID | file:line | Token / context use | Endpoint | Java in-process equivalent |
|---|---|---|---|---|
| P24 | `OrgInitTool.py:109-113` (token = `get_etendo_token()` unless `client_admin_user` given → `login_etendo`), `:61` | Bearer token against a classic HTML servlet. | `{server}/ad_forms/InitialOrgSetup.html?stateless=true` | Direct call of the initial-setup utility with a client-admin role context [INFERENCE: classic `ad_forms` code, not inspected]. `remote_host` / explicit credentials variant remains HTTP (P7). |
| P25 | `ClientInitTool.py:128-132`, `:34` | Same, system-admin credentials. | `{server}/ad_forms/InitialClientSetup.html?stateless=true` | Same; needs a System-admin role, i.e. NOT the asking user's role in most cases. |
| P26 | `ProcessDefinitionJasperTool.py:108-110` | `call_webhook(.., "ProcessDefinitionJasper")`. | `/webhooks/ProcessDefinitionJasper` → `DA …/webhooks/ProcessDefinitionJasper.java` | **WH**. |

### 2.5 MCP-server surface (external clients → Python, not the agent loop)

Here the token comes **from the external caller**, not from Java. It is a separate entry point; under the Java engine it stays Python or is superseded by the GO MCP (O5).

| ID | file:line | Use |
|---|---|---|
| P27 | `MOD copilot/core/mcp/simplified_dynamic_server.py:522-535` | `extract_etendo_token_from_request` (`auth_utils.py:21-55`: headers `etendo-token`, `Authorization`, `X-Etendo-Token`, query `token`) → `ThreadContext.set_data("extra_info", {"auth": {"ETENDO_TOKEN": …}})`. `:582,591` pass it to `fetch_agent_structure_from_etendo` / `setup_tools`; `:655-658` forwards it to the per-agent MCP instance (separate port; the `ContextVar` does not cross that HTTP hop, so the instance re-extracts it from the request via `basic_tools.py:36-38` / `extract_etendo_token_from_mcp_context`). |
| P28 | `mcp/tools/agent_tools.py:100-110` (`GET /sws/copilot/structure?app_id=` with the client's token), `:735-766` (`ask_agent_*`: `POST /sws/copilot/question`) | Imports `get_etendo_token` from `etendo_utils` (`:24`) (ThreadContext) while `basic_tools.py:36` has a different same-named function (MCP context). [INFERENCE: possible latent mismatch in instance threads, not exercised.] |
| P29 | `mcp/auth_provider.py:99-110` | `GET /sws/copilot/assistants` to verify the token authorizes the agent; caches by token value, `expires_at=None` (`:83`). |
| P30 | `mcp/oauth_login.py:171-173,240-244` | `POST /sws/login` (OAuth 2.1 login page) to turn credentials into an SWS JWT. |
| P31 | `MOD evaluation/utils.py:78-80`, `execute.py:207` | Offline evaluation harness: `login_etendo` + `call_etendo`. Not runtime. |

Every in-repo consumer of `ETENDO_TOKEN`/`get_etendo_token` is covered above (final grep over `modules/` and `modules_core/` for `get_etendo_token|call_etendo|call_webhook|request_to_etendo|simple_request_to_etendo|login_etendo|get_extra_info|ThreadContext.get_data(...)`, excluding tests/build).

---

## 3. Java in-process equivalents and gotchas

### 3.1 The shape of the replacement per endpoint class

| Python path | What the endpoint does server-side | In-process Java |
|---|---|---|
| `/sws/com.etendoerp.etendorx.datasource/*` (P6, P12, P13, P16) | SWS web service: `BaseSecureWebServiceServlet.service` (`SWS …service/BaseSecureWebServiceServlet.java:104-161`) decodes the JWT, `createContext`, `OBContext.setOBContext`, `SessionInfo.setUserId/ProcessType("WS")/ProcessId("DAL")`, `fillSessionVariables` under admin mode, rejects portal roles and `!isWebServiceEnabled()` (`:196-203`), then `doService`. The datasource implementation lives in the RX module; its source is **not in this checkout** [INFERENCE: `grep etendorx.datasource` finds only docs/tests/XML]. | **DAL** under `OBContext` of the asking user with entity-access checks ON (non-admin), or the GO NEO/DAL layer (O3). Replicate the portal-role and WS-enabled checks only if we want identical refusals (O2). |
| `/webhooks/<name>` (P5, P17–P19, P26) | `WebhookServiceHandler.handleRequest` (`WH …WebhookServiceHandler.java:231-278`): **admin mode on** (`:234`), looks up `DefinedWebHook` by name (`:143-155`), `isAllowed` (`:320-342`): API-key token, or JWT → `DefinedwebhookToken` role-access, or group access `DefinedwebhookRole(webhook, role)` (`:381-440`); the token path sets `OBContext` from the **token's userRole** (client/org of the UserRole, no warehouse; `:126-128`), the group path from the JWT claims (`createContext`, `:395-397`); builds params from `DefinedWebhookParam` (required-check, `:288-309`) and calls `BaseWebhookService.get(Map, Map)` through Weld (`:166-172`, `:264`). | **WH**: resolve `DefinedWebHook`, check `DefinedwebhookRole` for the context role (the same record `WebhookPermissionUtils.assignMissingPermissions` creates on assistant load, `RestServiceUtil.java:987-995`, `WebhookPermissionUtils.java:74-78`), validate required params, `WeldUtils.getInstanceFromStaticBeanManager(handlerClass).get(params, out)`. Webhook services currently execute **in admin mode**; in-process the choice is explicit (O2). |
| `/sws/copilot/{structure,question,assistants}` (P28, P29) | Java `RestService` | Java→Java: call `RestServiceUtil.generateAssistantStructure` / `handleQuestion` directly; no HTTP. |
| classic `ad_forms` servlets (P24, P25) | stateless Bearer on classic servlet | call the setup utilities directly [INFERENCE]. |
| external URLs (P11, P14) | not Etendo | plain HTTP client, no identity attached. |

### 3.2 `OBContext` and the thread model

Facts from `CORE`:

* `OBContext` current instance is a `ThreadLocal` (`OBContext.java:122`); `setOBContext(ctx)` sets it and **clears `adminModeSet`** (`:555-566`). The admin-mode stack is a separate `ThreadLocal<Stack<OBAdminMode>>` (`:124`, `:263-269`); `isInAdministratorMode()` is true when the top of that stack is admin (`:1195-1201`) and `doOrgClientAccessCheck()` is false for `setAdminMode()` (`:213-242`, `:1208-1214`).
* There is **no public API to push a "non-admin" entry**, and `setOBContext(newCtx)` does NOT clear the stack. So replacing the context on the request thread does not make it non-admin. The request thread is admin for the whole question (`RestService.java:168`).
* `SessionHandler` (Hibernate session/transaction) and `SessionInfo` (`userId`, `processType`, `processId`, `sessionId`, `command`, `queryProfile`, …; `SessionInfo.java:53-75`) are also thread-locals. `DalThreadHandler` / `DalThreadCleaner` (`CORE DalThreadHandler.java`, `DalThreadCleaner.java:41-92`) are the platform pattern to bind and clean them: `ThreadHandler.run()` = `doBefore` → `doAction` → `doFinal(err)`; `cleanWithCommit` / `cleanWithRollback` closes the session, deletes the `SessionHandler` and sets `OBContext` to null. `DalRequestFilter.doFinal` additionally calls `OBContext.clearAdminModeStack()` and `SessionInfo.init()` (`:101-114`).
* Entities hang off the Hibernate session of their thread. `OBContext.getUser()/getRole()/getCurrentOrganization()/getWarehouse()` return entity instances; used from another session or after `commitAndClose` they are detached. `getEtendoSWSToken` already guards against this by re-fetching by id (`CopilotUtils.java:830-835`).

Current Java engine (`WT engine/`):

* `LangChain4jEngine.execute` (`:53-104`) runs **synchronously on the servlet request thread**: `model.chat(...)` is a blocking call (`:67`), the answer is written once with `stream()` (`:119-127`). No executor, no `OBContext` handling, no thread hop. It never touches identity (it reads everything from the already-built payload).
* `ClientToolSupport` has no `OBContext`/thread code (grep: none). Tools are **client-executed** (WebMCP): they run in the browser under the user's own session (`docs/java-engine.md:253-258`), and the turn ends when the model asks for one (`LangChain4jEngine.java:73-83`); identity is the browser's session, so nothing in this path needs a token.
* `CopilotEngineSelector.unsupportedReason` (`:142-159`) currently routes to Python whenever the agent has `tools`, `specs`, `mcp_servers` or a knowledge base: **no server-side tool runs in Java today**, hence no context handling exists yet to build on.
* The engine runs *inside* `RestService.doPost`'s admin mode and inside `DalRequestFilter`'s session/transaction.

LangChain4j thread hops, verified in the 1.19.0 sources jar (`~/.gradle/caches/modules-2/files-2.1/dev.langchain4j/langchain4j/1.19.0/*/langchain4j-1.19.0-sources.jar`): `ToolService.executeToolsConcurrently()` / `executeToolsConcurrently(Executor)` (`ToolService.java:348-357`, since 1.4.0) are opt-in; when set, tools run on that executor **only if the model returned more than one tool request** (`ToolService.java:873`); otherwise (and by default) a tool runs on the calling thread. For streaming (`AiServiceStreamingResponseHandler.java:232,310`) the calling thread is the HTTP client's callback thread. With the blocking `ChatModel.chat` the engine uses today, the calling thread is the servlet request thread, i.e. the thread that is in admin mode (G1). `DefaultExecutorProvider` (`langchain4j-core .../internal/DefaultExecutorProvider.java`) is a cached pool with no OBContext handling. LangChain4j also offers `dev.langchain4j.invocation.InvocationParameters` (core, since 1.6.0, `InvocationParameters.java`): a thread-safe map handed to `@Tool` methods (and tool-error handlers) via an extra parameter and invisible to the LLM; this is the natural carrier for `ToolExecutionContext`. Never rely on the ambient `OBContext` inside a tool.

### 3.3 Gotchas, consolidated

| # | Gotcha | Evidence | Mitigation |
|---|---|---|---|
| G1 | **Privilege escalation by accident.** Python calls ran with the user's role through SWS. In-process on the request thread they would run in admin mode (no entity access check, no readable org/client filtering). | `RestService.java:65,168`; `OBContext.java:213-242,1195-1214` | Tools must not run on the request thread. Run each tool on a worker thread whose admin stack is empty and whose `OBContext` is the user's (§4). Narrow, explicit `setAdminMode()/restorePreviousMode()` only for config lookups (GO does the same, `McpToolRouter.java:174-182`). |
| G2 | **Role used by access checkers.** `ExecSQL` readable tables, `SimSearch` readable entities/clients/orgs all come from `OBContext.getOBContext()`. An `OBContext` created with the wrong role (e.g. `adminContext` because `OBContext` was null) widens access. | `ExecSQL.java:487-491`; `SimSearch.java:145,336-339`; `OBContext.java:218-221` | Never run a tool with a null `OBContext`; fail closed if none can be built. |
| G3 | **Identity equals the minted token, not `OBContext`.** Client = role's client; org/warehouse defaulted. `ad_client_id` in `ThreadContext`/KB filter = current client. | §1.2, P23 | Build the tool context from the same resolution (`capture`) and log the effective ids once per question. |
| G4 | **Webhook context differs by auth path.** Token path: org of the `UserRole`, no warehouse; group path: JWT claims. Both inside admin mode. | `WebhookServiceHandler.java:126-128`, `:395-397`, `:234` | Choose one rule in-process (the group path = context from claims is the one the copilot actually uses, because `assignMissingPermissions` creates `DefinedwebhookRole`). |
| G5 | **Thread-bound state not inherited by workers**: `OBContext`, admin stack, `SessionHandler`, `SessionInfo`. LangChain4j hops, `executeToolsConcurrently`, tool-internal pools (P16 uses 10 threads). | `OBContext.java:122-127`; `SessionInfo.java:53-75` | Pass `ToolExecutionContext` explicitly; one `call()` per unit of work (each acquires and cleans its own thread state); tools that fan out call `ctx.call(...)` per item. |
| G6 | **Session/transaction scope.** Tool writes on the request thread are committed only when `DalRequestFilter` finishes; a later exception rolls back every tool write (the Python path committed each SWS call independently). Calling `commitAndClose()` on the request thread detaches `CopilotApp`/`Role`/`User` entities that `processResponseAndTrack` and the conversation tracking still use afterwards (`RestServiceUtil.java:480-488`). | `DalThreadCleaner.java:41-92`; `RestServiceUtil.java:480` | Separate thread = separate Hibernate session = one transaction per tool call, committed on success, rolled back on error, request thread's session untouched. Matches today's per-call commit semantics. |
| G7 | **Visibility.** The tool's session does not see uncommitted request-thread data (e.g. the conversation row, attachments saved in `buildRequestJson`). | follows from G6 | Pass ids in, re-read by id; flush+commit the request thread before the first tool round if a tool needs it (O7). |
| G8 | **Detached entities.** Hand tools ids/JSON only, never `BaseOBObject` across threads; return `String`/`JSONObject`. | `CopilotUtils.java:830` | Enforced by the `ToolExecutionContext` API (§4). |
| G9 | **Long streaming request.** The request thread holds one DB connection (session opened by the first `OBDal` call in `buildRequestJson`) for the entire LLM+tool run; with tools this grows from seconds to minutes. | `DalRequestFilter` wraps the whole chain | Prefer closing the request-thread session before the long LLM/tool phase only after all request-thread entity use is done (O7); tool worker threads hold a connection only for their own duration. |
| G10 | **Cleanup.** A pooled worker that skips cleanup leaks `OBContext`/session/admin stack into the next call (`OBContext.clearAdminModeStack` warns on unbalanced stacks, `:381-395`). | `DalThreadHandler`, `DalRequestFilter.java:101-114` | Subclass `DalThreadHandler` (or call `DalThreadCleaner` + `OBContext.clearAdminModeStack()` + `SessionInfo.init()` in `finally`). GO's `McpSessionManager.executeInContext` (`GO McpSessionManager.java:77-127`) also restores the previous context in `finally` and rolls back without closing; a dedicated thread makes that unnecessary. |
| G11 | **`SessionInfo` / DB session info.** SWS sets `SessionInfo.setUserId/ProcessType("WS")/ProcessId("DAL")` per call (`BaseSecureWebServiceServlet.java:126-128`); audit columns/triggers that read the DB context info lose the user if a worker thread never sets it. | `SessionInfo.java:53-75,226` | Set `userId`, `processType`, `processId` in `doBefore`. [INFERENCE: which PL/SQL triggers depend on it was not checked.] |
| G12 | **Scheduled / background questions** (`ProcessScheduleApps`, `ConversationUtils.java:475`): no servlet, no JWT; the `OBContext` is the scheduler's (the user/role of the `ProcessRequest`). Today a non-WS role simply gets no token; a WS-enabled one gets a token minted from the process context. | `ProcessScheduleApps.java` (`63ac7aef`), `RestServiceUtil.java:915-925` | `capture()` must work from any thread that has an `OBContext`, not only from a servlet. |
| G13 | **Token-minting failure is swallowed** and the question proceeds token-less. In-process there is nothing to swallow, but the equivalent "context cannot be built" must be an explicit tool error, not a silent admin fallback (G2). | `RestServiceUtil.java:923-925` | `capture()` throws; the engine reports it as a tool failure. |
| G14 | **Warehouse can be null / invalid.** `OBContext.getWarehouse()` is nullable (`CopilotUtils.java:834`); `OBContext.setOBContext(user, role, client, org, lang, warehouse)` with a stale warehouse can fail. GO resolves an accessible warehouse (`GO EnvironmentRequestAuthenticator.java:365-372`, `DalWarehouseResolver`) and the effective client from the role when the claim is `0` (`EffectiveClientResolver`, ETP-5047). | `GO auth/` | Reuse `SecureWebServicesUtils.createContext` (`SWS :293-297`) exactly as GO and the JWT servlet do, so the context is built the same way as for an SWS call. |
| G15 | **Interrupted/cancelled tools.** The pre-engine `RestServiceUtil.sendRequestToCopilot` called `Thread.currentThread().interrupt()` on *any* failure (still present in `getGraphImg`, `RestServiceUtil.java:1041`; described in `docs/issues-and-improvements.md:17-23`); a tool executor must not inherit a poisoned interrupt flag. | `RestServiceUtil.java:1041`; docs/issues-and-improvements.md:17-23 | Fresh worker thread per call from a pool that clears interrupts. |
| G16 | **Secrets in logs.** Python logs `extra_info` incl. the token (`routes.py:177-183`); Java `sendRequestToCopilot` logged the whole request JSON when debug was on (pre-engine path). | cited | In-process: never log the token; `mintToken()` result must not reach prompts, SSE frames, or `ETCOP_MESSAGE`. |
| G17 | **Code execution tools receive a bearer.** P15/P20 hand the JWT to LLM-authored code (Riza cloud for `RizaExecutor`). | `code_evaluators.py:124-125`, `DockerTool.py:118-136` | Short TTL, role-bound, minted on demand and only for tools flagged `needsCredential` (O4). |

---

## 4. Recommended design: `ToolExecutionContext`

### 4.1 Principles

1. **Identity is data, not ambient state.** Capture ids once on the request thread; pass the object to every tool; no tool reads `OBContext.getOBContext()` on its own thread before the context binds it.
2. **Tools run as the asking user and role, non-admin, by default.** Admin mode is an explicit inner block, never inherited (G1).
3. **One tool call = one thread binding = one transaction.** Binding, commit/rollback and cleanup are done by the context, never by tool code (G5, G6, G10).
4. **Parity first.** Resolve the identity exactly like the JWT does (`generateToken` resolution + `createContext`, §1.2), so the in-process tool sees what the SWS endpoint saw.
5. **A real credential is a separate, optional capability** (`mintToken()`), only for code that leaves the JVM (P15, P20).

### 4.2 API (package `com.etendoerp.copilot.engine.tool`, sketch)

```java
public final class ToolExecutionContext {

  // ---- immutable identity snapshot: ids only, safe to share across threads ----
  public String userId();
  public String roleId();
  public String clientId();        // role's client, same as the JWT "client" claim (§1.2)
  public String orgId();           // resolved like SecureWebServicesUtils.generateToken
  public String warehouseId();     // nullable
  public String language();
  public String agentId();         // CopilotApp id        (replaces ThreadContext["assistant_id"])
  public String conversationId();  // (replaces ThreadContext["conversation_id"])
  public boolean webServiceEnabled(); // Role.isWebServiceEnabled() at capture time
  public JSONObject extraInfo();   // read-only: model_config, tool_config (replaces get_extra_info)
  public ConcurrentMap<String,Object> attributes(); // per-conversation scratch, e.g. oauth tokens (P17/P21)

  /** On the request thread (or any thread with an OBContext). Throws if there is none (G13). */
  public static ToolExecutionContext capture(CopilotApp app, String conversationId, JSONObject extraInfo);

  /**
   * Run `body` bound to this identity on a worker thread:
   *   doBefore : OBContext = SecureWebServicesUtils.createContext(user, role, org, warehouse, client)
   *              (fresh object per call, admin stack empty, entity access checks ON),
   *              SessionInfo.setUserId / ProcessType("COPILOT") / ProcessId(agentId)
   *   body     : the tool (receives this context, may call admin(...) for narrow privileged reads)
   *   doFinal  : success -> commit; any Throwable -> rollback; then OBContext.clearAdminModeStack(),
   *              SessionInfo.init(), OBContext null, session deleted  (DalThreadHandler)
   * Applies timeout; cancel interrupts the worker. Returns only JSON/String.
   */
  public <T> T call(Duration timeout, Callable<T> body) throws ToolExecutionException;

  /** Narrow, balanced admin block for config reads inside a tool body. */
  public <T> T admin(Callable<T> body);

  /** Real SWS JWT for the few consumers that leave the JVM; null if !webServiceEnabled. Short TTL; never logged. */
  public String mintToken();

  /** Literal-token compatibility: true for the string "ETENDO_TOKEN" (P10/P12/P14, prompts, OpenAPI notes). */
  public static boolean isTokenPlaceholder(String token);
}
```

Implementation notes:

* `capture`: read `OBContext.getOBContext()` once; resolve role (re-fetch by id), org, warehouse exactly as `getEtendoSWSToken` does (`CopilotUtils.java:826-837`) and take the client from the role (`SWS :768`). Refactor that resolution out of `generateToken` rather than duplicating it (it is private there: option = add a small public resolver in `CopilotUtils` that both `getEtendoSWSToken` and `capture` use). Store ids only.
* `call`: a dedicated bounded `ExecutorService` (daemon threads named `copilot-tool-N`), `DalThreadHandler` subclass as the body wrapper. The request thread blocks on `Future.get(timeout)`; it does not touch the worker's session. Tools that fan out (P16) call `ctx.call` per item rather than sharing a thread.
* `admin`: `OBContext.setAdminMode(); try { … } finally { OBContext.restorePreviousMode(); }`, same as `GO McpToolRouter.java:174-182`.
* `mintToken`: `SecureWebServicesUtils.generateToken(user, role, org, warehouse)` through the existing `CopilotUtils` path; only when a tool declares it needs a credential; honors `Role.isWebServiceEnabled()` (as `getAuthJson` does).
* Generic HTTP-style tools (P10-P14): a single `EtendoRequestRouter` that gets `(method, url, endpoint, body)`; if `url` host is the Etendo host (`CopilotUtils.getEtendoHost()`, same rule as `openapi_tool_gen.py:474-475`) → in-process (`/sws/copilot/*` → Java calls; `/webhooks/<n>` → `WebhookInvoker`; `/sws/com.etendoerp.etendorx.datasource/*` → DAL/NEO adapter or **SWS-loop** with `mintToken()` as an interim path, O1/O3); else plain HTTP, no identity. Keep accepting the literal `ETENDO_TOKEN` and ignore it for in-process calls.
* `WebhookInvoker`: resolve `DefinedWebHook` by name → check `DefinedwebhookRole(hook, ctx.roleId())` → validate `DefinedWebhookParam` required → `WeldUtils.getInstanceFromStaticBeanManager(class).get(params, out)`; runs inside `ctx.call`, with the admin choice explicit (O2).

### 4.3 How the engine uses it

```
RestService.doPost (admin mode, DalRequestFilter session)          <- request thread
  RestServiceUtil.buildRequestJson                                  (unchanged; may keep building extra_info)
  LangChain4jEngine.execute
     ctx = ToolExecutionContext.capture(app, conversationId, request.extra_info)
     model loop: model.chat(...) -> AiMessage with tool requests
         for each request: result = ctx.call(timeout, () -> tool.invoke(args, ctx))   <- worker thread, user role, non-admin, own tx
         append ToolExecutionResultMessage; repeat up to ClientToolSupport.maxToolRounds()
```

Because the context is explicit, the same object works unchanged for streaming callbacks, `executeToolsConcurrently`, and scheduled/background questions (G12). LangChain4j integration (all verified in the 1.19.0 sources, §3.2): put the `ToolExecutionContext` in `InvocationParameters` (`InvocationParameters.from(Map.of("ctx", ctx))`) and declare the tool method parameter `InvocationParameters` so each `@Tool` / `ToolExecutor` reads it from the call, never from a thread-local; wrap the tool body in `ctx.call(...)`. If `executeToolsConcurrently(Executor)` is enabled, pass the same bounded executor that `call()` uses, so a parallel batch of tool requests and a single request take the same code path (single requests run on the calling thread otherwise, `ToolService.java:873`, so the wrapper must always hop to the worker rather than assume the executor was used).

`auth` in `extra_info` can be dropped for the Java engine (no consumer); keep it for Python mode until Python is removed (clean cutover: delete `getAuthJson` / `getEtendoSWSToken` call from `addExtraContextWithHooks` together with the Python runtime; `CheckHostsButton` / `GetMCPConfiguration` keep `generateEtendoToken()`).

### 4.4 Test checklist (acceptance for the design)

* A tool run as role R (WS-enabled and not) sees exactly the entities `OBContext` of R can read (ExecSQL table list, SimSearch), not admin's (G1/G2).
* A tool write commits at tool end and survives a later exception of the agent loop; a failing tool leaves no partial writes (G6).
* Two concurrent tool calls for different users do not leak `OBContext`/session between pooled threads (G10).
* Tool called from a LangChain4j callback thread and from `executeToolsConcurrently` gets the right identity (G5).
* Scheduled app (`ProcessScheduleApps`) runs tools with the schedule's role (G12).
* `ETENDO_TOKEN` literal passed by an agent prompt to `APICallTool` against the Etendo host resolves in-process; against an external host is not substituted.

---

## 5. Open questions

| # | Question | Why it matters / what I could not verify |
|---|---|---|
| O1 | Interim strategy for `/sws/com.etendoerp.etendorx.datasource/*` (P6, P12, P13, P16, plus every OpenAPI-flow tool): loopback HTTP with `mintToken()` (works today for any endpoint, zero semantic drift, keeps the "WS enabled" gate and token TTL) vs a DAL/NEO adapter (true in-process, but re-implements projections, filters `q=`, `_startRow`, PATCH-like `exclude_unset` semantics). | RX datasource source is not in this checkout; behavior parity cannot be proven from here. |
| O2 | Admin vs user semantics per tool class. SWS endpoints enforce role access + portal-role + `isWebServiceEnabled`; webhooks run in **admin mode** with the user's context and gate by `DefinedwebhookRole`. Should the Java engine keep `isWebServiceEnabled` as a gate (currently a non-WS role gets `auth: {}` and all token tools fail), or let any role use server-side tools? | Product/security decision; affects `getAuthJson` removal. |
| O3 | Is GO's NEO / DAL service layer (`NeoServlet`, `McpToolRouter`, `McpSessionManager.executeInContext`) the intended in-process replacement for the RX datasource tools? | `com.etendoerp.go` is a different module/team; it already has an authenticated in-process router, `executeInContext` and per-spec authorization (`McpToolRouter.java:1623-1628`). Reuse would mean a dependency from copilot on go [INFERENCE: not checked]. |
| O4 | Which tools legitimately need a **portable credential** (P15 CodeAct/Riza, P20 Docker env)? TTL and role scope for `mintToken()`, and whether Riza (third-party) should ever receive it. | Security: the JWT currently leaves the system for Riza (`code_evaluators.py:124-125`). |
| O5 | Fate of the Python MCP server (P27-P31): external clients authenticate with their own SWS token; does the GO MCP replace it, or does it stay Python? | If it stays, Python mode cannot be fully deprecated. |
| O6 | Remote-host tools (`login_etendo` with explicit credentials / `remote_host`, P7, P24, P25) act on **another** Etendo instance and cannot be in-process. Keep as HTTP tools in Java? | Parity of devassistant org/client init. |
| O7 | Request-thread session lifecycle during a long agent run: close/commit the request-thread Hibernate session before the LLM/tool phase (frees the connection, G9) or keep it? `RestServiceUtil.handleQuestion` uses `copilotApp` after the engine returns (`:480-488`), so closing needs those entities re-fetched by id. | Needed for streaming and long tool chains. |
| O8 | Streaming: the engine currently emits a single frame (`LangChain4jEngine.java:119-127`); when real streaming arrives, tool execution is triggered from the HTTP client's callback thread (`AiServiceStreamingResponseHandler.java:232,310`), not the servlet thread, and SSE writes to `HttpServletResponse` come from another thread than the one Tomcat expects. Servlet async (`startAsync`) is not used anywhere in the module (grep: none in `RestServiceUtil`). Do we move to async servlet + executor, or keep the request thread blocked on the stream? | Determines whether `capture()` runs on the request thread only (as designed: it does, before the first model call) and whether `DalRequestFilter`'s end-of-request commit still fires after the stream ends. |
| O9 | `DalRequestFilter` coverage of `/sws/copilot/*` and `/copilot/*` was not verified (`web.xml:92` declares the filter; I did not read the mappings). The GO code comments assume it owns the session lifecycle for MCP. | Affects who commits the request thread's session. |
| O10 | Token TTL (`SWSConfig.expirationTime`) in the target environments: with `mintToken()` per tool call the TTL stops being a run-length problem (§1.3), but if O1 chooses loopback, do we re-mint per call? | Resolved by minting inside `call()`. |
| O11 | `docs/request-flow.md:82` lists `ETENDO_HOST` inside `auth`; code does not send it. Fix the doc as part of the cutover. | Doc accuracy. |
| O12 | Tool-loop style: the current engine drives `ChatModel.chat(ChatRequest)` by hand (`LangChain4jEngine.java:63-67`), not AiServices. Hand-rolled loop = call `ctx.call(...)` directly per `ToolExecutionRequest` (no `InvocationParameters` needed); AiServices = `InvocationParameters` + `@Tool` classes. Which does ETP-5136 adopt for server-side tools? | Both work with the design above; the choice changes where `ctx` is injected, not what it does. |
