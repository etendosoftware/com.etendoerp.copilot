import builtins
import contextlib
import importlib
import io
import os
import pickle
import subprocess
import sys
from typing import Any, Dict

from RestrictedPython import (
    PrintCollector,
    compile_restricted,
    safe_globals,
    utility_builtins,
)

EXECUTOR_TYPES = {
    "original": "OriginalExecutor",
    "pypy_sandbox": "PyPySandboxExecutor",
    "restricted": "RestrictedExecutor",
}


class OriginalExecutor:
    """Tu implementación original"""

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


class PyPySandboxExecutor:
    """Ejecutor con sandbox PyPy"""

    def __init__(self, pypy_path: str = "pypy"):
        self.pypy_path = pypy_path
        self.sandbox_script = """
import sys
import pickle
from code import interact

sys.stdout = open('sandbox_output.txt', 'w')
sys.stderr = sys.stdout

data = pickle.load(sys.stdin.buffer)
code = data['code']
locals_dict = data['locals']

exec(code, {}, locals_dict)

with open('sandbox_output.txt', 'r') as f:
    output = f.read()
result = {'output': output, 'locals': locals_dict}
pickle.dump(result, sys.stdout.buffer)
sys.stdout.flush()
"""

    def _serialize_function(self, func) -> str:
        if not callable(func):
            return func
        try:
            import inspect

            source = inspect.getsource(func)
            return source
        except Exception:
            raise ValueError("Solo se pueden pasar funciones definidas en el código fuente")

    def execute(self, code: str, variables: Dict[str, Any]) -> tuple[str, Dict[str, Any]]:
        sandbox_locals = {}
        for key, value in variables.items():
            if callable(value):
                sandbox_locals[key] = self._serialize_function(value)
            else:
                sandbox_locals[key] = value

        input_data = {"code": code, "locals": sandbox_locals}

        with open("sandbox.py", "w") as f:
            f.write(self.sandbox_script)

        try:
            process = subprocess.Popen(
                [self.pypy_path, "-S", "--sandbox", "sandbox.py"],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

            pickle.dump(input_data, process.stdin)
            process.stdin.flush()

            output_data = pickle.load(process.stdout)
            process.wait()

            os.remove("sandbox.py")
            if os.path.exists("sandbox_output.txt"):
                os.remove("sandbox_output.txt")

            result_output = output_data["output"]
            new_locals = output_data["locals"]
            new_vars = {k: v for k, v in new_locals.items() if k not in variables}
            return result_output or "<code ran, no output>", new_vars

        except Exception as e:
            return f"Error en sandbox: {repr(e)}", {}


class CodeExecutor:
    """Clase contenedora para cambiar entre ejecutores"""

    def __init__(self, executor_type: str = "original", pypy_path: str = "pypy"):
        if executor_type not in EXECUTOR_TYPES:
            raise ValueError(f"Tipo de ejecutor inválido. Opciones: {list(EXECUTOR_TYPES.keys())}")

        if executor_type == "original":
            self.executor = OriginalExecutor()
        elif executor_type == "pypy_sandbox":
            self.executor = PyPySandboxExecutor(pypy_path)
        elif executor_type == "restricted":
            self.executor = RestrictedExecutor()

    def execute(self, code: str, variables: Dict[str, Any]) -> tuple[str, Dict[str, Any]]:
        return self.executor.execute(code, variables)


class RestrictedExecutor:
    def __init__(self, allowed_modules: set[str] = None):
        self.allowed_modules = allowed_modules or set(sys.modules.keys())

    def _safe_import(self, name: str, globals=None, locals=None, fromlist=(), level=0):
        """Función de importación segura"""
        if name in self.allowed_modules:
            return importlib.import_module(name)
        raise ImportError(f"Módulo '{name}' no está permitido")

    def execute(self, code: str, variables: Dict[str, Any]) -> tuple[str, Dict[str, Any]]:
        # Preparar entorno seguro
        globals_dict = safe_globals.copy()
        globals_dict.update(utility_builtins)

        # Configurar __import__ explícitamente
        globals_dict["__import__"] = self._safe_import

        # Configurar _print_ con PrintCollector para capturar la salida
        _print = PrintCollector
        globals_dict["_print_"] = _print

        local_vars = variables.copy()

        try:
            # Compilar el código con restricciones
            byte_code = compile_restricted(code, "<inline>", "exec")

            # Redirigir stdout para capturar la salida
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                exec(byte_code, globals_dict, local_vars)

            # Obtener la salida capturada
            result = output.getvalue()
            if not result:
                result = "¡Código ejecutado exitosamente pero no se imprimió nada en " "stdout!"

            # Procesar nuevas variables
            new_vars = {k: v for k, v in local_vars.items() if k not in variables}
            if new_vars:
                new_vars_msg = f"Se añadieron nuevas variables: {', '.join(new_vars.keys())}"
                result = f"{result}\n{new_vars_msg}"

            return result, new_vars
        except Exception as e:
            return f"Error: {repr(e)}", {}
