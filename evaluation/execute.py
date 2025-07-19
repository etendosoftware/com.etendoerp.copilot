# In execute.py
import json
import os
import subprocess  # <--- Añadir subprocess
import sys
import time
from typing import Any, List, Optional, Tuple

# Legacy imports for compatibility
from copilot.core.etendo_utils import get_etendo_host
from copilot.core.schemas import AssistantSchema
from dotenv import load_dotenv

# ... (resto de tus importaciones) ...
from utils import (
    calc_md5,
    get_tools_for_agent,
    prepare_report_data,
    save_conversation_from_run,
    send_evaluation_to_supabase,
    tool_to_openai_function,
)

# Import common utilities and specialized execution classes
from .common_utils import ArgumentParser, EvaluationLogger, FileHandler
from .execution_utils import (
    AgentConfigManager,
    AgentEvaluator,
    ConversationLoader,
    ReportGenerator,
)
from .schemas import Conversation

# ...

# Optional dependency handling
try:
    from langsmith import Client, wrappers

    LANGSMITH_AVAILABLE = True
except ImportError:
    LANGSMITH_AVAILABLE = False
    Client = None
    wrappers = None

try:
    from openai import OpenAI

    OPENAI_AVAILABLE = True
except ImportError:
    OPENAI_AVAILABLE = False
    OpenAI = None

try:
    from pydantic import ValidationError

    PYDANTIC_AVAILABLE = True
except ImportError:
    PYDANTIC_AVAILABLE = False
    ValidationError = Exception

DEFAULT_EXECUTIONS = 5


def exec_agent(args):
    """Execute an agent with simplified orchestration using specialized utilities"""
    logger = EvaluationLogger("EXEC_AGENT")

    # Extract configuration
    config = _extract_agent_config(args)

    # Get agent configuration
    agent_config = _get_agent_configuration(config, logger)

    # Handle save conversation mode
    if config["save_run_id"]:
        return _handle_save_conversation(config, agent_config)

    # Evaluate agent
    return _handle_agent_evaluation(args, config, agent_config, logger)


def _extract_agent_config(args) -> dict:
    """Extract configuration from arguments"""
    return {
        "user": args.user,
        "password": args.password,
        "token": args.token,
        "agent_id": args.agent_id,
        "repetitions": int(args.k),
        "save_run_id": args.save,
        "skip_evaluators": args.skip_evaluators,
        "base_path": args.dataset,
        "etendo_host": getattr(args, "etendohost", None),
    }


def _get_agent_configuration(config: dict, logger: EvaluationLogger) -> Optional[dict]:
    """Get agent configuration from API"""
    logger.info("Retrieving agent configuration...")

    host = get_etendo_host()
    if config["etendo_host"]:
        host = config["etendo_host"]

    config_manager = AgentConfigManager(logger)
    return config_manager.get_agent_config(
        config["agent_id"], host, config["token"], config["user"], config["password"]
    )


def _handle_save_conversation(
    config: dict, agent_config: Optional[dict]
) -> Tuple[None, None, None, None, None]:
    """Handle save conversation mode"""
    system_prompt = None
    if isinstance(agent_config, dict):
        system_prompt = agent_config.get("system_prompt")

    save_conversation_from_run(
        config["agent_id"], config["save_run_id"], system_prompt, base_path=config["base_path"]
    )

    return None, None, None, None, None


def _handle_agent_evaluation(
    args, config: dict, agent_config: Optional[dict], logger: EvaluationLogger
) -> Tuple[Any, str, int, Optional[dict], str, str]:
    """Handle agent evaluation workflow"""
    evaluator = AgentEvaluator(logger)

    # Run evaluation
    results, eval_link, dataset_len = evaluator.evaluate_agent(
        config["agent_id"],
        agent_config,
        config["repetitions"],
        config["base_path"],
        config["skip_evaluators"],
    )

    # Generate report
    report_generator = ReportGenerator(logger)
    report_html_path, report_ts = report_generator.generate_html_report(args, eval_link, results)

    return results, eval_link, dataset_len, agent_config, report_html_path, report_ts


def load_conversations(agent_id: str, base_path: str, prompt: str = None) -> List[Conversation]:
    """Load conversations using specialized ConversationLoader utility"""
    logger = EvaluationLogger("LOAD_CONV")
    loader = ConversationLoader(logger)

    return loader.load_conversations(agent_id, base_path, prompt)


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


def _process_html_report(report_html_path: str, logger: EvaluationLogger) -> str:
    """Process HTML report file and return content"""
    html_content_str = None
    if report_html_path and os.path.exists(report_html_path):
        try:
            with open(report_html_path, "r", encoding="utf-8") as f:
                html_content_str = f.read()
            logger.info(f"HTML report content read from: {report_html_path}")
        except Exception as e:
            logger.error(f"Error reading HTML report file {report_html_path}: {e}")
    else:
        logger.warning(f"HTML report file not found: {report_html_path}")
    return html_content_str


def _create_summary_data(results, dataset_length, args, link, html_content_str) -> dict:
    """Create summary data dictionary"""
    report_data_for_json = prepare_report_data(results)
    avg_score = report_data_for_json.get("avg_score")
    dataset_git_branch = get_git_branch_for_directory(args.dataset)

    return {
        "score": float(f"{avg_score:.2f}") if avg_score is not None else None,
        "dataset_length": dataset_length,
        "agent_id": args.agent_id,
        "agent_name": "N/A",  # Simplified for now
        "branch": dataset_git_branch,
        "threads": int(args.k),
        "experiment_link": link,
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "html_report_content": html_content_str,
    }


def _generate_csv_report(results, args, report_ts: str, logger: EvaluationLogger):
    """Generate CSV report from results"""
    if hasattr(results, "to_pandas"):
        res_pand = results.to_pandas()
        output_file_csv = f"evaluation_output/results_{args.agent_id}_{report_ts}.csv"
        res_pand.to_csv(output_file_csv, index=False)
        logger.info(f"CSV report generated: {os.path.abspath(output_file_csv)}")

        errores = res_pand[
            (res_pand["feedback.correctness"] is False)
            | (res_pand["error"].notnull())
            | (res_pand["outputs.answer"].isnull())
        ]

        if not errores.empty:
            logger.warning(f"{len(errores)} error results detected")
        else:
            logger.info("No critical errors detected in results")
    else:
        logger.warning("Results object does not have 'to_pandas' method, skipping CSV generation")


def main():
    """Main function to process user parameters and execute the agent."""
    logger = EvaluationLogger("MAIN")
    parser = ArgumentParser()

    # Parse and validate arguments
    args = parser.parse_args()
    logger.info(f"Starting evaluation for agent: {args.agent_id}")

    # Setup environment
    if args.envfile:
        logger.info(f"Loading environment variables from {args.envfile}")
        load_dotenv(args.envfile, verbose=True)

    # Validate authentication
    if not args.token and not (args.user and args.password):
        logger.error("You must provide a token or a username and password")
        sys.exit(1)

    if args.save and not args.agent_id:
        logger.error("You must provide an agent_id when using the --save flag")
        sys.exit(1)

    # Create output directory
    file_handler = FileHandler(logger)
    file_handler.ensure_directory("./evaluation_output")

    # Execute agent evaluation
    exec_results = exec_agent(args)

    # Handle save conversation mode
    if args.save is not None:
        logger.info("Conversation saved successfully")
        return

    # Process results
    _process_evaluation_results(exec_results, args, logger)


def _process_evaluation_results(exec_results, args, logger):
    """Process evaluation results and generate reports"""
    # Unpack results
    results, link, dataset_length, _, report_html_path, report_ts = exec_results

    # Process HTML report
    html_content_str = _process_html_report(report_html_path, logger)

    # Create and save summary data
    summary_data = _create_summary_data(results, dataset_length, args, link, html_content_str)

    json_output_file = f"evaluation_output/summary_{args.agent_id}_{report_ts}.json"
    with open(json_output_file, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=4)
    logger.info(f"Summary JSON generated: {os.path.abspath(json_output_file)}")

    # Send to Supabase
    _send_to_supabase(summary_data, html_content_str, args, logger)

    # Generate CSV report
    _generate_csv_report(results, args, report_ts, logger)


def _send_to_supabase(summary_data, html_content_str, args, logger):
    """Send evaluation results to Supabase"""
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
        "html_report_content": html_content_str,
    }

    logger.info(
        f"Sending payload to Supabase (HTML size: {len(html_content_str)/1024 if html_content_str else 0:.2f} KB)"
    )
    send_evaluation_to_supabase(payload_for_supabase)


if __name__ == "__main__":
    main()
