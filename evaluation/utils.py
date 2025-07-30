import hashlib
import html
import json
import os
import sys
import time

import pandas as pd
import requests
from core.utils.etendo_utils import call_etendo, login_etendo
from langchain_core.utils.function_calling import convert_to_openai_function
from langsmith import Client
from openai.types.chat import (
    ChatCompletionAssistantMessageParam,
    ChatCompletionMessageToolCallParam,
    ChatCompletionToolMessageParam,
    ChatCompletionUserMessageParam,
)
from openai.types.chat.chat_completion_message_function_tool_call_param import Function

from evaluation.schemas import Conversation, Message

FILE_NAME = "conversations.json"


def calc_md5(examples):
    """
    Calculates the SHA-256 hash of a serialized JSON object.

    This function serializes the given `examples` object into a JSON string with sorted keys
    and ensures all characters are encoded in UTF-8. It then computes the SHA-256 hash of the
    serialized string.

    Args:
        examples (object): The object to be serialized and hashed. Typically, this is a list or dictionary.

    Returns:
        str: The SHA-256 hash of the serialized JSON object, represented as a hexadecimal string.
    """
    serialized = json.dumps(str(examples), sort_keys=True, ensure_ascii=False)
    hasher = hashlib.new("sha256")
    hasher.update(serialized.encode("utf-8"))
    return hasher.hexdigest()


def get_agent_config(
    agent_id,
    host,
    token=None,
    user=None,
    password=None,
):
    """
    Retrieves the configuration of an agent from the Etendo system.

    This function constructs an API endpoint using the provided `agent_id` and sends a GET request
    to retrieve the agent's configuration. If no `token` is provided, it logs in to the Etendo system
    using the provided `user` and `password` to obtain one.

    Args:
        agent_id (str): The unique identifier of the agent whose configuration is being retrieved.
        token (str, optional): The authentication token for accessing the Etendo API. Defaults to None.
        user (str, optional): The username for authentication if `token` is not provided. Defaults to None.
        password (str, optional): The password for authentication if `token` is not provided. Defaults to None.

    Returns:
        dict: The response from the Etendo API containing the agent's configuration.

    Side Effects:
        - Prints the response from the Etendo API to the console.

    Raises:
        Exception: If the API call fails or authentication is unsuccessful.
    """
    etendo_host = host
    endpoint = f"/sws/copilot/structure?app_id={agent_id}"
    if token is None:
        token = login_etendo(etendo_host, user, password)

    response = call_etendo(
        method="GET",
        url=etendo_host,
        endpoint=endpoint,
        body_params={},
        access_token=token,
    )
    print(response)
    return response


def convert_tool_call(tool_call):
    """
    Converts a tool call dictionary to a LangSmith-compatible format.

    This function extracts relevant fields from a tool call dictionary and maps them
    to the `ChatCompletionMessageToolCallParam` model used in the OpenAI format.

    Args:
        tool_call (dict): A dictionary representing a tool call. It should contain the following keys:
            - "id" (str): The ID of the tool call.
            - "function" (dict): A dictionary representing the function called by the model.
                It should contain the following keys:
                - "name" (str): The name of the function.
                - "arguments" (str): The arguments to call the function with, in JSON format.

    Returns:
        ChatCompletionMessageToolCallParam: A `ChatCompletionMessageToolCallParam` object
        in the OpenAI format, containing the extracted fields.
    """
    return ChatCompletionMessageToolCallParam(
        id=tool_call.get("id"),
        function=Function(name=tool_call.get("name"), arguments=json.dumps(tool_call.get("args"))),
        type="function",
    )


def ls_msg_2_openai_msg(msg):
    """
    Converts a LangSmith message format to an OpenAI message format.

    This function extracts relevant fields from a LangSmith message dictionary and maps them
    to the `Message` model used in the OpenAI format.

    Args:
        msg (dict): A dictionary representing a LangSmith message. It should contain a `kwargs` key
                    with the following sub-keys:
                    - "type" (str): The role of the message sender (e.g., "system", "user", "assistant").
                    - "content" (str): The content of the message.
                    - "tool_call_id" (str, optional): The ID of the tool call, if applicable.
                    - "tool_calls" (list, optional): A list of tool calls, if applicable.

    Returns:
        Message: A `Message` object in the OpenAI format, containing the extracted fields.


    ChatCompletionDeveloperMessageParam,
    ChatCompletionSystemMessageParam,
    ChatCompletionUserMessageParam,
    ChatCompletionAssistantMessageParam,
    ChatCompletionToolMessageParam,
    ChatCompletionFunctionMessageParam,
    """
    role = msg.get("kwargs").get("type")
    content = msg.get("kwargs").get("content")
    tool_call_id = msg.get("kwargs").get("tool_call_id", None)
    tool_calls = msg.get("kwargs").get("tool_calls", None)
    if tool_calls is not None and len(tool_calls) > 0:
        tool_calls = [convert_tool_call(tool_call) for tool_call in tool_calls]
    else:
        tool_calls = None
    if role == "human":
        return ChatCompletionUserMessageParam(content=content, role="user")
    if role == "ai":
        return ChatCompletionAssistantMessageParam(content=content, role="assistant", tool_calls=tool_calls)
    if role == "tool":
        return ChatCompletionToolMessageParam(
            content=content,
            role="tool",
            tool_call_id=msg.get("kwargs").get("tool_call_id", None),
        )

    message_openai_format = Message(
        role=role, content=content, tool_call_id=tool_call_id, tool_calls=tool_calls
    )
    return message_openai_format


def get_tools_for_agent(agent_config):
    """
    Retrieves a list of tools configured for a specific agent.

    This function processes the agent's configuration to extract tools from multiple sources,
    including pre-configured tools, tools defined in the agent's structure, and tools generated
    from API specifications.

    Args:
        agent_config (AssistantSchema): The configuration of the agent, which includes details
            about tools, knowledge base specifications, and API specifications.

    Returns:
        list: A list of tools configured for the agent.

    Notes:
        - Tools are retrieved from the following sources:
            1. Pre-configured tools available in the `MultimodelAgent`.
            2. Tools specified in the agent's structure (`agent_config.tools`).
            3. Tools generated from API specifications (`agent_config.specs`).
        - Knowledge base tools are also included if available.
    """
    # Use the unified tool loader to get all tools
    from copilot.core.tool_loader import ToolLoader

    tool_loader = ToolLoader()

    tools = tool_loader.get_all_tools(
        agent_configuration=agent_config,
        enabled_tools=agent_config.tools,
        include_kb_tool=True,
        include_openapi_tools=True,
    )

    return tools


def tool_to_openai_function(tool):
    """
    Converts a tool object into an OpenAI-compatible function format.

    This function takes a tool object, converts it into an OpenAI function specification
    using the `convert_to_openai_function` utility, and wraps it in a dictionary with
    the appropriate type.

    Args:
        tool (object): The tool object to be converted.

    Returns:
        dict: A dictionary representing the tool in OpenAI function format, containing:
            - "type" (str): The type of the item, set to "function".
            - "function" (dict): The OpenAI-compatible function specification.
    """
    toolspec = convert_to_openai_function(tool)
    tool_item = {
        "type": "function",
        "function": toolspec,
    }
    return tool_item


def validate_dataset_folder(agent_path):
    """
    Validates the existence of a dataset folder and initializes it if it does not exist.

    This function checks whether the specified directory exists. If it does not, the function
    creates the directory and initializes a `conversations.json` file with an empty list.

    Args:
        agent_path (str): The path to the dataset folder to validate.

    Side Effects:
        - Creates the specified directory if it does not exist.
        - Creates a `conversations.json` file in the directory with an empty list as its content.

    Prints:
        A message indicating that the directory was created if it did not exist.
    """
    if not os.path.exists(agent_path):
        # If the directory does not exist, create it and initialize the
        # conversations.json file
        os.makedirs(agent_path, exist_ok=True)
        with open(os.path.join(agent_path, FILE_NAME), "w", encoding="utf-8") as f:
            json.dump([], f)
        print(f"The directory {agent_path} was created.")


def save_conversation_from_run(
    agent_id: str, run_id: str, system_prompt: str = None, base_path: str = "dataset"
):
    """
    Extracts a conversation from LangSmith using the provided run ID and saves it in the dataset/<agent_id> folder.

    This function retrieves a run from LangSmith, processes its inputs and outputs to construct a conversation,
    and saves the conversation in a JSON file. The system prompt is excluded if present.

    Args:
        agent_id (str): The unique identifier of the agent.
        run_id (str): The ID of the LangSmith run to extract the conversation from.
        system_prompt (str, optional): The system prompt to exclude from the conversation. Defaults to None.
        base_path (str, optional): The base directory where the dataset folder is located. Defaults to "dataset".

    Side Effects:
        - Creates the dataset folder and `conversations.json` file if they do not exist.
        - Updates the `conversations.json` file with the new conversation.

    Raises:
        SystemExit: If the run cannot be retrieved, contains invalid inputs/outputs, or lacks expected messages.

    Prints:
        - Error messages if the run retrieval or processing fails.
        - A success message indicating where the conversation was saved.
    """
    ls_client = Client()
    try:
        # Retrieve the run from LangSmith
        run = ls_client.read_run(run_id)
    except Exception as e:
        print(f"Error retrieving run {run_id}: {e}")
        sys.exit(1)

    # Extract inputs and outputs from the run
    inputs = run.inputs
    outputs = run.outputs

    if not inputs or not outputs:
        print(f"Run {run_id} does not contain valid inputs or outputs.")
        sys.exit(1)

    # Retrieve the messages from the conversation
    messages = inputs.get("messages", [])
    messages = messages[0] if isinstance(messages, list) and len(messages) > 0 else messages
    if not messages:
        print(f"No messages found in run {run_id}.")
        sys.exit(1)

    # Filter out the system prompt (if present) and convert messages to the Message format
    filtered_messages = []

    for msg in messages:
        role = msg.get("kwargs").get("type")
        if role == "system":
            continue  # Exclude the system prompt
        filtered_messages.append(ls_msg_2_openai_msg(msg))

    generations = outputs.get("generations")
    if not isinstance(generations, list) or len(generations) == 0:
        print(f"No valid generations found in run {run_id}.")
        sys.exit(1)
    response_element = generations[0]
    # if response_element is a list, take the first element
    if isinstance(response_element, list) and len(response_element) > 0:
        response_element = response_element[0]
    expected_response = ls_msg_2_openai_msg(response_element.get("message"))
    if not expected_response:
        print(f"No expected response found in run {run_id}.")
        sys.exit(1)

    # Create the Conversation object
    conversation = Conversation(
        messages=filtered_messages, expected_response=expected_response, run_id=run_id
    )

    # Save the conversation to a JSON file
    agent_path = os.path.join(base_path, agent_id)
    validate_dataset_folder(agent_path)

    # Check if the conversations.json file exists, otherwise create it with an empty list
    conversations_file = os.path.join(agent_path, FILE_NAME)
    if not os.path.exists(conversations_file):
        with open(conversations_file, "w", encoding="utf-8") as f:
            json.dump([], f)

    # Read existing conversations
    with open(conversations_file, "r", encoding="utf-8") as f:
        existing_conversations = json.load(f)

    # Add the new conversation to the list, overwriting if it already exists
    existing_conversations = [conv for conv in existing_conversations if conv["run_id"] != run_id]
    existing_conversations.append(conversation.model_dump(exclude_none=True))

    # Save the updated conversations, overwriting the file and
    # sorting by run_id (None first)
    existing_conversations.sort(key=lambda x: (x["run_id"] is None, x["run_id"]))
    with open(conversations_file, "w", encoding="utf-8") as f:
        json.dump(existing_conversations, f, indent=4)
        # Add a new line at the end of the file
        f.write("\n")
    print(f"Conversation saved in {conversations_file}.")


def shorten(text, max_length=200):
    if not isinstance(text, str):
        text = str(text)
    # Use Tailwind classes for the "Ver más" link if this function is used later
    escaped_text = html.escape(text)

    if len(escaped_text.strip()) == 0:
        return ""

    if len(escaped_text) <= max_length:
        # Simple div, can be styled with Tailwind if needed
        return f"<div class='whitespace-pre-wrap break-words'>{escaped_text}</div>"

    return f"""
        <div class="whitespace-pre-wrap break-words">
            {escaped_text[:max_length]}...
            <a href="#" onclick="this.parentElement.nextElementSibling.style.display='block'; this.parentElement.style.display='none'; return false;" class="text-blue-600 dark:text-blue-400 hover:underline">Ver más</a>
        </div>
        <div style="display:none;" class="whitespace-pre-wrap break-words">{escaped_text}</div>
        """


def create_accordion(df):
    html_items = []
    for i, row in df.iterrows():
        row_style = (
            ' style="background-color:#ffe6e6;"'
            if (
                row.get("feedback.correctness") is False
                or pd.notnull(row.get("error"))
                or pd.isnull(row.get("outputs.answer"))
            )
            else ""
        )

        content = ""
        for col in df.columns:
            value = shorten(row[col])
            content += f"<strong>{col}:</strong><br>{value}<hr>"

        html_items.append(
            f"""
            <div class="accordion-item"{row_style}>
                <h2 class="accordion-header" id="heading{i}">
                    <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#collapse{i}">
                        Resultado {i + 1} - {"❌ Error" if "style" in row_style else "✅ OK"}
                    </button>
                </h2>
                <div id="collapse{i}" class="accordion-collapse collapse">
                    <div class="accordion-body">
                        {content}
                    </div>
                </div>
            </div>
            """
        )
    return "\n".join(html_items)


def prepare_report_data(results_obj):
    """Prepares data from evaluation results for HTML report generation."""
    if not (
        results_obj
        and hasattr(results_obj, "_results")
        and isinstance(results_obj._results, list)
        and results_obj._results
    ):
        # Return a structure indicating an error or empty data
        return {"error": "Invalid or empty 'results_obj' structure.", "table_items": [], "avg_score": None}

    table_items = []
    error_occurred = False
    error_message = ""

    # Process results
    for result_item in results_obj._results:
        error_message, error_occurred = process_result(
            error_message, error_occurred, result_item, table_items
        )

    return build_report_data(error_message, error_occurred, table_items)


def process_result(error_message, error_occurred, result_item, table_items):
    try:
        # Assuming evaluation_results might be a list or a single dict
        evaluation_results_data = result_item.get("evaluation_results", {}).get("results", [])
        if not isinstance(evaluation_results_data, list):  # Handle if 'results' is not a list
            evaluation_results_data = [evaluation_results_data] if evaluation_results_data else []

        child_runs_data = result_item.get("run", {}).child_runs or []

        # Heuristic: Try to match eval results to child runs or inputs
        # This part might need adjustment based on the exact structure of results_obj
        num_items_to_process = max(len(evaluation_results_data), len(child_runs_data))
        if num_items_to_process == 0 and result_item.get(
            "run"
        ):  # Case where there are no child runs but a main run
            num_items_to_process = 1

        for i in range(num_items_to_process):
            score = -1
            eval_comment_content = "N/A"
            input_comment_content = "N/A"
            output_data = "N/A"

            # Get score and eval_comment from evaluation_results
            if i < len(evaluation_results_data) and evaluation_results_data[i]:
                eval_result = evaluation_results_data[i]
                score = getattr(eval_result, "score", -1)
                eval_comment_content = getattr(eval_result, "comment", "N/A")

            # Get input_comment from child_runs or main run inputs
            current_run_for_input = child_runs_data[i] if i < len(child_runs_data) else result_item.get("run")
            input_comment_content = read_input_comment_content(current_run_for_input, input_comment_content)

            # Get output_data from child_runs or main run outputs
            current_run_for_output = (
                child_runs_data[i] if i < len(child_runs_data) else result_item.get("run")
            )
            output_data = read_output_data(current_run_for_output, output_data)

            table_items.append(
                {
                    "comment": input_comment_content,
                    "score": score,
                    "output": output_data,
                    "eval_comment": eval_comment_content,
                }
            )

    except Exception as e:
        error_message = f"Error processing result_item: {str(e)}. Item: {json.dumps(result_item, default=str, indent=2)[:500]}..."  # Log part of the item
        error_occurred = True
        # Add an error placeholder to table_items to acknowledge the item
        table_items.append(
            {
                "comment": f"Error processing item: {str(e)}",
                "score": -1,
                "output": "Error",
                "eval_comment": "Error processing",
            }
        )
        # Decide whether to break or continue: for now, continue to see other items if possible
        # break
    return error_message, error_occurred


def build_report_data(error_message, error_occurred, table_items):
    if error_occurred and not error_message:  # If individual items had errors but no global one
        error_message = "Errors occurred while processing some evaluation items."
    valid_scores = [
        item["score"]
        for item in table_items
        if isinstance(item.get("score"), (int, float)) and item["score"] != -1
    ]
    avg_score = None
    if valid_scores:
        avg_score = sum(valid_scores) / len(valid_scores)
    result = {
        "table_items": table_items,
        "avg_score": avg_score,
        "error": error_message if error_message else None,
    }
    return result


def read_output_data(current_run_for_output, output_data):
    if current_run_for_output:
        outputs = getattr(current_run_for_output, "outputs", {})
        if isinstance(outputs, dict) and "generations" in outputs:
            # Try to extract AI message content
            generations = outputs["generations"]
            ai_message = next(
                (
                    gen.get("message", {}).get("kwargs", {}).get("content")
                    for gen in generations
                    if isinstance(gen, dict) and gen.get("message")
                ),
                None,
            )
            if ai_message:
                output_data = ai_message
            else:  # Fallback to stringifying outputs
                output_data = json.dumps(outputs)
        elif isinstance(outputs, dict):
            output_data = json.dumps(outputs)
        else:
            output_data = str(outputs)
    return output_data


def read_input_comment_content(current_run_for_input, input_comment_content):
    if current_run_for_input:
        inputs = getattr(current_run_for_input, "inputs", {})
        if isinstance(inputs, dict) and "messages" in inputs:
            messages = inputs["messages"]
            # Try to find user message
            user_message = next(
                (
                    msg.get("kwargs", {}).get("content")
                    for msg in messages
                    if isinstance(msg, dict) and msg.get("kwargs", {}).get("type") == "human"
                ),
                None,
            )
            if user_message:
                input_comment_content = user_message
            elif messages:  # Fallback to stringifying messages
                input_comment_content = json.dumps(messages)
            else:
                input_comment_content = json.dumps(inputs)  # Fallback if no messages
        elif isinstance(inputs, dict):
            input_comment_content = json.dumps(inputs)
        else:
            input_comment_content = str(inputs)
    return input_comment_content


def get_score_tailwind_classes(score_value):
    """Returns Tailwind CSS classes for score badges based on EvalDash style."""
    if score_value is None or score_value == -1:  # Error or N/A
        return "px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300"
    if score_value >= 0.9:
        return "px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200"
    if score_value >= 0.8:
        return "px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300"
    if score_value >= 0.7:
        return "px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-yellow-50 text-yellow-700 dark:bg-yellow-950 dark:text-yellow-300"
    if score_value >= 0.6:
        return "px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-orange-50 text-orange-700 dark:bg-orange-950 dark:text-orange-300"
    return "px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300"


def format_report_data_to_html(report_data: dict) -> str:
    """Generates an HTML table and average score display from prepared report data using Tailwind CSS."""
    if report_data.get("error") and not report_data.get("table_items"):  # Only show global error if no items
        return f"""
        <div class="bg-red-100 border-l-4 border-red-500 text-red-700 p-4 rounded my-4" role="alert">
            <p class="font-bold">Error</p>
            <p>{html.escape(report_data['error'])}</p>
        </div>"""

    # If there's a non-blocking error message but we still have items, display it.
    error_message_html = ""
    if report_data.get("error"):
        error_message_html = f"""
        <div class="bg-yellow-100 border-l-4 border-yellow-500 text-yellow-700 p-4 rounded my-4" role="alert">
            <p class="font-bold">Notice</p>
            <p>{html.escape(report_data['error'])}</p>
        </div>"""

    table_items = report_data.get("table_items", [])
    avg_score = report_data.get("avg_score")

    avg_score_html = ""
    if avg_score is not None:
        score_text_color_class = ""
        if avg_score >= 0.9:
            score_text_color_class = "text-green-600 dark:text-green-400"
        elif avg_score >= 0.7:
            score_text_color_class = "text-yellow-500 dark:text-yellow-400"
        elif avg_score >= 0.5:
            score_text_color_class = "text-orange-500 dark:text-orange-400"
        else:
            score_text_color_class = "text-red-500 dark:text-red-400"

        avg_score_html = f"""
        <div class="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6 my-6 text-center">
            <h2 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-1 uppercase">Score Promedio Global</h2>
            <p class="text-4xl font-bold {score_text_color_class}">{(avg_score * 100):.1f}%</p>
        </div>
        """

    if not table_items and not avg_score_html and not error_message_html:  # If truly no data at all
        return """
         <div class="bg-white dark:bg-gray-800 rounded-lg shadow-md p-8 text-center my-6">
            <h2 class="text-xl font-semibold text-gray-900 dark:text-white mb-2">No se encontraron resultados de evaluación</h2>
            <p class="text-gray-600 dark:text-gray-400">No hay datos para mostrar en el reporte.</p>
        </div>
        """

    table_html_rows = []
    for item in table_items:
        score = item.get("score", -1)
        score_val_str = (
            f"{(score * 100):.1f}%" if isinstance(score, (int, float)) and score != -1 else "Error"
        )
        score_classes = get_score_tailwind_classes(score)

        table_html_rows.append(
            f"""
      <tr class="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
        <td class="px-6 py-4 text-sm text-gray-800 dark:text-gray-200">{shorten(item.get('comment', 'N/A'))}</td>
        <td class="px-6 py-4 text-sm text-gray-800 dark:text-gray-200">{shorten(item.get('output', 'N/A'))}</td>
        <td class="px-6 py-4 whitespace-nowrap text-center">
            <span class="{score_classes}">{score_val_str}</span>
        </td>
        <td class="px-6 py-4 text-sm text-gray-800 dark:text-gray-200">{shorten(item.get('eval_comment', 'N/A'))}</td>
      </tr>
"""
        )

    html_string = (
        error_message_html
        + avg_score_html
        + """
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow overflow-hidden my-6">
        <div class="overflow-x-auto">
            <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                <thead class="bg-gray-50 dark:bg-gray-700">
                <tr>
                    <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Input</th>
                    <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Output</th>
                    <th scope="col" class="px-6 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Score</th>
                    <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Eval Comment</th>
                </tr>
                </thead>
                <tbody class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                """
        + "\n".join(table_html_rows)
        + """
                </tbody>
            </table>
        </div>
    </div>"""
    )
    return html_string


def generate_html_report(args, link, results_obj):
    """Generates an HTML report file with evaluation results, styled with Tailwind CSS."""
    # Ensure the output directory exists
    output_dir = "evaluation_output"
    os.makedirs(output_dir, exist_ok=True)
    # Consistent timestamp for filenames
    report_timestamp = int(time.time())
    html_file_name = f"results_{args.agent_id}_{report_timestamp}.html"
    html_file_path = os.path.join(output_dir, html_file_name)

    report_data_dict = prepare_report_data(results_obj)
    results_html_content = format_report_data_to_html(report_data_dict)

    with open(html_file_path, "w", encoding="utf-8") as f:
        f.write(
            f"""
    <!DOCTYPE html>
    <html lang="es" class="">
    <head>
        <meta charset="UTF-8">
        <title>Reporte de Resultados del Agente</title>
        <script src="https://cdn.tailwindcss.com"></script>
        <script>
            const htmlEl = document.documentElement;
            const currentTheme = localStorage.getItem('theme');
            if (currentTheme === 'dark' || (!currentTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {{
                htmlEl.classList.add('dark');
            }} else {{
                htmlEl.classList.remove('dark');
            }}
            function toggleTheme() {{
                if (htmlEl.classList.contains('dark')) {{
                    htmlEl.classList.remove('dark');
                    localStorage.setItem('theme', 'light');
                }} else {{
                    htmlEl.classList.add('dark');
                    localStorage.setItem('theme', 'dark');
                }}
            }}
        </script>
        <style>
            body {{ font-family: Inter, system-ui, Avenir, Helvetica, Arial, sans-serif; }}
        </style>
    </head>
    <body class="bg-gray-100 dark:bg-gray-900 text-gray-900 dark:text-white transition-colors duration-200">
        <div class="container mx-auto px-4 py-8">
            <div class="flex justify-between items-center mb-6">
                <h1 class="text-3xl font-bold text-gray-900 dark:text-white">Reporte de Resultados del Agente</h1>
                <button onclick="toggleTheme()" class="p-2 rounded-full hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors" aria-label="Toggle dark mode">
                    <svg id="theme-toggle-light-icon" class="w-5 h-5 hidden dark:block" fill="currentColor" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg"><path d="M10 2a1 1 0 011 1v1a1 1 0 11-2 0V3a1 1 0 011-1zm4 8a4 4 0 11-8 0 4 4 0 018 0zm-.464 4.95l.707.707a1 1 0 001.414-1.414l-.707-.707a1 1 0 00-1.414 1.414zm2.12-10.607a1 1 0 010 1.414l-.706.707a1 1 0 11-1.414-1.414l.707-.707a1 1 0 011.414 0zM17 11a1 1 0 100-2h-1a1 1 0 100 2h1zm-7 4a1 1 0 011 1v1a1 1 0 11-2 0v-1a1 1 0 011-1zM5.05 6.464A1 1 0 106.465 5.05l-.708-.707a1 1 0 00-1.414 1.414l.707.707zm-.707 12.122a1 1 0 011.414 0l.707.707a1 1 0 11-1.414 1.414l-.707-.707a1 1 0 010-1.414zM1 11a1 1 0 100-2H0a1 1 0 100 2h1z"></path></svg>
                    <svg id="theme-toggle-dark-icon" class="w-5 h-5 dark:hidden" fill="currentColor" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg"><path d="M17.293 13.293A8 8 0 016.707 2.707a8.001 8.001 0 1010.586 10.586z"></path></svg>
                </button>
            </div>
            <h3 class="text-xl font-semibold text-gray-700 dark:text-gray-300 mb-1">Experimento:</h3>
            <p class="mb-6"><a href="{html.escape(str(link))}" target="_blank" class="text-blue-600 dark:text-blue-400 hover:underline">{html.escape(str(link))}</a></p>
            {results_html_content}
        </div>
    </body>
    </html>
    """
        )
    print(f"HTML report generated: {os.path.abspath(html_file_path)}")
    return html_file_path, report_timestamp  # Return the path and timestamp


def send_evaluation_to_supabase(data_payload: dict):
    """
    Sends evaluation data to a Supabase Function.
    This function constructs a POST request to the specified Supabase Function URL with the provided
    """
    supabase_function_url = "https://hvxogjhuwjyqhsciheyd.supabase.co/functions/v1/evaluations"
    headers = {
        "Content-Type": "application/json",
        # Considera si necesitas una 'Authorization': 'Bearer TU_SUPABASE_KEY_SI_ES_NECESARIA'
        # o 'apikey': 'TU_SUPABASE_ANON_KEY_SI_ES_NECESARIA'
        # Esto depende de la configuración de seguridad de tu Supabase Function.
        # Por ahora, el curl de ejemplo no muestra una, así que la omito.
    }
    try:
        response = requests.post(supabase_function_url, headers=headers, json=data_payload, timeout=15)
        response.raise_for_status()
        print(f"Datos de evaluación enviados exitosamente a Supabase. Status: {response.status_code}")
        try:
            print(f"Respuesta de Supabase: {response.json()}")
        except requests.exceptions.JSONDecodeError:
            print(f"Respuesta de Supabase (no JSON): {response.text}")
    except requests.exceptions.HTTPError as e:
        print(
            f"Error HTTP al enviar datos a Supabase: {e}. Respuesta: {e.response.text if e.response else 'N/A'}"
        )
    except requests.exceptions.RequestException as e:
        print(f"Error de red/petición al enviar datos a Supabase: {e}")
