class ApplicationError(RuntimeError):
    message = "There was an unexpected error, if this error persist contact support."

    def __init__(self, msg: str = None):
        self._message = msg or self.message

    def __str__(self) -> str:
        return self._message


class OpenAIApiKeyNotFound(ApplicationError):
    message = "The OpenAPI api-key is not found as environment variable"
