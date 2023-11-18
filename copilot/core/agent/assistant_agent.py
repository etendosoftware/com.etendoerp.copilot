import os
import json

from openai.types.beta.assistant import Assistant
from langchain.tools.render import format_tool_to_openai_function

from time import sleep
from typing import Final

from .agent import AgentResponse, AssistantResponse, CopilotAgent
from ..schemas import QuestionSchema


class AssistantAgent(CopilotAgent):
    """OpenAI Assistant Agent implementation."""
    OPENAI_MODEL: Final[str] = os.getenv("OPENAI_MODEL", "gpt-4-1106-preview")
    ASSISTANT_NAME: Final[str] = "Copilot [LOCAL]"
    OPENAI_VERSION: Final[str] = "1.2.4"

    def __init__(self):
        # https://platform.openai.com/docs/assistants/overview/agents
        super().__init__()
        self._assert_openai_is_installed(version=self.OPENAI_VERSION)
        self._client = None
        self._formated_tools_openai = None
        self._assistant: Assistant = self._get_openai_assistant()

    def _get_openai_assistant(self) -> Assistant:
        """Creates an assistant. An Assistant represents an entity that can be
        configured to respond to users Messages."""
        self._assert_open_api_key_is_set()
        self._assert_system_prompt_is_set()

        # import delayed to avoid issue when langchain is configured under v0.28.0
        from openai import OpenAI
        self._client = OpenAI(api_key=self.OPENAI_API_KEY)

        # Convert configured tools into a format compatible with OpenAI functions.
        tools = [format_tool_to_openai_function(tool) for tool in self._configured_tools]
        self._formated_tools_openai = [{"type": "function", "function": tool} for tool in tools]
        assistant = self._client.beta.assistants.create(
            name=self.ASSISTANT_NAME,
            instructions=self.SYSTEM_PROMPT,
            tools=self._formated_tools_openai,
            model=self.OPENAI_MODEL
        )
        return assistant

    def _update_assistant(self, assistant_id: int):
        self._assistant = self._client.beta.assistants.update(
            assistant_id,
            name=self.ASSISTANT_NAME,
            instructions=self.SYSTEM_PROMPT,
            tools=self._formated_tools_openai,
            model=self.OPENAI_MODEL,
        )

    def execute(self, question: QuestionSchema) -> AgentResponse:
        # If no conversation_id is provided, create a new conversation thread.
        thread_id = question.conversation_id
        if not thread_id:
            thread_id = self._client.beta.threads.create().id

        # Create a message in the conversation thread with the user's question.
        message = self._client.beta.threads.messages.create(
            thread_id=thread_id,
            role="user",
            content=question.question
        )

        # Start processing the conversation thread with the assistant.
        run = self._client.beta.threads.runs.create(
            thread_id=thread_id,
            assistant_id=question.assistant_id
        )

        # Retrieve the current status of the processing run.
        run = self._client.beta.threads.runs.retrieve(thread_id=thread_id, run_id=run.id)

        # Wait for the run to complete.
        while run.status == "in_progress":
            run = self._client.beta.threads.runs.retrieve(thread_id=thread_id, run_id=run.id)
            sleep(0.05)

        # If the run requires action, process the required tool outputs.
        if run.required_action:
            for tool_call in run.required_action.submit_tool_outputs.tool_calls:
                # Parse arguments for the tool call.
                args = json.loads(tool_call.function.arguments)
                for tool in self._configured_tools:
                    if tool.name == tool_call.function.name:
                        output = tool.run("", **args)
                        break

                # Submit the output of the tool.
                run = self._client.beta.threads.runs.submit_tool_outputs(
                    thread_id=thread_id,
                    run_id=run.id,
                    tool_outputs=[
                        {
                            "tool_call_id": tool_call.id,
                            "output": json.dumps(output)
                        }
                    ],
                )

        # Wait until the run status is completed.
        while run.status != "completed":
            run = self._client.beta.threads.runs.retrieve(thread_id=thread_id, run_id=run.id)
            sleep(0.05)

        # Retrieve all messages from the thread.
        messages = self._client.beta.threads.messages.list(thread_id=thread_id)

        # Extract the content of the first message as the response.
        message = messages.data[0].content[0].text.value

        # Return the response along with the assistant and conversation IDs.
        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                message=message,
                assistant_id=question.assistant_id,
                conversation_id=thread_id
            )
        )
