from typing import List

from dotenv import load_dotenv
from langsmith import Client, wrappers
from openai import OpenAI
from pydantic import ValidationError

from copilot.core.etendo_utils import get_etendo_host
from copilot.core.schemas import AssistantSchema
from schemas import Conversation, Message
from utils import (
    send_evaluation_to_supabase,
)
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

    if save_run_id:
        # Ensure system_prompt is correctly accessed if config_agent is a dict
        system_prompt_val = config_agent.get("system_prompt") if isinstance(config_agent, dict) else None
        save_conversation_from_run(
            agent_id, save_run_id, system_prompt_val, base_path=base_path
        )
        return None, None, None, None, None # Added one None for report_html_path

    results, link, dataset_len = evaluate_agent(agent_id, config_agent, repetitions, base_path, skip_evaluators)

    # Generate HTML report and get its local path and timestamp
    report_html_path, report_ts = generate_html_report(args, link, results)

    return results, link, dataset_len, config_agent, report_html_path, report_ts

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
    except Exception as e: # Catch potential errors from AssistantSchema or get_tools_for_agent
        print(f"Error processing agent configuration for tools: {e}")
        available_tools_openai = []


    conversations = load_conversations(agent_id, base_path=base_path, prompt=prompt)
    examples = convert_conversations_to_examples(
        conversations, model, tools=available_tools_openai
    )
    ls_client_eval = Client()
    examples_md5 = calc_md5(examples)
    dat_name = f"Dataset for evaluation {agent_id} MD5:{examples_md5}"
    dataset = None
    try:
        dataset = ls_client_eval.read_dataset(dataset_name=dat_name)
    except Exception: # Catches if dataset not found, which is expected
        pass # dataset remains None

    if dataset is None or dataset.id is None:
        print(f"Dataset '{dat_name}' not found, creating new one.")
        dataset = ls_client_eval.create_dataset(dataset_name=dat_name)
        ls_client_eval.create_examples(
            dataset_id=dataset.id,
            examples=examples,
        )
    else:
        print(f"Using existing dataset: '{dat_name}' with ID: {dataset.id}")


    print("\n" * 3) # Reduced excessive newlines
    print(f"Starting evaluation for agent: {agent_id} on dataset: {dataset.name} with {k} repetitions.")
    results = ls_client_eval.evaluate(
        target, # This 'target' function should be defined or imported
        data=dataset.name,
        evaluators=get_evaluators() if not skip_evaluators else None,
        experiment_prefix=f"{agent_id}-{int(time.time())}", # More unique prefix
        description=f"Evaluation for agent {agent_id}",
        max_concurrency=4,
        num_repetitions=k,
    )
    print(f"Evaluation finished. Experiment link: {results.url if hasattr(results, 'url') else 'N/A'}")
    return results, getattr(results, 'url', results.experiment_name if hasattr(results, 'experiment_name') else 'N/A'), len(examples)


# In execute.py
import argparse
import json
import os
import sys
import time
import subprocess  # <--- Añadir subprocess
from copy import deepcopy
from typing import List, Dict, Any
# ... (resto de tus importaciones) ...
from utils import (
    calc_md5,
    generate_html_report,
    get_agent_config,
    get_tools_for_agent,
    save_conversation_from_run,
    tool_to_openai_function,
    prepare_report_data,
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
            timeout=5  # Timeout para evitar que se cuelgue indefinidamente
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
    except Exception as e:
        # print(f"Advertencia: Ocurrió un error inesperado al obtener la rama Git: {e}")
        return "unknown_git_exception"
    finally:
        os.chdir(original_cwd)  # Siempre restaurar el directorio de trabajo original




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
    parser.add_argument("--dataset", help="Base path for dataset", default="../com.etendoerp.copilot/dataset")
    parser.add_argument("--agent_id", required=True, help="Agent ID")
    parser.add_argument("--k", help="Executions per 'conversation'", default=DEFAULT_EXECUTIONS, type=int)
    parser.add_argument("--save", help="LangSmith Run ID to extract and save conversation")
    parser.add_argument("--skip_evaluators", help="Skip custom evaluators", action="store_true")
    parser.add_argument("--project_name", help="Project name for Supabase payload", default="default_project")


    args = parser.parse_args()
    if args.envfile:
        print(f"Loading environment variables from {args.envfile}.")
        load_dotenv(args.envfile, verbose=True)

    if not args.token and not (args.user and args.password):
        print("Error: You must provide a token or a username and password.")
        sys.exit(1)
    if args.save and not args.agent_id:
        print("Error: You must provide an agent_id when using the --save flag.")
        sys.exit(1)

    if not os.path.exists("./evaluation_output"):
        os.makedirs("evaluation_output", exist_ok=True)

    # exec_agent now returns report_html_path and report_ts
    exec_results = exec_agent(args)
    if args.save is not None: # Check if in "save_conversation_from_run" mode
        print("Conversation saved.")
        return

    # Unpack results if not in save mode
    results, link, dataset_length, agent_config_dict, report_html_path, report_ts = exec_results


    # Read HTML content from the generated file
    html_content_str = None
    if report_html_path and os.path.exists(report_html_path):
        try:
            with open(report_html_path, 'r', encoding='utf-8') as f:
                html_content_str = f.read()
            print(f"Contenido del reporte HTML leído desde: {report_html_path}")
        except Exception as e:
            print(f"Error al leer el contenido del archivo HTML {report_html_path}: {e}")
    else:
        print(f"Archivo de reporte HTML no encontrado o no generado: {report_html_path}")


    report_data_for_json = prepare_report_data(results)
    avg_score = report_data_for_json.get("avg_score")
    dataset_git_branch = get_git_branch_for_directory(args.dataset)

    summary_data = {
        "score": float(f"{avg_score:.2f}") if avg_score is not None else None,
        "dataset_length": dataset_length,
        "agent_id": args.agent_id,
        "agent_name": agent_config_dict.get("name", "N/A") if isinstance(agent_config_dict, dict) else "N/A",
        "branch": dataset_git_branch,
        "threads": int(args.k), # Ensure args.k is int
        "experiment_link": link,
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        # "html_report_url": None, # No longer using Supabase storage URL here
        "html_report_content": html_content_str # Add the HTML content directly (can be very large)
    }

    json_output_file = f"evaluation_output/summary_{args.agent_id}_{report_ts}.json" # Use report_ts
    with open(json_output_file, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=4)
    print(f"Summary JSON generated: {os.path.abspath(json_output_file)}")

    # Determine project name for Supabase payload
    project_name_for_payload = args.project_name
    if not project_name_for_payload or project_name_for_payload == "default_project":
        try:
            project_name_for_payload = os.path.basename(os.path.dirname(os.path.abspath(args.dataset)))
        except Exception:
            project_name_for_payload = "unknown_project"

    payload_for_supabase = {
        "project": project_name_for_payload,
        "agent": summary_data["agent_id"],
        "agent_name": summary_data["agent_name"],
        "branch": summary_data["branch"],
        "score": summary_data["score"],
        "dataset_size": summary_data["dataset_length"],
        "threads": summary_data["threads"],
        "experiment": "dataset",
        "html_report_content": html_content_str # Add the HTML content string
        # "html_report_url": None, # Remove or set to null if column still exists but unused
    }

    print(f"Enviando payload a Supabase (HTML embebido, tamaño aproximado: {len(html_content_str)/1024 if html_content_str else 0:.2f} KB)...")
    send_evaluation_to_supabase(payload_for_supabase)

    if hasattr(results, 'to_pandas'):
        res_pand = results.to_pandas()
        output_file_csv = f"evaluation_output/results_{args.agent_id}_{report_ts}.csv"
        res_pand.to_csv(output_file_csv, index=False)
        print(f"CSV report generado: {os.path.abspath(output_file_csv)}")
        errores = res_pand[
            (res_pand["feedback.correctness"] == False) |
            (res_pand["error"].notnull()) |
            (res_pand["outputs.answer"].isnull())
        ]
        if not errores.empty:
            print(f"{len(errores)} resultados erróneos detectados.")
        else:
            print("No critical errors detected in results.")
    else:
        print("Results object does not have 'to_pandas' method, skipping CSV generation.")


if __name__ == "__main__":
    main()