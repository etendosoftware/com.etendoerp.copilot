from copilot.core.agent.langgraph_agent import LanggraphAgent
from copilot.core.schemas import GraphQuestionSchema


def test_langgraph_agent():
    langgraph_agent = LanggraphAgent()
    question = GraphQuestionSchema.model_validate({
        "assistants": [
            {
                "name": "SQLExpert",
                "type": "openai-assistant",
                "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v"
            },
            {
                "name": "Ticketgenerator",
                "type": "openai-assistant",
                "assistant_id": "asst_7xpJ0v7UxjzWlhkQyPYbseC6"
            },
            {
                "name": "Emojiswriter",
                "type": "langchain",
                "assistant_id": "FD8485BBE72D4B69BED2626D72114834",
                "tools": [],
                "provider": "openai",
                "model": "gpt-4o",
                "system_prompt": "Eres un experto en hacer redacciones usando emojis. Si recibes un texto, lo redactaras de manera muy amigable y usando muchos emojis\n"
            },
        ],
        "history": [],
        "graph": {
            "stages": [
                {
                    "name": "stage1",
                    "assistants": [
                        "SQLExpert",
                        "Ticketgenerator",
                        "Emojiswriter"
                    ]
                }
            ]
        },
        "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
        "question": "What is the capital of France?",
        "local_file_ids": [],
        "extra_info": {
            "auth": {
                "ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"
            }
        }
    })

    execution = langgraph_agent.execute(question)