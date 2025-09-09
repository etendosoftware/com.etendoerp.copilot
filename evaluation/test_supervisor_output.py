import pytest
from copilot.core.agent.langgraph_agent import setup_graph
from copilot.core.schemas import GraphQuestionSchema
from copilot.core.utils.models import get_openai_client
from langsmith import Client, wrappers
from pydantic import BaseModel, Field

grafo = {
    "conversation_id": "ASAAASDADASDASD",
    "assistants": [
        {
            "name": "Node2Emojis",
            "type": "multimodel",
            "description": "Add emojis to a text and traduce it to english. The "
            'recommended input is "Process the following text: <TEXT>"',
            "assistant_id": "AC6C184726B149C28064FE4E8AC86D0B",
            "temperature": 1,
            "tools": [],
            "provider": "openai",
            "model": "gpt-4o",
            "kb_vectordb_id": "KB_AC6C184726B149C28064FE4E8AC86D0B",
            "system_prompt": "Recibes texto en cualquier idioma y devuelves exactamente"
            " la traduccion al ingles, con muchos emojis. No puedes"
            " realizar otra tarea.\n",
        },
        {
            "name": "NODE1Text",
            "type": "langchain",
            "description": "Spanish stories generator",
            "assistant_id": "3C4D54A938E64DBCBCF1757EB71B54DC",
            "temperature": 1,
            "tools": [],
            "provider": "openai",
            "model": "gpt-4o",
            "kb_vectordb_id": "KB_3C4D54A938E64DBCBCF1757EB71B54DC",
            "system_prompt": "Eres un asistente que solo genera cuentos de 3 actos de"
            " menos de 10 palabras. Solo en Español y sin emojis.\n",
        },
    ],
    "system_prompt": "Resuelves peticiones dividiendo la tarea en subtareas entre los"
    " miembros de tu equipo.\n\nCuando decidas que el flujo termina,"
    " debes dar una respuesta final con lo que se hizo y lo que fallo."
    " No omitas informacion con esa respuesta final.",
    "temperature": 0.1,
    "graph": {"stages": [{"name": "stage1", "assistants": ["Node2Emojis", "NODE1Text"]}]},
    "question": "What is the capital of France?",
    "local_file_ids": [],
    "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
    "history": [],
}

client = Client()
openai_client = wrappers.wrap_openai(get_openai_client())

# Create inputs and reference outputs
examples = [{"input": "perros y gatos", "output": " Habia una vez un gato y un perro y fueron felices."}]
# field impuyt in examples
inputs = [{"question": elem["input"]} for elem in examples]
outputs = [{"output": elem["output"]} for elem in examples]

SYSTEM_PROMPT1 = """
Resuelves peticiones dividiendo la tarea en subtareas entre los miembros de tu equipo.

Cuando decidas que el flujo termina, debes dar una respuesta final con lo que se hizo y
 lo que fallo. No omitas informacion con esa respuesta final.

"""


# Define the application logic you want to evaluate inside a target function
def target(inputs: dict) -> dict:
    # set question to grafo
    grafo["question"] = inputs["question"]
    # convert graph dict to GraphQuestionSchema
    question = GraphQuestionSchema.model_validate(grafo)
    graph, _ = setup_graph(question=question, memory=None, store=None)

    response = graph.invoke(inputs["question"], thread_id=question.conversation_id)

    return response


# Define instructions for the LLM judge evaluator
instructions = """Evaluate the Assistant's response based on the Reference Instructions.
- **False**: The response does not strictly follow the Reference Instructions. This includes:
  - Introducing new information or inventing data not present in the Reference Instructions.
  - Omitting required details explicitly stated in the Reference Instructions.
  - Misinterpreting or altering the intent of the Reference Instructions.
- **True**: The response strictly adheres to the Reference Instructions by:
  - Including all requested details as specified.
  - Avoiding any additions, omissions, or modifications to the requested details.
  - Maintaining the intended purpose of the Reference Instructions.

Key Criteria for Evaluation:
- The response should follow the Reference Instructions exactly without adding or omitting information.
- Additional creativity or elaboration beyond the Reference Instructions is not allowed.
- Don't evaluate extra new lines or spaces that do not affect the meaning of the response.
- In case of numbers is valid to use or ommit the $ symbol and the decimal part of the number if is 0 (e.g., $5.00 or $5).
- If the user is not explicit with names or description, the assistant can fix grammar or spelling errors with changes that do not alter the meaning of the response.

Provide your judgment as **True** or **False**, followed by a brief explanation of why the response is valid or invalid.
"""

# Define context for the LLM judge evaluator
context = """Ground Truth instruccions: {instructions}, next: {next};

-- AI Generated:
next: {next_generated}
instructions: {generated_instructions}

"""


# Define output schema for the LLM judge
class Grade(BaseModel):
    score: bool = Field(
        description="Boolean that indicates whether the response is accurate relative to the reference answer"
    )


# Define LLM judge that grades the accuracy of the response relative to reference output
def accuracy(outputs: dict, reference_outputs: dict) -> bool:
    content = (
        context.replace("{instructions}", reference_outputs["instructions"])
        .replace("{next}", reference_outputs["next"])
        .replace("{next_generated}", outputs["next"])
        .replace("{generated_instructions}", outputs["instructions"])
    )
    response = openai_client.beta.chat.completions.parse(
        model="gpt-4o",
        messages=[{"role": "system", "content": instructions}, {"role": "user", "content": content}],
        response_format=Grade,
    )
    return response.choices[0].message.parsed.score


@pytest.fixture
def dataset():
    return client.read_dataset(dataset_id="f835b05c-257f-447f-b973-2a41f34eb698")


@pytest.fixture
def examples_dataset(dataset):
    for ex in client.list_examples(dataset_id=dataset.id):
        client.delete_example(ex.id)
    client.create_examples(inputs=inputs, outputs=outputs, dataset_id=dataset.id)
    return dataset


def test_evaluation(examples_dataset):
    experiment_results = client.evaluate(
        target,
        data=examples_dataset.name,
        evaluators=[accuracy],
        experiment_prefix="first-eval-in-langgraph",
        max_concurrency=2,
        num_repetitions=3,
    )
    assert experiment_results is not None
