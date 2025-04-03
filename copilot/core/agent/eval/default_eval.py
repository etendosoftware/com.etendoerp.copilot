import builtins
import contextlib
import io
from typing import Any


def default_eval(code: str, _locals: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    # Store original keys before execution
    original_keys = set(_locals.keys())

    try:
        with contextlib.redirect_stdout(io.StringIO()) as f:
            #  ensure that requests is installed
            if "requests" not in _locals or _locals.get("requests") is None:
                import requests

                _locals["requests"] = requests

            if "json" not in _locals or _locals.get("json") is None:
                import json

                _locals["json"] = json

            exec(code, builtins.__dict__, _locals)
        result = f.getvalue()
        if not result:
            result = "<code ran, no output printed to stdout>"
    except Exception as e:
        result = f"Error during execution: {repr(e)}"

    # Determine new variables created during execution
    new_keys = set(_locals.keys()) - original_keys
    new_vars = {key: _locals[key] for key in new_keys}
    return result, new_vars
