# OpenAPI/FLOW operation matrix

## Inventory scope and evidence

The Java request builder supplies each application source marked `spec` as `{name,type,spec}`. The Python generator emits GET, POST, PUT, and DELETE operations from the attached document's `paths`. The installed database's `ETAPI_OPENAPI_FLOWPOINT` flags expose the concrete request resource, route category, and allowed HTTP operations for configured flows, even though the emitted OpenAPI JSON itself is not stored in source data.

The environment query (`localhost:55434/etendo_local`) found 12 active `ETAPI_OPENAPI_FLOW` records. It found one installed `ETCOP_APP_SOURCE` with `behaviour='spec'`: SQL Expert / SQL Expert Flow. Its `ETCOP_FILE.TEXT` is empty, and checked-in sourcedata has no exported OpenAPI JSON fixture. Thus operation IDs and exact emitted URL path strings are unavailable; the operation rows below are the exact configured RX request resources and method flags from `ETAPI_OPENAPI_FLOWPOINT` (the API's GET-by-id route is shown as `GET/{id}`).

## Exact configured operations and routing decision

All configured `ETRX_Tab` resources target the RX datasource service with tab/window-backed entity mapping. They remain on EtendoHttp loopback: the exact emitted resource path is absent, model arguments must not choose the entity, and RX prepares tab/session/projection behavior. All `SMFWHE_WBHK` resources target registered webhook/process actions and remain on loopback because they are not plain entity data-service calls. In addition, all configured POST/PUT operations remain loopback: core `DefaultJsonDataService.add/update` enters cross-org-reference admin mode, and `remove` commits internally, so these APIs violate the caller-context/no-admin-escalation contract.

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

At runtime each actual OpenAPI operation logs its tool name, HTTP method, path, classification, and reason. Only a local Etendo RX GET with a static datasource path and explicit `x-etendo-entity` plus `x-etendo-fields` extensions, and with simple `id`/projection/sort/paging/criteria arguments, may use core `DefaultJsonDataService.fetch` in the caller's non-admin `OBContext`. There is no such proof in the installed flowpoint metadata above, so none of the configured operation rows is certified in-process. Dynamic entity paths, `q` RSQL, tab/window/selector/projection adaptation, all writes, webhook/process actions, external routes, and binary operations stay on EtendoHttp loopback. The runtime does not silently fall back.

## Endpoint evidence

RX registers `com.etendoerp.etendorx.services.DataSourceServlet` as the datasource web service. Its servlet facade constructs request/session/tab state and adapts reads/writes. Core JSON read can be called without a servlet wrapper, but generic writes are not a safe caller-context shortcut: add/update set cross-org-reference admin mode, and remove commits the DAL transaction. Webhook actions and SQL Expert are also not generic entities. The exact operation ID and emitted URL path require the FLOW JSON attachment, which is absent from the checked DB/source tree.
