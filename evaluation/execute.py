import argparse
import json
import os
import sys
import time
from typing import List

from copilot.core.etendo_utils import get_etendo_host
from copilot.core.schemas import AssistantSchema
from dotenv import load_dotenv
from langsmith import Client, wrappers
from openai import OpenAI
from pydantic import ValidationError
from schemas import Conversation, Message
from utils import (
    calc_md5,
    generate_html_report,
    get_agent_config,
    get_tools_for_agent,
    save_conversation_from_run,
    tool_to_openai_function,
    validate_dataset_folder,
)

DEFAULT_EXECUTIONS = 5


def exec_agent(args):
    """
    Executes an agent with the specified parameters.

    This function retrieves the agent's configuration, evaluates the agent, or saves a conversation
    from a specific run ID if provided.

    Args:
        args (argparse.Namespace): The parsed command-line arguments containing the following attributes:
            - user (str): The username for authentication.
            - password (str): The password for authentication.
            - token (str): The authentication token.
            - agent_id (str): The unique identifier of the agent to execute.
            - k (int): The number of repetitions for the evaluation.
            - save (str, optional): The run ID to extract and save the conversation. Defaults to None.
            - skip_evaluators (bool, optional): Whether to skip custom evaluators. Defaults to False.

    Side Effects:
        - Prints the execution details to the console.
        - Calls `save_conversation_from_run` if `save_run_id` is provided.
        - Calls `evaluate_agent` to evaluate the agent if `save_run_id` is not provided.
    """
    user = args.user
    password = args.password
    token = args.token
    agent_id = args.agent_id
    repetitions = int(args.k)
    save_run_id = args.save
    skip_evaluators = args.skip_evaluators
    base_path = args.dataset

    print("Executing agent with:")
    print(f"User: {user}")
    print(f"Password: {password}")
    print(f"Token: {token}")
    print(f"Agent ID: {agent_id}")
    print(f"Base Path: {base_path}")
    print(f"Repetitions: {repetitions}")
    host = get_etendo_host()
    if args.etendohost:
        host = args.etendohost
    config_agent = get_agent_config(agent_id, host, token, user, password)

    # If a save_run_id is provided, extract and save the conversation
    if save_run_id:
        save_conversation_from_run(
            agent_id, save_run_id, config_agent.get("system_prompt"), base_path=base_path
        )
        return None, None
    return evaluate_agent(agent_id, config_agent, repetitions, base_path, skip_evaluators)


# Read conversations from a JSON file
def load_conversations(agent_id: str, base_path: str, prompt: str = None) -> List[Conversation]:
    """
    Loads conversations from JSON files in a specified dataset folder.

    This function reads all JSON files in the folder corresponding to the given `agent_id`,
    validates their content, and converts them into `Conversation` objects. If a `prompt` is provided,
    it is added as a system message to each conversation.

    Args:
        agent_id (str): The unique identifier of the agent whose conversations are being loaded.
        base_path (str, optional): The base directory where the dataset folders are located. Defaults to "dataset".
        prompt (str, optional): A system prompt to prepend to each conversation. Defaults to None.

    Returns:
        List[Conversation]: A list of `Conversation` objects loaded from the dataset.

    Side Effects:
        - Prints messages for skipped non-JSON files or errors encountered during file reading or validation.

    Raises:
        ValidationError: If a conversation entry in a JSON file does not match the `Conversation` schema.
        json.JSONDecodeError: If a JSON file cannot be parsed.

    Notes:
        - The function ensures the dataset folder exists by calling `validate_dataset_folder`.
        - Non-JSON files in the folder are ignored.
    """
    agent_path = os.path.join(base_path, agent_id)
    validate_dataset_folder(agent_path)

    conversations = []
    for filename in os.listdir(agent_path):
        if not filename.endswith(".json"):
            print(f"Skipping non-JSON file: {filename}")
            continue
        filepath = os.path.join(agent_path, filename)
        with open(filepath, "r", encoding="utf-8") as f:
            try:
                raw_data = json.load(f)
                for entry in raw_data:
                    try:
                        conversation = Conversation(**entry)
                        # If a prompt is provided, add it as a system message
                        if prompt:
                            conversation.messages.insert(0, Message(role="system", content=prompt))
                        conversations.append(conversation)
                    except ValidationError as e:
                        print(f"Invalid conversation in {filename}: {e}")
            except json.JSONDecodeError as e:
                print(f"Error while reading {filename}: {e}")
    return conversations


def target(inputs: dict) -> dict:
    """
    Sends a chat completion request to the OpenAI client and retrieves the response.

    Args:
        inputs (dict): A dictionary containing the following keys:
            - "model" (str): The name of the model to use for the chat completion.
            - "messages" (list): A list of message dictionaries, where each message
            contains the role (e.g., "user", "assistant") and the content of the message.

    Returns:
        dict: A dictionary containing the response with the key:
            - "answer" (str): The content of the response message from the OpenAI client.
    """
    openai_client = wrappers.wrap_openai(OpenAI())
    response = openai_client.chat.completions.create(
        model=inputs["model"],
        messages=inputs["messages"],
        tools=inputs["tools"],
    )
    # Extract the content and tool calls from the response
    answer = response.choices[0].message.model_dump(include={"role", "content", "tool_calls"})
    return {"answer": answer}


def convert_conversations_to_examples(conversations, model, tools):
    """
    Converts a list of conversations into a list of examples for evaluation.

    This function processes each conversation, extracting the messages and expected response,
    and formats them into a structure suitable for evaluation.

    Args:
        conversations (list): A list of Conversation objects, each containing messages and an expected response.
        model (str): The name of the model to be used in the evaluation.

    Returns:
        list: A list of examples, where each example is a dictionary containing:
            - 'inputs': A dictionary with the model name and a list of messages.
            - 'outputs': A dictionary with the expected response.
    """
    examples = []
    for conversation in conversations:
        example = {
            "inputs": {
                "model": model,
                "messages": conversation.messages,
                "tools": tools,
                "considerations": conversation.considerations,
            },
            "outputs": {
                "answer": conversation.expected_response,
            },
        }
        examples.append(example)
    return examples


def get_evaluators():
    from openevals.llm import create_llm_as_judge
    from openevals.prompts import CORRECTNESS_PROMPT

    evaluator_prompt = (
        CORRECTNESS_PROMPT
        + """
    Ensure that the output is of the same type as the reference output. i.e. if the reference output its a message, the output should be a message too. If the reference output its a tool/function call, the output should be a tool/function call too.
    If you consider that the output has sense, you can mark as true.
    Feedback possible:
    0: The output is wrong.
    0.5: The output is partially correct or has sense.
    1: The output is correct.
    """
    )

    def correctness_evaluator(inputs: dict, outputs: dict, reference_outputs: dict):
        considerations = inputs["considerations"]
        evaluator = create_llm_as_judge(
            prompt=(
                evaluator_prompt
                if considerations is None
                else f"{evaluator_prompt}\n\n Considerations:\n{considerations}"
            ),
            model="openai:gpt-4.1",
            feedback_key="correctness",
            continuous=True,
            choices=[0, 0.5, 1],
        )
        eval_result = evaluator(inputs=inputs, outputs=outputs, reference_outputs=reference_outputs)
        return eval_result

    return [correctness_evaluator]


def evaluate_agent(agent_id, agent_config, k, base_path, skip_evaluators=False):
    """
    Evaluates an agent's performance using a dataset of conversations.

    This function retrieves the agent's configuration, loads conversations, converts
    them into examples, and evaluates the agent using the LangSmith client.

    Args:
        skip_evaluators: Whether to skip custom evaluators. Defaults to False.
        agent_id (str): The unique identifier of the agent to evaluate.
        agent_config (dict): Configuration of the agent, including the system prompt
            and model.
        k (int): Number of repetitions for the evaluation.
        evaluators (bool, optional): Whether to use custom evaluators. Defaults to False.

    Notes:
        - The function creates or updates a dataset for the agent's evaluation.
        - If no dataset exists, it creates one and populates it with examples.
        - The evaluation is performed using the LangSmith client.

    Raises:
        Exception: If there is an error reading or creating the dataset.
    """
    prompt = agent_config.get("system_prompt")  # Retrieve the system prompt from
    # the agent config
    model = agent_config.get("model")  # Retrieve the model from the agent config
    agent_config_assch = AssistantSchema(**agent_config)
    available_tools = get_tools_for_agent(agent_config_assch)  # Get the tools for the
    # agent
    conversations = load_conversations(agent_id, base_path=base_path, prompt=prompt)
    available_tools = [
        tool_to_openai_function(tool) for tool in available_tools
    ]  # Convert tools to OpenAI format
    examples = convert_conversations_to_examples(
        conversations, model, tools=available_tools
    )  # Convert conversations to examples?
    ls_client = Client()  # Initialize the LangSmith client
    examples_md5 = calc_md5(examples)  # Calculate the MD5 hash of the examples
    dat_name = f"Dataset for evaluation {agent_id} MD5:{examples_md5}"  # Generate a unique dataset name
    try:
        dataset = ls_client.read_dataset(dataset_name=dat_name)  # Attempt to read the dataset
    except Exception as e:
        print(f"Error reading dataset: {e}")  # Log any errors
        dataset = None
    if dataset is None or dataset.id is None:
        # Create a new dataset if it doesn't exist
        dataset = ls_client.create_dataset(dataset_name=dat_name)
        ls_client.create_examples(
            dataset_id=dataset.id,
            examples=examples,
        )

    print("\n" * 20)  # Print spacing for readability
    results = ls_client.evaluate(
        target,  # Target function for evaluation
        data=dataset.name,  # Dataset name
        evaluators=get_evaluators() if not skip_evaluators else None,
        # Use evaluators if specified
        experiment_prefix=agent_id,  # Prefix for the evaluation experiment
        description="Testing agent",  # Description of the evaluation
        max_concurrency=4,  # Maximum concurrency for evaluation
        num_repetitions=k,  # Number of repetitions
    )

    return results, results.experiment_name  # Return the evaluation results


def main():
    """
    Main function to process user parameters and execute the agent.

    This function parses command-line arguments, validates the provided inputs,
    and calls the `exec_agent` function to execute the agent with the specified parameters.

    Command-line Arguments:
        --user (str, optional): Username for authentication.
        --password (str, optional): Password for authentication.
        --token (str, optional): Authentication token. Either this or `--user` and `--password` must be provided.
        --agent_id (str, required): ID of the agent to execute.
        --k (int, optional): Number of executions per conversation. Defaults to `DEFAULT_EXECUTIONS`.
        --save (str, optional): Run ID of LangSmith to extract and save the conversation.

    Raises:
        SystemExit: If required arguments are missing or invalid combinations of arguments are provided.

    Notes:
        - Either `--token` or both `--user` and `--password` must be provided for authentication.
        - If the `--save` flag is used, the `--agent_id` argument is also required.
    """
    parser = argparse.ArgumentParser(description="Process user parameters.")
    parser.add_argument("--user", help="Username")
    parser.add_argument("--envfile", help="Environment file", default=None)
    parser.add_argument("--etendohost", help="Etendo host.", default=None)

    parser.add_argument("--password", help="User password")
    parser.add_argument("--token", help="Authentication token")
    parser.add_argument(
        "--dataset",
        help="Base path where the dataset will be read/writed",
        default="../com.etendoerp.copilot/dataset",
    )
    parser.add_argument("--agent_id", required=True, help="Agent ID")
    parser.add_argument("--k", help="Executions per 'conversation'", default=DEFAULT_EXECUTIONS)
    parser.add_argument("--save", help="LangSmith Run ID to extract and save the conversation")
    parser.add_argument("--skip_evaluators", help="Use custom evaluators", action="store_true")

    args = parser.parse_args()
    if args.envfile:
        print(
            f"Loading environment variables from {args.envfile}. Make sure to set the variables in the file."
        )
        load_dotenv(args.envfile, verbose=True)

    if not args.token and not (args.user and args.password):
        print("Error: You must provide a token or a username and password.")
        sys.exit(1)

    if args.save and not args.agent_id:
        print("Error: You must provide an agent_id when using the --save flag.")
        sys.exit(1)

    # Create folder if it doesn't exist
    if not os.path.exists("./evaluation_output"):
        os.makedirs("evaluation_output")
    results, link = exec_agent(args)
    if args.save is not None:
        print("Conversation saved.")
        return
    # Extract the url of the results
    res_pand = results.to_pandas()
    # Save the results to a CSV file
    output_file = f"evaluation_output/results_{args.agent_id}_{int(time.time())}.csv"
    res_pand.to_csv(output_file, index=False)
    # Detectar errores
    errores = res_pand[
        (res_pand["feedback.correctness"] is False)
        | (res_pand["error"].notnull())
        | (res_pand["outputs.answer"].isnull())
    ]

    generate_html_report(args, errores, link, res_pand)

    # Salir con error si hay errores
    if not errores.empty:
        print(f"{len(errores)} resultados erróneos detectados.")
        sys.exit(1)


if __name__ == "__main__":
    """
    Entry point of the script.

    Calls the `main` function to process command-line arguments and execute the agent.
    """
    main()
