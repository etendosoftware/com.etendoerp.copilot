import unittest

from copilot.core.agent.multimodel_agent import (
    convert_mcp_servers_config,
    get_default_user_agent,
)


class TestMCPConfig(unittest.TestCase):
    def test_convert_mcp_servers_config_headers(self):
        # Mock mcp_servers_list as it would come from QuestionSchema
        mcp_servers_list = [
            {
                "name": "algolia",
                "url": "https://mcp.us.algolia.com/1/mcp/",
                "type": "streamable-http",
                "headers": {"Custom-Header": "value"},
            }
        ]

        config = convert_mcp_servers_config(mcp_servers_list)

        self.assertIn("algolia", config)
        algolia_config = config["algolia"]
        self.assertEqual(algolia_config["transport"], "streamable-http")

        headers = algolia_config["headers"]
        self.assertEqual(headers["Custom-Header"], "value")
        self.assertIn("User-Agent", headers)
        self.assertIn("Accept", headers)
        self.assertIn("Content-Type", headers)
        self.assertEqual(headers["Content-Type"], "application/json")
        self.assertIn("text/event-stream", headers["Accept"])

        # Check that it uses a credible User-Agent
        ua = headers["User-Agent"]
        self.assertNotIn("python-httpx", ua.lower())
        self.assertIn("Mozilla/5.0", ua)

    def test_convert_mcp_servers_config_ua_override(self):
        # Test that it overrides a generic User-Agent
        mcp_servers_list = [
            {
                "name": "test",
                "url": "http://localhost/mcp",
                "transport": "sse",
                "headers": {"User-Agent": "python-httpx/0.27.0"},
            }
        ]

        config = convert_mcp_servers_config(mcp_servers_list)
        ua = config["test"]["headers"]["User-Agent"]
        self.assertNotEqual(ua, "python-httpx/0.27.0")
        self.assertIn("Mozilla/5.0", ua)

    def test_get_default_user_agent(self):
        ua = get_default_user_agent()
        self.assertIsInstance(ua, str)
        self.assertTrue(len(ua) > 20)
        self.assertIn("Mozilla/5.0", ua)


if __name__ == "__main__":
    unittest.main()
