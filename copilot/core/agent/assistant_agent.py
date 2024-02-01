import json
import os
import time

from time import sleep
from typing import Final

from langchain.tools.render import format_tool_to_openai_function

from .. import utils
from ..exceptions import AssistantIdNotFound, AssistantTimeout
from ..schemas import QuestionSchema
from .agent import AgentResponse, AssistantResponse, CopilotAgent
from ..utils import print_blue, print_yellow

SLEEP_SECONDS = 0.05


def _get_openai_client():
    from openai import OpenAI
    return OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


class AssistantAgent(CopilotAgent):
    """OpenAI Assistant Agent implementation."""

    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4-1106-preview")
    ASSISTANT_NAME: Final[str] = "Copilot [LOCAL]"

    def __init__(self):
        # https://platform.openai.com/docs/assistants/overview/agents
        super().__init__()
        self._client = _get_openai_client()
        self._formated_tools_openai = None
        self._assistant = None  # self._get_openai_assistant()

    def _get_openai_assistant(self):
        """Creates an assistant. An Assistant represents an entity that can be
        configured to respond to users Messages."""
        self._assert_open_api_key_is_set()
        self._assert_system_prompt_is_set()

        # import delayed to avoid conflict with openai version used by langchain agent
        from openai import OpenAI
        self._client = OpenAI(api_key=self.OPENAI_API_KEY)

        # Convert configured tools into a format compatible with OpenAI functions.
        tools = [format_tool_to_openai_function(tool) for tool in self._configured_tools]
        self._formated_tools_openai = [{"type": "function", "function": tool} for tool in tools]
        # name with timestamp to avoid name conflicts
        name = self.ASSISTANT_NAME + " " + str(int(time.time()))
        assistant = self._client.beta.assistants.create(
            name=name,
            instructions=self.SYSTEM_PROMPT,
            tools=self._formated_tools_openai,
            model=self.OPENAI_MODEL,
        )
        return assistant

    def _update_assistant(self, assistant_id: int):
        from openai import NotFoundError
        try:
            self._assistant = self._client.beta.assistants.update(
                assistant_id,
                name=self.ASSISTANT_NAME,
                instructions=self.SYSTEM_PROMPT,
                tools=self._formated_tools_openai,
                model=self.OPENAI_MODEL,
            )
        except NotFoundError as ex:
            raise AssistantIdNotFound(assistant_id=assistant_id) from ex

    def get_assistant_id(self) -> str:
        return self._assistant.id

    def execute(self, question: QuestionSchema) -> AgentResponse:
        from openai import NotFoundError, APIConnectionError, APITimeoutError
        try:

            # If no conversation_id is provided, create a new conversation thread.
            thread_id = question.conversation_id
            if not thread_id:
                thread_id = self._client.beta.threads.create().id
            try:
                # Create a message in the conversation thread with the user's question.
                message = self._client.beta.threads.messages.create(
                    thread_id=thread_id, role="user", content=question.question, file_ids=(question.file_ids or [])
                )

                # Start processing the conversation thread with the assistant.
                run = self._client.beta.threads.runs.create(
                    thread_id=thread_id, assistant_id=question.assistant_id
                )
            except NotFoundError as ex:
                raise AssistantIdNotFound(assistant_id=question.assistant_id) from ex

            # Retrieve the current status of the processing run.
            # Wait for the run to complete.
            run = self.wait_while_status(run.id, thread_id, "in_progress", SLEEP_SECONDS)
            # If the run requires action, process the required tool outputs.
            while run.status == "requires_action":
                for tool_call in run.required_action.submit_tool_outputs.tool_calls:
                    # Parse arguments for the tool call.
                    args = json.loads(tool_call.function.arguments)
                    for tool in self._configured_tools:
                        if tool.name == tool_call.function.name:
                            # If args has only one key called "query", replace args with that value
                            if len(args) == 1 and "query" in args:
                                args = args["query"]
                            print_blue("Calling tool: " + tool.name + " with args: " + str(args))
                            output = tool.run(args, {}, None)
                            print_yellow("Tool output: " + str(output))
                            break

                    # Submit the output of the tool.
                    run = self._client.beta.threads.runs.submit_tool_outputs(
                        thread_id=thread_id,
                        run_id=run.id,
                        tool_outputs=[{"tool_call_id": tool_call.id, "output": json.dumps(output)}],
                    )
                    run = self.wait_while_status(run.id, thread_id, "queued", SLEEP_SECONDS)
                    run = self.wait_while_status(run.id, thread_id, "in_progress", SLEEP_SECONDS)


            # Wait until the run status is completed.
            self.wait_while_not_status(run.id, thread_id, "completed", SLEEP_SECONDS)

            # Retrieve all messages from the thread.
            messages = self._client.beta.threads.messages.list(thread_id=thread_id)

            # Extract the content of the first message as the response.

            message = messages.data[0].content[0].text.value

            # Return the response along with the assistant and conversation IDs.
            return AgentResponse(
                input=question.model_dump_json(),
                output=AssistantResponse(
                    response=message, assistant_id=question.assistant_id, conversation_id=thread_id
                ),
            )
        except (APIConnectionError, APITimeoutError) as ex:
            raise AssistantTimeout() from ex

    def wait_while_status(self, run_id, thread_id, status, seconds):
        run = self._client.beta.threads.runs.retrieve(thread_id=thread_id, run_id=run_id)
        while run.status == status:
            run = self._client.beta.threads.runs.retrieve(thread_id=thread_id, run_id=run.id)
            sleep(seconds)
        return run

    def wait_while_not_status(self, run_id, thread_id, status, seconds):
        run = self._client.beta.threads.runs.retrieve(thread_id=thread_id, run_id=run_id)
        while run.status != status:
            run = self._client.beta.threads.runs.retrieve(thread_id=thread_id, run_id=run.id)
            sleep(seconds)
        return run

    def get_run_status(self, thread_id, run_id):
        return self._client.beta.threads.runs.retrieve(thread_id=thread_id, run_id=run_id).status
