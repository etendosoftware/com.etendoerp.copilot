import json

import pytest
from langsmith import wrappers, Client
from pydantic import BaseModel, Field
from openai import OpenAI

from copilot.core.langgraph.special_nodes.supervisor_node import SupervisorNode

client = Client()
openai_client = wrappers.wrap_openai(OpenAI())

# Create inputs and reference outputs
examples = [
    (
        "Create a new product named Screws priced at $5.50",
        "Please create a product with the following specifications:\n- Product Name: Screws\n- Price: $5.50\nPlease provide the product ID once the entry is created",
        "ProductGenerator"
    ),
    (
        "create product Nails $12.20",
        "Please create a product with the following specifications:\n- Product Name: Nails\n- Price: $12.20\nPlease provide the product ID once the entry is created",
        "ProductGenerator"
    ),
    (
        "Generate a product entry for: \n- Name: Bolts \n- Price: $15.75",
        " Please create a product with the following specifications:\n- Product Name: Bolts\n- Price: $15.75\nPlease provide the product ID once the entry is created",
        "ProductGenerator"
    ),
    (
        "Add a product with the name Wrench and a price of $25.00",
        "Please create a product with the following specifications:\n- Product Name: Wrench\n- Price: $25.00\nPlease provide the product ID once the entry is created",
        "ProductGenerator"
    ),
    (
        "Create a new tool entry: \nName: Hammer, Price: $18.45",
        "Please create a product with the following specifications:\n- Product Name: Hammer\n- Price: $18.45\nPlease provide the product ID once the entry is created",
        "ProductGenerator"
    ),
    (
        "Add an item named Pliers.",
        "Please create a product with the following specifications:\n- Product Name: Pliers\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Could you register a product called Drill?",
        "Please create a product with the following specifications:\n- Product Name: Drill\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "I'd like to create something: Spanner.",
        "Please create a product with the following specifications:\n- Product Name: Spanner\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Insert the product data: Tape, price $2.50.",
        "Please create a product with the following specifications:\n- Product Name: Tape\n- Price: $2.50\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Make a record for this: Name - Screwdriver.",
        "Please create a product with the following specifications:\n- Product Name: Screwdriver\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Add details for the following tool: Mallet.",
        "Please create a product with the following specifications:\n- Product Name: Mallet\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Register a new item:\nTool name - Chisel, $5.",
        "Please create a product with the following specifications:\n- Product Name: Chisel\n- Price: $5\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Set up a product entry: Crowbar.",
        "Please create a product with the following specifications:\n- Product Name: Crowbar\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Include a new entry with this name: Clamp.",
        "Please create a product with the following specifications:\n- Product Name: Clamp\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Create something useful: Item - Socket Wrench.",
        "Please create a product with the following specifications:\n- Product Name: Socket Wrench\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    ),
    (
        "Create a bump for Ford mondeo",
        "Please create a product with the following specifications:\n- Product Name: Ford Mondeo Bump\nPlease provide the product ID once the entry is created.",
        "ProductGenerator"
    )
]

inputs = [{"question": input_prompt} for input_prompt, _, _ in examples]
outputs = [{"instructions": output_answer, "next": next} for _, output_answer, next in examples]

SYSTEM_PROMPT1 = """You are a supervisor assistant to help in the initialization of the system instance. You can help delegating tasks to other assistants that are specialized in different areas.
The tasks possible to be delegated are:
- Creation of Products.
- Creation of Business Partners.

Rules for generating responses:
Always end the response with the phrase: 
   Please provide the product ID once the entry is created.
"""

SYSTEM_PROMPT2 = """You are a supervisor assistant to help in the initialization of the system instance. You can help delegating tasks to other assistants that are specialized in different areas.
The tasks possible to be delegated are:
- Creation of Products.
- Creation of Business Partners.

Rules for generating responses:
1. Only include information explicitly provided by the human in the instructions. 
   - Do not add descriptions, prices, categories, or any other attributes unless they are explicitly mentioned.
   - If information is missing, do not infer or invent values. Simply omit them.
2. Follow the format demonstrated in the examples below.
3. Always end the response with: 
   **"Please provide the product ID once the entry is created."**

### **Examples:**

#### Example 1:
**HUMAN:** Add a product with the name Floor and a price of $9.20  
**AI:** Please create a product with the following specifications:  
- Product Name: Floor  
- Price: $9.20  

Please provide the product ID once the entry is created.

#### Example 2:
**HUMAN:** Create something useful: Item - Socket Wrench  
**AI:** Please create a product with the following specifications:  
- Product Name: Socket Wrench  

Please provide the product ID once the entry is created.

#### Example 3:
**HUMAN:** Add a product with the name Light Bulb  
**AI:** Please create a product with the following specifications:  
- Product Name: Light Bulb  

Please provide the product ID once the entry is created.

### Key Note:
If the human does not specify a detail (e.g., price, category, description), do not include it in the response. Simply exclude it.
"""

# Define the application logic you want to evaluate inside a target function
def target(inputs: dict) -> dict:
    node = SupervisorNode()
    executor = node.build(
        members_names=['BusinessPartnerGenerator', 'ClientOrganizationremoteinitializer', 'ProductGenerator', 'FINISH'],
        members_descriptions=[': This assistant is specialized in the creation of business partners.',
                              ': This assistant is specialized in the creation of clients and organizations.',
                              ': This assistant is specialized in the creation of products.', 'End of response cycle.'],
        system_prompt=SYSTEM_PROMPT1)
    response = executor.invoke({"messages": [{
        "role": "user",
        "content": inputs["question"]
    }]})
    return {"next": response["next"], "instructions": response["instructions"]}


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
        description="Boolean that indicates whether the response is accurate relative to the reference answer")


# Define LLM judge that grades the accuracy of the response relative to reference output
def accuracy(outputs: dict, reference_outputs: dict) -> bool:
    content = (context
               .replace("{instructions}", reference_outputs["instructions"])
               .replace("{next}", reference_outputs["next"])
               .replace("{next_generated}", outputs["next"])
               .replace("{generated_instructions}", outputs["instructions"])
               )
    response = openai_client.beta.chat.completions.parse(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": instructions},
            {
                "role": "user",
                "content": content
            }
        ],
        response_format=Grade
    )
    return response.choices[0].message.parsed.score


@pytest.fixture
def dataset():
    return client.read_dataset(dataset_id="19cfed68-b55b-4c5f-a545-c21b1d624a8d")


@pytest.fixture
def examples_dataset(dataset):

    for ex in client.list_examples(dataset_id=dataset.id):
        client.delete_example(ex.id)
    client.create_examples(inputs=inputs, outputs=outputs, dataset_id=dataset.id)
    return dataset


def test_evaluation(examples_dataset):
    experiment_results = client.evaluate(
        target,
        data="Langgraph-supervisor",
        evaluators=[accuracy],
        experiment_prefix="first-eval-in-langgraph",
        max_concurrency=2,
        num_repetitions=2
    )
    assert experiment_results is not None
