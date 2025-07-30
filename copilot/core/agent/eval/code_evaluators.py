import builtins
import contextlib
import inspect
import io
from typing import Any, Dict, Tuple

from copilot.core.threadcontext import ThreadContext
from langchain_sandbox import PyodideSandbox
from langgraph_codeact import EvalCoroutine
from rizaio import Riza

SANDBOX_PY = "sandbox.py"

EXECUTOR_TYPES = {
    "original": "OriginalExecutor",
    "riza": "RizaExecutor",
    "sandbox": "SandboxExecutor",
}


class OriginalExecutor:
    """Your original implementation"""

    def execute(self, code: str, _locals: Dict[str, Any]) -> tuple[str, Dict[str, Any]]:
        original_keys = set(_locals.keys())

        try:
            with contextlib.redirect_stdout(io.StringIO()) as f:
                exec(code, builtins.__dict__, _locals)
            result = f.getvalue()
            if not result:
                result = "<code ran, no output printed to stdout>"
        except Exception as e:
            result = f"Error during execution: {repr(e)}"

        new_keys = set(_locals.keys()) - original_keys
        new_vars = {key: _locals[key] for key in new_keys}
        return result, new_vars


def get_context_line(key, value):
    """Get the context line for a variable.

    Args:
        key: The variable name
        value: The variable value

    Returns:
        A string representation of the variable
    """
    if callable(value):
        # Get the function's source code
        src = inspect.getsource(value)
        return f"\n{src}"
    else:
        return f"\n{key} = {repr(value)}"


def create_pyodide_eval_fn(sandbox_dir: str = "./sessions", session_id: str | None = None) -> EvalCoroutine:
    """Create an eval_fn that uses PyodideSandbox.

    Args:
        sandbox_dir: Directory to store session files
        session_id: ID of the session to use

    Returns:
        A function that evaluates code using PyodideSandbox
    """
    sandbox = PyodideSandbox(sandbox_dir, allow_net=True)

    async def async_eval_fn(code: str, _locals: dict[str, Any]) -> tuple[str, dict[str, Any]]:
        # Create a wrapper function that will execute the code and return locals
        wrapper_code = f"""
def execute():
    try:
        # Execute the provided code
{chr(10).join("        " + line for line in code.strip().split(chr(10)))}
        return locals()
    except Exception as e:
        return {{"error": str(e)}}

execute()
"""
        # Convert functions in _locals to their string representation
        context_setup = ""
        for key, value in _locals.items():
            context_setup += get_context_line(key, value)

        try:
            # Execute the code and get the result
            response = await sandbox.execute(
                code=context_setup + "\n\n" + wrapper_code,
                session_id=session_id,
            )

            # Check if execution was successful
            if response.stderr:
                return f"Error during execution: {response.stderr}", {}

            # Get the output from stdout
            output = response.stdout if response.stdout else "<Code ran, no output printed to stdout>"
            result = response.result

            # If there was an error in the result, return it
            if isinstance(result, dict) and "error" in result:
                return f"Error during execution: {result['error']}", {}

            # Get the new variables by comparing with original locals
            new_vars = {k: v for k, v in result.items() if k not in _locals and not k.startswith("_")}
            return output, new_vars

        except Exception as e:
            return f"Error during PyodideSandbox execution: {repr(e)}", {}

    return async_eval_fn


class SandboxExecutor:
    """Ejecutor de código sandbox"""

    def __init__(self):
        self.eval_fn = create_pyodide_eval_fn("./sessions", ThreadContext.get_data("conversation_id"))

    async def execute(self, code: str, _locals: Dict[str, Any]) -> tuple[str, Dict[str, Any]]:
        """
        Execute code in an isolated environment using PyodideSandbox.

        Args:
            code: The code to execute
            _locals: Dictionary containing local variables (can include functions)

        Returns:
            Tuple containing:
            - The output from stdout (or error message if execution failed)
            - Dictionary of new variables created during execution
        """


class CodeExecutor:
    """Clase contenedora para cambiar entre ejecutores"""

    def __init__(self, executor_type: str = "original", pypy_path: str = "pypy"):
        if executor_type not in EXECUTOR_TYPES:
            raise ValueError(f"Tipo de ejecutor inválido. Opciones: {list(EXECUTOR_TYPES.keys())}")

        if executor_type == "original":
            self.executor = OriginalExecutor()
        elif executor_type == "riza":
            self.executor = RizaExecutor()

    def execute(self, code: str, variables: Dict[str, Any]) -> tuple[str, Dict[str, Any]]:
        # if in variables not exists "etendo_token" add it
        if "ETENDO_TOKEN" not in variables:
            from core.utils import etendo_utils

            variables["ETENDO_TOKEN"] = etendo_utils.get_etendo_token()
        return self.executor.execute(code, variables)


class RizaExecutor:
    def __init__(self):
        self.client = Riza()

    def execute(self, code: str, _locals: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
        """
        Execute code in an isolated environment using Riza's API.

        Args:
            code: The code to execute
            _locals: Dictionary containing local variables (can include functions)

        Returns:
            Tuple containing:
            - The output from stdout (or error message if execution failed)
            - Dictionary of new variables created during execution
        """
        # Create a wrapper function that will execute the code and return locals
        wrapper_code = f"""
    def execute(input):
        # Make input variables available in the execution context
        globals().update(input)

        # Handle function definitions in input
        for key, value in input.items():
            if isinstance(value, str) and value.startswith('def '):
                # Execute the function definition
                exec(value, globals())

        try:
            # Execute the provided code
    {chr(10).join('        ' + line for line in code.strip().split(chr(10)))}
            return locals()
        except Exception as e:
            return {{"error": str(e)}}
    """

        # Convert functions in _locals to their string representation
        processed_locals = {}
        for key, value in _locals.items():
            if callable(value):
                # Get the function's source code
                import inspect

                processed_locals[key] = inspect.getsource(value)
            else:
                processed_locals[key] = value

        try:
            # Execute the code and get the result

            response = self.client.command.exec_func(
                language="python", code=wrapper_code, input=processed_locals
            )

            # Check if execution was successful
            if response.execution.exit_code != 0:
                return f"Error during execution: {response.execution.stderr}", {}

            # Get the output from stdout
            output = (
                response.execution.stdout
                if response.execution.stdout
                else "<code ran, no output printed to stdout>"
            )

            # Get the result from the output
            if hasattr(response, "output") and response.output:
                result = response.output
            else:
                return output, {}

            # If there was an error in the result, return it
            if isinstance(result, dict) and "error" in result:
                return f"Error during execution: {result['error']}", {}

            # Get the new variables by comparing with original locals
            new_vars = {k: v for k, v in result.items() if k not in _locals}

            return output, new_vars

        except Exception as e:
            return f"Error during Riza API call: {repr(e)}", {}
