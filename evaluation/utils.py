import hashlib
import html
import json
import os
import sys
import time

import html
import pandas as pd
from copilot.core.agent import MultimodelAgent
from copilot.core.agent.agent import get_kb_tool
from copilot.core.etendo_utils import call_etendo, login_etendo
from copilot.core.langgraph.tool_utils.ApiTool import generate_tools_from_openapi
from langchain_core.utils.function_calling import convert_to_openai_function
from langsmith import Client
from openai.types.chat import (
    ChatCompletionAssistantMessageParam,
    ChatCompletionMessageToolCallParam,
    ChatCompletionToolMessageParam,
    ChatCompletionUserMessageParam,
)
from openai.types.chat.chat_completion_message_tool_call_param import Function
from schemas import Conversation, Message

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
    tools = []
    # Retrieve pre-configured tools
    configured_tools = MultimodelAgent().get_tools()
    tools_from_strctr = agent_config.tools

    # Add tools from the agent's structure
    for tool in tools_from_strctr if tools_from_strctr is not None else []:
        for t in configured_tools:
            if t.name == tool.function.name:
                tools.append(t)
                break

    # Add knowledge base tool if available
    kb_tool = get_kb_tool(agent_config)
    if kb_tool is not None:
        tools.append(kb_tool)

    # Add tools generated from API specifications
    if agent_config.specs is not None:
        for spec in agent_config.specs:
            if spec.type == "FLOW":
                api_spec = json.loads(spec.spec)
                openapi_tools = generate_tools_from_openapi(api_spec)
                tools.extend(openapi_tools)

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
    text = html.escape(text)

    if len(text.strip()) == 0:
        return ""

    if len(text) <= max_length:
        return f"<div>{text}</div>"

    return f"""
        <div>{text[:max_length]}... <a href="#" onclick="this.nextElementSibling.style.display='block';this.style.display='none';return false;">Ver más</a></div>
        <div style="display:none; white-space: pre-wrap;">{text}</div>
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

def generate_html_table_results(results_obj):
    """Generates an HTML table from evaluation results."""
    # Validate input structure
    if not (results_obj and hasattr(results_obj, '_results') and
            isinstance(results_obj._results, list) and results_obj._results):
        return "<p>Error: Invalid or empty 'results_obj' structure.</p>"

    table_items = []

    # Process results
    for result_item in results_obj._results:
        try:
            evaluation_data = result_item['evaluation_results']['results']
            child_runs_data = result_item['run'].child_runs

            if len(evaluation_data) != len(child_runs_data):
                return "<p>Error: Discrepancy between evaluation results and child_runs count.</p>"

            # Process each evaluation result
            for i, eval_result in enumerate(evaluation_data):
                try:
                    # Extract data
                    score = eval_result.score

                    # Get input data
                    inputs = child_runs_data[i].inputs
                    if isinstance(inputs, dict) and 'messages' in inputs:
                        input_comment_data = inputs['messages'][1]
                    else:
                        input_comment_data = json.dumps(inputs['messages'])

                    # Get output data
                    outputs = result_item['run'].outputs
                    output_data = (json.dumps(outputs) if isinstance(outputs, dict) and 'answer' in outputs
                                  else json.dumps(result_item['run']))

                    # Store data
                    table_items.append({
                        'comment': input_comment_data['content'],
                        'score': score,
                        'output': output_data,
                        'eval_comment': eval_result.comment
                    })
                except (KeyError, IndexError, AttributeError) as e:
                    table_items.append({
                        'comment': f"Error getting data for index {i}: {html.escape(str(e))}",
                        'score': -1
                    })
        except (KeyError, AttributeError, IndexError) as e:
            return f"<p>Error accessing necessary data: {html.escape(str(e))}</p>"

    # Sort results by score
    table_items.sort(key=lambda x: x['score'])

    # Calculate average score
    valid_scores = [item['score'] for item in table_items if item['score'] != -1]
    avg_score_html = ""

    if valid_scores:
        avg_score = sum(valid_scores) / len(valid_scores)

        # Determine score class
        if avg_score < 0.5:
            avg_score_class = "score-0"
        elif avg_score < 1:
            avg_score_class = "score-0_5"
        else:
            avg_score_class = "score-1"

        avg_score_html = f"""
        <div style="text-align: center; margin: 20px; font-size: 2em;">
            <strong>Average Score:</strong>
            <span class="{avg_score_class}">{avg_score:.2f}</span>
        </div>
        """

    # Build table HTML
    html_string = avg_score_html + """  <table>
    <thead>
      <tr>
        <th>Input</th>
        <th>Output</th>
        <th>Score</th>
        <th>Eval Comment</th>
      </tr>
    </thead>
    <tbody>
    """

    # Add table rows
    for item in table_items:
        score = item['score']

        # Set score class
        if score == 0:
            score_class = "score-0"
        elif score == 0.5:
            score_class = "score-0_5"
        elif score == 1:
            score_class = "score-1"
        else:
            score_class = "score-error"

        # Add row
        html_string += f"""      <tr>
        <td>{html.escape(str(item['comment']))}</td>
        <td>{html.escape(str(item['output']))}</td>
        <td class="{score_class}">{score if score != -1 else 'Error'}</td>
        <td>{html.escape(str(item['eval_comment']))}</td>
      </tr>
"""

    html_string += "    </tbody>\n  </table>"
    return html_string


def generate_html_report(args, link, results):
    """Generates an HTML report file with evaluation results."""
    html_file = f"evaluation_output/results_{args.agent_id}_{int(time.time())}.html"

    with open(html_file, "w", encoding="utf-8") as f:
        f.write(f"""
    <!DOCTYPE html>
    <html lang="es">
    <head>
        <meta charset="UTF-8">
        <title>Agent Results Report</title>
        <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
        <style>
            body {{ padding: 20px; }}
            .tab-content {{ margin-top: 20px; }}
            table {{ border-collapse: collapse; width: 80%; margin: 20px auto; font-family: Arial, sans-serif; }}
            th, td {{ border: 1px solid #ddd; padding: 10px; text-align: left; }}
            th {{ background-color: #f2f2f2; }}
            .score-0 {{ background-color: #FFCDD2; color: #B71C1C; }}
            .score-0_5 {{ background-color: #FFF9C4; color: #F57F17; }}
            .score-1 {{ background-color: #C8E6C9; color: #1B5E20; }}
            .score-error {{ background-color: #FFEBEE; color: #D32F2F; font-style: italic; }}
        </style>
    </head>
    <body>
        <h1>Agent Results Report</h1>
        <h3>Experiment: {link}</h3>
        {generate_html_table_results(results_obj=results)}
        <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
    </body>
    </html>
    """)

    print(f"HTML report generated: {os.getcwd()}/{html_file}")
