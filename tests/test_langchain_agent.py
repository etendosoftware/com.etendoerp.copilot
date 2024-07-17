"""
Test the LangchainAgent class
"""
import pytest
from unittest.mock import patch, MagicMock

from langchain_core.agents import AgentFinish
from langchain_core.runnables import RunnableSequence
from langsmith import unit 


from copilot.core.agent.langchain_agent import LangchainAgent
from copilot.core.agent.langchain_agent import CustomOutputParser
from copilot.core.schemas import QuestionSchema
from copilot.core.utils import get_full_question

@unit
@pytest.fixture
def langchain_agent():
    with patch('copilot.core.tool_loader.ToolLoader') as mock_tool_loader:
        mock_load_tools = mock_tool_loader.return_value.load_tools
        mock_load_tools.return_value = MagicMock()  # Mock the configured tools
        return LangchainAgent()

@unit
def test_get_agent_openai(langchain_agent):
    with patch('langchain_openai.ChatOpenAI') as MockChatOpenAI:
        mock_openai = MockChatOpenAI.return_value
        agent = langchain_agent.get_agent(provider='openai', open_ai_model='gpt-4', tools=[],
                                          system_prompt='Test system prompt')
        assert agent is not None
        assert isinstance(agent, RunnableSequence)


@unit
def test_get_agent_gemini(langchain_agent):
    with patch('langchain_google_genai.ChatGoogleGenerativeAI') as MockChatGoogleGenerativeAI:
        mock_gemini = MockChatGoogleGenerativeAI.return_value
        agent = langchain_agent.get_agent(provider='gemini', open_ai_model='gpt-4', tools=[],
                                          system_prompt='Test system prompt')
        assert agent is not None
        assert isinstance(agent, RunnableSequence)

@unit
def test_get_agent_executor(langchain_agent):
    with patch.object(langchain_agent, 'get_openai_agent') as mock_get_openai_agent:
        agent = langchain_agent.get_agent(provider='gemini', open_ai_model='gpt-4', tools=[],
                                          system_prompt='Test system prompt')
        mock_get_openai_agent.return_value = agent
        agent_executor = langchain_agent.get_agent_executor(agent)
        assert agent_executor.agent.runnable == agent

@unit
def test_execute(langchain_agent):
    question = QuestionSchema(provider='openai', model='gpt-4', tools=[], system_prompt=
        'Test system prompt', history=[], question='Test question', conversation_id='123')
    with patch.object(langchain_agent, 'get_agent') as mock_get_agent, \
            patch.object(langchain_agent, 'get_agent_executor') as mock_get_agent_executor, \
            patch.object(langchain_agent._memory, 'get_memory') as mock_get_memory, \
            patch('langchain.agents.AgentExecutor.invoke') as mock_invoke:
        mock_agent = MagicMock()
        mock_executor = MagicMock()
        mock_get_agent.return_value = mock_agent
        mock_get_agent_executor.return_value = mock_executor
        mock_get_memory.return_value = 'mock memory'
        mock_invoke.return_value = {'output': 'mock output'}

        response = langchain_agent.execute(question)
        assert response.input == get_full_question(question)

@unit
async def test_aexecute():
    langchain_agent = LangchainAgent()
    question = QuestionSchema(
        provider='openai',
        model='gpt-4',
        tools=[],
        system_prompt='Test system prompt',
        history=[],
        question='Test question',
        conversation_id='123'
    )
    with patch.object(langchain_agent, 'get_agent') as mock_get_agent, \
            patch.object(langchain_agent, 'get_agent_executor') as mock_get_agent_executor, \
            patch.object(langchain_agent._memory, 'get_memory') as mock_get_memory, \
            patch('langchain.agents.AgentExecutor.astream_events') as mock_astream_events:
        mock_agent = MagicMock()
        mock_executor = MagicMock()
        mock_get_agent.return_value = mock_agent
        mock_get_agent_executor.return_value = mock_executor
        mock_get_memory.return_value = 'mock memory'
        mock_astream_events.return_value = [
            {'event': 'on_tool_start', 'name': 'tool', 'parent_ids': [1]},
            {'event': 'on_chain_end', 'data': {
                'output': AgentFinish(return_values={"output": 'mock output'}, log='mock log')
            }}
        ]

        response_generator = langchain_agent.aexecute(question)
        responses = [response async for response in response_generator]

@unit
def test_custom_output_parser():
    parser = CustomOutputParser()
    output = "Test output"
    parsed_output = parser.parse(output)
    assert isinstance(parsed_output, AgentFinish)
    assert parsed_output.return_values["output"] == output
