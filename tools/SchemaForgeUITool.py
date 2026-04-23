from typing import Any, Dict, List, Literal, Optional, Type

from copilot.baseutils.logging_envvar import copilot_debug
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_wrapper import CopilotTool
from copilot.core.utils.etendo_utils import (
    build_headers,
    get_etendo_host,
    get_etendo_token,
)
from pydantic import BaseModel, Field, field_validator

NEO_BASE = "/sws/neo"

Action = Literal[
    "list_windows",
    "get_schema",
    "query",
    "get",
    "create",
    "update",
    "delete",
    "resolve_lookup",
    "list_actions",
    "run_action",
    "run_process",
    "navigate",
]


class SchemaForgeUIToolInput(BaseModel):
    action: Action = Field(
        description=(
            "Operation to perform. One of: list_windows, get_schema, query, get, "
            "create, update, delete, resolve_lookup, list_actions, run_action, "
            "run_process, navigate. Use 'navigate' to open a window in the user's "
            "UI: pass only `window` to open the list view; pass `record_id` with a "
            "UUID to open that record; pass `record_id=\"new\"` to open a blank form. "
            "IMPORTANT: When the user asks to create a record with specific values "
            "(or asks you to pick values), ALWAYS use a single navigate call with "
            "`record_id=\"new\"` AND a `values` dict — DO NOT call 'create' and DO "
            "NOT ask the user to fill the form manually. Prefill is a pure UI hint: "
            "it needs no write permission, never submits the record, and the user "
            "reviews and saves. For FK fields, call 'resolve_lookup' first to turn "
            "names into UUIDs, then put them in `values`. Only call navigate twice "
            "if you genuinely need both a list view and a detail view; never emit "
            "a bare /{window} navigate immediately before /{window}/new."
        )
    )
    window: Optional[str] = Field(
        default=None,
        description="Kebab-case spec name, e.g. 'purchase-order'. Required for all actions except list_windows.",
    )
    entity: Optional[str] = Field(
        default="header",
        description="Entity within the window. Defaults to 'header'. Use 'lines' for child rows.",
    )
    record_id: Optional[str] = Field(
        default=None,
        description="Record UUID. Required for get, update, delete, list_actions, run_action. For 'navigate', pass a UUID to open that record or the literal string \"new\" to open a blank create form.",
    )
    data: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Field values. Used by create, update, run_action, run_process.",
    )
    lines: Optional[List[Dict[str, Any]]] = Field(
        default=None,
        description="Child rows to POST after the header for action='create'. Each row is a dict of field values.",
    )
    lines_entity: Optional[str] = Field(
        default="lines",
        description="Child entity name for action='create' when posting lines. Defaults to 'lines'. Override if the window uses a different child entity name.",
    )
    filters: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Query-string filters for action='query' (e.g. {'parentId': 'ABC'}).",
    )
    field: Optional[str] = Field(
        default=None,
        description="Column name of the FK field for action='resolve_lookup'.",
    )
    search: Optional[str] = Field(
        default=None,
        description="Search text for action='resolve_lookup'.",
    )
    values: Optional[Dict[str, Any]] = Field(
        default=None,
        description=(
            "For action='navigate' with record_id='new': pre-populate form fields. "
            "Keys are column names from get_schema, values are raw values (FK fields "
            "need UUIDs — call resolve_lookup first). Prefill is a UI hint only: it "
            "needs NO permissions, does NOT submit the record, and the user edits "
            "and saves. Whenever the user asks to create a record with any values "
            "(or asks you to pick values), pass them here — never tell the user to "
            "fill fields manually."
        ),
    )
    action_name: Optional[str] = Field(
        default=None,
        description="Button column name for action='run_action' (e.g. 'docaction', 'posted').",
    )
    limit: Optional[int] = Field(
        default=50, description="Row limit for action='query'. Default 50."
    )

    # LLMs occasionally emit identifiers wrapped in or suffixed with invisible
    # Unicode (word joiner U+2060, zero-width space, BOM, soft hyphen, etc.).
    # Any such character silently breaks URL routing (Tomcat 404). Strip them
    # from every string identifier at the input boundary.
    @field_validator(
        "window", "entity", "record_id", "field", "action_name", "lines_entity",
        mode="before",
    )
    @classmethod
    def _strip_invisible(cls, v):
        if not isinstance(v, str):
            return v
        invisible = {"​", "‌", "‍", "⁠", "﻿", "­"}
        return "".join(ch for ch in v if ch not in invisible)


class SchemaForgeUITool(CopilotTool):
    name: str = "SchemaForgeUITool"
    description: str = (
        "Drive Schema Forge windows via NEO Headless. "
        "Supports discovery (list_windows, get_schema), "
        "CRUD (query, get, create, update, delete), "
        "FK resolution (resolve_lookup), "
        "and process execution (list_actions, run_action, run_process). "
        "Always returns {ok, data, error, hint}."
    )
    args_schema: Type[BaseModel] = SchemaForgeUIToolInput

    def _run(self, **kwargs) -> Dict[str, Any]:
        action = kwargs["action"]
        window = kwargs.get("window")
        entity = kwargs.get("entity") or "header"

        if action == "list_windows":
            return _act_list_windows()
        if action == "get_schema":
            return _act_get_schema(window, entity)
        if action == "query":
            return _act_query(window, entity, kwargs.get("filters"), kwargs.get("limit"))
        if action == "get":
            return _act_get(window, entity, kwargs.get("record_id"))
        if action == "create":
            return _act_create(
                window, entity, kwargs.get("data"), kwargs.get("lines"), kwargs.get("lines_entity") or "lines"
            )
        if action == "update":
            return _act_update(window, entity, kwargs.get("record_id"), kwargs.get("data"))
        if action == "delete":
            return _act_delete(window, entity, kwargs.get("record_id"))
        if action == "resolve_lookup":
            return _act_resolve_lookup(
                window, entity, kwargs.get("field"), kwargs.get("search"), kwargs.get("limit")
            )
        if action == "list_actions":
            return _act_list_actions(window, entity, kwargs.get("record_id"))
        if action == "run_action":
            return _act_run_action(
                window, entity, kwargs.get("record_id"), kwargs.get("action_name"), kwargs.get("data")
            )
        if action == "run_process":
            return _act_run_process(window, kwargs.get("data"))
        if action == "navigate":
            return _act_navigate(window, kwargs.get("record_id"), kwargs.get("values"))

        return _error(f"Unknown action: {action}")


def _ok(data: Any) -> Dict[str, Any]:
    return {"ok": True, "data": data, "error": None, "hint": None}


def _error(error: str, hint: Optional[str] = None) -> Dict[str, Any]:
    return {"ok": False, "data": None, "error": error, "hint": hint}


def _neo_call(
    method: str,
    path: str,
    *,
    query: Optional[Dict[str, Any]] = None,
    body: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """Call NEO Headless and return {ok, data, error, hint}.

    `path` must start with '/' and is appended after /sws/neo (NEO_BASE).
    Delegates the actual HTTP roundtrip to `call_etendo` (shared helper that
    handles auth header + `copilot_debug_curl` request logging).
    """
    import json as _json
    from urllib.parse import urlencode

    import requests

    try:
        token = get_etendo_token()
    except Exception as e:
        return _error(str(e), hint="Copilot has no ETENDO_TOKEN; tool must be called in an authenticated user session.")

    endpoint = NEO_BASE + path
    if query:
        endpoint = f"{endpoint}?{urlencode({k: v for k, v in query.items() if v is not None})}"
    base_url = get_etendo_host().rstrip("/")

    # call_etendo returns parsed JSON on success, `{"error": <text>}` on
    # failure — it hides the HTTP status, so we still need a direct request
    # to preserve `_error_hint` behavior (400/404/401/etc. map to different
    # hints). We do, however, reuse `build_headers` and `copilot_debug` for
    # consistent auth + logging with other tools.
    headers = build_headers(token)
    full_url = base_url + endpoint
    copilot_debug(f"NEO {method} {full_url}")
    try:
        resp = requests.request(
            method.upper(),
            full_url,
            headers=headers,
            data=None if body is None else _json.dumps(body),
        )
    except requests.RequestException as e:
        return _error(f"Network error calling NEO: {e}", hint="Retry or check that the Etendo server is reachable.")

    try:
        payload = resp.json() if resp.text else {}
    except ValueError:
        payload = {"raw": resp.text}

    if resp.ok:
        return _ok(payload)

    def _extract_msg(p):
        if isinstance(p, dict):
            m = p.get("message")
            if isinstance(m, str):
                return m
            err = p.get("error")
            if isinstance(err, str):
                return err
            if isinstance(err, dict):
                em = err.get("message")
                if isinstance(em, str):
                    return em
        return None

    error_msg = _extract_msg(payload) or resp.text or f"HTTP {resp.status_code}"
    if not isinstance(error_msg, str):
        error_msg = str(error_msg)
    copilot_debug(f"NEO {method} {full_url} -> {resp.status_code} {error_msg[:200]}")
    return _error(error_msg, hint=_error_hint(resp.status_code, error_msg))


def _error_hint(status: int, message: str) -> Optional[str]:
    msg = (message or "").lower()
    if status == 404 and "spec" in msg:
        return "Unknown window. Call action='list_windows' to see available specs."
    if status == 404:
        return "Record or resource not found. Verify window/entity/record_id."
    if status == 400 and "mandatory" in msg:
        return "A required field is missing. Call action='get_schema' to see which fields are required."
    if status == 400 and ("uuid" in msg or "invalid id" in msg):
        return "A foreign-key value was not a valid UUID. Call action='resolve_lookup' to translate a display value into the correct ID."
    if status == 401 or status == 403:
        return "The current Etendo user lacks permission for this window or record."
    if status == 409:
        return "Conflict — the record may have been modified. Re-fetch with action='get' before retrying."
    return None


def _act_list_windows() -> Dict[str, Any]:
    return _neo_call("GET", "/")


def _require(value: Any, field_name: str) -> Optional[Dict[str, Any]]:
    if value in (None, ""):
        return _error(f"'{field_name}' is required for this action.")
    return None


def _act_get_schema(window: Optional[str], entity: Optional[str]) -> Dict[str, Any]:
    err = _require(window, "window")
    if err:
        return err
    describe = _neo_call("GET", f"/{window}")
    if not describe["ok"]:
        return describe
    selectors = _neo_call("GET", f"/{window}/{entity or 'header'}/selectors")
    return _ok({"describe": describe["data"], "selectors": selectors["data"] if selectors["ok"] else None})


def _act_query(window: Optional[str], entity: str, filters: Optional[Dict[str, Any]], limit: Optional[int]) -> Dict[str, Any]:
    err = _require(window, "window")
    if err:
        return err
    query: Dict[str, Any] = dict(filters or {})
    if limit is not None:
        query.setdefault("_limit", limit)
    return _neo_call("GET", f"/{window}/{entity}", query=query)


def _act_get(window: Optional[str], entity: str, record_id: Optional[str]) -> Dict[str, Any]:
    err = _require(window, "window") or _require(record_id, "record_id")
    if err:
        return err
    return _neo_call("GET", f"/{window}/{entity}/{record_id}")


def _extract_id(created: Dict[str, Any]) -> Optional[str]:
    if not isinstance(created, dict):
        return None
    for key in ("id", "Id", "ID", "recordId"):
        if created.get(key):
            return str(created[key])
    return None


def _act_create(
    window: Optional[str],
    entity: str,
    data: Optional[Dict[str, Any]],
    lines: Optional[List[Dict[str, Any]]],
    lines_entity: str = "lines",
) -> Dict[str, Any]:
    err = _require(window, "window")
    if err:
        return err

    header_resp = _neo_call("POST", f"/{window}/{entity}", body=data or {})
    if not header_resp["ok"]:
        return header_resp
    if not lines:
        return header_resp

    header_id = _extract_id(header_resp["data"])
    if not header_id:
        return _error(
            "Header created but no id returned — cannot attach lines.",
            hint="Inspect 'data' field from the preceding response to find the header id, then call action='create' for each line with entity='lines' and include the parent id.",
        )

    created_lines: List[Dict[str, Any]] = []
    for idx, line in enumerate(lines):
        body = dict(line)
        body.setdefault("parentId", header_id)
        line_resp = _neo_call("POST", f"/{window}/{lines_entity}", body=body)
        if not line_resp["ok"]:
            return {
                "ok": False,
                "data": {"header": header_resp["data"], "created_lines": created_lines, "failed_line_index": idx},
                "error": line_resp["error"],
                "hint": line_resp["hint"] or "Header was created but line failed. Decide whether to delete the header or retry the line.",
            }
        created_lines.append(line_resp["data"])

    return _ok({"header": header_resp["data"], "lines": created_lines})


def _act_update(
    window: Optional[str], entity: str, record_id: Optional[str], data: Optional[Dict[str, Any]]
) -> Dict[str, Any]:
    err = _require(window, "window") or _require(record_id, "record_id") or _require(data, "data")
    if err:
        return err
    return _neo_call("PUT", f"/{window}/{entity}/{record_id}", body=data)


def _act_delete(window: Optional[str], entity: str, record_id: Optional[str]) -> Dict[str, Any]:
    err = _require(window, "window") or _require(record_id, "record_id")
    if err:
        return err
    return _neo_call("DELETE", f"/{window}/{entity}/{record_id}")


def _act_resolve_lookup(
    window: Optional[str], entity: str, field: Optional[str], search: Optional[str], limit: Optional[int]
) -> Dict[str, Any]:
    err = _require(window, "window") or _require(field, "field")
    if err:
        return err
    query: Dict[str, Any] = {}
    if search:
        query["q"] = search
        query["_searchText"] = search
    if limit is not None:
        query["_limit"] = limit
    return _neo_call("GET", f"/{window}/{entity}/selectors/{field}", query=query)


def _act_list_actions(window: Optional[str], entity: str, record_id: Optional[str]) -> Dict[str, Any]:
    err = _require(window, "window") or _require(record_id, "record_id")
    if err:
        return err
    return _neo_call("GET", f"/{window}/{entity}/{record_id}/action")


def _act_run_action(
    window: Optional[str],
    entity: str,
    record_id: Optional[str],
    action_name: Optional[str],
    data: Optional[Dict[str, Any]],
) -> Dict[str, Any]:
    err = (
        _require(window, "window")
        or _require(record_id, "record_id")
        or _require(action_name, "action_name")
    )
    if err:
        return err
    return _neo_call("POST", f"/{window}/{entity}/{record_id}/action/{action_name}", body=data or {})


def _act_run_process(window: Optional[str], data: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    err = _require(window, "window")
    if err:
        return err
    return _neo_call("POST", f"/{window}", body=data or {})


def _act_navigate(
    window: Optional[str],
    record_id: Optional[str],
    values: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    err = _require(window, "window")
    if err:
        return err
    path = f"/{window}/{record_id}" if record_id else f"/{window}"
    emitted: List[Dict[str, Any]] = [{"type": "navigate", "path": path}]
    if record_id == "new" and isinstance(values, dict) and values:
        emitted.append({"type": "prefill_form", "window": window, "values": values})
    try:
        bucket = ThreadContext.get_data("ui_actions") or []
        bucket.extend(emitted)
        ThreadContext.set_data("ui_actions", bucket)
    except Exception:
        pass
    # Keep data.ui_action as the primary (navigate) for LLM readability.
    return _ok({"ui_action": emitted[0], "ui_actions": emitted})
