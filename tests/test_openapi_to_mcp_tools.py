"""
Tests for OpenAPI tool generation AND MCP conversion pipeline.

Validates that tools generated from OpenAPI specs:
1. Have correct args_schema with proper parameters (for agent use)
2. Expose those parameters correctly when converted to MCP format (for MCP clients)

Uses real Etendo Headless OpenAPI spec from tests/resources/openapi1.json.
"""

import json
import os
from typing import Any, Dict
from unittest.mock import patch

import pytest

# ---------------------------------------------------------------------------
# Load the OpenAPI spec fixture
# ---------------------------------------------------------------------------

SPEC_PATH = os.path.join(os.path.dirname(__file__), "resources", "openapi1.json")


@pytest.fixture(scope="module")
def openapi_spec() -> Dict[str, Any]:
    with open(SPEC_PATH) as f:
        return json.load(f)


# ---------------------------------------------------------------------------
# Helper: generate LangChain tools from the spec
# ---------------------------------------------------------------------------


def _generate_tools(spec):
    from copilot.core.toolgen.openapi_tool_gen import generate_tools_from_openapi

    with patch(
        "copilot.core.toolgen.openapi_tool_gen.get_etendo_host",
        return_value="http://localhost:8080/etendo",
    ), patch("copilot.core.toolgen.openapi_tool_gen.do_request"):
        return generate_tools_from_openapi(spec)


def _find_tools(tools, method: str, entity: str):
    """Find tools by HTTP method and entity name substring (case-insensitive)."""
    return [t for t in tools if method.upper() in t.name.upper() and entity.lower() in t.name.lower()]


# ===========================================================================
# PART 1 — Tool generation from OpenAPI (agent use)
# ===========================================================================


class TestToolGeneration:
    """Tools generated from the spec have correct args_schema."""

    def test_all_endpoints_produce_tools(self, openapi_spec):
        """Every operation in the spec should produce a tool."""
        tools = _generate_tools(openapi_spec)
        names = [t.name for t in tools]
        # 12 operations in the spec (GET/POST/GET{id}/PUT for Webhook & WebhookParam,
        # GET for ModuleHeader, GET for ModuleDBPrefix, POST /sws/login,
        # GET ModuleHeader{id})
        assert len(tools) == 12, f"Expected 12 tools, got {len(tools)}: {names}"

    def test_get_list_has_query_params(self, openapi_spec):
        """GET list endpoints (Webhook, WebhookParam, etc.) have q, startRow, endRow."""
        tools = _generate_tools(openapi_spec)
        get_webhook_list = [
            t
            for t in _find_tools(tools, "GET", "webhook")
            if "param" not in t.name.lower() and "id" not in getattr(t.args_schema, "model_fields", {})
        ]
        assert len(get_webhook_list) >= 1, "No GET Webhook list tool found"
        fields = get_webhook_list[0].args_schema.model_fields
        assert "q" in fields, f"Missing 'q'. Fields: {list(fields.keys())}"
        assert "startRow" in fields, f"Missing 'startRow'. Fields: {list(fields.keys())}"
        assert "endRow" in fields, f"Missing 'endRow'. Fields: {list(fields.keys())}"

    def test_get_by_id_has_path_param(self, openapi_spec):
        """GET /{id} endpoints have an 'id' path parameter."""
        tools = _generate_tools(openapi_spec)
        get_webhook_id = [
            t
            for t in _find_tools(tools, "GET", "webhook")
            if "param" not in t.name.lower() and "id" in getattr(t.args_schema, "model_fields", {})
        ]
        assert len(get_webhook_id) >= 1, "No GET Webhook/{id} tool found"

    def test_put_has_id_and_body(self, openapi_spec):
        """PUT endpoints have 'id' and 'body' params."""
        tools = _generate_tools(openapi_spec)
        put_tools = _find_tools(tools, "PUT", "webhook")
        assert len(put_tools) >= 1, f"No PUT Webhook tool. All: {[t.name for t in tools]}"
        fields = put_tools[0].args_schema.model_fields
        assert "id" in fields, f"PUT missing 'id'. Fields: {list(fields.keys())}"
        assert "body" in fields, f"PUT missing 'body'. Fields: {list(fields.keys())}"

    def test_post_webhook_has_body(self, openapi_spec):
        """POST Webhook (oneOf schema) should have a 'body' parameter."""
        tools = _generate_tools(openapi_spec)
        post_tools = [t for t in _find_tools(tools, "POST", "webhook") if "param" not in t.name.lower()]
        assert len(post_tools) >= 1, f"No POST Webhook tool. All: {[t.name for t in tools]}"
        fields = post_tools[0].args_schema.model_fields
        assert "body" in fields, (
            f"POST Webhook has no 'body'. Fields: {list(fields.keys())}. "
            f"The oneOf schema was not processed."
        )

    def test_post_webhookparam_has_body(self, openapi_spec):
        """POST WebhookParam (oneOf schema) should have a 'body' parameter."""
        tools = _generate_tools(openapi_spec)
        post_tools = _find_tools(tools, "POST", "webhookparam")
        assert len(post_tools) >= 1
        fields = post_tools[0].args_schema.model_fields
        assert "body" in fields, (
            f"POST WebhookParam has no 'body'. Fields: {list(fields.keys())}. "
            f"The oneOf schema was not processed."
        )

    def test_post_webhook_body_fields(self, openapi_spec):
        """POST Webhook body model should contain the entity fields."""
        tools = _generate_tools(openapi_spec)
        post_tools = [t for t in _find_tools(tools, "POST", "webhook") if "param" not in t.name.lower()]
        fields = post_tools[0].args_schema.model_fields
        if "body" not in fields:
            pytest.skip("body not present (oneOf not handled yet)")
        body_type = fields["body"].annotation
        if hasattr(body_type, "model_fields"):
            body_fields = set(body_type.model_fields.keys())
            expected = {"module", "javaClass", "name", "description", "allowGroupAccess"}
            missing = expected - body_fields
            assert not missing, f"Body model missing: {missing}. Has: {body_fields}"

    def test_post_login_has_body(self, openapi_spec):
        """POST /sws/login (standard object schema) should have a 'body' with username, password."""
        tools = _generate_tools(openapi_spec)
        login_tools = _find_tools(tools, "POST", "login")
        assert len(login_tools) >= 1, f"No POST login tool. All: {[t.name for t in tools]}"
        fields = login_tools[0].args_schema.model_fields
        assert "body" in fields, f"POST login missing 'body'. Fields: {list(fields.keys())}"

    def test_all_schemas_have_properties(self, openapi_spec):
        """Every tool's JSON schema should have non-empty properties."""
        tools = _generate_tools(openapi_spec)
        for tool in tools:
            schema = tool.args_schema.model_json_schema()
            props = schema.get("properties", {})
            assert len(props) > 0, f"Tool {tool.name} has empty properties: {json.dumps(schema, indent=2)}"

    def test_etendo_classic_no_token_param(self, openapi_spec):
        """Etendo Classic endpoint tools should NOT have a 'token' parameter."""
        tools = _generate_tools(openapi_spec)
        for tool in tools:
            fields = getattr(tool.args_schema, "model_fields", {})
            assert (
                "token" not in fields
            ), f"Tool {tool.name} should not have 'token' for Etendo Classic endpoint"


# ===========================================================================
# PART 2 — MCP conversion (for MCP clients)
# ===========================================================================


class TestMCPConversion:
    """Tools converted to MCP format retain correct parameter schemas."""

    def test_mcp_tools_have_non_empty_properties(self, openapi_spec):
        """Every MCP tool should have non-empty properties."""
        from copilot.core.mcp.tools.agent_tools import _convert_single_tool_to_mcp

        tools = _generate_tools(openapi_spec)
        failures = []
        for tool in tools:
            mcp_tool = _convert_single_tool_to_mcp(tool)
            props = mcp_tool.parameters.get("properties", {})
            if not props:
                failures.append(f"  - {mcp_tool.name}: parameters.properties is empty")
        assert not failures, "MCP tools with empty properties:\n" + "\n".join(failures)

    def test_mcp_parameters_match_langchain_schema(self, openapi_spec):
        """MCP tool properties should match the original args_schema properties."""
        from copilot.core.mcp.tools.agent_tools import _convert_single_tool_to_mcp

        tools = _generate_tools(openapi_spec)
        for tool in tools:
            original_props = set(tool.args_schema.model_json_schema().get("properties", {}).keys())
            mcp_tool = _convert_single_tool_to_mcp(tool)
            mcp_props = set(mcp_tool.parameters.get("properties", {}).keys())
            assert (
                original_props == mcp_props
            ), f"Tool '{tool.name}': Original {original_props} != MCP {mcp_props}"

    def test_to_mcp_tool_input_schema_not_empty(self, openapi_spec):
        """MCPTool.inputSchema (sent to clients) should have non-empty properties."""
        from copilot.core.mcp.tools.agent_tools import _convert_single_tool_to_mcp

        tools = _generate_tools(openapi_spec)
        failures = []
        for tool in tools:
            mcp_tool = _convert_single_tool_to_mcp(tool)
            protocol_tool = mcp_tool.to_mcp_tool()
            props = protocol_tool.inputSchema.get("properties", {})
            if not props:
                failures.append(f"  - {protocol_tool.name}: inputSchema.properties is empty")
        assert not failures, "MCPTool inputSchema empty (invisible params to MCP clients):\n" + "\n".join(
            failures
        )

    def test_post_webhook_mcp_has_body(self, openapi_spec):
        """POST Webhook MCP tool should expose 'body' in its parameters."""
        from copilot.core.mcp.tools.agent_tools import _convert_single_tool_to_mcp

        tools = _generate_tools(openapi_spec)
        post_tools = [t for t in _find_tools(tools, "POST", "webhook") if "param" not in t.name.lower()]
        assert len(post_tools) >= 1
        mcp_tool = _convert_single_tool_to_mcp(post_tools[0])
        props = mcp_tool.parameters.get("properties", {})
        assert "body" in props, f"MCP POST Webhook has no 'body'. Props: {list(props.keys())}"

    def test_get_webhook_mcp_has_query_params(self, openapi_spec):
        """GET Webhook list MCP tool should expose q, startRow, endRow."""
        from copilot.core.mcp.tools.agent_tools import _convert_single_tool_to_mcp

        tools = _generate_tools(openapi_spec)
        list_tools = [
            t
            for t in _find_tools(tools, "GET", "webhook")
            if "param" not in t.name.lower() and "id" not in getattr(t.args_schema, "model_fields", {})
        ]
        assert len(list_tools) >= 1
        mcp_tool = _convert_single_tool_to_mcp(list_tools[0])
        props = mcp_tool.parameters.get("properties", {})
        for expected in ["q", "startRow", "endRow"]:
            assert expected in props, f"Missing '{expected}'. Props: {list(props.keys())}"

    def test_batch_conversion_preserves_all(self, openapi_spec):
        """Batch conversion should keep all tools with non-empty schemas."""
        from copilot.core.mcp.tools.agent_tools import convert_langchain_tools_to_mcp

        tools = _generate_tools(openapi_spec)
        mcp_tools = convert_langchain_tools_to_mcp(tools)
        assert len(mcp_tools) == len(tools)
        for mcp_tool in mcp_tools:
            props = mcp_tool.parameters.get("properties", {})
            assert len(props) > 0, f"MCP tool '{mcp_tool.name}' has empty properties"


# ===========================================================================
# PART 3 — End-to-end: spec → LangChain → MCP → inputSchema
# ===========================================================================


class TestEndToEnd:
    """Full pipeline validation."""

    def test_all_tools_have_input_schema(self, openapi_spec):
        """Every tool should have non-empty inputSchema at protocol level."""
        from copilot.core.mcp.tools.agent_tools import convert_langchain_tools_to_mcp

        tools = _generate_tools(openapi_spec)
        mcp_tools = convert_langchain_tools_to_mcp(tools)
        failures = []
        for mcp_tool in mcp_tools:
            protocol_tool = mcp_tool.to_mcp_tool()
            props = protocol_tool.inputSchema.get("properties", {})
            if not props:
                failures.append(f"  - {protocol_tool.name}")
        assert not failures, "MCP tools with empty inputSchema:\n" + "\n".join(failures)

    def test_params_match_across_layers(self, openapi_spec):
        """LangChain fields == MCP properties for every tool."""
        from copilot.core.mcp.tools.agent_tools import convert_langchain_tools_to_mcp

        tools = _generate_tools(openapi_spec)
        mcp_tools = convert_langchain_tools_to_mcp(tools)
        for lc, mcp in zip(tools, mcp_tools, strict=True):
            lc_fields = set(getattr(lc.args_schema, "model_fields", {}).keys())
            mcp_props = set(mcp.parameters.get("properties", {}).keys())
            assert lc_fields == mcp_props, f"Tool '{lc.name}': LangChain {lc_fields} != MCP {mcp_props}"
