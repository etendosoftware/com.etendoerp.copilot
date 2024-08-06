from typing import Final

from langchain_openai import ChatOpenAI

from copilot.core import utils


def build_system_prompt(members_descriptions, members_names, system_prompt):
    if system_prompt is None:
        system_prompt_section = (
            "You are a supervisor."
        )
    else:
        system_prompt_section = (
            f"You are a supervisor with the following charecteristics: {system_prompt}"
        )
    core_prompt = (" You are tasked with managing a conversation between the"
                   " following workers:  {members}. Given the following user request,"
                   " respond with the worker to act next. When finished,"
                   " respond with FINISH.")
    if members_descriptions is not None and len(members_descriptions) > 0:
        core_prompt += " Each worker has the following description: "
        for name, description in zip(members_names, members_descriptions):
            core_prompt += f"{name}: {description}"
    system_prompt = system_prompt_section + core_prompt
    return system_prompt


class SupervisorNode:
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4o")

    def build(self, members_names, members_descriptions=None, system_prompt=None, temperature=1):
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
                    }
                },
                "required": ["next"],
            },
        }
        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", system_prompt),
                MessagesPlaceholder(variable_name="messages"),
                (
                    "system",
                    "Given the conversation above, who should act next?"
                    " Select one of: {options}",
                ),
            ]
        ).partial(options=str(options), members=", ".join(members_names))

        llm = ChatOpenAI(model=self.OPENAI_MODEL, temperature=temperature, streaming=False)

        supervisor_chain = (
                prompt
                | llm.bind_functions(functions=[function_def], function_call="route")
                | JsonOutputFunctionsParser()
        )

        return supervisor_chain


def get_supervisor_system_prompt(full_question):
    if full_question is not None and hasattr(full_question,
                                             "system_prompt") and full_question.system_prompt is not None:
        return full_question.system_prompt
    return None


def get_supervisor_temperature(full_question):
    if full_question is not None and hasattr(full_question, "temperature") and full_question.temperature is not None:
        return full_question.temperature
    return 1
