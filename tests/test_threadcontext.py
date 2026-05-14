from copilot.core.threadcontext import ThreadContext, _conversation_contexts, request_context


def setup_function():
    request_context().set({})
    _conversation_contexts.clear()


def test_set_get_and_has_data_in_request_context():
    assert ThreadContext.get_data("missing", "fallback") == "fallback"
    assert ThreadContext.has_data("answer") is False

    ThreadContext.set_data("answer", 42)

    assert ThreadContext.has_data("answer") is True
    assert ThreadContext.get_data("answer") == 42


def test_save_and_load_conversation_copies_context_data():
    ThreadContext.set_data("conversation_id", "conversation-1")
    ThreadContext.set_data("user", "etendo")

    ThreadContext.save_conversation()

    # Mutating the current context after saving must not change the stored conversation.
    ThreadContext.set_data("user", "modified")
    ThreadContext.load_conversation("conversation-1")

    assert ThreadContext.get_data("conversation_id") == "conversation-1"
    assert ThreadContext.get_data("user") == "etendo"


def test_load_unknown_conversation_resets_to_empty_context():
    ThreadContext.set_data("conversation_id", "conversation-1")
    ThreadContext.set_data("value", "stored")
    ThreadContext.save_conversation()

    ThreadContext.load_conversation("unknown-conversation")

    assert ThreadContext.get_data("conversation_id") is None
    assert ThreadContext.get_data("value") is None


def test_loaded_context_is_independent_from_stored_conversation():
    ThreadContext.set_data("conversation_id", "conversation-1")
    ThreadContext.set_data("counter", 1)
    ThreadContext.save_conversation()

    ThreadContext.load_conversation("conversation-1")
    ThreadContext.set_data("counter", 2)

    ThreadContext.load_conversation("conversation-1")
    assert ThreadContext.get_data("counter") == 1
