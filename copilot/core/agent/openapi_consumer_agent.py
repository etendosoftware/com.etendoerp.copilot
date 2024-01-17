import yaml

from typing import Final, Optional

from langchain.agents.agent import AgentExecutor
from langchain.chat_models import ChatOpenAI
from langchain_community.agent_toolkits.openapi import planner
from langchain_community.agent_toolkits.openapi.spec import (
    reduce_openapi_spec,
    ReducedOpenAPISpec
)
from langchain.requests import RequestsWrapper

from .agent import AgentResponse, CopilotAgent
from ..schemas import QuestionSchema
from .. import utils


class OpenApiSpecConsumerAgent(CopilotAgent):
    """OpenApi Consumer Agent implementation.

    This agent consume arbitrary APIs via the OpenAPI/Swagger specification.
    Doc reference: https://python.langchain.com/docs/integrations/toolkits/openapi
    """
    OPENAI_MODEL_FOR_OPENAPI: Final[str] = utils.read_optional_env_var("OPENAI_MODEL_FOR_OPENAPI", "gpt-4")

    def __init__(self):
        super().__init__()
        self._openapi_agent_executor: Optional[AgentExecutor] = None
        self._openapi_yaml: Optional[str] = None

    def _get_openapi_agent_executor(
            self,
            reduced_openapi_spec: ReducedOpenAPISpec,
            requests_wrapper: RequestsWrapper,
            open_ai_model: str
        ) -> AgentExecutor:
        """Construct and return an agent from scratch, using LangChain Expression Language.

        Raises:
            OpenAIApiKeyNotFound: raised when OPENAI_API_KEY is not configured
        """
        self._assert_open_api_key_is_set()

        # loads the language model we are going to use to control the agent
        llm = ChatOpenAI(temperature=0, model_name=open_ai_model)

        openapi_agent_executor: AgentExecutor = planner.create_openapi_agent(
            reduced_openapi_spec, requests_wrapper, llm
        )
        return openapi_agent_executor

    def execute(self, api_consumer_schema: QuestionSchema) -> AgentResponse:
        # TODO: handle api auth
        requests_wrapper = RequestsWrapper()

        agent_executor_need_to_be_updated: bool = (
            (self._openapi_yaml and self._openapi_yaml != api_consumer_schema.api_spec_file)
            or not self._openapi_agent_executor
        )
        if agent_executor_need_to_be_updated:
            with open(api_consumer_schema.api_spec_file) as yaml_spec:
                raw_spotify_api_spec = yaml.load(yaml_spec, Loader=yaml.Loader)

            reduced_openapi_spec = reduce_openapi_spec(raw_spotify_api_spec)
            self._openapi_agent_executor = self._get_openapi_agent_executor(
                reduced_openapi_spec=reduced_openapi_spec,
                requests_wrapper=requests_wrapper,
                open_ai_model=self.OPENAI_MODEL_FOR_OPENAPI
            )

        output = self._openapi_agent_executor.run(api_consumer_schema.question)
        return AgentResponse(input=api_consumer_schema.question, output=output)
