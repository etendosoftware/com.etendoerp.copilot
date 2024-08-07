from contextvars import ContextVar

from copilot.core.utils import copilot_debug

request_context: ContextVar[dict] = ContextVar("request_context", default={})


def get_request_context():
    return request_context.get()


class ThreadContext:

    @classmethod
    def identifier_data(cls):
        return ""

    @classmethod
    def set_data(cls, key, value):
        get_request_context()[key] = value

    @classmethod
    def get_data(cls, key):
        """Get a value for a specific key, isolated by thread."""
        data = get_request_context()[key]
        copilot_debug('  data: ' + str(data))
        return data
