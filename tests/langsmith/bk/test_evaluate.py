import pytest
from langsmith import wrappers, Client
from pydantic import BaseModel, Field
from openai import OpenAI

client = Client()
openai_client = wrappers.wrap_openai(OpenAI())

# Create inputs and reference outputs
examples = [
    ("Which country is Mount Kilimanjaro located in?", "Mount Kilimanjaro is located in Tanzania."),
    ("What is Earth's lowest point?", "Earth's lowest point is The Dead Sea."),
]

inputs = [{"question": input_prompt} for input_prompt, _ in examples]
outputs = [{"answer": output_answer} for _, output_answer in examples]

# Define the application logic you want to evaluate inside a target function
def target(inputs: dict) -> dict:
    response = openai_client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": "Answer the following question accurately"},
            {"role": "user", "content": inputs["question"]},
        ]
    )
    return {"response": response.choices[0].message.content.strip()}

# Define instructions for the LLM judge evaluator
instructions = """Evaluate Student Answer against Ground Truth for conceptual similarity and classify true or false:
- False: No conceptual match and similarity
- True: Most or full conceptual match and similarity
- Key criteria: Concept should match, not exact wording.
"""

# Define context for the LLM judge evaluator
context = """Ground Truth answer: {reference}; Student's Answer: {prediction}"""

# Define output schema for the LLM judge
class Grade(BaseModel):
    score: bool = Field(description="Boolean that indicates whether the response is accurate relative to the reference answer")

# Define LLM judge that grades the accuracy of the response relative to reference output
def accuracy(outputs: dict, reference_outputs: dict) -> bool:
    response = openai_client.beta.chat.completions.parse(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": instructions},
            {"role": "user", "content": context.replace("{prediction}", outputs["response"]).replace("{reference}", reference_outputs["answer"])},
        ],
        response_format=Grade
    )
    return response.choices[0].message.parsed.score

@pytest.fixture
def dataset():
    return client.read_dataset(dataset_id="be99d381-eb3c-4edd-a3e3-99217bc9546e")

@pytest.fixture
def examples_dataset(dataset):
    client.create_examples(inputs=inputs, outputs=outputs, dataset_id=dataset.id)
    return dataset

def test_evaluation(examples_dataset):
    experiment_results = client.evaluate(
        target,
        data="Sample dataset",
        evaluators=[accuracy],
        experiment_prefix="first-eval-in-langsmith",
        max_concurrency=2,
    )
    assert experiment_results is not None