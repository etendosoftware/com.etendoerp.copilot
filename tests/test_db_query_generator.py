from unittest.mock import MagicMock
import pytest
from langsmith import unit
from copilot.core.threadcontext import ThreadContext
from tools import DBQueryGenerator
from tools.DBQueryGenerator import DBEtendoToolInput  # Ajusta la importación según sea necesario

@pytest.fixture
def valid_input_params_show_tables():
    return {
        "p_mode": "SHOW_TABLES",
        "p_data": ""
    }

@pytest.fixture
def valid_input_params_show_columns():
    return {
        "p_mode": "SHOW_COLUMNS",
        "p_data": "test_table"
    }

@pytest.fixture
def valid_input_params_execute_query():
    return {
        "p_mode": "EXECUTE_QUERY",
        "p_data": "SELECT * FROM test_table"
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
def test_show_tables_valid(valid_input_params_show_tables, mock_requests_post, thread_context_extra_info):
    tool = DBQueryGenerator()

    # Configura el mock de la respuesta exitosa como una cadena JSON válida
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.text = '{"result": "dummy_tables"}'
    mock_requests_post.return_value = mock_response

    result = tool.run(valid_input_params_show_tables)

    assert "error" not in result, "Should not return an error for valid inputs in SHOW_TABLES mode."
    assert result == {"result": "dummy_tables"}

@unit
def test_show_columns_valid(valid_input_params_show_columns, mock_requests_post, thread_context_extra_info):
    tool = DBQueryGenerator()

    # Configura el mock de la respuesta exitosa como una cadena JSON válida
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.text = '{"result": "dummy_columns"}'
    mock_requests_post.return_value = mock_response

    result = tool.run(valid_input_params_show_columns)

    assert "error" not in result, "Should not return an error for valid inputs in SHOW_COLUMNS mode."
    assert result == {"result": "dummy_columns"}

@unit
def test_execute_query_valid(valid_input_params_execute_query, mock_requests_post, thread_context_extra_info):
    tool = DBQueryGenerator()

    # Configura el mock de la respuesta exitosa como una cadena JSON válida
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.text = '{"result": "dummy_query_result"}'
    mock_requests_post.return_value = mock_response

    result = tool.run(valid_input_params_execute_query)

    assert "error" not in result, "Should not return an error for valid inputs in EXECUTE_QUERY mode."
    assert result == {"result": "dummy_query_result"}

@unit
@pytest.mark.parametrize(
    "input_params, expected_response",
    [
        ({"p_mode": "SHOW_TABLES", "p_data": ""}, {"result": "dummy_tables"}),
        ({"p_mode": "SHOW_COLUMNS", "p_data": "test_table"}, {"result": "dummy_columns"}),
        ({"p_mode": "EXECUTE_QUERY", "p_data": "SELECT * FROM test_table"}, {"result": "dummy_query_result"})
    ],
)
def test_dbquerygenerator_parametrized(mock_requests_post, input_params, expected_response, thread_context_extra_info):
    tool = DBQueryGenerator()

    mock_response = MagicMock()
    mock_response.ok = True
    # Configura el mock de la respuesta exitosa como una cadena JSON válida
    mock_response.text = '{"result": "dummy_result"}'
    mock_requests_post.return_value = mock_response

    if input_params["p_mode"] == "SHOW_TABLES":
        mock_response.text = '{"result": "dummy_tables"}'
    elif input_params["p_mode"] == "SHOW_COLUMNS":
        mock_response.text = '{"result": "dummy_columns"}'
    elif input_params["p_mode"] == "EXECUTE_QUERY":
        mock_response.text = '{"result": "dummy_query_result"}'

    result = tool.run(input_params)

    assert "error" not in result
    assert result == expected_response
