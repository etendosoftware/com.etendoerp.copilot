import threading

from copilot.core.utils import copilot_debug


class ThreadContext:
    _thread_data = threading.local()

    @classmethod
    def identifier_data(cls):
        return id(cls._thread_data)

    @classmethod
    def set_data(cls, key, value):
        """Set a value for a specific key, isolated by thread."""
        if not hasattr(cls._thread_data, 'data'):
            cls._thread_data.data = {}
        copilot_debug('  old data: ' + str(cls._thread_data.data) + 'new data: ' + str(value))
        cls._thread_data.data[key] = value

    @classmethod
    def get_data(cls, key):
        """Get a value for a specific key, isolated by thread."""
        copilot_debug('  data: ' + str(cls._thread_data.data.get(key, None)))
        return cls._thread_data.data.get(key, None)
