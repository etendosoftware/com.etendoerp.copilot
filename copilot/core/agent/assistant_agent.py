import os
from typing import Final, Generator

from langchain.agents import AgentExecutor
from langchain_community.agents.openai_assistant import OpenAIAssistantV2Runnable

from .agent import AgentResponse, AssistantResponse, CopilotAgent
from .. import utils
from ..schemas import QuestionSchema


def _get_openai_client():
    from openai import OpenAI
    return OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


class AssistantAgent(CopilotAgent):
    """OpenAI Assistant Agent implementation."""

    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4-1106-preview")
    ASSISTANT_NAME: Final[str] = "Copilot [LOCAL]"

    def __init__(self):
        super().__init__()
        self._assistant_id = None

    def get_assistant_id(self) -> str:
        return self._assistant_id

    def get_agent(self, assistant_id: str):
        agent = OpenAIAssistantV2Runnable(assistant_id=assistant_id, as_agent=True)
        return agent

    def get_executor(self, agent) -> AgentExecutor:
        return AgentExecutor(agent=agent, tools=self._configured_tools)

    def execute(self, question: QuestionSchema) -> AgentResponse:
        agent = self.get_agent(question.assistant_id)
        agent_executor = self.get_executor(agent)
        full_question = question.question
        if question.local_file_ids is not None and len(question.local_file_ids) > 0:
            full_question += "\n\n" + "LOCAL FILES: " + "\n".join(question.local_file_ids)

        _input = {
            "content": full_question,
        }
        if question.conversation_id is not None:
            _input["thread_id"] = question.conversation_id
        response = agent_executor.invoke(_input)

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

    def _response(self, response: AssistantResponse):
        json_value = json.dumps({"answer": {"assistant_id": response.assistant_id, "response": response.response,
                                            "conversation_id": response.conversation_id}})
        return "data: " + json_value + "\n"

    def aexecute(self, question: QuestionSchema) -> AgentResponse:
        from openai import NotFoundError, APIConnectionError, APITimeoutError
        try:

            # If no conversation_id is provided, create a new conversation thread.
            thread_id = question.conversation_id
            message_id = None
            if not thread_id:
                thread_id = self._client.beta.threads.create().id
            try:
                # Create a message in the conversation thread with the user's question.
                if question.file_ids is None or len(question.file_ids) == 0:
                    message = self._client.beta.threads.messages.create(
                        thread_id=thread_id, role="user", content=get_full_question(question)
                    )
                else:
                    message = self._client.beta.threads.messages.create(
                        thread_id=thread_id, role="user", content=get_full_question(question),
                        file_ids=question.file_ids
                    )
                message_id = message.id

                # Start processing the conversation thread with the assistant.
                run = self._client.beta.threads.runs.create(
                    thread_id=thread_id, assistant_id=question.assistant_id
                )
            except NotFoundError as ex:
                raise AssistantIdNotFound(assistant_id=question.assistant_id) from ex

            yield AssistantResponse(message_id=message_id, conversation_id=thread_id,
                                    assistant_id=question.assistant_id, role="tool",
                                    response="⏳")
            # Retrieve the current status of the processing run.
            # Wait for the run to complete.
            run = self.wait_while_status(run.id, thread_id, "in_progress", SLEEP_SECONDS)
            # If the run requires action, process the required tool outputs.
            while run.status == "requires_action":
                tools_outputs_array = []
                for tool_call in run.required_action.submit_tool_outputs.tool_calls:
                    # Parse arguments for the tool call.
                    print("Tool call: " + str(tool_call))
                    args = json.loads(tool_call.function.arguments)
                    yield AssistantResponse(message_id = message_id, conversation_id=thread_id, assistant_id=question.assistant_id, response="calling '" + tool_call.function.name + "'", role="tool")
                    for tool in self._configured_tools:
                        if tool.name == tool_call.function.name:
                            # If args has only one key called "query", replace args with that value
                            if len(args) == 1 and "query" in args:
                                args = args["query"]
                            print_blue("Calling tool: " + tool.name + " with args: " + str(args))
                            output = tool.run(args, {}, None)
                            print_yellow("Tool output: " + str(output))
                            tools_outputs_array.append({"tool_call_id": tool_call.id, "output": json.dumps(output)})
                            break

                    # Submit the output of the tool.
                run = self._client.beta.threads.runs.submit_tool_outputs(
                    thread_id=thread_id,
                    run_id=run.id,
                    tool_outputs=tools_outputs_array,
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
            yield AssistantResponse(
                message_id=message_id, response=message, assistant_id=question.assistant_id, conversation_id=thread_id
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
