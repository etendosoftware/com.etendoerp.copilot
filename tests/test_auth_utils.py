from copilot.core.mcp import auth_utils


class DummyHeaders(dict):
    def get(self, key, default=None):
        # case-insensitive header access simulation
        for k, v in self.items():
            if k.lower() == key.lower():
                return v
        return default


class DummyRequest:
    def __init__(self, headers):
        self.headers = DummyHeaders(headers)


def test_extract_etendo_token_from_request_authorization_bearer():
    req = DummyRequest({"authorization": "Bearer abc123"})
    token = auth_utils.extract_etendo_token_from_request(req)
    assert token is not None
    assert token.lower().startswith("bearer")


def test_extract_etendo_token_from_request_etendo_token_header():
    req = DummyRequest({"etendo-token": "abc-token-value"})
    token = auth_utils.extract_etendo_token_from_request(req)
    assert token is not None
    assert "abc-token-value" in token


def test_extract_etendo_token_from_request_x_etendo_token():
    req = DummyRequest({"X-Etendo-Token": "somevalue"})
    token = auth_utils.extract_etendo_token_from_request(req)
    assert token is not None
    assert "somevalue" in token


def test_extract_etendo_token_from_request_none():
    req = DummyRequest({})
    token = auth_utils.extract_etendo_token_from_request(req)
    assert token is None
