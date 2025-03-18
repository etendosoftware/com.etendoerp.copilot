"""
    Unit tests for the GenUI route handling functions.

    This module contains tests for GenUI API route handlers, including token counting,
    message format conversion, chunk generation, and streaming response functionality.
    Tests cover both basic text responses and tool-calling scenarios.
    """
import json
from typing import List, AsyncGenerator
from unittest.mock import patch

import pytest
from langchain_core.messages import AIMessage, SystemMessage, HumanMessage, ToolMessage

from copilot.core.genui.genui_route import (
    count_tokens,
    convert_tools_to_langchain,
    generate_stream,
    _convert_messages_to_langchain_format,
    _calculate_prompt_tokens,
    _create_base_chunk_data,
    _has_tool_calls,
    _extract_tool_call_info,
    _create_tool_call_start_delta,
    _has_arguments,
    _try_process_arguments,
    _create_final_chunk,
)
from copilot.core.genui.genui_types import (
    GenuiPayload,
    GenUITool,
    GenUIMessage,
    GenUIToolFunction,
    GenUIToolFunctionParameters,
)

# Test fixtures and sample data
SAMPLE_TOOL_FUNCTION_PARAMS = GenUIToolFunctionParameters(
    type="object",
    properties={"param": {"type": "string"}},
    required=["param"],
)
SAMPLE_TOOL_FUNCTION = GenUIToolFunction(
    name="test_tool",
    description="A test tool",
    parameters=SAMPLE_TOOL_FUNCTION_PARAMS
)
SAMPLE_TOOL = GenUITool(
    type="function",
    function=SAMPLE_TOOL_FUNCTION
)
SAMPLE_MESSAGE = GenUIMessage(
    role="user",
    content="Hello, world!",
    tool_call_id="",
    tool_calls=[]
)
SAMPLE_PAYLOAD = GenuiPayload(
    model="gpt-3.5-turbo",
    messages=[SAMPLE_MESSAGE],
    tools=[SAMPLE_TOOL],
    tool_choice="auto",
    stream=True
)


@pytest.mark.asyncio
async def test_count_tokens():
    """
    Test the count_tokens function for correct token counting behavior.

    Verifies that:
    - The function returns a positive count for non-empty strings
    - The function returns zero for empty strings
    - The function always returns an integer
    """
    assert count_tokens("Hello, world!") > 0
    assert count_tokens("") == 0
    assert isinstance(count_tokens("test"), int)


def test_convert_tools_to_langchain():
    """
    Test conversion of GenUI tool formats to LangChain format.

    Verifies that the converted tools:
    - Are in a list format
    - Maintain the same count as input
    - Preserve important properties like type, name, and description
    """
    tools: List[GenUITool] = [SAMPLE_TOOL]
    result = convert_tools_to_langchain(tools)
    assert isinstance(result, list)
    assert len(result) == 1
    assert result[0]["type"] == "function"
    assert result[0]["function"]["name"] == "test_tool"
    assert result[0]["function"]["description"] == "A test tool"


async def async_generator_mock(data) -> AsyncGenerator:
    """
    Helper function to create an async generator from data.

    Args:
        data: Iterable data to yield asynchronously

    Yields:
        Items from the data sequence one by one
    """
    for item in data:
        yield item


@pytest.mark.asyncio
async def test_generate_stream_basic_content():
    """
    Test basic text response streaming from generate_stream.

    Uses a mock ChatOpenAI that returns a simple text message.
    Verifies that:
    - Multiple chunks are generated
    - Content is included in the response
    - Stream ends with [DONE] marker
    """
    with patch("copilot.core.genui.genui_route.ChatOpenAI") as mock_chat:
        mock_model = type("MockModel", (), {})()
        mock_model.astream = lambda messages: async_generator_mock([AIMessage(content="Hello")])
        mock_chat.return_value.bind_tools.return_value = mock_model

        chunks = [chunk async for chunk in generate_stream(SAMPLE_PAYLOAD)]
        assert len(chunks) >= 2  # Content + [DONE]
        assert "data: " in chunks[0]
        assert "hello" in chunks[0].lower()
        assert chunks[-1] == "data: [DONE]\n\n"


@pytest.mark.asyncio
async def test_generate_stream_with_tool_call():
    """
    Test tool call handling in generate_stream.

    Uses a mock ChatOpenAI that returns a tool call response.
    Verifies that:
    - Multiple chunks are generated for tool call phases
    - Tool name is correctly included
    - Tool arguments are correctly formatted
    - Finish reason is included in the response
    """
    with patch("copilot.core.genui.genui_route.ChatOpenAI") as mock_chat:
        mock_model = type("MockModel", (), {})()
        mock_model.astream = lambda messages: async_generator_mock([
            AIMessage(
                content="",
                additional_kwargs={
                    "tool_calls": [{
                        "id": "call_123",
                        "type": "function",
                        "function": {
                            "name": "test_tool",
                            "arguments": '{"param": "value"}'
                        }
                    }]
                }
            )
        ])
        mock_chat.return_value.bind_tools.return_value = mock_model

        chunks = [chunk async for chunk in generate_stream(SAMPLE_PAYLOAD)]
        assert len(chunks) >= 3  # Tool start, args, finish + [DONE]
        assert "test_tool" in chunks[0]

        # Parse the JSON from chunk[1] to verify tool arguments
        chunk_data = json.loads(chunks[1].replace("data: ", "").strip())
        assert chunk_data["choices"][0]["delta"]["tool_calls"][0]["function"]["arguments"] == '{"param": "value"}'

        assert "finish_reason" in chunks[2]


def test_convert_messages_to_langchain_format():
    """
    Test conversion of GenUI message formats to LangChain format.

    Verifies that:
    - Messages are correctly converted to their respective LangChain types
    - The correct number of messages is maintained
    - Message metadata like tool_call_id is preserved
    """
    messages = [
        GenUIMessage(role="system", content="System message"),
        GenUIMessage(role="user", content="User message"),
        GenUIMessage(role="tool", content="Tool result", tool_call_id="call_123"),
    ]
    result = _convert_messages_to_langchain_format(messages)
    assert len(result) == 3
    assert isinstance(result[0], SystemMessage)
    assert isinstance(result[1], HumanMessage)
    assert isinstance(result[2], ToolMessage)
    assert result[2].tool_call_id == "call_123"


def test_calculate_prompt_tokens():
    """
    Test token calculation for prompt messages.

    Verifies that:
    - Token count is positive for non-empty messages
    - The function accounts for both content and message overhead
    """
    messages = [SystemMessage(content="Hello"), HumanMessage(content="World")]
    tokens = _calculate_prompt_tokens(messages)
    assert tokens > 0
    assert tokens == count_tokens("Hello World") + 4 * 2  # 4 tokens per message


def test_create_base_chunk_data():
    """
    Test creation of base chunk data structure.

    Verifies that:
    - The structure contains all required fields
    - ID, timestamp, and model are correctly set
    - The choices array is properly initialized
    """
    chunk = _create_base_chunk_data("chat_123", 1234567890, "test_model")
    assert chunk["id"] == "chat_123"
    assert chunk["created"] == 1234567890
    assert chunk["model"] == "test_model"
    assert "choices" in chunk
    assert chunk["choices"][0]["index"] == 0


def test_has_tool_calls():
    """
    Test detection of tool calls in message chunks.

    Verifies that:
    - Messages with tool calls are correctly identified
    - Messages without tool calls return false
    """
    chunk_with_tools = AIMessage(content="", additional_kwargs={"tool_calls": [{}]})
    chunk_without_tools = AIMessage(content="text", additional_kwargs={})
    assert _has_tool_calls(chunk_with_tools)
    assert not _has_tool_calls(chunk_without_tools)


def test_extract_tool_call_info():
    """
    Test extraction of tool call ID and name.

    Verifies that ID and name are correctly extracted from tool call data.
    """
    tool_call = {"id": "call_123", "function": {"name": "test_tool"}}
    tool_id, tool_name = _extract_tool_call_info(tool_call)
    assert tool_id == "call_123"
    assert tool_name == "test_tool"


def test_create_tool_call_start_delta():
    """
    Test creation of delta object for initiating tool calls.

    Verifies that:
    - The delta has the correct role
    - Tool call ID is properly set
    - Tool name is included in the function object
    """
    delta = _create_tool_call_start_delta("call_123", "test_tool")
    assert delta["role"] == "assistant"
    assert delta["tool_calls"][0]["id"] == "call_123"
    assert delta["tool_calls"][0]["function"]["name"] == "test_tool"


def test_has_arguments():
    """
    Test detection of arguments in tool calls.

    Verifies that:
    - Tool calls with arguments return true
    - Tool calls without arguments return false
    """
    tool_call_with_args = {"function": {"arguments": '{"key": "value"}'}}
    tool_call_no_args = {"function": {"name": "test_tool"}}
    assert _has_arguments(tool_call_with_args)
    assert not _has_arguments(tool_call_no_args)


def test_try_process_arguments():
    """
    Test JSON validation for tool call arguments.

    Verifies that:
    - Valid JSON returns true
    - Invalid or incomplete JSON returns false
    """
    assert _try_process_arguments('{"key": "value"}')
    assert not _try_process_arguments('{"key": "value"')  # Incomplete JSON
    assert not _try_process_arguments("invalid json")


def test_create_final_chunk():
    """
    Test creation of the final response chunk with usage statistics.

    Verifies that:
    - Usage data is included
    - Token counts are correctly set
    - Total token count is the sum of prompt and completion tokens
    """
    chunk = _create_final_chunk("chat_123", 1234567890, "test_model", 10, 5)
    assert "data: " in chunk
    # Remove the data: prefix and parse the JSON
    chunk = json.loads(chunk[6:])
    assert "usage" in chunk
    assert chunk["usage"]["prompt_tokens"] == 10
    assert chunk["usage"]["completion_tokens"] == 5
    assert chunk["usage"]["total_tokens"] == 15

@pytest.mark.asyncio
async def test_generate_stream_empty_messages():
    """
    Test streaming with an empty message list.

    Verifies that:
    - The stream still completes with minimal chunks
    - No errors occur with empty input
    - Final chunk and [DONE] are sent
    """
    empty_payload = GenuiPayload(
        model="gpt-3.5-turbo",
        messages=[],  # Lista de mensajes vacía
        tools=[SAMPLE_TOOL],
        tool_choice="auto",
        stream=True
    )
    with patch("copilot.core.genui.genui_route.ChatOpenAI") as mock_chat:
        mock_model = type("MockModel", (), {})()
        mock_model.astream = lambda messages: async_generator_mock([])
        mock_chat.return_value.bind_tools.return_value = mock_model

        chunks = [chunk async for chunk in generate_stream(empty_payload)]
        assert len(chunks) == 2  # Only final chunk + [DONE]
        assert "usage" in chunks[0]  # Final chunk with stats
        assert chunks[1] == "data: [DONE]\n\n"

@pytest.mark.asyncio
async def test_generate_stream_no_tools():
    """
    Test streaming with no tools provided.

    Verifies that:
    - The stream works without tools
    - Basic content is still processed correctly
    """
    no_tools_payload = GenuiPayload(
        model="gpt-3.5-turbo",
        messages=[SAMPLE_MESSAGE],
        tools=[],  # Sin herramientas
        tool_choice="auto",
        stream=True
    )
    with patch("copilot.core.genui.genui_route.ChatOpenAI") as mock_chat:
        mock_model = type("MockModel", (), {})()
        mock_model.astream = lambda messages: async_generator_mock([AIMessage(content="Hello")])
        mock_chat.return_value.bind_tools.return_value = mock_model

        chunks = [chunk async for chunk in generate_stream(no_tools_payload)]
        assert len(chunks) >= 2  # Content + [DONE]
        assert "hello" in chunks[0].lower()
        assert chunks[-1] == "data: [DONE]\n\n"

@pytest.mark.asyncio
async def test_generate_stream_invalid_tool_args():
    """
    Test streaming with invalid tool call arguments.

    Verifies that:
    - Invalid JSON arguments are handled gracefully
    - The stream continues without crashing
    """
    with patch("copilot.core.genui.genui_route.ChatOpenAI") as mock_chat:
        mock_model = type("MockModel", (), {})()
        mock_model.astream = lambda messages: async_generator_mock([
            AIMessage(
                content="",
                additional_kwargs={
                    "tool_calls": [{
                        "id": "call_123",
                        "type": "function",
                        "function": {
                            "name": "test_tool",
                            "arguments": "{invalid_json"  # JSON inválido
                        }
                    }]
                }
            )
        ])
        mock_chat.return_value.bind_tools.return_value = mock_model

        chunks = [chunk async for chunk in generate_stream(SAMPLE_PAYLOAD)]
        assert len(chunks) >= 2  # Tool start + final + [DONE], args don't get processed
        assert "test_tool" in chunks[0]
        assert "finish_reason" not in chunks[1]  # Should not finish until valid JSON

@pytest.mark.asyncio
async def test_generate_stream_multiple_tool_calls():
    """
    Test streaming with multiple tool calls in a single chunk.

    Verifies that:
    - Only the first tool call is processed (current limitation)
    - Additional tool calls are ignored safely
    """
    with patch("copilot.core.genui.genui_route.ChatOpenAI") as mock_chat:
        mock_model = type("MockModel", (), {})()
        mock_model.astream = lambda messages: async_generator_mock([
            AIMessage(
                content="",
                additional_kwargs={
                    "tool_calls": [
                        {
                            "id": "call_123",
                            "type": "function",
                            "function": {"name": "test_tool", "arguments": '{"param": "value1"}'}
                        },
                        {
                            "id": "call_456",
                            "type": "function",
                            "function": {"name": "another_tool", "arguments": '{"param": "value2"}'}
                        }
                    ]
                }
            )
        ])
        mock_chat.return_value.bind_tools.return_value = mock_model

        chunks = [chunk async for chunk in generate_stream(SAMPLE_PAYLOAD)]
        assert len(chunks) >= 3  # Tool start, args, finish + [DONE]
        assert "test_tool" in chunks[0]  # Only first tool processed
        chunk_data = json.loads(chunks[1].replace("data: ", "").strip())
        assert chunk_data["choices"][0]["delta"]["tool_calls"][0]["function"]["arguments"] == '{"param": "value1"}'
        assert "another_tool" not in "".join(chunks)  # Another tool call is ignored

@pytest.mark.asyncio
async def test_generate_stream_malformed_tool_call():
    """
    Test streaming with a malformed tool call (missing fields).

    Verifies that:
    - The stream handles missing fields gracefully
    - Default values are used where applicable
    """
    with patch("copilot.core.genui.genui_route.ChatOpenAI") as mock_chat:
        mock_model = type("MockModel", (), {})()
        mock_model.astream = lambda messages: async_generator_mock([
            AIMessage(
                content="",
                additional_kwargs={
                    "tool_calls": [{
                        "type": "function",  # Without 'id' or 'function.name'
                        "function": {"arguments": '{"param": "value"}'}
                    }]
                }
            )
        ])
        mock_chat.return_value.bind_tools.return_value = mock_model

        chunks = [chunk async for chunk in generate_stream(SAMPLE_PAYLOAD)]
        assert len(chunks) >= 3  # Tool start, args, finish + [DONE]
        assert "call_" in chunks[0]  # Default ID used
        chunk_data = json.loads(chunks[1].replace("data: ", "").strip())
        assert chunk_data["choices"][0]["delta"]["tool_calls"][0]["function"]["arguments"] == '{"param": "value"}'

