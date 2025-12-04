import unittest

from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.schemas import GraphQuestionSchema


class TestOpenAIAssistantDeprecation(unittest.TestCase):
    """
    Tests to validate that the openai-assistant type is no longer supported
    and raises NotImplementedError when attempted to be used.
    """

    def test_openai_assistant_raises_not_implemented(self):
        """
        Test that using an openai-assistant type raises NotImplementedError.
        """
        payload = GraphQuestionSchema.model_validate(
            {
                "assistants": [
                    {
                        "name": "DeprecatedAssistant",
                        "type": "openai-assistant",
                        "assistant_id": "asst_test_deprecated_id",
                    }
                ],
                "history": [],
                "graph": {"stages": [{"name": "stage1", "assistants": ["DeprecatedAssistant"]}]},
                "conversation_id": "test-conversation-id",
                "question": "Test question",
                "local_file_ids": [],
                "extra_info": {"auth": {"ETENDO_TOKEN": "test_token"}},
            }
        )

        with self.assertRaises(NotImplementedError) as context:
            MembersUtil().get_members(payload)

        self.assertIn("OpenAI Assistant type is not longer supported", str(context.exception))

    def test_multiple_assistants_with_one_openai_assistant(self):
        """
        Test that having multiple assistants where one is openai-assistant
        still raises NotImplementedError.
        """
        payload = GraphQuestionSchema.model_validate(
            {
                "assistants": [
                    {
                        "name": "ValidAssistant",
                        "type": "langchain",
                        "assistant_id": "valid_assistant_id",
                        "tools": [],
                        "provider": "openai",
                        "model": "gpt-4o",
                        "system_prompt": "You are a helpful assistant.",
                    },
                    {
                        "name": "DeprecatedAssistant",
                        "type": "openai-assistant",
                        "assistant_id": "asst_deprecated_id",
                    },
                ],
                "history": [],
                "graph": {
                    "stages": [
                        {
                            "name": "stage1",
                            "assistants": ["ValidAssistant", "DeprecatedAssistant"],
                        }
                    ]
                },
                "conversation_id": "test-conversation-id",
                "question": "Test question",
                "local_file_ids": [],
                "extra_info": {"auth": {"ETENDO_TOKEN": "test_token"}},
            }
        )

        with self.assertRaises(NotImplementedError) as context:
            MembersUtil().get_members(payload)

        self.assertIn("OpenAI Assistant type is not longer supported", str(context.exception))

    def test_valid_assistant_types_work(self):
        """
        Test that valid assistant types (langchain, multimodel-assistant) work correctly
        without raising NotImplementedError.
        """
        payload = GraphQuestionSchema.model_validate(
            {
                "assistants": [
                    {
                        "name": "LangchainAssistant",
                        "type": "langchain",
                        "assistant_id": "langchain_id",
                        "tools": [],
                        "provider": "openai",
                        "model": "gpt-4o",
                        "system_prompt": "You are a helpful assistant.",
                    },
                    {
                        "name": "MultimodelAssistant",
                        "type": "multimodel-assistant",
                        "assistant_id": "multimodel_id",
                        "provider": "openai",
                        "model": "gpt-4o",
                    },
                ],
                "history": [],
                "graph": {
                    "stages": [
                        {
                            "name": "stage1",
                            "assistants": ["LangchainAssistant", "MultimodelAssistant"],
                        }
                    ]
                },
                "conversation_id": "test-conversation-id",
                "question": "Test question",
                "local_file_ids": [],
                "extra_info": {"auth": {"ETENDO_TOKEN": "test_token"}},
            }
        )

        # This should not raise any exception
        try:
            members = MembersUtil().get_members(payload)
            self.assertEqual(len(members), 2)
        except NotImplementedError:
            self.fail("Valid assistant types should not raise NotImplementedError")


if __name__ == "__main__":
    unittest.main()
