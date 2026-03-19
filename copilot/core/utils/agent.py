import json
import os

from copilot.baseutils.logging_envvar import copilot_error, read_optional_env_var
from copilot.core.schemas import QuestionSchema
from copilot.core.utils import etendo_utils
from copilot.core.utils.models import get_api_key, get_proxy_url
from langchain.chat_models import init_chat_model

_PROVIDER_ALIAS_MAP = {
    "gemini": "google_genai",
}
_NATIVE_SDK_PROVIDERS = {"google_genai"}
DEFS_KEY = "$defs"


def get_full_question(question: QuestionSchema) -> str:
    if question.local_file_ids is None or len(question.local_file_ids) == 0:
        return question.question
    result = question.question
    result += "\n" + "Local Files Ids for Context:"
    for file_id in question.local_file_ids:
        parent_dir_of_current_dir = os.path.dirname(os.getcwd())
        result += "\n - " + parent_dir_of_current_dir + file_id
    return result


def get_llm(model, provider, temperature):
    """
    Initialize the language model with the given parameters.

    Args:
        model (str): The name of the model to be used.
        provider (str): The provider of the model.
        temperature (float): The temperature setting for the model, which controls the
        randomness of the output.

    Returns:
        ChatModel: An initialized language model instance.
    """
    # Initialize the language model
    if provider and "ollama" in provider:
        ollama_host = read_optional_env_var("copilot.ollama.host", "ollama")
        ollama_port = read_optional_env_var("copilot.ollama.port", "11434")
        llm = init_chat_model(
            model_provider=provider,
            model=model,
            temperature=temperature,
            streaming=True,
            base_url=f"{ollama_host}:{ollama_port}",
            model_kwargs={"stream_options": {"include_usage": True}},
        )

    else:
        resolved_provider = _PROVIDER_ALIAS_MAP.get(provider, provider)

        if get_proxy_url() is not None:
            # Proxy mode: prefix model with original provider, route through OpenAI
            model_to_use = provider + "/" + model
            llm = init_chat_model(
                model_provider="openai",
                model=model_to_use,
                temperature=temperature,
                base_url=get_proxy_url(),
                model_kwargs={"stream_options": {"include_usage": True}},
                streaming=True,
            )
        elif resolved_provider in _NATIVE_SDK_PROVIDERS:
            # Native SDK providers (e.g. google_genai): no OpenAI-specific kwargs
            llm = init_chat_model(
                model_provider=resolved_provider,
                model=model,
                temperature=temperature,
                streaming=True,
            )
        else:
            # OpenAI-compatible direct mode (no proxy)
            llm = init_chat_model(
                model_provider=resolved_provider,
                model=model,
                temperature=temperature,
                model_kwargs={"stream_options": {"include_usage": True}},
                streaming=True,
            )
    # Adjustments for specific models, because some models have different
    # default parameters
    model_config = get_model_config(provider, model)
    if not model_config:
        return llm
    if "max_tokens" in model_config:
        llm.max_tokens = int(model_config["max_tokens"])
    return llm


def _fix_array_schemas(schema):
    """Recursively fix array schemas by adding missing 'items' property."""
    if not isinstance(schema, dict):
        return schema

    fixed_schema = schema.copy()

    if fixed_schema.get("type") == "array" and (
        "items" not in fixed_schema or fixed_schema.get("items") == {}
    ):
        fixed_schema["items"] = {"type": "string"}

    if fixed_schema.get("type") == "object" and "properties" not in fixed_schema:
        fixed_schema["properties"] = {}

    if "properties" in fixed_schema:
        fixed_schema["properties"] = {
            k: _fix_array_schemas(v) for k, v in fixed_schema["properties"].items()
        }

    if "items" in fixed_schema:
        fixed_schema["items"] = _fix_array_schemas(fixed_schema["items"])

    if "additionalProperties" in fixed_schema and isinstance(fixed_schema["additionalProperties"], dict):
        fixed_schema["additionalProperties"] = _fix_array_schemas(fixed_schema["additionalProperties"])

    for keyword in ("anyOf", "oneOf", "allOf"):
        if keyword in fixed_schema and isinstance(fixed_schema[keyword], list):
            fixed_schema[keyword] = [_fix_array_schemas(item) for item in fixed_schema[keyword]]

    if DEFS_KEY in fixed_schema:
        fixed_schema[DEFS_KEY] = {
            k: _fix_array_schemas(v) for k, v in fixed_schema[DEFS_KEY].items()
        }

    return fixed_schema


def _fix_list_annotation(annotation, visited):
    """Fix bare list or List[Any] annotations to List[str] for Gemini compatibility."""
    from typing import Any, get_args

    args = get_args(annotation)
    if not args or args == (Any,):
        return list[str]
    new_args = tuple(_fix_annotation(a, visited) for a in args)
    if new_args != args:
        return list[new_args[0]]
    return annotation


def _fix_union_annotation(annotation, visited):
    """Simplify Union types for Gemini which doesn't support anyOf in function declarations."""
    from typing import Union, get_args

    from pydantic import BaseModel

    args = get_args(annotation)
    non_none_args = [a for a in args if a is not type(None)]
    if len(non_none_args) > 1:
        return _simplify_multi_union(args, non_none_args, visited, BaseModel)
    new_args = tuple(_fix_annotation(a, visited) for a in args)
    if new_args != args:
        return Union[tuple(new_args)]
    return annotation


def _simplify_multi_union(args, non_none_args, visited, base_model_cls):
    """Pick the best single type from a multi-type Union for Gemini compatibility."""
    model_args = [a for a in non_none_args
                  if isinstance(a, type) and issubclass(a, base_model_cls)]
    picked = model_args[0] if model_args else non_none_args[0]
    if type(None) in args:
        from typing import Optional
        return Optional[_fix_annotation(picked, visited)]
    return _fix_annotation(picked, visited)


def _fix_pydantic_model_annotation(annotation, visited):
    """Recursively fix field annotations in a Pydantic model."""
    model_id = id(annotation)
    if model_id in visited:
        return annotation
    visited.add(model_id)
    needs_rebuild = _fix_model_fields(annotation, visited)
    if needs_rebuild:
        annotation.model_rebuild(force=True)
    return annotation


def _fix_model_fields(model_cls, visited):
    """Fix annotations on all fields of a Pydantic model. Returns True if any changed."""
    needs_rebuild = False
    for field_info in model_cls.model_fields.values():
        fixed = _fix_annotation(field_info.annotation, visited)
        if fixed is not field_info.annotation:
            field_info.annotation = fixed
            needs_rebuild = True
    return needs_rebuild


def _fix_annotation(annotation, visited=None):
    """Fix type annotations that produce invalid JSON schemas for Gemini.

    Bare ``list`` and ``List[Any]`` generate ``{"type": "array", "items": {}}``
    which Gemini rejects. This replaces them with ``List[str]`` so that
    ``items`` has a concrete type.
    """
    from typing import Union, get_origin

    from pydantic import BaseModel

    if visited is None:
        visited = set()

    if annotation is list:
        return list[str]

    origin = get_origin(annotation)

    if origin is list:
        return _fix_list_annotation(annotation, visited)

    if origin is Union:
        return _fix_union_annotation(annotation, visited)

    if isinstance(annotation, type) and issubclass(annotation, BaseModel):
        return _fix_pydantic_model_annotation(annotation, visited)

    return annotation


def _fix_tool_schema(tool):
    """Fix a single tool's schema for Gemini compatibility."""
    from pydantic import BaseModel

    if not hasattr(tool, "args_schema") or tool.args_schema is None:
        return
    if isinstance(tool.args_schema, dict):
        fixed = _fix_array_schemas(tool.args_schema)
        if fixed != tool.args_schema:
            tool.args_schema = fixed
    elif isinstance(tool.args_schema, type) and issubclass(tool.args_schema, BaseModel):
        visited = set()
        if _fix_model_fields(tool.args_schema, visited=visited):
            tool.args_schema.model_rebuild(force=True)


def fix_tools_for_provider(tools, provider):
    """Fix tool schemas for providers that require strict JSON Schema compliance (e.g. Gemini).

    Gemini's API requires 'items' on array-type properties and 'properties' on object types.
    This fixes the actual Pydantic field annotations so the fix propagates through
    LangChain's _create_subset_model pipeline (which regenerates models from annotations).
    Falls back to replacing args_schema with a fixed dict for non-Pydantic schemas.
    This is a no-op for providers that don't need it.
    """
    resolved = _PROVIDER_ALIAS_MAP.get(provider, provider)
    if resolved not in _NATIVE_SDK_PROVIDERS:
        return tools

    for tool in tools:
        try:
            _fix_tool_schema(tool)
        except Exception:
            pass


def get_model_config(provider, model):
    """
    Retrieve the configuration for a specific model from the extra information.

    Args:
        provider (str): The provider of the model.
        model (str): The name of the model.

    Returns:
        dict: The configuration dictionary for the specified model.
    """
    extra_info = etendo_utils.get_extra_info()
    if extra_info is None:
        return {}
    model_configs = extra_info.get("model_config")
    if model_configs is None:
        return {}
    provider_searchkey = provider or "null"  # if provider is None, set it to "null"
    provider_configs = model_configs.get(provider_searchkey, {})
    return provider_configs.get(model, {})


def get_structured_output(agent_configuration):
    """Get structured output format based on agent configuration."""
    if agent_configuration.structured_output_json_schema is None:
        return None
    try:
        # convert string to json
        json_schema_obj = json.loads(agent_configuration.structured_output_json_schema)
        return json_schema_obj
    except Exception as e:
        copilot_error("Error parsing structured output schema, falling back to default. Error: " + str(e))
        return None
