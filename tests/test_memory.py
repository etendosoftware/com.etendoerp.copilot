from langchain_core.messages import HumanMessage, AIMessage

from copilot.core.memory.memory_handler import MemoryHandler
from copilot.core.schemas import MessageSchema


def test_get_memory_with_no_history_and_no_question():
    memory_handler = MemoryHandler()
    result = memory_handler.get_memory(None, None)
    assert result == []


def test_get_memory_with_empty_history_and_no_question():
    memory_handler = MemoryHandler()
    result = memory_handler.get_memory([], None)
    assert result == []


def test_get_memory_with_history_and_no_question():
    history = [
        MessageSchema.model_validate({"role": "USER", "content": "Hello"}),
        MessageSchema.model_validate({"role": "ASSISTANT", "content": "Hi"})
    ]
    memory_handler = MemoryHandler()
    result = memory_handler.get_memory(history, None)
    assert len(result) == 2
    assert isinstance(result[0], HumanMessage)
    assert isinstance(result[1], AIMessage)


def test_get_memory_with_no_history_and_question():
    memory_handler = MemoryHandler()
    result = memory_handler.get_memory(None, "How are you?")
    assert len(result) == 1
    assert isinstance(result[0], HumanMessage)


def test_get_memory_with_history_and_question():
    history = [
        MessageSchema.model_validate({"role": "USER", "content": "Hello"}),
        MessageSchema.model_validate({"role": "ASSISTANT", "content": "Hi"})
    ]
    memory_handler = MemoryHandler()
    result = memory_handler.get_memory(history, "How are you?")
    assert len(result) == 3
    assert isinstance(result[0], HumanMessage)
    assert isinstance(result[1], AIMessage)
    assert isinstance(result[2], HumanMessage)
