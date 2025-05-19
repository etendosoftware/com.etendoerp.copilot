import argparse
import json
import os
import sys
import time
from copy import deepcopy
from typing import List, Dict, Any

from copilot.core.etendo_utils import get_etendo_host
from copilot.core.schemas import AssistantSchema
from dotenv import load_dotenv
from langsmith import Client, wrappers
from openai import OpenAI
from pydantic import ValidationError
from utils import (
    calc_md5,
    generate_html_report,
    get_agent_config,
    get_tools_for_agent,
    save_conversation_from_run,
    tool_to_openai_function,
    validate_dataset_folder,
)

from schemas import Conversation, Message
from utils import validate_dataset_folder

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


def load_conversations(agent_id: str, base_path: str, prompt: str = None) -> List[Conversation]:
    """
    Loads conversations from JSON files in a specified dataset folder,
    processing variants and file references.

    Args:
        agent_id (str): The unique identifier of the agent.
        base_path (str): The base directory for dataset folders.
        prompt (str, optional): A system prompt to prepend to each conversation.

    Returns:
        List[Conversation]: A list of Conversation objects.
    """
    agent_path = os.path.join(base_path, agent_id)
    validate_dataset_folder(agent_path)  # Ensures the folder exists

    final_conversations: List[Conversation] = []

    for filename in os.listdir(agent_path):
        if not filename.endswith(".json"):
            print(f"Skipping non-JSON file: {filename}")
            continue

        filepath = os.path.join(agent_path, filename)
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                # Each JSON file is expected to contain a list of base conversation entries
                raw_data_list: List[Dict[str, Any]] = json.load(f)
        except json.JSONDecodeError as e:
            print(f"Error decoding JSON from file {filename}: {e}")
            continue
        except Exception as e:
            print(f"Error reading file {filename}: {e}")
            continue

        for base_entry_dict in raw_data_list:
            # This list will hold all dictionary versions of conversations
            # derived from the current base_entry_dict
            potential_conversation_dicts: List[Dict[str, Any]] = []

            if "variants" in base_entry_dict and base_entry_dict["variants"]:
                for variant_patch_dict in base_entry_dict["variants"]:
                    # 1. Create a patched entry from the base entry and the variant patch
                    # Start with a deep copy of the base entry
                    current_patched_dict = deepcopy(base_entry_dict)

                    # Remove 'variants' key from the working copy as it's a processing instruction,
                    # not part of the Conversation model itself.
                    if "variants" in current_patched_dict:
                        del current_patched_dict["variants"]

                    # Apply general patch items (e.g., 'considerations', 'expected_response')
                    # These will overwrite values from the base_entry_dict.
                    for key, value in variant_patch_dict.items():
                        if key != "messages":  # 'messages' are handled specially
                            current_patched_dict[key] = deepcopy(value)

                    # Apply message patching if 'messages' are specified in the variant
                    if "messages" in variant_patch_dict:
                        # Get messages from the current state of current_patched_dict (could be from base or prior patches if logic was nested)
                        base_messages = current_patched_dict.get("messages", [])
                        # Create a working list of messages, initially from the base/current state
                        processed_message_list = [deepcopy(m) for m in base_messages]

                        # Iterate through messages provided in the variant patch
                        for msg_data_from_patch in variant_patch_dict["messages"]:
                            found_and_replaced = False
                            # If the patch message has an ID, try to find and replace an existing message
                            if msg_data_from_patch.get("id"):
                                for i, existing_msg_data in enumerate(processed_message_list):
                                    if existing_msg_data.get("id") == msg_data_from_patch["id"]:
                                        processed_message_list[i] = deepcopy(msg_data_from_patch)
                                        found_and_replaced = True
                                        break
                            # If not replaced (no ID in patch message, or ID didn't match), append it
                            if not found_and_replaced:
                                processed_message_list.append(deepcopy(msg_data_from_patch))
                        current_patched_dict["messages"] = processed_message_list

                    # 2. Check messages in current_patched_dict for file references like "@{filename.txt}"
                    # This list will hold dictionaries that are fully resolved (file content inserted).
                    # One variant patch can expand into multiple conversation dicts if a file ref yields multiple instances.
                    resolved_dicts_for_this_variant: List[Dict[str, Any]] = []
                    variant_had_file_expansion = False

                    # Iterate through a copy of messages to check for file references
                    # We'll assume for now that if multiple messages have file refs, only the first one encountered is expanded.
                    # For more complex scenarios (e.g., Cartesian product of multiple file refs), this logic would need extension.
                    messages_to_scan = current_patched_dict.get("messages", [])
                    for msg_idx, msg_data in enumerate(messages_to_scan):
                        msg_content = msg_data.get("content")
                        if isinstance(msg_content, str) and \
                                msg_content.startswith("@{") and \
                                msg_content.endswith("}"):

                            file_ref_name = msg_content[2:-1]  # Extract filename
                            # Assume the referenced file is located within the agent's specific folder
                            actual_file_path = os.path.join(agent_path, file_ref_name)

                            try:
                                with open(actual_file_path, "r", encoding="utf-8") as frf:
                                    file_content_full = frf.read()

                                # Split file content by the specified delimiter
                                instances_text = file_content_full.split("<END_OF_INSTANCE>")

                                if not file_content_full.strip() or \
                                        (len(instances_text) == 1 and not instances_text[0].strip()):
                                    # Handle empty or effectively empty files
                                    print(
                                        f"Warning: Referenced file '{file_ref_name}' in {filename} (path: {actual_file_path}) is empty or contains no instances. The original reference or an error message will be used.")
                                    # Create one entry with an error/note in the content
                                    error_dict = deepcopy(current_patched_dict)
                                    error_dict["messages"][msg_idx][
                                        "content"] = f"Error: Referenced file '{file_ref_name}' was empty or had no instances."
                                    resolved_dicts_for_this_variant.append(error_dict)
                                else:
                                    for instance_str in instances_text:
                                        cleaned_instance_str = instance_str.strip()
                                        if not cleaned_instance_str:  # Skip empty instances resulting from split
                                            continue

                                        # Create a new conversation dict for this specific instance
                                        instance_specific_dict = deepcopy(current_patched_dict)
                                        # Update the content of the message that had the file reference
                                        instance_specific_dict["messages"][msg_idx]["content"] = cleaned_instance_str
                                        resolved_dicts_for_this_variant.append(instance_specific_dict)

                                variant_had_file_expansion = True
                                # Processed the first file reference found in this variant's messages.
                                # If multiple file refs per variant message list need independent expansion, this break should be removed
                                # and logic adjusted (e.g. recursive expansion or iterative processing).
                                break

                            except FileNotFoundError:
                                print(
                                    f"Error: File reference '{file_ref_name}' not found at {actual_file_path} (referenced in {filename}).")
                                error_dict = deepcopy(current_patched_dict)
                                error_dict["messages"][msg_idx][
                                    "content"] = f"Error: Referenced file '{file_ref_name}' not found."
                                resolved_dicts_for_this_variant.append(error_dict)
                                variant_had_file_expansion = True  # Mark as "handled" to avoid falling through
                                break
                            except Exception as e:
                                print(f"Error processing file reference {actual_file_path}: {e}")
                                error_dict = deepcopy(current_patched_dict)
                                error_dict["messages"][msg_idx][
                                    "content"] = f"Error processing referenced file '{file_ref_name}': {e}"
                                resolved_dicts_for_this_variant.append(error_dict)
                                variant_had_file_expansion = True  # Mark as "handled"
                                break

                    if not variant_had_file_expansion:
                        # If no file reference was found or expanded in this variant,
                        # the current_patched_dict (with modifications from the patch) forms a single conversation.
                        resolved_dicts_for_this_variant.append(current_patched_dict)

                    potential_conversation_dicts.extend(resolved_dicts_for_this_variant)

            else:  # No 'variants' key in base_entry_dict, so process it directly as one conversation
                # Ensure 'variants' key is not accidentally passed to Conversation model if it exists but is empty
                base_copy = deepcopy(base_entry_dict)
                if "variants" in base_copy:
                    del base_copy["variants"]
                potential_conversation_dicts.append(base_copy)

            # Convert all collected dictionaries (fully resolved) to Conversation objects
            for conv_dict in potential_conversation_dicts:
                try:
                    # Ensure 'messages' key exists for Pydantic model, even if empty
                    if 'messages' not in conv_dict:
                        conv_dict['messages'] = []

                    conversation_obj = Conversation(**conv_dict)
                    if prompt:  # If a system prompt is provided, add it to the beginning of messages
                        # Ensure messages list exists on the Pydantic object
                        if conversation_obj.messages is None:  # Should be initialized by Pydantic if type is List[Message]
                            conversation_obj.messages = []
                        conversation_obj.messages.insert(0, Message(role="system", content=prompt))
                    final_conversations.append(conversation_obj)
                except ValidationError as e:
                    print(
                        f"Pydantic Validation Error for conversation in {filename} (derived from base or variant): {e}. Data: {conv_dict}")
                except Exception as e:
                    print(
                        f"Unexpected error creating Conversation object from dict in {filename}: {e}. Data: {conv_dict}")

    return final_conversations


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

    generate_html_report(args, link, results)

    if not errores.empty:
        print(f"{len(errores)} resultados erróneos detectados.")
        sys.exit(1)


if __name__ == "__main__":
    """
    Entry point of the script.

    Calls the `main` function to process command-line arguments and execute the agent.
    """
    main()
