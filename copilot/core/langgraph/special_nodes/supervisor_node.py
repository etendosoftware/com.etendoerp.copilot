from typing import Final

from copilot.core import utils
from langchain_openai import ChatOpenAI


def build_system_prompt(members_descriptions, members_names, system_prompt):
    if system_prompt is None:
        system_prompt_section = "You are a supervisor."
    else:
        system_prompt_section = f"You are a supervisor with the following characteristics: {system_prompt}"
    core_prompt = """# Role:

You are a supervisor. Based on the conversation provided, your task is to decide who should act next.

## Conversation Structure:

	* "human" messages: Represent the main task to complete, which generally requires several steps.
	* "ai" messages: Responses from AI workers based on the previous tasks assigned. The name of the AI indicates which worker it is.
	* If the name is “Supervisor,” it refers to your previous instructions.

## Guidelines:

1.	Task Completion Evaluation:
	* Always assess whether all required steps to fulfill the main task are completed.
	* Even if the latest message from an AI worker is an action (successful or failed), consider that task as completed.
2.	Planning:
	* Main tasks require a plan. Determine this plan based on the main task and the available assistants.
	* If a plan does not exist, create one and output it as a to-do list in your instructions.
3.	Delegation Rules:
	* Decide which step should be done next based on the plan.
	* Never delegate a task to the same worker who acted after the last “human” message.
	* Avoid assigning tasks to the same worker twice in a row.
4.	Instructions for the Next Worker:
	* Your response must contain the action or actions required and only data explicitly provided by the user.
	* Present the information as a list, enumerating each item with its corresponding label.
	* Do not include additional information, interpretations, or assumptions.
	* If the user provides only one piece of data, respond with only that data in the list.
	* Include all obtained IDs from the entire conversation and any additional information necessary to perform the task.
	* Ask include all obtained IDs to add the context of the conversation.

## Action Required:

Given the conversation above, who should act next?

## Example of Instructions:

* HUMAN: Create new note with title "Meeting Notes" and content "Discuss project timeline."
* AI INSTRUCTIONS:
Create new note with the following specifications:
Title: "Meeting Notes"
Content: "Discuss project timeline."


## Available Workers:

Select one of the following workers:
"""

    if members_descriptions is not None and len(members_descriptions) > 0:
        core_prompt += " Each worker has the following next tag and description:\n"
        core_prompt += "| Next | Description |\n"
        for name, description in zip(members_names, members_descriptions, strict=True):
            core_prompt += f"| {name} | {description} |\n"
    system_prompt = system_prompt_section + core_prompt
    return system_prompt


class SupervisorNode:
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4o-mini")

    def build(self, members_names, members_descriptions=None, system_prompt=None, temperature=0):
        from langchain.output_parsers.openai_functions import JsonOutputFunctionsParser
        from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

        system_prompt = build_system_prompt(members_descriptions, members_names, system_prompt)

        # Our team supervisor is an LLM node. It just picks the next agent to process
        # and decides when the work is completed
        options = members_names
        # Using openai function calling can make output parsing easier for us
        function_def = {
            "name": "route",
            "description": "Select the next role.",
            "parameters": {
                "title": "routeSchema",
                "type": "object",
                "properties": {
                    "next": {
                        "title": "Next",
                        "anyOf": [
                            {"enum": options},
                        ],
                    },
                    "instructions": {
                        "title": "Instructions",
                        "Description": "Instructions for the next agent, including all necessary data. "
                        "This should be a comprehensive API call that incorporates all data from the conversation.",
                        "type": "string",
                    },
                },
                "required": ["next", "instructions"],
            },
        }
        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", system_prompt),
                MessagesPlaceholder(variable_name="messages"),
            ]
        )

        llm = ChatOpenAI(model=self.OPENAI_MODEL, temperature=temperature, streaming=False)

        supervisor_chain = (
            prompt
            | llm.bind_functions(functions=[function_def], function_call="route")
            | JsonOutputFunctionsParser()
        )

        return supervisor_chain


# Receives a list of members names and members descriptions
def get_supervisor_system_prompt(
    full_question, members_names: list[str] = None, members_descriptions: list[str] = None
):
    prompt = ""
    if (
        full_question is not None
        and hasattr(full_question, "system_prompt")
        and full_question.system_prompt is not None
    ):
        prompt += full_question.system_prompt

    if members_names is not None and len(members_names) > 0:
        prompt += "\n\n"
        prompt += "Each worker has the following next tag and description:\n"
        prompt += "| Next | Description |\n"
        for name, description in zip(members_names, members_descriptions, strict=True):
            prompt += f"| {name} | {description} |\n"

    return prompt


def get_supervisor_temperature(full_question):
    if (
        full_question is not None
        and hasattr(full_question, "temperature")
        and full_question.temperature is not None
    ):
        return full_question.temperature
    return 1
