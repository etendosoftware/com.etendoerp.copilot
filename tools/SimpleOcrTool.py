"""SimpleOcrTool — lightweight OCR via litellm proxy.

No similarity search. Sends native PDFs and images directly to the model.
Supports OpenAI (Responses API) and Gemini (Chat Completions via litellm).
"""

import base64
import os
import time
from pathlib import Path
from typing import Optional, Type

from copilot.baseutils.logging_envvar import copilot_debug
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils.etendo_utils import get_extra_info

from tools.schemas import load_schema

DEFAULT_MODEL = "gpt-5-mini"
DEFAULT_PROVIDER = "openai"
DEFAULT_OCR_TEMPERATURE = 0.2
SIMPLE_OCR_TOOL_ID = "BD5CBAAA442F42E289F5C7438E3C2EDF"

SUPPORTED_MIME_TYPES = {
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
    "application/pdf",
}

SYSTEM_PROMPT = (
    "You are an expert OCR agent. Extract ALL information from the provided document "
    "thoroughly. Do not summarize or omit any detail, even if information is repeated "
    "or there are similar parts. Return the result in JSON format."
)


class SimpleOcrToolInput(ToolInput):
    path: str = ToolField(
        description=(
            "Absolute or relative path to the image or PDF file to be processed. "
            "The file must exist in the local file system."
        )
    )
    question: str = ToolField(
        description=(
            "Specific instructions describing what information to extract "
            "from the document. Be precise about the data fields needed."
        )
    )
    structured_output: Optional[str] = ToolField(
        default=None,
        description=(
            "Schema name for structured output (e.g., 'Invoice'). "
            "Schemas are loaded from tools/schemas/. "
            "Leave None for unstructured JSON extraction."
        ),
    )


def _resolve_file_path(rel_path):
    """Resolve a relative path to an existing file on disk."""
    for prefix in ("/app", "..", ""):
        candidate = os.path.join(prefix, rel_path.lstrip("/")) if prefix else rel_path
        if Path(candidate).is_file():
            return candidate
    raise FileNotFoundError(f"File not found: {rel_path}")


def _read_mime(file_path):
    """Detect MIME type using the filetype library."""
    import filetype

    guess = filetype.guess(file_path)
    return guess.mime if guess else None


def _validate_mime(file_path, mime):
    """Raise if MIME type is not supported."""
    if mime not in SUPPORTED_MIME_TYPES:
        raise ValueError(
            f"Unsupported MIME type '{mime}' for {file_path}. "
            f"Supported: {sorted(SUPPORTED_MIME_TYPES)}"
        )


def _get_model_config(agent_id):
    """Read model name and provider from Etendo extra_info tool config."""
    model_name = None
    provider = None
    try:
        extra_info = get_extra_info()
        if extra_info:
            tool_cfg = (
                extra_info.get("tool_config", {})
                .get(agent_id, {})
                .get(SIMPLE_OCR_TOOL_ID, {})
            )
            if tool_cfg:
                model_name = tool_cfg.get("model")
                provider = tool_cfg.get("provider")
    except Exception as e:
        copilot_debug(f"SimpleOcrTool config error: {e}")

    return model_name or DEFAULT_MODEL, provider or DEFAULT_PROVIDER


def _is_gemini(provider):
    return provider in ("gemini", "google_genai")


def _get_client():
    """Create an OpenAI client configured for the litellm proxy."""
    from copilot.core.utils.models import get_api_key, get_proxy_url
    from openai import OpenAI

    proxy_url = get_proxy_url()
    client_kwargs = {}
    if proxy_url:
        client_kwargs["base_url"] = proxy_url
    api_key = get_api_key("openai")
    if api_key:
        client_kwargs["api_key"] = api_key

    return OpenAI(**client_kwargs), proxy_url


def _model_id(provider, model_name, proxy_url):
    """Build the model identifier, prefixing with provider when using proxy."""
    return f"{provider}/{model_name}" if proxy_url else model_name


def _structured_schema_dict(structured_schema):
    """Extract JSON schema dict from a Pydantic model class, or None."""
    if not structured_schema:
        return None
    try:
        return structured_schema.model_json_schema()
    except Exception as e:
        copilot_debug(f"Structured output schema error: {e}")
        return None


def _call_openai(model_name, provider, system_prompt, question,
                 file_bytes, mime, structured_schema=None):
    """Call OpenAI via the Responses API through the litellm proxy."""
    client, proxy_url = _get_client()
    mid = _model_id(provider, model_name, proxy_url)

    file_b64 = base64.b64encode(file_bytes).decode("utf-8")
    user_content = []

    if mime == "application/pdf":
        user_content.append({
            "type": "input_file",
            "filename": "document.pdf",
            "file_data": f"data:application/pdf;base64,{file_b64}",
        })
    else:
        user_content.append({
            "type": "input_image",
            "image_url": f"data:{mime};base64,{file_b64}",
        })
    user_content.append({"type": "input_text", "text": question})

    api_kwargs = {
        "model": mid,
        "input": [
            {"role": "developer",
             "content": [{"type": "input_text", "text": system_prompt}]},
            {"role": "user", "content": user_content},
        ],
    }

    # gpt-5 family does not support the temperature parameter
    if not model_name.startswith("gpt-5"):
        api_kwargs["temperature"] = DEFAULT_OCR_TEMPERATURE

    schema_dict = _structured_schema_dict(structured_schema)
    if schema_dict:
        api_kwargs["text"] = {
            "format": {
                "type": "json_schema",
                "name": "ocr_output",
                "schema": schema_dict,
            }
        }

    t0 = time.time()
    response = client.responses.create(**api_kwargs)
    copilot_debug(f"OpenAI Responses API: {time.time() - t0:.2f}s")

    return response.output_text


def _call_gemini(model_name, provider, system_prompt, question,
                 file_bytes, mime, structured_schema=None):
    """Call Gemini via Chat Completions API routed through the litellm proxy."""
    client, proxy_url = _get_client()
    mid = _model_id(provider, model_name, proxy_url)

    file_b64 = base64.b64encode(file_bytes).decode("utf-8")

    api_kwargs = {
        "model": mid,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": [
                {"type": "image_url",
                 "image_url": {"url": f"data:{mime};base64,{file_b64}"}},
                {"type": "text", "text": question},
            ]},
        ],
        "temperature": DEFAULT_OCR_TEMPERATURE,
    }

    schema_dict = _structured_schema_dict(structured_schema)
    if schema_dict:
        api_kwargs["response_format"] = {
            "type": "json_schema",
            "json_schema": {"name": "ocr_output", "schema": schema_dict},
        }

    t0 = time.time()
    response = client.chat.completions.create(**api_kwargs)
    copilot_debug(f"Gemini Chat Completions: {time.time() - t0:.2f}s")

    return response.choices[0].message.content


class SimpleOcrTool(ToolWrapper):
    """Lightweight OCR tool via litellm proxy.

    Sends native PDFs and images directly to the model without rendering
    or similarity search. OpenAI uses the Responses API; Gemini uses
    Chat Completions routed through litellm.
    """

    name: str = "SimpleOcrTool"
    description: str = (
        "Extracts structured data from images and PDF documents using direct "
        "vision AI APIs. Sends files natively without image conversion. "
        "Best for: invoices, receipts, forms, contracts, and any structured "
        "document. Supports structured output schemas from tools/schemas/ "
        "directory. Supports: JPEG, PNG, WebP, GIF, and PDF files."
    )
    args_schema: Type[ToolInput] = SimpleOcrToolInput

    def run(self, input_params, *args, **kwargs):
        t_start = time.time()
        try:
            file_path = _resolve_file_path(input_params.get("path"))
            mime = _read_mime(file_path)
            _validate_mime(file_path, mime)

            with open(file_path, "rb") as f:
                file_bytes = f.read()

            model_name, provider = _get_model_config(self.agent_id)
            copilot_debug(
                f"SimpleOcrTool: model={model_name}, "
                f"provider={provider}, mime={mime}"
            )

            question = input_params.get("question")
            structured_schema = None
            schema_name = input_params.get("structured_output")
            if schema_name:
                structured_schema = load_schema(schema_name)
                if structured_schema:
                    copilot_debug(f"Using structured schema: {schema_name}")

            call = _call_gemini if _is_gemini(provider) else _call_openai
            result = call(
                model_name, provider, SYSTEM_PROMPT, question,
                file_bytes, mime, structured_schema,
            )

            copilot_debug(
                f"SimpleOcrTool total: {time.time() - t_start:.2f}s"
            )
            return result

        except Exception as e:
            import traceback

            copilot_debug(f"SimpleOcrTool error: {traceback.format_exc()}")
            copilot_debug(
                f"SimpleOcrTool error: {time.time() - t_start:.2f}s"
            )
            return {"error": str(e)}
