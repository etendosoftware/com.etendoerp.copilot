"""
GenUI API route handler module.
This module provides functionality to handle GenUI API requests by converting them to LangChain calls
and streaming the responses back in a compatible format.
"""
import json
import time
from typing import AsyncGenerator, List, Dict, Any

import tiktoken
from langchain_core.messages import SystemMessage, HumanMessage, ToolMessage, AIMessage
from langchain_core.messages.tool import tool_call
from langchain_openai import ChatOpenAI

from copilot.core.genui.genui_types import GenuiPayload, GenUITool, GenUIMessage, GenUIToolCall


def count_tokens(text: str) -> int:
    """
    Count the number of tokens in the given text using OpenAI's CL100K tokenizer.

    Args:
        text: The text to tokenize and count

    Returns:
        The number of tokens in the text
    """
    return len(tiktoken.get_encoding("cl100k_base").encode(text))


def convert_tools_to_langchain(tools: List[GenUITool]) -> List[Dict[str, Any]]:
    """
    Convert GenUI Tool objects to the dictionary format expected by LangChain.

    Args:
        tools: A list of Tool objects from the GenUI types module

    Returns:
        A list of dictionaries representing the tools in LangChain format
    """
    return [tool.model_dump() for tool in tools]


async def generate_stream(payload: GenuiPayload) -> AsyncGenerator[str, None]:
    """
    Generate a stream of server-sent events containing AI responses.

    Creates a streaming response that handles both text content and tool calls,
    formatting them as server-sent events compatible with the GenUI API protocol.

    Args:
        payload: A GenUI payload object containing model, messages, and tool information

    Yields:
        JSON-formatted server-sent events strings containing response chunks
    """
    model = ChatOpenAI(
        temperature=0.1,
        streaming=True,
    ).bind_tools(convert_tools_to_langchain(payload.tools), tool_choice=payload.tool_choice)

    chat_id = f"chatcmpl-{int(time.time())}"
    created_time = int(time.time())

    # Convert messages to LangChain format
    messages = _convert_messages_to_langchain_format(payload.messages)

    # Calculate prompt tokens
    prompt_tokens = _calculate_prompt_tokens(messages)
    completion_tokens = 0
    tool_call_args_buffer = ""
    tool_call_started = False

    # Stream response
    async for chunk in model.astream(messages):
        chunk_data = _create_base_chunk_data(chat_id, created_time, payload.model)

        if chunk.content:
            content = chunk.content.lower()
            completion_tokens += count_tokens(content)
            chunk_data["choices"][0]["delta"] = {"content": content}
            yield f"data: {json.dumps(chunk_data)}\n\n"

        if not _has_tool_calls(chunk):
            continue

        tool_call_ = chunk.additional_kwargs["tool_calls"][0]

        if not tool_call_started:
            tool_call_started = True
            tool_call_id, tool_call_name = _extract_tool_call_info(tool_call_)
            chunk_data["choices"][0]["delta"] = _create_tool_call_start_delta(
                tool_call_id, tool_call_name
            )
            yield f"data: {json.dumps(chunk_data)}\n\n"

        # Process arguments if present
        if not _has_arguments(tool_call_):
            continue

        tool_call_args_buffer += tool_call_["function"]["arguments"]

        # Only proceed if we have valid JSON
        if not _try_process_arguments(tool_call_args_buffer):
            continue

        # Send the arguments
        chunk_data["choices"][0]["delta"] = {
            "tool_calls": [{
                "index": 0,
                "function": {"arguments": tool_call_args_buffer}
            }]
        }
        completion_tokens += count_tokens(tool_call_args_buffer)
        yield f"data: {json.dumps(chunk_data)}\n\n"

        # Send completion
        chunk_data["choices"][0]["delta"] = {}
        chunk_data["choices"][0]["finish_reason"] = "tool_calls"
        yield f"data: {json.dumps(chunk_data)}\n\n"

        tool_call_args_buffer = ""

    # Send final stats
    yield _create_final_chunk(chat_id, created_time, payload.model,
                              prompt_tokens, completion_tokens)
    yield "data: [DONE]\n\n"


def _convert_messages_to_langchain_format(messages: List[GenUIMessage]):
    """Convert GenUI messages to LangChain format."""
    result = []
    for msg in messages:
        if msg.role == 'system':
            result.append(SystemMessage(content=msg.content))
        elif msg.role == 'user':
            result.append(HumanMessage(content=msg.content))
        elif msg.role == 'tool':
            result.append(ToolMessage(content=msg.content, tool_call_id=msg.tool_call_id))
        elif msg.role == 'assistant':
            tool_calls = []
            for tc in msg.tool_calls:  # type: GenUIToolCall
                tool_calls.append(
                    tool_call(
                        id=tc.id,
                        name=tc.function_.name,
                        args=json.loads(tc.function_.arguments)
                    )
                )
            result.append(AIMessage(content=msg.content, tool_calls=tool_calls))
    return result


def _calculate_prompt_tokens(messages):
    """Calculate tokens used in the prompt."""
    prompt_text = " ".join([msg.content for msg in messages])
    return count_tokens(prompt_text) + 4 * len(messages)


def _create_base_chunk_data(chat_id, created_time, model):
    """Create the base structure for a chunk response."""
    delta: Dict[str, Any] = {}
    finish_reason: str | None = None
    return {
        "id": chat_id,
        "object": "chat.completion.chunk",
        "created": created_time,
        "model": model,
        "service_tier": "default",
        "system_fingerprint": "fp_f9f4fb6dbf",
        "choices": [{"index": 0, "delta": delta, "logprobs": None, "finish_reason": finish_reason}],
        "usage": None
    }


def _has_tool_calls(chunk):
    """Check if the chunk contains tool calls."""
    return "tool_calls" in chunk.additional_kwargs and chunk.additional_kwargs["tool_calls"]


def _extract_tool_call_info(tool_call_):
    """Extract tool call ID and name."""
    tool_call_id = tool_call_.get("id", f"call_{int(time.time())}")
    tool_call_name = tool_call_["function"]["name"] if "name" in tool_call_["function"] else ""
    return tool_call_id, tool_call_name


def _create_tool_call_start_delta(tool_call_id, tool_call_name):
    """Create the delta object for starting a tool call."""
    return {
        "role": "assistant",
        "content": None,
        "tool_calls": [{
            "index": 0,
            "id": tool_call_id,
            "type": "function",
            "function": {"name": tool_call_name, "arguments": ""}
        }],
        "refusal": None
    }


def _has_arguments(tool_call_):
    """Check if the tool call has arguments."""
    return "arguments" in tool_call_["function"] and tool_call_["function"]["arguments"]


def _try_process_arguments(args_buffer):
    """Try to parse arguments as valid JSON."""
    try:
        json.loads(args_buffer)
        return True
    except json.JSONDecodeError:
        return False


def _create_final_chunk(chat_id, created_time, model, prompt_tokens, completion_tokens):
    """Create the final chunk with usage statistics."""
    final_chunk = {
        "id": chat_id,
        "object": "chat.completion.chunk",
        "created": created_time,
        "model": model,
        "service_tier": "default",
        "system_fingerprint": "fp_f9f4fb6dbf",
        "choices": [],
        "usage": {
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": prompt_tokens + completion_tokens,
            "prompt_tokens_details": {"cached_tokens": 0, "audio_tokens": 0},
            "completion_tokens_details": {
                "reasoning_tokens": 0,
                "audio_tokens": 0,
                "accepted_prediction_tokens": 0,
                "rejected_prediction_tokens": 0
            }
        }
    }
    return f"data: {json.dumps(final_chunk)}\n\n"
