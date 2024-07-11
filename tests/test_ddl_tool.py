import pytest
from unittest.mock import MagicMock
from langsmith import unit
from copilot.core.threadcontext import ThreadContext
from tools import DDLTool
from tools.DDLTool import DDLToolInput


@pytest.fixture
def valid_input_params_create_table():
    return {
        "i_mode": "CREATE_TABLE",
        "i_prefix": "TST",
        "i_name": "valid_table_name"
    }

@pytest.fixture
def valid_input_params_add_column():
    return {
        "i_mode": "ADD_COLUMN",
        "i_prefix": "TST",
        "i_name": "valid_table_name",
        "i_column": "valid_column_name",
        "i_column_type": "String",
        "i_can_be_null": True
    }

@pytest.fixture
def thread_context_extra_info(monkeypatch):
    extra_info = {'auth': {'ETENDO_TOKEN': 'test_token'}}
    monkeypatch.setattr(ThreadContext, 'get_data', lambda key: extra_info)
    return extra_info

@pytest.fixture
def mock_requests_post(monkeypatch):
    mock_post = MagicMock()
    monkeypatch.setattr("requests.post", mock_post)
    return mock_post


@unit
def test_create_table_valid(valid_input_params_create_table, mock_requests_post, thread_context_extra_info):
    tool = DDLTool()

    # Configura la respuesta mock exitosa como una cadena JSON válida
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.text = '{"result": "table_created"}'
    mock_requests_post.return_value = mock_response

    result = tool.run(valid_input_params_create_table)

    assert "error" not in result, "Should not return an error for valid inputs in CREATE_TABLE mode."
    assert result == {"result": "table_created"}

@unit
def test_add_column_valid(valid_input_params_add_column, mock_requests_post, thread_context_extra_info):
    tool = DDLTool()

    # Configura la respuesta mock exitosa como una cadena JSON válida
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.text = '{"result": "column_added"}'
    mock_requests_post.return_value = mock_response

    result = tool.run(valid_input_params_add_column)

    assert "error" not in result, "Should not return an error for valid inputs in ADD_COLUMN mode."
    assert result == {"result": "column_added"}

@unit
def test_missing_token(valid_input_params_create_table, monkeypatch):
    # Limpia el contexto explícitamente para esta prueba
    monkeypatch.setattr(ThreadContext, 'get_data', lambda key: {})

    tool = DDLTool()
    response = tool.run(valid_input_params_create_table)

    assert "error" in response
    expected_error_message = "No access token provided, to work with Etendo, an access token is required." \
                             "Make sure that the Webservices are enabled to the user role and the WS are configured for" \
                             " the Entity."
    assert response["error"] == expected_error_message

@unit
@pytest.mark.parametrize(
    "input_params, expected_response",
    [
        ({"i_mode": "CREATE_TABLE", "i_prefix": "TST", "i_name": "valid_table_name"}, {"result": "table_created"}),
        ({"i_mode": "ADD_COLUMN", "i_prefix": "TST", "i_name": "valid_table_name", "i_column": "valid_column_name", 
          "i_column_type": "String", "i_can_be_null": True}, {"result": "column_added"})
    ],
)
def test_ddltool_parametrized(mock_requests_post, input_params, expected_response, thread_context_extra_info):
    tool = DDLTool()

    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.text = '{"result": "dummy_result"}'
    if input_params["i_mode"] == "CREATE_TABLE":
        mock_response.text = '{"result": "table_created"}'
    elif input_params["i_mode"] == "ADD_COLUMN":
        mock_response.text = '{"result": "column_added"}'

    mock_requests_post.return_value = mock_response

    result = tool.run(input_params)

    assert "error" not in result
    assert result == expected_response
