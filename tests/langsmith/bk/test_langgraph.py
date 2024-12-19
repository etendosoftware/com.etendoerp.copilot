from typing import TypedDict, Annotated, Literal

import pytest
from langchain.chat_models import init_chat_model
from langchain_core.tools import tool
from langgraph.graph import END, START, StateGraph
from langgraph.prebuilt import ToolNode
from langgraph.graph.message import add_messages
from langsmith import Client, aevaluate

# Definición del estado y herramientas
class State(TypedDict):
    messages: Annotated[list, add_messages]

@tool
def search(query: str) -> str:
    """Realiza una búsqueda en la web."""
    if "sf" in query.lower() or "san francisco" in query.lower():
        return "Está a 15 grados y con niebla."
    return "Está a 32 grados y soleado."

tools = [search]
tool_node = ToolNode(tools)
model = init_chat_model("claude-3-5-sonnet-latest").bind_tools(tools)

# Definición del flujo de trabajo
def should_continue(state: State) -> Literal["tools", END]:
    messages = state['messages']
    last_message = messages[-1]
    if last_message.tool_calls:
        return "tools"
    return END

def call_model(state: State):
    messages = state['messages']
    response = model.invoke(messages)
    return {"messages": [response]}

workflow = StateGraph(State)
workflow.add_node("agent", call_model)
workflow.add_node("tools", tool_node)
workflow.add_edge(START, "agent")
workflow.add_conditional_edges("agent", should_continue)
workflow.add_edge("tools", 'agent')
app = workflow.compile()

# Creación del conjunto de datos
questions = [
    "¿Cuál es el clima en SF?",
    "¿Cómo está el tiempo en San Francisco?",
    "¿Qué clima hay en Tánger?"
]
answers = [
    "Está a 15 grados y con niebla.",
    "Está a 15 grados y con niebla.",
    "Está a 32 grados y soleado.",
]

inputs = [{"question": q} for q in questions]
outputs = [{"answer": a} for a in answers]

ls_client = Client()
dataset = ls_client.create_dataset(
    dataset_name="agente_del_clima",
    description="Conjunto de datos para evaluar el agente del clima."
)

ls_client.create_examples(
    inputs=inputs,
    outputs=outputs,
    dataset_id=dataset.id
)

# Definición del evaluador
judge_llm = init_chat_model("gpt-4o")

async def correct(outputs: dict, reference_outputs: dict) -> bool:
    instructions = (
        "Dada una respuesta actual y una respuesta esperada, determina si"
        " la respuesta actual contiene toda la información de la"
        " respuesta esperada. Responde con 'CORRECTO' si la respuesta actual"
        " contiene toda la información esperada y 'INCORRECTO'"
        " en caso contrario. No incluyas nada más en tu respuesta."
    )
    actual_answer = outputs["messages"][-1].content
    expected_answer = reference_outputs["answer"]
    user_msg = (
        f"RESPUESTA ACTUAL: {actual_answer}"
        f"\n\nRESPUESTA ESPERADA: {expected_answer}"
    )
    response = await judge_llm.ainvoke(
        [
            {"role": "system", "content": instructions},
            {"role": "user", "content": user_msg}
        ]
    )
    return response.content.upper() == "CORRECTO"

# Función de prueba principal
@pytest.mark.asyncio
async def test_langgraph_langsmith_integration():
    def example_to_state(inputs: dict) -> dict:
        return {"messages": [{"role": "user", "content": inputs['question']}]}

    target = example_to_state | app
    experiment_results = await aevaluate(
        target,
        data="agente del clima",
        evaluators=[correct],
        max_concurrency=4,
        experiment_prefix="claude-3.5-baseline",
    )

    # Verificar que los resultados del experimento sean correctos
    assert experiment_results is not None
    # Agrega más aserciones según tus criterios de éxito
