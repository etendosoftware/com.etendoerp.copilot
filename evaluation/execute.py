# In execute.py
import argparse
import json
import os
import subprocess  # <--- Añadir subprocess
import sys
import time
from copy import deepcopy
from typing import List

from copilot.core.schemas import AssistantSchema
from copilot.core.utils.etendo_utils import get_etendo_host
from copilot.core.utils.models import get_openai_client
from dotenv import load_dotenv
from langsmith import Client, wrappers
from pydantic import ValidationError
from schemas import Conversation, Message
from utils import (
    calc_md5,
    generate_html_report,
    get_agent_config,
    get_tools_for_agent,
    prepare_report_data,
    save_conversation_from_run,
    send_evaluation_to_supabase,
    tool_to_openai_function,
    validate_dataset_folder,
)

DEFAULT_EXECUTIONS = 5


def _read_json_file(filepath):
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON from {filepath}: {e}")
    except Exception as e:
        print(f"Error reading {filepath}: {e}")
    return None


def _read_file_content(filepath):
    try:
        with open(filepath, "r", encoding="utf-8") as frf:
            return frf.read()
    except Exception as e:
        print(f"Error reading file {filepath}: {e}")
        return None


def _resolve_file_reference(agent_path, msg_idx, msg_data, current_patched_dict):
    msg_content = msg_data.get("content")
    if not (isinstance(msg_content, str) and msg_content.startswith("@{") and msg_content.endswith("}")):
        return None  # No referencia
    file_ref_name = msg_content[2:-1]
    actual_file_path = os.path.join(agent_path, file_ref_name)
    file_content_full = _read_file_content(actual_file_path)
    if file_content_full is None:
        return [
            {
                **deepcopy(current_patched_dict),
                "messages": [
                    (
                        msg
                        if i != msg_idx
                        else {**msg, "content": f"Error: Referenced file '{file_ref_name}' not found."}
                    )
                    for i, msg in enumerate(current_patched_dict["messages"])
                ],
            }
        ]
    instances_text = file_content_full.split("<END_OF_INSTANCE>")
    if not file_content_full.strip() or (len(instances_text) == 1 and not instances_text[0].strip()):
        return [
            {
                **deepcopy(current_patched_dict),
                "messages": [
                    (
                        msg
                        if i != msg_idx
                        else {
                            **msg,
                            "content": f"Error: Referenced file '{file_ref_name}' was empty or had no instances.",
                        }
                    )
                    for i, msg in enumerate(current_patched_dict["messages"])
                ],
            }
        ]
    resolved = []
    for instance_str in instances_text:
        cleaned = instance_str.strip()
        if not cleaned:
            continue
        patched = deepcopy(current_patched_dict)
        patched["messages"][msg_idx]["content"] = cleaned
        resolved.append(patched)
    return resolved


def _apply_variant_patch(base_entry_dict, variant_patch_dict):
    current_patched = deepcopy(base_entry_dict)
    current_patched.pop("variants", None)
    for key, value in variant_patch_dict.items():
        if key != "messages":
            current_patched[key] = deepcopy(value)
    if "messages" not in variant_patch_dict:
        return current_patched
    base_msgs = current_patched.get("messages", [])
    processed_msgs = get_processed_msgs(base_msgs, variant_patch_dict)
    current_patched["messages"] = processed_msgs
    return current_patched


def get_processed_msgs(base_msgs, variant_patch_dict):
    processed_msgs = [deepcopy(m) for m in base_msgs]
    for patch_msg in variant_patch_dict["messages"]:
        replaced = False
        if patch_msg.get("id"):
            for i, msg in enumerate(processed_msgs):
                if msg.get("id") == patch_msg["id"]:
                    processed_msgs[i] = deepcopy(patch_msg)
                    replaced = True
                    break
        if not replaced:
            processed_msgs.append(deepcopy(patch_msg))
    return processed_msgs


def _process_conversation_dict(conv_dict, prompt):
    if "messages" not in conv_dict:
        conv_dict["messages"] = []
    conversation_obj = Conversation(**conv_dict)
    if prompt:
        if conversation_obj.messages is None:
            conversation_obj.messages = []
        conversation_obj.messages.insert(0, Message(role="system", content=prompt))
    return conversation_obj


def _validate_and_prepare_args(args):
    if not args.token and not (args.user and args.password):
        print("Error: You must provide a token or a username and password.")
        sys.exit(1)
    if args.save and not args.agent_id:
        print("Error: You must provide an agent_id when using the --save flag.")
        sys.exit(1)
    if not os.path.exists("./evaluation_output"):
        os.makedirs("evaluation_output", exist_ok=True)


def _get_project_name(args):
    if args.project_name and args.project_name != "default_project":
        return args.project_name
    try:
        return os.path.basename(os.path.dirname(os.path.abspath(args.dataset)))
    except Exception:
        return "unknown_project"


def _save_json_summary(json_output_file, summary_data):
    with open(json_output_file, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=4)
    print(f"Summary JSON generated: {os.path.abspath(json_output_file)}")


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


def load_conversations(agent_id: str, base_path: str, prompt: str = None) -> List[Conversation]:
    agent_path = os.path.join(base_path, agent_id)
    validate_dataset_folder(agent_path)
    final_conversations = []
    for filename in os.listdir(agent_path):
        if not filename.endswith(".json"):
            continue
        filepath = os.path.join(agent_path, filename)
        raw_data_list = _read_json_file(filepath)
        if not raw_data_list:
            continue
        for base_entry_dict in raw_data_list:
            dicts_to_convert = get_dict_to_convert(agent_path, base_entry_dict)
            for conv_dict in dicts_to_convert:
                conv = conv_to_dict(conv_dict, filename, prompt)
                if conv is not None:
                    final_conversations.append(conv)
    return final_conversations


def conv_to_dict(conv_dict, filename, prompt):
    try:
        conv = _process_conversation_dict(conv_dict, prompt)
    except ValidationError as e:
        print(f"Pydantic Validation Error in {filename}: {e}. Data: {conv_dict}")
        conv = None  # Set conv to None if validation fails
    except Exception as e:
        print(f"Error creating Conversation object in {filename}: {e}. Data: {conv_dict}")
        conv = None  # Set conv to None if any other error occurs
    return conv


def get_dict_to_convert(agent_path, base_entry_dict):
    dicts_to_convert = []
    if "variants" in base_entry_dict and base_entry_dict["variants"]:
        for variant_patch in base_entry_dict["variants"]:
            patched = _apply_variant_patch(base_entry_dict, variant_patch)
            file_expanded = False
            for msg_idx, msg in enumerate(patched.get("messages", [])):
                expanded = _resolve_file_reference(agent_path, msg_idx, msg, patched)
                if expanded:
                    dicts_to_convert.extend(expanded)
                    file_expanded = True
                    break  # Expand solo el primer file ref
            if not file_expanded:
                dicts_to_convert.append(patched)
    else:
        base_copy = deepcopy(base_entry_dict)
        base_copy.pop("variants", None)
        dicts_to_convert.append(base_copy)
    return dicts_to_convert


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
    openai_client = wrappers.wrap_openai(get_openai_client())
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


DEFAULT_EXECUTIONS = 5  # Asegúrate que esta constante está definida


def get_git_branch_for_directory(directory_path: str) -> str:
    def find_git_root(path):
        while path != os.path.dirname(path):
            if os.path.isdir(os.path.join(path, ".git")):
                return path
            path = os.path.dirname(path)
        if os.path.isdir(os.path.join(path, ".git")):
            return path
        return None

    original_cwd = os.getcwd()
    repo_root = find_git_root(os.path.abspath(directory_path))
    if not repo_root:
        return "not_a_git_repo"
    try:
        os.chdir(repo_root)
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True,
            text=True,
            check=False,
            timeout=5,
        )
        if result.returncode == 0:
            return result.stdout.strip()
        if "fatal: not a git repository" in result.stderr:
            return "not_a_git_repo"
        return "unknown_git_branch_error"
    except FileNotFoundError:
        return "git_not_found"
    except subprocess.TimeoutExpired:
        return "git_timeout"
    except Exception as e:
        print(f"Warning: Error while getting Git branch for {repo_root}: {e}")
        return "unknown_git_exception"
    finally:
        os.chdir(original_cwd)


def main():
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
    _validate_and_prepare_args(args)

    exec_results = exec_agent(args)
    if args.save is not None:
        print("Conversation saved.")
        return

    results, link, dataset_length, agent_config_dict, report_html_path, report_ts = exec_results
    html_content_str = None
    if report_html_path and os.path.exists(report_html_path):
        try:
            with open(report_html_path, "r", encoding="utf-8") as f:
                html_content_str = f.read()
            print(f"Reporte HTML leído desde: {report_html_path}")
        except Exception as e:
            print(f"Error leyendo HTML {report_html_path}: {e}")
    else:
        print(f"Reporte HTML no generado: {report_html_path}")

    report_data_for_json = prepare_report_data(results)
    avg_score = report_data_for_json.get("avg_score")
    dataset_git_branch = get_git_branch_for_directory(args.dataset)
    summary_data = {
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

    json_output_file = f"evaluation_output/summary_{args.agent_id}_{report_ts}.json"
    _save_json_summary(json_output_file, summary_data)

    project_name_for_payload = _get_project_name(args)
    payload_for_supabase = {
        "project": project_name_for_payload,
        "agent": summary_data["agent_id"],
        "agent_name": summary_data["agent_name"],
        "branch": summary_data["branch"],
        "score": summary_data["score"],
        "dataset_size": summary_data["dataset_length"],
        "threads": summary_data["threads"],
        "experiment": "dataset",
        "html_report_content": html_content_str,
    }
    print(
        f"Enviando payload a Supabase (HTML embebido, tamaño: {len(html_content_str)/1024 if html_content_str else 0:.2f} KB)..."
    )
    send_evaluation_to_supabase(payload_for_supabase)

    if hasattr(results, "to_pandas"):
        res_pand = results.to_pandas()
        output_file_csv = f"evaluation_output/results_{args.agent_id}_{report_ts}.csv"
        res_pand.to_csv(output_file_csv, index=False)
        print(f"CSV report generado: {os.path.abspath(output_file_csv)}")
        errores = res_pand[
            (res_pand["feedback.correctness"] is False)
            | (res_pand["error"].notnull())
            | (res_pand["outputs.answer"].isnull())
        ]
        if not errores.empty:
            print(f"{len(errores)} resultados erróneos detectados.")
        else:
            print("No critical errors detected in results.")
    else:
        print("Results object does not have 'to_pandas' method, skipping CSV generation.")


if __name__ == "__main__":
    main()
