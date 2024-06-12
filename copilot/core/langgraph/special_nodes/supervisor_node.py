from typing import Final

from langchain_openai import ChatOpenAI

from copilot.core import utils


class SupervisorNode:
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4o")

    def build(self, members_names, system_prompt=None):
        from langchain.output_parsers.openai_functions import JsonOutputFunctionsParser
        from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

        if system_prompt is None:
            system_prompt = (
                "You are a supervisor tasked with managing a conversation between the"
                " following workers:  {members}. Given the following user request,"
                " respond with the worker to act next. Each worker will perform a"
                " task and respond with their results and status. When finished,"
                " respond with FINISH."
            )
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

        llm = ChatOpenAI(model=self.OPENAI_MODEL, temperature=0, streaming=False)

        supervisor_chain = (
                prompt
                | llm.bind_functions(functions=[function_def], function_call="route")
                | JsonOutputFunctionsParser()
        )

        return supervisor_chain