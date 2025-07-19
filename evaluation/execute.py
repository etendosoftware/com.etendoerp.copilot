from typing import List, Dict, Any

from copilot.core.etendo_utils import get_etendo_host
from copilot.core.schemas import AssistantSchema
from dotenv import load_dotenv
from langsmith import Client, wrappers
from openai import OpenAI
from pydantic import ValidationError
from schemas import Conversation, Message
from utils import (
    send_evaluation_to_supabase,
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

    if save_run_id:
        # Ensure system_prompt is correctly accessed if config_agent is a dict
        system_prompt_val = config_agent.get("system_prompt") if isinstance(config_agent, dict) else None
        save_conversation_from_run(agent_id, save_run_id, system_prompt_val, base_path=base_path)
        return None, None, None, None, None  # Added one None for report_html_path

    results, link, dataset_len = evaluate_agent(
        agent_id, config_agent, repetitions, base_path, skip_evaluators
    )

    # Generate HTML report and get its local path and timestamp
    report_html_path, report_ts = generate_html_report(args, link, results)

    return results, link, dataset_len, config_agent, report_html_path, report_ts


# --- ADDED HELPER FUNCTIONS ---

def _load_json_file(filepath: str) -> List[Dict[str, Any]] | None:
    """Loads and decodes a JSON file, handling common errors."""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON from file {os.path.basename(filepath)}: {e}")
        return None
    except Exception as e:
        print(f"Error reading file {os.path.basename(filepath)}: {e}")
        return None


# --- ADDED HELPER FUNCTIONS (New) ---

def _patch_messages(base_messages: List[Dict[str, Any]], patch_messages: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Applies message-specific patches. Messages with matching 'id' are replaced;
    others are appended.
    """
    processed_message_list = [deepcopy(m) for m in base_messages]

    for msg_data_from_patch in patch_messages:
        found_and_replaced = False
        if msg_data_from_patch.get("id"):
            for i, existing_msg_data in enumerate(processed_message_list):
                if existing_msg_data.get("id") == msg_data_from_patch["id"]:
                    processed_message_list[i] = deepcopy(msg_data_from_patch)
                    found_and_replaced = True
                    break
        if not found_and_replaced:
            processed_message_list.append(deepcopy(msg_data_from_patch))

    return processed_message_list


# --- REFACTORED _apply_variant_patch FUNCTION ---

def _apply_variant_patch(base_entry: Dict[str, Any], variant_patch: Dict[str, Any]) -> Dict[str, Any]:
    """
    Applies a variant patch to a base conversation entry.
    Handles general key-value overwrites and special message patching.
    """
    patched_dict = deepcopy(base_entry)

    # 'variants' is a processing instruction, remove it if present
    patched_dict.pop("variants", None)  # Using .pop() is cleaner and safer

    # Apply general patch items (overwrite base values)
    for key, value in variant_patch.items():
        if key != "messages":  # 'messages' are handled specially
            patched_dict[key] = deepcopy(value)

    # Apply message patching if 'messages' are specified in the variant
    if "messages" in variant_patch:
        base_messages = patched_dict.get("messages", [])
        patched_dict["messages"] = _patch_messages(base_messages, variant_patch["messages"])

    return patched_dict


# --- ADDED HELPER FUNCTIONS (New for _resolve_file_references) ---

def _read_file_content_for_reference(file_path: str, file_ref_name: str, original_filename: str) -> str | None:
    """
    Reads the content of a file referenced by a conversation message.
    Handles FileNotFoundError and other potential read errors.
    """
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            return f.read()
    except FileNotFoundError:
        print(f"Error: File reference '{file_ref_name}' not found at {file_path} (referenced in {original_filename}).")
        return None
    except Exception as e:
        print(f"Error processing file reference {file_path}: {e}")
        return None


def _process_file_instances(
        conversation_dict: Dict[str, Any],
        msg_idx: int,
        file_content_full: str,
        file_ref_name: str,
        original_filename: str
) -> List[Dict[str, Any]]:
    """
    Splits file content into instances and creates new conversation dictionaries for each.
    Handles empty file content and returns appropriate error messages or expanded instances.
    """
    resolved_instances: List[Dict[str, Any]] = []
    instances_text = file_content_full.split("<END_OF_INSTANCE>")

    if not file_content_full.strip() or (len(instances_text) == 1 and not instances_text[0].strip()):
        print(
            f"Warning: Referenced file '{file_ref_name}' in {original_filename} (path: {os.path.dirname(file_ref_name) or '.'}/{file_ref_name}) is empty or contains no instances."
        )
        error_dict = deepcopy(conversation_dict)
        error_dict["messages"][msg_idx][
            "content"] = f"Error: Referenced file '{file_ref_name}' was empty or had no instances."
        resolved_instances.append(error_dict)
    else:
        for instance_str in instances_text:
            cleaned_instance_str = instance_str.strip()
            if not cleaned_instance_str:
                continue
            instance_specific_dict = deepcopy(conversation_dict)
            instance_specific_dict["messages"][msg_idx]["content"] = cleaned_instance_str
            resolved_instances.append(instance_specific_dict)

    return resolved_instances


# --- REFACTORED _resolve_file_references FUNCTION ---

def _resolve_file_references(conversation_dict: Dict[str, Any], agent_path: str, filename: str) -> List[Dict[str, Any]]:
    """
    Resolves file references within messages of a single conversation dictionary.
    Expands a conversation into multiple ones if a file reference is found and has multiple instances.
    Returns a list of conversation dictionaries (original or expanded/error-handled).
    """
    resolved_conversations: List[Dict[str, Any]] = []
    messages = conversation_dict.get("messages", [])

    for msg_idx, msg_data in enumerate(messages):
        msg_content = msg_data.get("content")

        # Check if the message content is a file reference
        if isinstance(msg_content, str) and msg_content.startswith("@{") and msg_content.endswith("}"):
            file_ref_name = msg_content[2:-1]
            actual_file_path = os.path.join(agent_path, file_ref_name)

            file_content_full = _read_file_content_for_reference(actual_file_path, file_ref_name, filename)

            if file_content_full is None:  # Error reading file (FileNotFound or other)
                # Create an error dict if file not found or reading failed
                error_dict = deepcopy(conversation_dict)
                error_dict["messages"][msg_idx][
                    "content"] = f"Error: Referenced file '{file_ref_name}' could not be read."
                resolved_conversations.append(error_dict)
            else:
                # Process file content, splitting into instances
                instances = _process_file_instances(conversation_dict, msg_idx, file_content_full, file_ref_name,
                                                    filename)
                resolved_conversations.extend(instances)

            # Assuming only the first file reference found per dict is expanded.
            # If multiple file refs per conversation should lead to a Cartesian product,
            # this logic would need significant adjustment (e.g., recursive calls).
            return resolved_conversations  # Exit after first expansion

    # If no file references were found or expanded, return the original conversation dict
    resolved_conversations.append(conversation_dict)
    return resolved_conversations


def _process_conversation_dict(conv_dict: Dict[str, Any], prompt: str | None, filename: str) -> Conversation | None:
    """
    Converts a dictionary to a Conversation object, applies a system prompt if provided,
    and handles Pydantic validation errors.
    """
    try:
        if "messages" not in conv_dict:
            conv_dict["messages"] = []

        conversation_obj = Conversation(**conv_dict)
        if prompt:
            # Ensure messages list exists and prepend the system prompt
            if conversation_obj.messages is None:
                conversation_obj.messages = []
            conversation_obj.messages.insert(0, Message(role="system", content=prompt))
        return conversation_obj
    except ValidationError as e:
        print(f"Pydantic Validation Error for conversation in {filename}: {e}. Data: {conv_dict}")
        return None
    except Exception as e:
        print(f"Unexpected error creating Conversation object from dict in {filename}: {e}. Data: {conv_dict}")
        return None


# --- REFACTORED load_conversations FUNCTION ---
# --- ADDED HELPER FUNCTIONS ---

def _get_potential_conversation_dicts(
        base_entry_dict: Dict[str, Any],
        agent_path: str,
        filename: str
) -> List[Dict[str, Any]]:
    """
    Processes a single base conversation entry, applying variants if present,
    and resolving file references to generate a list of potential conversation dictionaries.
    """
    potential_conversation_dicts: List[Dict[str, Any]] = []

    if "variants" in base_entry_dict and base_entry_dict["variants"]:
        for variant_patch_dict in base_entry_dict["variants"]:
            patched_dict = _apply_variant_patch(base_entry_dict, variant_patch_dict)
            resolved_dicts = _resolve_file_references(patched_dict, agent_path, filename)
            potential_conversation_dicts.extend(resolved_dicts)
    else:
        # If no variants, process the base entry directly
        base_copy = deepcopy(base_entry_dict)
        base_copy.pop("variants", None)  # Ensure 'variants' key is not passed to Conversation model
        resolved_dicts = _resolve_file_references(base_copy, agent_path, filename)
        potential_conversation_dicts.extend(resolved_dicts)

    return potential_conversation_dicts


# --- REFACTORED load_conversations FUNCTION ---

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
    validate_dataset_folder(agent_path)

    final_conversations: List[Conversation] = []

    for filename in os.listdir(agent_path):
        if not filename.endswith(".json"):
            print(f"Skipping non-JSON file: {filename}")
            continue

        filepath = os.path.join(agent_path, filename)
        raw_data_list = _load_json_file(filepath)
        if raw_data_list is None:
            continue

        for base_entry_dict in raw_data_list:
            # Delegate the complex logic of handling variants and file references
            # for a single base entry to a new helper function.
            potential_conversation_dicts = _get_potential_conversation_dicts(
                base_entry_dict, agent_path, filename
            )

            # Convert all collected dictionaries to Conversation objects
            for conv_dict in potential_conversation_dicts:
                conversation_obj = _process_conversation_dict(conv_dict, prompt, filename)
                if conversation_obj:
                    final_conversations.append(conversation_obj)

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
    prompt = agent_config.get("system_prompt")
    model = agent_config.get("model")

    # Ensure agent_config is a dict before passing to AssistantSchema
    if not isinstance(agent_config, dict):
        print(f"Error: agent_config is not a dictionary. Received: {type(agent_config)}")
        # Handle error appropriately, e.g., return default/error values or raise exception
        # For now, let's try to make it work or provide a clear error for this example.
        # If it's an object that can be dict-like, you might try vars(agent_config)
        # but this depends heavily on what get_agent_config actually returns.
        # Assuming it's a dict for now based on .get() usage.
        agent_config_dict_for_schema = {}
    else:
        agent_config_dict_for_schema = agent_config

    try:
        agent_config_assch = AssistantSchema(**agent_config_dict_for_schema)
        available_tools_objects = get_tools_for_agent(agent_config_assch)
        available_tools_openai = [tool_to_openai_function(tool) for tool in available_tools_objects]
    except Exception as e:  # Catch potential errors from AssistantSchema or get_tools_for_agent
        print(f"Error processing agent configuration for tools: {e}")
        available_tools_openai = []

    conversations = load_conversations(agent_id, base_path=base_path, prompt=prompt)
    examples = convert_conversations_to_examples(conversations, model, tools=available_tools_openai)
    ls_client_eval = Client()
    examples_md5 = calc_md5(examples)
    dat_name = f"Dataset for evaluation {agent_id} MD5:{examples_md5}"
    dataset = None
    try:
        dataset = ls_client_eval.read_dataset(dataset_name=dat_name)
    except Exception:  # Catches if dataset not found, which is expected
        pass  # dataset remains None

    if dataset is None or dataset.id is None:
        print(f"Dataset '{dat_name}' not found, creating new one.")
        dataset = ls_client_eval.create_dataset(dataset_name=dat_name)
        ls_client_eval.create_examples(
            dataset_id=dataset.id,
            examples=examples,
        )
    else:
        print(f"Using existing dataset: '{dat_name}' with ID: {dataset.id}")

    print("\n" * 3)  # Reduced excessive newlines
    print(f"Starting evaluation for agent: {agent_id} on dataset: {dataset.name} with {k} repetitions.")
    results = ls_client_eval.evaluate(
        target,  # This 'target' function should be defined or imported
        data=dataset.name,
        evaluators=get_evaluators() if not skip_evaluators else None,
        experiment_prefix=f"{agent_id}-{int(time.time())}",  # More unique prefix
        description=f"Evaluation for agent {agent_id}",
        max_concurrency=4,
        num_repetitions=k,
    )
    print(f"Evaluation finished. Experiment link: {results.url if hasattr(results, 'url') else 'N/A'}")
    return (
        results,
        getattr(results, "url", results.experiment_name if hasattr(results, "experiment_name") else "N/A"),
        len(examples),
    )


# In execute.py
import argparse
import json
import os
import subprocess  # <--- Añadir subprocess
import sys
import time
from copy import deepcopy
from typing import Any, Dict, List

# ... (resto de tus importaciones) ...
from utils import (
    calc_md5,
    generate_html_report,
    get_agent_config,
    get_tools_for_agent,
    prepare_report_data,
    save_conversation_from_run,
    tool_to_openai_function,
)

# ...

DEFAULT_EXECUTIONS = 5  # Asegúrate que esta constante está definida


def get_git_branch_for_directory(directory_path: str) -> str:
    """
    Intenta obtener la rama Git actual para el directorio dado.
    Busca el repositorio Git raíz subiendo desde el directory_path.
    """
    original_cwd = os.getcwd()
    current_path = os.path.abspath(directory_path)

    # Buscar el directorio .git subiendo en la jerarquía
    git_repo_path = None
    while current_path != os.path.dirname(current_path):  # Mientras no lleguemos a la raíz del sistema
        if os.path.isdir(os.path.join(current_path, ".git")):
            git_repo_path = current_path
            break
        current_path = os.path.dirname(current_path)

    # Verificar si se encontró un .git en la raíz (por si acaso)
    if not git_repo_path and os.path.isdir(os.path.join(current_path, ".git")):
        git_repo_path = current_path

    if not git_repo_path:
        # print(f"Advertencia: El directorio {directory_path} no parece estar dentro de un repositorio Git.")
        return "not_a_git_repo"

    try:
        os.chdir(git_repo_path)  # Cambiar al directorio raíz del repo para el comando git
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True,
            text=True,
            check=False,  # No lanzar excepción en error para manejarlo nosotros
            timeout=5,  # Timeout para evitar que se cuelgue indefinidamente
        )
        if result.returncode == 0:
            return result.stdout.strip()
        else:
            # print(f"Advertencia: No se pudo obtener la rama Git para {git_repo_path}. Error: {result.stderr.strip()}")
            if "fatal: not a git repository" in result.stderr:
                return "not_a_git_repo"  # Puede que .git sea un archivo o algo inesperado
            return "unknown_git_branch_error"
    except FileNotFoundError:
        # print("Advertencia: Comando 'git' no encontrado. Asegúrate de que Git esté instalado y en el PATH.")
        return "git_not_found"
    except subprocess.TimeoutExpired:
        # print(f"Advertencia: El comando git para obtener la rama en {git_repo_path} tardó demasiado.")
        return "git_timeout"
    except Exception:
        # print(f"Advertencia: Ocurrió un error inesperado al obtener la rama Git: {e}")
        return "unknown_git_exception"
    finally:
        os.chdir(original_cwd)  # Siempre restaurar el directorio de trabajo original


# --- ADDED HELPER FUNCTIONS ---

def _read_html_content(file_path: str) -> str | None:
    """Reads HTML content from a given file path, handling errors."""
    if file_path and os.path.exists(file_path):
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                return f.read()
        except Exception as e:
            print(f"Error reading HTML file {file_path}: {e}")
    else:
        print(f"HTML report file not found or not generated: {file_path}")
    return None


def _setup_environment(args: argparse.Namespace):
    """Loads environment variables and creates necessary output directories."""
    if args.envfile:
        print(f"Loading environment variables from {args.envfile}.")
        load_dotenv(args.envfile, verbose=True)
    os.makedirs("evaluation_output", exist_ok=True)


def _validate_arguments(args: argparse.Namespace):
    """Validates command-line arguments and exits if invalid combinations are provided."""
    if not args.token and not (args.user and args.password):
        print("Error: You must provide a token or a username and password.")
        sys.exit(1)
    if args.save and not args.agent_id:
        print("Error: You must provide an agent_id when using the --save flag.")
        sys.exit(1)


def _build_summary_data(
        args: argparse.Namespace,
        exec_results: tuple,
        html_content_str: str | None
) -> dict:
    """Builds the summary data dictionary for JSON output."""
    # Unpack results for clarity
    results, link, dataset_length, agent_config_dict, _, _ = exec_results

    report_data_for_json = prepare_report_data(results)
    avg_score = report_data_for_json.get("avg_score")
    dataset_git_branch = get_git_branch_for_directory(args.dataset)

    return {
        "score": float(f"{avg_score:.2f}") if avg_score is not None else None,
        "dataset_length": dataset_length,
        "agent_id": args.agent_id,
        "agent_name": agent_config_dict.get("name", "N/A") if isinstance(agent_config_dict, dict) else "N/A",
        "branch": dataset_git_branch,
        "threads": int(args.k),
        "experiment_link": link,
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "html_report_content": html_content_str,
    }


def _save_summary_json(args: argparse.Namespace, summary_data: dict, report_ts: int):
    """Saves the summary data to a JSON file."""
    json_output_file = f"evaluation_output/summary_{args.agent_id}_{report_ts}.json"
    with open(json_output_file, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=4)
    print(f"Summary JSON generated: {os.path.abspath(json_output_file)}")


def _determine_project_name(args: argparse.Namespace) -> str:
    """Determines the project name for the Supabase payload."""
    if args.project_name and args.project_name != "default_project":
        return args.project_name
    try:
        # Uses the parent directory name of the dataset path as the project name
        return os.path.basename(os.path.dirname(os.path.abspath(args.dataset)))
    except Exception:
        return "unknown_project"


def _send_supabase_payload(summary_data: dict, html_content_str: str | None, project_name: str):
    """Sends evaluation data to Supabase."""
    payload_for_supabase = {
        "project": project_name,
        "agent": summary_data["agent_id"],
        "agent_name": summary_data["agent_name"],
        "branch": summary_data["branch"],
        "score": summary_data["score"],
        "dataset_size": summary_data["dataset_length"],
        "threads": summary_data["threads"],
        "experiment": "dataset",
        "html_report_content": html_content_str,  # HTML content directly embedded
    }
    print(
        f"Sending payload to Supabase (HTML embedded, approx size: {len(html_content_str) / 1024 if html_content_str else 0:.2f} KB)..."
    )
    send_evaluation_to_supabase(payload_for_supabase)


def _generate_csv_report(results: object, args: argparse.Namespace, report_ts: int):
    """Generates a CSV report from results if the object supports it, and reports errors."""
    if hasattr(results, "to_pandas"):
        res_pand = results.to_pandas()
        output_file_csv = f"evaluation_output/results_{args.agent_id}_{report_ts}.csv"
        res_pand.to_csv(output_file_csv, index=False)
        print(f"CSV report generated: {os.path.abspath(output_file_csv)}")

        # Identify and report errors based on feedback correctness, error presence, or null answers
        errores = res_pand[
            (res_pand.get("feedback.correctness") == False) |  # Use .get() for safety or check column existence
            (res_pand["error"].notnull()) |
            (res_pand["outputs.answer"].isnull())
            ]
        if not errores.empty:
            print(f"{len(errores)} erroneous results detected.")
        else:
            print("No critical errors detected in results.")
    else:
        print("Results object does not have 'to_pandas' method, skipping CSV generation.")


# --- REFACTORED MAIN FUNCTION ---
def main():
    """
    Main function to process user parameters and execute the agent.

    This function parses command-line arguments, validates the provided inputs,
    and orchestrates the execution of the agent, report generation, and data
    upload to Supabase.
    """
    parser = argparse.ArgumentParser(description="Process user parameters.")
    # Define all command-line arguments
    parser.add_argument("--user", help="Username")
    parser.add_argument("--envfile", help="Environment file", default=None)
    parser.add_argument("--etendohost", help="Etendo host.", default=None)
    parser.add_argument("--password", help="User password")
    parser.add_argument("--token", help="Authentication token")
    parser.add_argument("--dataset", help="Base path for dataset", default="../com.etendoerp.copilot/dataset")
    parser.add_argument("--agent_id", required=True, help="Agent ID")
    parser.add_argument("--k", help="Executions per 'conversation'", default=DEFAULT_EXECUTIONS, type=int)
    parser.add_argument("--save", help="LangSmith Run ID to extract and save conversation")
    parser.add_argument("--skip_evaluators", help="Skip custom evaluators", action="store_true")
    parser.add_argument("--project_name", help="Project name for Supabase payload", default="default_project")
    args = parser.parse_args()

    # Setup environment (load .env, create dirs)
    _setup_environment(args)

    # Validate essential arguments (auth, save mode)
    _validate_arguments(args)

    # Execute the agent based on provided arguments
    exec_results = exec_agent(args)

    # Handle "save conversation" mode and exit early if activated
    if args.save is not None:
        print("Conversation saved.")
        return

    # Unpack results for further processing
    results, _, _, _, report_html_path, report_ts = exec_results

    # Read the HTML report content
    html_content_str = _read_html_content(report_html_path)

    # Build and save the summary JSON report
    summary_data = _build_summary_data(args, exec_results, html_content_str)
    _save_summary_json(args, summary_data, report_ts)

    # Determine project name and send payload to Supabase
    project_name_for_payload = _determine_project_name(args)
    _send_supabase_payload(summary_data, html_content_str, project_name_for_payload)

    # Generate CSV report if results object supports it
    _generate_csv_report(results, args, report_ts)


if __name__ == "__main__":
    main()
