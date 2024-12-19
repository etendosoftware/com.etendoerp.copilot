from langsmith import Client

# 1. Create and/or select your dataset
client = Client()
dataset = client.clone_public_dataset(
    "https://smith.langchain.com/public/a63525f9-bdf2-4512-83e3-077dc9417f96/d"
)

# 2. Define an evaluator
def is_concise(outputs: dict, reference_outputs: dict) -> bool:
    return len(outputs["answer"]) < (3 * len(reference_outputs["answer"]))

# 3. Define the interface to your app
def chatbot(inputs: dict) -> dict:
    return {"answer": inputs["question"] + " is a good question. I don't know the answer."}

# 4. Run an evaluation
experiment_results = client.evaluate(
    chatbot,
    data=dataset,
    evaluators=[is_concise],
    experiment_prefix="my first experiment ",
    max_concurrency=4,
)