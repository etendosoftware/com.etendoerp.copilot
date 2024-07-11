import pytest
from langsmith import unit
from unittest.mock import MagicMock
from tools import CreateReferencesTool  # Ajusta la importación según sea necesario
from copilot.core.threadcontext import ThreadContext

# Fixture para la respuesta mock exitosa
def mock_response_success():
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.json.return_value = {"result": "success"}
    return mock_response

# Fixture para el mock de requests.post
@pytest.fixture
def mock_requests_post(monkeypatch):
    mock_post = MagicMock()
    monkeypatch.setattr("requests.post", mock_post)
    return mock_post

# Fixture para los parámetros de entrada válidos
@pytest.fixture
def valid_input_params():
    return {
        "i_prefix": "MOD",
        "i_name": "Test Reference",
        "i_reference_list": "item1,item2,item3",
        "i_help": "Help text example",
        "i_description": "Description example"
    }

# Fixture para simular el contexto de hilo con token de acceso
@pytest.fixture
def thread_context_extra_info(monkeypatch):
    extra_info = {'auth': {'ETENDO_TOKEN': 'test_token'}}
    monkeypatch.setattr(ThreadContext, 'get_data', lambda key: extra_info)
    return extra_info

# Prueba para entradas válidas
@unit
def test_create_references_valid(valid_input_params, mock_requests_post, thread_context_extra_info):
    tool = CreateReferencesTool()
    
    # Configura el mock de la respuesta exitosa
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.json.return_value = {"result": "success"}
    mock_requests_post.return_value = mock_response

    # Configura el contexto del hilo antes de ejecutar la prueba
    ThreadContext.set_data('extra_info', thread_context_extra_info)

    result = tool.run(valid_input_params)

    assert "error" not in result, "Should not return an error for valid inputs."
    assert result == {"result": "success"}

# Prueba para manejo de token ausente
@unit
def test_create_references_tool_no_token(valid_input_params, monkeypatch):
    # Limpia el contexto explícitamente para esta prueba
    monkeypatch.setattr(ThreadContext, 'get_data', lambda key: {})

    tool = CreateReferencesTool()
    response = tool.run(valid_input_params)

    assert "error" in response
    expected_error_message = "No access token provided. To work with Etendo, an access token is required.Make sure that the Webservices are enabled for the user role and the WS are configured for the Entity."
    assert response["error"] == expected_error_message

# Prueba parametrizada para diferentes respuestas esperadas
@unit
@pytest.mark.parametrize(
    "expected_response, mock_response",
    [
        ({"result": "success"}, pytest.lazy_fixture('mock_response_success')),
        ({"error": "No access token provided. To work with Etendo, an access token is required.Make sure that the Webservices are enabled for the user role and the WS are configured for the Entity."}, None)
    ],
)
def test_create_references_tool(mock_requests_post, valid_input_params, thread_context_extra_info, expected_response, mock_response, monkeypatch):
    if mock_response:
        mock_requests_post.return_value = mock_response
        ThreadContext.set_data('extra_info', thread_context_extra_info)
    else:
        # Limpia el contexto del hilo si no hay mock de respuesta
        monkeypatch.setattr(ThreadContext, 'get_data', lambda key: {})

    tool = CreateReferencesTool()
    response = tool.run(valid_input_params)
    
    # Convert the response to dict if it's a MagicMock to simulate the actual behavior
    if isinstance(response, MagicMock):
        response = response()

    assert response == expected_response
