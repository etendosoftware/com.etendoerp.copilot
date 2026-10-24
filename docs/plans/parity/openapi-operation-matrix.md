# OpenAPI/FLOW operation matrix

## Inventory scope and evidence

The Java request builder supplies each application source marked `spec` as `{name,type,spec}`. The Python generator emits GET, POST, PUT, and DELETE operations from the attached document's `paths`. The installed database's `ETAPI_OPENAPI_FLOWPOINT` flags expose the concrete request resource, route category, and allowed HTTP operations for configured flows, even though the emitted OpenAPI JSON itself is not stored in source data.

The environment query (`localhost:55434/etendo_local`) found 12 active `ETAPI_OPENAPI_FLOW` records. It found one installed `ETCOP_APP_SOURCE` with `behaviour='spec'`: SQL Expert / SQL Expert Flow. Its `ETCOP_FILE.TEXT` is empty, and checked-in sourcedata has no exported OpenAPI JSON fixture. Thus operation IDs and exact emitted URL path strings are unavailable; the operation rows below are the exact configured RX request resources and method flags from `ETAPI_OPENAPI_FLOWPOINT` (the API's GET-by-id route is shown as `GET/{id}`).

## Exact configured operations and routing decision

None of the configured operation rows below is eligible for in-process execution with the checked-in operation metadata: the FLOW attachment/specs are unavailable, so no operation has a fixed descriptor-side entity and field allowlist. Independently, the RX `DataSourceServlet.doGet` enters `OBContext.setAdminMode()` while preparing session defaults (source `com.etendoerp.etendorx/services/DataSourceServlet.java:120-191`); invoking it from a caller-context tool would violate the no-admin-escalation contract. RX exposes GET, POST and PUT `WebService` entrypoints, but no DELETE entrypoint; POST/PUT depend on RX tab/session/form/callout state and their non-admin safety has not been established. Thus the configured RX GET/POST/PUT/DELETE rows remain EtendoHttp loopback until RX offers a caller-context-safe service API. `SMFWHE_WBHK` operations also remain loopback through this generated HTTP tool: an OpenAPI route does not identify a configured webhook name/argument map that can safely be passed to `WebhookInvoker`. The dedicated webhook tool path must use `WebhookInvoker` directly. This is an evidence-based limitation, not a claim that a synthetic servlet request cannot be constructed.

| Flow | Request resource | Route type | Configured operations | Route decision and reason |
|---|---|---|---|---|
| ColumnList | Column | ETRX_Tab | PUT | EtendoHttp loopback — RX tab-backed write; core JSON write security/transaction semantics are unsuitable. |
| ColumnList | Reference | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — RX tab-backed datasource; writes have the core service context/commit issue. |
| Copilot | AgentAccess | ETRX_Tab | POST | Loopback — RX tab-backed write. |
| Copilot | Agents | ETRX_Tab | GET | Loopback — RX tab/tab-session mapping; not proven fixed-entity simple read. |
| Message Creator Flow | ADMessage | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes additionally require caller-context transaction-safe implementation. |
| Message Creator Flow | moduleDBPrefix | ETRX_Tab | GET | Loopback — RX tab-backed datasource mapping. |
| Message Creator Flow | moduleHeader | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| Module Creator | moduleDBPrefix | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Module Creator | moduleDataPackage | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Module Creator | moduleDependency | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Module Creator | moduleHeader | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Reference Creator Flow | ModulePrefix | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| Reference Creator Flow | ReferencesHeader | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Reference Creator Flow | ReferencesList | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Reference Creator Flow | ReferencesTab | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Reference Creator Flow | Table | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| Reference Creator Flow | ViewColumn | ETRX_Tab | GET, GET/{id}, PUT | Loopback — RX tab-backed datasource mapping and write semantics. |
| SQL Expert Flow | SQLExec | SMFWHE_WBHK | POST | Loopback — webhook/query action, not entity CRUD. |
| TableColumnChecker | CheckTablesColumnHook | SMFWHE_WBHK | GET, GET/{id}, POST, PUT | Loopback — webhook action, not a plain entity data-service route. |
| TableIsTree | ModulePrefix | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| TableIsTree | TableTreeConfiguration | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| TableIsTree | TreeCkeck | ETRX_Tab | GET, GET/{id}, PUT | Loopback — tab-backed RX semantics; PUT has the core service context/commit issue. |
| Task Flow | Task | ETRX_Tab | POST | Loopback — RX tab-backed write. |
| Task Flow | TaskStatus | ETRX_Tab | GET | Loopback — RX tab-backed datasource mapping. |
| Task Flow | TaskType | ETRX_Tab | GET, POST | Loopback — RX tab-backed datasource; POST has the core service context/commit issue. |
| WebHookCreator | Webhook | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| WebHookCreator | WebhookParam | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| WebHookCreator | moduleDBPrefix | ETRX_Tab | GET | Loopback — RX tab-backed datasource mapping. |
| WebHookCreator | moduleHeader | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| Tables, Windows and Tabs Flow | CheckTablesColumnHook | SMFWHE_WBHK | GET, GET/{id}, POST, PUT | Loopback — webhook action, not plain entity CRUD. |
| Tables, Windows and Tabs Flow | Column | ETRX_Tab | GET, GET/{id}, PUT | Loopback — tab-backed RX semantics; PUT has the core service context/commit issue. |
| Tables, Windows and Tabs Flow | CreateAndRegisterTable | SMFWHE_WBHK | GET, GET/{id}, POST, PUT | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | CreateColumn | SMFWHE_WBHK | GET, GET/{id}, POST, PUT | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | CreateView | SMFWHE_WBHK | GET, GET/{id}, POST | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | ElementsHandler | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | GetWindowTabOrTableInfo | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | ModulePrefix | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| Tables, Windows and Tabs Flow | Reference | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| Tables, Windows and Tabs Flow | RegisterColumns | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | RegisterFields | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | RegisterTab | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | RegisterWindow | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | SyncTerms | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Tables, Windows and Tabs Flow | Table | ETRX_Tab | GET, GET/{id}, PUT | Loopback — tab-backed RX semantics; PUT has the core service context/commit issue. |
| Tables, Windows and Tabs Flow | TableTreeConfiguration | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Tables, Windows and Tabs Flow | TreeCkeck | ETRX_Tab | GET, GET/{id}, PUT | Loopback — tab-backed RX semantics; PUT has the core service context/commit issue. |
| Tables, Windows and Tabs Flow | ValidationSetup | ETRX_Tab | GET, GET/{id}, POST, PUT | Loopback — tab-backed RX semantics; writes have the core service context/commit issue. |
| Tables, Windows and Tabs Flow | moduleHeader | ETRX_Tab | GET | Loopback — RX tab-backed datasource mapping. |
| Windows, Tabs and Fields Agent Flow | ElementsHandler | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Windows, Tabs and Fields Agent Flow | Field | ETRX_Tab | POST | Loopback — RX tab-backed write. |
| Windows, Tabs and Fields Agent Flow | GetWindowTabOrTableInfo | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Windows, Tabs and Fields Agent Flow | ModulePrefix | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| Windows, Tabs and Fields Agent Flow | Reference | ETRX_Tab | GET, GET/{id} | Loopback — RX tab-backed datasource mapping. |
| Windows, Tabs and Fields Agent Flow | RegisterFields | SMFWHE_WBHK | POST, PUT | Loopback — webhook action. |
| Windows, Tabs and Fields Agent Flow | RegisterTab | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Windows, Tabs and Fields Agent Flow | RegisterWindow | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Windows, Tabs and Fields Agent Flow | SyncTerms | SMFWHE_WBHK | POST | Loopback — webhook action. |
| Windows, Tabs and Fields Agent Flow | TreeCkeck | ETRX_Tab | GET, GET/{id}, PUT | Loopback — tab-backed RX semantics; PUT has the core service context/commit issue. |

## Generic runtime classification

At runtime each actual OpenAPI operation logs its tool name, HTTP method, path, classification, and reason. Only a local Etendo RX GET with a static datasource path and explicit `x-etendo-entity` plus `x-etendo-fields` extensions, and with simple `id`/projection/sort/paging/criteria arguments, may use core `DefaultJsonDataService.fetch` in the caller's non-admin `OBContext`. This is not dispatch through RX's `DataSourceServlet`: the service call is narrowly bounded and descriptor-derived. The installed flowpoint metadata does not include exported FLOW specs, so none of the concrete configured rows below is certified in-process. Dynamic entity paths, `q` RSQL, tab/window/selector/projection adaptation, writes, webhook/process actions, external routes, and binary operations stay on EtendoHttp loopback. The runtime does not silently fall back.

## Endpoint evidence

RX's registered `com.etendoerp.etendorx.services.DataSourceServlet` is a `WebService`, not a plain JSON facade. Its `doGet` explicitly enters admin mode to initialize session defaults before dispatching to the core datasource servlet, then restores the prior mode; it also expects Etendo session/request state and performs RX tab/window adaptation. Synthetic servlet interfaces can provide request/response mechanics, but cannot make this admin-mode call safe in the Copilot caller context. The facade has `doGet`, `doPost`, and `doPut` entrypoints but no `doDelete`; POST/PUT use tab lookup, form initialization/change handlers, callouts, and write processing. No configured operation is therefore asserted as RX-servlet in-process. A fixed-field GET may instead use the narrower core `DefaultJsonDataService.fetch` path described above, only when its operation descriptor carries explicit entity and field declarations. `SMFWHE_WBHK` is a webhook/process action rather than an entity datasource call and must not be routed as CRUD.
