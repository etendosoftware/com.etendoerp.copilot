import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from copilot.core.agent.agent import AssistantResponse
from copilot.core.agent.langgraph_agent import (
    LanggraphAgent,
    _extract_response_text,
    _handle_on_chain_end,
    build_config,
    build_msg_input,
    fullfill_question,
)
from copilot.core.langgraph.copilot_langgraph import CopilotLangGraph
from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.schemas import GraphQuestionSchema
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage


class TestLanggraphAgent(unittest.TestCase):
    def get_graph_question(self):
        return GraphQuestionSchema.model_validate(
            {
                "assistants": [
                    {
                        "name": "SQLExpert",
                        "type": "langchain",
                        "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v",
                        "tools": [],
                        "provider": "openai",
                        "model": "gpt-4o",
                        "system_prompt": "You are a SQL expert assistant.",
                    },
                    {
                        "name": "Ticketgenerator",
                        "type": "langchain",
                        "assistant_id": "asst_7xpJ0v7UxjzWlhkQyPYbseC6",
                        "tools": [],
                        "provider": "openai",
                        "model": "gpt-4o",
                        "system_prompt": "You are a ticket generator assistant.",
                    },
                    {
                        "name": "Emojiswriter",
                        "type": "langchain",
                        "assistant_id": "FD8485BBE72D4B69BED2626D72114834",
                        "tools": [],
                        "provider": "openai",
                        "model": "gpt-4o",
                        "system_prompt": "Eres un experto en hacer redacciones usando emojis. Si recibes un texto, lo redactaras de manera muy amigable y usando muchos emojis\n",
                    },
                ],
                "history": [],
                "graph": {
                    "stages": [
                        {"name": "stage1", "assistants": ["SQLExpert", "Ticketgenerator", "Emojiswriter"]}
                    ]
                },
                "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
                "question": "What is the capital of France?",
                "local_file_ids": [],
                "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
            }
        )

    @pytest.mark.asyncio
    async def test_aexecute(self):
        langgraph_agent = LanggraphAgent()
        responses = []
        async for response in langgraph_agent.aexecute(self.get_graph_question()):
            responses.append(response)

        assert len(responses) > 0
        assert responses[-1].response is not None
        assert responses[-1].conversation_id == "test_conversation_async"

    @pytest.mark.asyncio
    async def test_aexecute_with_recording(self):
        langgraph_agent = LanggraphAgent()
        responses = []
        async for response in langgraph_agent.aexecute(self.get_graph_question()):
            responses.append(response)

        assert len(responses) > 0
        assert responses[-1].response is not None
        assert responses[-1].conversation_id == "test_conversation_async_with_recording"

    @pytest.mark.asyncio
    async def test_aexecute2(self):
        # Mock the necessary components
        # Instantiate the agent
        agent = LanggraphAgent()
        # Mock the methods that are called within aexecute
        with patch.object(MembersUtil, "get_members", return_value=[]), patch(
            "path.to.AsyncSqliteSaver.from_conn_string", return_value=AsyncMock()
        ), patch.object(CopilotLangGraph, "invoke", return_value=AsyncMock()), patch(
            "path.to.LanggraphAgent._configured_tools", new_callable=AsyncMock
        ):
            # Record the responses
            responses = []
            async for response in agent.aexecute(self.get_graph_question()):
                responses.append(response)
            # Now you can assert the responses as per your expectation
            assert len(responses) > 0
            assert responses[0].response == "Expected response"


class TestHandleOnChainEnd:
    """Test cases for _handle_on_chain_end with backwards message walking."""

    @pytest.mark.asyncio
    async def test_returns_last_ai_message_with_content(self):
        """Test normal case where last AIMessage has content."""
        event = {
            "data": {
                "output": {
                    "messages": [
                        HumanMessage(content="Hello"),
                        AIMessage(content="The answer is 42"),
                    ]
                }
            },
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is not None
        assert result.response == "The answer is 42"
        assert result.conversation_id == "thread-1"

    @pytest.mark.asyncio
    async def test_skips_empty_supervisor_response(self):
        """Test that empty supervisor response is skipped and child response is found."""
        event = {
            "data": {
                "output": {
                    "messages": [
                        HumanMessage(content="Create a BP"),
                        AIMessage(content="BP created successfully", name="ChildAgent"),
                        AIMessage(content="", name="Supervisor"),
                    ]
                }
            },
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is not None
        assert result.response == "BP created successfully"

    @pytest.mark.asyncio
    async def test_skips_malformed_empty_responses(self):
        """Test that multiple empty AIMessages are skipped."""
        event = {
            "data": {
                "output": {
                    "messages": [
                        HumanMessage(content="Create a BP"),
                        AIMessage(content="", name="Agent"),  # MALFORMED_FUNCTION_CALL
                        AIMessage(content="", name="Agent"),  # MALFORMED_FUNCTION_CALL
                        AIMessage(content="Done!", name="Agent"),
                        AIMessage(content="", name="Supervisor"),
                    ]
                }
            },
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is not None
        assert result.response == "Done!"

    @pytest.mark.asyncio
    async def test_gemini_list_content_normalized(self):
        """Test that Gemini-style list content is normalized."""
        event = {
            "data": {
                "output": {
                    "messages": [
                        HumanMessage(content="Hello"),
                        AIMessage(
                            content=[{"type": "text", "text": "Gemini response"}],
                            name="Agent",
                        ),
                        AIMessage(content="", name="Supervisor"),
                    ]
                }
            },
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is not None
        assert result.response == "Gemini response"

    @pytest.mark.asyncio
    async def test_returns_none_for_non_root_events(self):
        """Test that non-root events (parent_ids not empty) return None."""
        event = {
            "data": {
                "output": {
                    "messages": [AIMessage(content="response")],
                }
            },
            "parent_ids": ["parent-1"],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is None

    @pytest.mark.asyncio
    async def test_returns_none_for_non_dict_output(self):
        """Test that Command/ParentCommand outputs (non-dict) return None."""
        command = MagicMock()  # Simulate a Command object
        event = {
            "data": {"output": command},
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is None

    @pytest.mark.asyncio
    async def test_returns_none_when_no_messages(self):
        """Test that empty messages list returns None."""
        event = {
            "data": {"output": {"messages": []}},
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is None

    @pytest.mark.asyncio
    async def test_returns_none_when_all_ai_messages_empty(self):
        """Test returns None when all AIMessages have empty content."""
        event = {
            "data": {
                "output": {
                    "messages": [
                        HumanMessage(content="Hello"),
                        AIMessage(content=""),
                        AIMessage(content=""),
                    ]
                }
            },
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is None

    @pytest.mark.asyncio
    async def test_structured_response_takes_priority(self):
        """Test that structured_response overrides message-based response."""
        event = {
            "data": {
                "output": {
                    "messages": [AIMessage(content="normal response")],
                    "structured_response": "structured output",
                }
            },
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is not None
        assert result.response == "structured output"

    @pytest.mark.asyncio
    async def test_skips_tool_messages(self):
        """Test that ToolMessages are not picked as the response."""
        event = {
            "data": {
                "output": {
                    "messages": [
                        HumanMessage(content="Do something"),
                        AIMessage(content="I'll help", name="Agent"),
                        ToolMessage(content="Tool result", tool_call_id="tc-1"),
                        AIMessage(content="", name="Supervisor"),
                    ]
                }
            },
            "parent_ids": [],
        }
        result = await _handle_on_chain_end(event, "thread-1")
        assert result is not None
        assert result.response == "I'll help"


class TestExtractResponseText:
    """Test cases for _extract_response_text helper."""

    def test_structured_response_preferred(self):
        """Test that structured_response takes priority over messages."""
        response = {
            "structured_response": "structured",
            "messages": [AIMessage(content="from messages")],
        }
        assert _extract_response_text(response) == "structured"

    def test_last_non_empty_ai_message(self):
        """Test finding last non-empty AIMessage."""
        response = {
            "messages": [
                AIMessage(content="first"),
                AIMessage(content="second"),
                AIMessage(content=""),
            ]
        }
        assert _extract_response_text(response) == "second"

    def test_all_empty_returns_empty(self):
        """Test returns empty string when all messages are empty."""
        response = {"messages": [AIMessage(content=""), AIMessage(content="")]}
        assert _extract_response_text(response) == ""

    def test_no_messages_returns_empty(self):
        """Test returns empty string when no messages."""
        assert _extract_response_text({}) == ""
        assert _extract_response_text({"messages": []}) == ""
        assert _extract_response_text({"messages": None}) == ""

    def test_normalizes_gemini_content(self):
        """Test Gemini list content is normalized."""
        response = {
            "messages": [
                AIMessage(content=[{"type": "text", "text": "Gemini says hi"}]),
            ]
        }
        assert _extract_response_text(response) == "Gemini says hi"

    def test_skips_non_ai_messages(self):
        """Test that HumanMessage and ToolMessage are skipped."""
        response = {
            "messages": [
                HumanMessage(content="user question"),
                AIMessage(content="answer"),
                ToolMessage(content="tool result", tool_call_id="tc-1"),
            ]
        }
        assert _extract_response_text(response) == "answer"


class TestBuildMsgInput:
    """Test cases for build_msg_input function."""

    def test_plain_text(self):
        """Test basic text message."""
        result = build_msg_input("Hello")
        assert len(result["messages"]) == 1
        assert isinstance(result["messages"][0], HumanMessage)
        assert result["messages"][0].content == "Hello"

    def test_with_image_payloads(self):
        """Test message with image attachments."""
        images = [{"type": "image_url", "image_url": {"url": "data:image/png;base64,abc"}}]
        result = build_msg_input("Describe this", images)
        msg = result["messages"][0]
        assert isinstance(msg.content, list)
        assert msg.content[0] == {"type": "text", "text": "Describe this"}
        assert msg.content[1] == images[0]

    def test_with_other_files(self):
        """Test message with non-image file attachments."""
        files = ["/path/to/file.txt"]
        result = build_msg_input("Analyze", other_file_paths=files)
        msg = result["messages"][0]
        assert isinstance(msg.content, list)
        assert "Attached files:" in msg.content[1]["text"]


class TestFullfillQuestion:
    """Test cases for fullfill_question function."""

    def test_no_local_files(self):
        """Test question without local files."""
        question = MagicMock()
        question.question = "What is Python?"
        result = fullfill_question(None, question)
        assert result == "What is Python?"

    def test_with_local_files(self):
        """Test question with local file IDs."""
        question = MagicMock()
        question.question = "Analyze these"
        result = fullfill_question(["/file1.txt", "/file2.py"], question)
        assert "LOCAL FILES:" in result
        assert "/file1.txt" in result
        assert "/file2.py" in result

    def test_empty_local_files(self):
        """Test question with empty local file list."""
        question = MagicMock()
        question.question = "Hello"
        result = fullfill_question([], question)
        assert result == "Hello"


class TestBuildConfig:
    """Test cases for build_config function."""

    def test_with_thread_id(self):
        """Test config with provided thread_id."""
        config = build_config("my-thread")
        assert config["configurable"]["thread_id"] == "my-thread"

    def test_without_thread_id(self):
        """Test config generates UUID when thread_id is None."""
        config = build_config(None)
        assert config["configurable"]["thread_id"] is not None
        assert len(config["configurable"]["thread_id"]) > 0
