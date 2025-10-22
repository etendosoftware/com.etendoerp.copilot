from contextvars import ContextVar
from typing import Dict

# Thread-safe context variable to store request-specific data
_request_context: ContextVar[dict] = ContextVar("request_context", default={})

# Store conversation contexts in a thread-safe manner
_conversation_contexts: Dict[str, dict] = {}


def request_context():
    """
    Returns the current request context.

    This function retrieves the current request context, which is a dictionary
    that can be used to store and retrieve data specific to the current request.
    """
    return _request_context


class ThreadContext:
    """
    ThreadContext provides class methods to manage per-thread conversation contexts.

    Class Methods:
        load_conversation(conversation_id: str):
            Loads the conversation context associated with the given conversation_id
            into the current thread's request context.

        save_conversation():
            Saves the current thread's request context into the global conversation
            contexts using the conversation_id from the current context.

        get_data(key, default=None):
            Retrieves a value from the current thread's request context by key.
            Returns the specified default if the key is not present.

        set_data(key, value):
            Sets a value in the current thread's request context for the given key.
    """

    @classmethod
    def load_conversation(cls, conversation_id: str):
        context = _conversation_contexts.get(conversation_id, {})
        _request_context.set(context.copy())

    @classmethod
    def save_conversation(cls):
        conversation_id = _request_context.get().get("conversation_id")
        _conversation_contexts[conversation_id] = _request_context.get().copy()

    @classmethod
    def get_data(cls, key, default=None):
        return _request_context.get().get(key, default)

    @classmethod
    def set_data(cls, key, value):
        ctx = _request_context.get()
        ctx[key] = value

    @classmethod
    def has_data(cls, key):
        """Check if a specific key exists in the context."""
        return key in _request_context.get()
