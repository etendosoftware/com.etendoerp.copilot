# Contexto del Código Fuente del Proyecto

Generado el: 2025-05-29 22:23:52
Directorio raíz del proyecto: `.`
Archivos incluidos (1): 6

---

## Archivo: `bulk_tasks_eval.py`

```typescript
"""
Etendo Task Creation & Execution Script

This script automates the complete process of creating and executing tasks in Etendo:
1. Creates tasks by combining data from a CSV with text templates
2. Executes the created tasks by calling an Etendo API
3. Monitors changes in database tables
4. Generates an HTML report with the results

Dependencies:
    - psycopg2: Adapter for PostgreSQL
    - requests: HTTP library for API calls
    - pandas: For CSV data handling
    - dotenv: For loading environment variables

Usage:
    python etendo_tasks.py --csv CSV_FILE --templates TEMPLATE_FILE [additional_options]
"""

import argparse
import os
import time
import uuid
import pandas as pd
import psycopg2
import requests
import traceback
from datetime import datetime
from dotenv import load_dotenv
from psycopg2 import sql

# Default database connection configuration
DB_CONFIG = {
    'dbname': 'copilot_test',
    'user': 'postgres',
    'password': 'syspass',
    'host': 'localhost',
    'port': '5432'
}

# Default Etendo API configuration
ETENDO_BASE_URL = 'http://localhost:8080/etendo'
ETENDO_API_PATH = '/org.openbravo.client.kernel'
PROCESS_ID = '7260F458FA2E43A7968E25E4B5242E60'
WINDOW_ID = '172C8727CDB74C948A207A1405CE445B'
ACTION = 'com.etendoerp.copilot.process.ExecTask'
AUTH_TOKEN = 'YWRtaW46YWRtaW4='  # admin:admin

# Default table to monitor
DEFAULT_TABLE = 'm_product'

# Default SQL query template
DEFAULT_QUERY = "SELECT COUNT(*) FROM {}"

# Default task configuration
DEFAULT_CLIENT_ID = '23C59575B9CF467C9620760EB255B389'
DEFAULT_ORG_ID = '0'
DEFAULT_IS_ACTIVE = 'Y'
DEFAULT_USER_ID = '100'
DEFAULT_STATUS = 'D0FCC72902F84486A890B70C1EB10C9C'  # Default status for new tasks
DEFAULT_TASK_TYPE_ID = 'D693563C21374AEEA47CDEBD23C8A0F0'
DEFAULT_AGENT_ID = '767849A7D3B442EB923A46CCDA41223C'

# Status of tasks to be executed
TASKS_STATUS = 'D0FCC72902F84486A890B70C1EB10C9C' # Status indicating tasks ready for execution

# HTTP Headers for API requests
HEADERS = {
    'Content-Type': 'application/json;charset=UTF-8',
    'Authorization': 'Basic ' + AUTH_TOKEN
}


def get_db_connection(config):
    """
    Establishes a connection to the PostgreSQL database.

    Args:
        config (dict): Database connection parameters

    Returns:
        psycopg2.connection: Database connection object or None if it fails
    """
    try:
        return psycopg2.connect(**config)
    except Exception as e:
        print(f"Database connection error: {e}")
        return None


def load_templates(template_file):
    """
    Loads templates from a file.

    Args:
        template_file (str): Path to the template file

    Returns:
        list: List of template strings
    """
    if not os.path.exists(template_file):
        print(f"Template file not found: {template_file}")
        return []

    templates = []
    try:
        with open(template_file, 'r', encoding='utf-8') as f:
            current_template = ""
            for line in f:
                if line.strip() == "<END_OF_INSTANCE>":
                    if current_template.strip():
                        templates.append(current_template.strip())
                    current_template = ""
                else:
                    current_template += line

            # Add the last template if the file doesn't end with <END_OF_INSTANCE>
            if current_template.strip():
                templates.append(current_template.strip())

        print(f"Loaded {len(templates)} templates from {template_file}")
        return templates
    except Exception as e:
        print(f"Error reading template file: {e}")
        return []


def load_csv_data(csv_file):
    """
    Loads data from a CSV file.

    Args:
        csv_file (str): Path to the CSV file

    Returns:
        pandas.DataFrame: DataFrame with CSV data or None if loading fails
    """
    if not os.path.exists(csv_file):
        print(f"CSV file not found: {csv_file}")
        return None

    try:
        df = pd.read_csv(csv_file)
        print(f"CSV loaded with {len(df)} rows and columns: {', '.join(df.columns)}")
        return df
    except Exception as e:
        print(f"Error reading CSV file: {e}")
        return None


def create_tasks_from_templates_and_csv(conn, df, templates):
    """
    Creates tasks by combining templates with CSV data.

    Args:
        conn: Database connection object
        df (pandas.DataFrame): DataFrame with CSV data
        templates (list): List of template strings

    Returns:
        tuple: (group_id, tasks_created) or (None, -1) if creation fails
    """
    if not conn or df is None or not templates:
        print("Missing required data for task creation")
        return None, -1

    # Generate a group ID for this batch
    group_id = str(uuid.uuid4())
    tasks_created_count = 0
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]

    try:
        cur = conn.cursor()

        # If there are multiple templates, use the first one for simplicity
        # For production use, one might rotate through templates or select randomly
        template = templates[0]

        # Process each row in the CSV
        for index, row in df.iterrows():
            try:
                # Replace placeholders in the template with CSV values
                task_text = template
                for column in df.columns:
                    placeholder = "{" + column + "}"
                    if placeholder in task_text:
                        task_text = task_text.replace(placeholder, str(row[column]))

                # Insert the task into the database
                insert_query = """
                INSERT INTO etask_task (
                    etask_task_id, ad_client_id, ad_org_id, isactive, created, createdby,
                    updated, updatedby, status, assigned_user, etask_task_type_id,
                    em_etcop_question, em_etcop_response, em_etcop_agentid,
                    em_etcop_bulkadd, em_etcop_exec, em_etcop_group
                ) VALUES (get_uuid(), %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """

                cur.execute(insert_query, (
                    DEFAULT_CLIENT_ID, DEFAULT_ORG_ID, DEFAULT_IS_ACTIVE,
                    now, DEFAULT_USER_ID, now, DEFAULT_USER_ID,
                    DEFAULT_STATUS, None, DEFAULT_TASK_TYPE_ID,
                    task_text, None, DEFAULT_AGENT_ID,
                    'Y', 'N', group_id
                ))

                tasks_created_count += 1

                # Show progress
                if tasks_created_count % 100 == 0:
                    print(f"Created {tasks_created_count} tasks...")

            except Exception as ex:
                print(f"CRITICAL ERROR during template instantiation for CSV row {index}: {ex}")
                print(f"  Original template: {template}")
                print(f"  Row data (partial): {dict(list(row.items())[:3])}...")
                traceback.print_exc()

        conn.commit()
        cur.close()
        print(f"\n{tasks_created_count} tasks created with group ID: {group_id}")
        return group_id, tasks_created_count
    except Exception as e:
        conn.rollback()
        print(f"Error creating tasks: {e}")
        return None, -1


def get_task_ids_from_db(conn, group_id=None):
    """
    Retrieves task IDs from the 'etask_task' table.

    Args:
        conn: Database connection object
        group_id (str, optional): Group ID to filter tasks

    Returns:
        list: List of task IDs or an empty list if retrieval fails
    """
    ids = []
    if not conn:
        return ids
    try:
        cur = conn.cursor()

        if group_id:
            query = sql.SQL("SELECT {} FROM {} WHERE status = %s AND em_etcop_group = %s").format(
                sql.Identifier("etask_task_id"),
                sql.Identifier("etask_task")
            )
            cur.execute(query, (TASKS_STATUS, group_id))
        else:
            query = sql.SQL("SELECT {} FROM {} WHERE status = %s").format(
                sql.Identifier("etask_task_id"),
                sql.Identifier("etask_task")
            )
            cur.execute(query, (TASKS_STATUS,))

        ids = [row[0] for row in cur.fetchall()]
        cur.close()
        print(f"{len(ids)} tasks found.")
    except Exception as e:
        print(f"Error querying task IDs: {e}")
    return ids


def count_db_records(conn, table_name, query_template=DEFAULT_QUERY):
    """
    Counts the number of records using a custom query.

    Args:
        conn: Database connection object
        table_name (str): Name of the table in which to count records
        query_template (str): Custom SQL query with {} as a placeholder for the table name

    Returns:
        int: Number of records or -1 if counting fails
    """
    if not conn:
        return -1
    try:
        # Prepare the query with the proper SQL identifier
        table_identifier = sql.Identifier(table_name)
        formatted_query = sql.SQL(query_template).format(table_identifier)

        # Execute the query
        cur = conn.cursor()
        cur.execute(formatted_query)
        count = cur.fetchone()[0]
        cur.close()
        return count
    except Exception as e:
        print(f"Error counting records in {table_name}: {e}")
        print(f"Attempted query: {query_template.format(table_name)}")
        return -1


def execute_etendo_task(task_id, etendo_url):
    """
    Executes a task in Etendo by making an API call.

    Args:
        task_id (str): ID of the task to execute
        etendo_url (str): Base URL for the Etendo API

    Returns:
        tuple: (success_flag, execution_time, error_message)
               - success_flag (bool): True if execution was successful
               - execution_time (float): Time taken in seconds
               - error_message (str): Error message if execution failed, None otherwise
    """
    start_time_task = time.time()
    full_url = f"{etendo_url}{ETENDO_API_PATH}?processId={PROCESS_ID}&reportId=null&windowId={WINDOW_ID}&_action={ACTION}"
    payload = {
        "recordIds": [task_id],
        "_buttonValue": "DONE",
        "_params": {},
        "_entityName": "ETASK_Task"
    }

    try:
        print(f"  Executing task for ID: {task_id}...")
        response = requests.post(full_url, headers=HEADERS, json=payload, timeout=60)
        response.raise_for_status()  # Raises an HTTPError for bad responses (4XX or 5XX)
        print(f"  Response: {response.status_code}")
        return True, round(time.time() - start_time_task, 2), None
    except Exception as e:
        error_msg_details = f"Error: {str(e)}"
        print(f"  {error_msg_details}")
        return False, round(time.time() - start_time_task, 2), error_msg_details


def generate_html_report(data):
    """
    Generates an HTML report with task execution results.

    Args:
        data (dict): Dictionary with report data with the following keys:
            - total_tasks_found: Number of tasks found
            - successful_tasks: Number of successfully executed tasks
            - failed_tasks: Number of failed tasks
            - total_script_duration: Total script execution time
            - table_name: Name of the monitored table
            - records_before: Record count before execution
            - records_after: Record count after execution
            - records_created: Number of records created
            - task_details: List of dictionaries with task execution details
            - query_template: SQL query template used for counting
    """
    now = datetime.now()
    # create directories if they don't exist
    os.makedirs("evaluation_output", exist_ok=True)

    filename = f"evaluation_output/etendo_results_{now.strftime('%Y%m%d_%H%M%S')}.html"

    html_content = f"""<!DOCTYPE html>
    <html>
    <head>
        <title>Etendo Task Execution Report</title>
        <style>
            body {{ font-family: Arial, sans-serif; margin: 20px; background-color: #f4f4f4; color: #333; }}
            .container {{ background-color: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }}
            h1, h2 {{ color: #0056b3; }}
            table {{ width: 100%; border-collapse: collapse; margin-top: 20px; }}
            th, td {{ border: 1px solid #ddd; padding: 10px; text-align: left; }}
            th {{ background-color: #007bff; color: white; }}
            tr:nth-child(even) {{ background-color: #f9f9f9; }}
            .success {{ color: green; font-weight: bold; }}
            .failure {{ color: red; font-weight: bold; }}
            .skipped {{ color: orange; font-weight: bold; }}
            .code {{ font-family: 'Courier New', Courier, monospace; background-color: #e9e9e9; padding: 3px 6px; border-radius: 4px; }}
            p {{ line-height: 1.6; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Etendo Task Execution Report</h1>
            <p><strong>Date:</strong> {now.strftime("%Y-%m-%d %H:%M:%S")}</p>

            <h2>Summary</h2>
            <p>Tasks Found/Created: {data['total_tasks_found']}</p>
            <p>Successful Tasks: <span class="success">{data['successful_tasks']}</span></p>
            <p>Failed Tasks: <span class="failure">{data['failed_tasks']}</span></p>
            <p>Tasks Skipped (Creation Only): <span class="skipped">{data['total_tasks_found'] - (data['successful_tasks'] + data['failed_tasks'])}</span></p>
            <p>Total Script Duration: {data['total_script_duration']:.2f} seconds</p>

            <h2>Record Monitoring in Table: {data['table_name']}</h2>
            <p>Query Used: <span class="code">{data['query_template'].format(data['table_name'])}</span></p>
            <p>Initial Count: {data['records_before'] if data['records_before'] != -1 else 'Error'}</p>
            <p>Final Count: {data['records_after'] if data['records_after'] != -1 else 'Error'}</p>
            <p>Records Created/Modified: <strong>{data['records_created'] if data['records_created'] != -1 else 'N/A'}</strong></p>

            <h2>Task Details</h2>
            <table>
                <tr><th>Task ID/Reference</th><th>Status</th><th>Duration (s)</th><th>Error Message</th></tr>
    """

    for task in data['task_details']:
        status_class = "success" if task['status'] == "Success" else ("failure" if task['status'] == "Failure" else "skipped")
        error_msg_display = task['error'] if task['error'] else "N/A"
        html_content += f"""<tr>
            <td>{task['id']}</td>
            <td class="{status_class}">{task['status']}</td>
            <td>{task['duration']:.2f}</td>
            <td>{error_msg_display}</td>
        </tr>"""

    html_content += """
            </table>
        </div>
    </body>
    </html>"""

    try:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(html_content)
        print(f"\nHTML report generated: {filename}")
    except Exception as e:
        print(f"Error generating HTML report: {e}")


def parse_arguments():
    """
    Parses command-line arguments for the script.

    Returns:
        argparse.Namespace: Parsed arguments
    """
    parser = argparse.ArgumentParser(description="Process Etendo tasks from CSV and templates.")
    parser.add_argument("--csv", required=True, help="CSV file with data for placeholders")
    parser.add_argument("--templates", required=True, help="File containing templates with placeholders")
    parser.add_argument("--envfile", help="Environment file (e.g., .env)", default=None)
    parser.add_argument("--etendo_url", help="Base URL of Etendo", default=ETENDO_BASE_URL)
    parser.add_argument("--table", help="Database table to monitor record count", default=DEFAULT_TABLE)
    parser.add_argument("--query", help="Custom SQL query to count records (use {} as placeholder for table name)",
                        default=DEFAULT_QUERY)
    parser.add_argument("--max_tasks", type=int, help="Maximum number of tasks to process from CSV", default=50)
    parser.add_argument("--skip_exec", action="store_true", help="Skip task execution (only create tasks)")

    # Database connection parameters (optional)
    parser.add_argument("--dbname", help="Database name", default=None)
    parser.add_argument("--dbuser", help="Database user", default=None)
    parser.add_argument("--dbpassword", help="Database password", default=None)
    parser.add_argument("--dbhost", help="Database host", default=None)
    parser.add_argument("--dbport", help="Database port", default=None)

    return parser.parse_args()


def load_config(args):
    """
    Loads configuration from environment file and command-line arguments.

    Args:
        args (argparse.Namespace): Command-line arguments

    Returns:
        tuple: (db_config, etendo_url)
    """
    current_db_config = DB_CONFIG.copy()
    current_etendo_url = args.etendo_url

    # Load from environment file if provided
    if args.envfile:
        print(f"Loading environment variables from {args.envfile}")
        load_dotenv(args.envfile, verbose=True)

        # Update from environment variables using correct property names
        if os.getenv('BBDD_SID'): # Common alternative for DB name/SID
            current_db_config['dbname'] = os.getenv('BBDD_SID')
        if os.getenv('BBDD_USER'):
            current_db_config['user'] = os.getenv('BBDD_USER')
        if os.getenv('BBDD_PASSWORD'):
            current_db_config['password'] = os.getenv('BBDD_PASSWORD')
        if os.getenv('BBDD_HOST'):
            current_db_config['host'] = os.getenv('BBDD_HOST')
        if os.getenv('BBDD_PORT'):
            current_db_config['port'] = os.getenv('BBDD_PORT')
        if os.getenv('ETENDO_BASE_URL'):
            current_etendo_url = os.getenv('ETENDO_BASE_URL')

    # Command-line arguments override environment file and defaults
    if args.dbname:
        current_db_config['dbname'] = args.dbname
    if args.dbuser:
        current_db_config['user'] = args.dbuser
    if args.dbpassword:
        current_db_config['password'] = args.dbpassword
    if args.dbhost:
        current_db_config['host'] = args.dbhost
    if args.dbport:
        current_db_config['port'] = args.dbport
    # args.etendo_url is already set to current_etendo_url or its default if not overridden by env
    # Ensure command line argument takes precedence if it was explicitly set (it is by default due to parser)
    if args.etendo_url != ETENDO_BASE_URL: # If user provided --etendo_url
         current_etendo_url = args.etendo_url
    elif os.getenv('ETENDO_BASE_URL'): # else if env var was set
        current_etendo_url = os.getenv('ETENDO_BASE_URL')
    # else it remains the script's default ETENDO_BASE_URL

    return current_db_config, current_etendo_url


def main():
    """
    Main function that orchestrates the entire task execution process:
    1. Parses command-line arguments
    2. Loads configuration from environment file and arguments
    3. Connects to the database
    4. Loads templates and CSV data
    5. Creates tasks by combining templates with CSV data
    6. Counts records in the specified table before execution
    7. Executes the created tasks
    8. Counts records in the specified table after execution
    9. Generates an HTML report
    10. Displays execution summary
    """
    script_start_time = time.time()
    print(f"Starting script... [{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}]")

    # Parse arguments and load configuration
    args = parse_arguments()
    effective_db_config, effective_etendo_url = load_config(args)

    # Get table to monitor and query template
    table_to_monitor = args.table
    query_template_for_count = args.query
    max_tasks_to_process = args.max_tasks

    print(f"Using database: {effective_db_config['host']}:{effective_db_config['port']}/{effective_db_config['dbname']}")
    print(f"Using Etendo URL: {effective_etendo_url}")
    print(f"Monitoring table: {table_to_monitor}")
    print(f"Using count query template: {query_template_for_count}")
    print(f"Maximum task limit: {max_tasks_to_process}")

    # Connect to the database
    conn = get_db_connection(effective_db_config)
    if not conn:
        print("Could not connect to the database. Exiting.")
        return

    # Load templates and CSV data
    templates_list = load_templates(args.templates)
    if not templates_list:
        print("No templates loaded. Exiting.")
        conn.close()
        return

    df_csv_data = load_csv_data(args.csv)
    if df_csv_data is None:
        print("No CSV data loaded. Exiting.")
        conn.close()
        return

    # If there are too many rows, limit to the specified maximum
    if len(df_csv_data) > max_tasks_to_process:
        print(f"Limiting to {max_tasks_to_process} rows from CSV (out of {len(df_csv_data)} total)")
        df_csv_data = df_csv_data.head(max_tasks_to_process)

    # Count initial records in the specified table using custom query
    records_before_execution = count_db_records(conn, table_to_monitor, query_template_for_count)
    print(f"Records in {table_to_monitor} before: {records_before_execution if records_before_execution != -1 else 'Error'}")

    # Create tasks by combining templates with CSV data
    group_id_for_batch, num_tasks_created = create_tasks_from_templates_and_csv(conn, df_csv_data, templates_list)

    if num_tasks_created <= 0:
        print("No tasks were created. Exiting.")
        conn.close()
        return

    # Initialize variables for the report
    task_execution_details = []
    successful_task_count = 0
    failed_task_count = 0

    # If task execution is not skipped
    if not args.skip_exec:
        # Get newly created tasks
        task_ids_to_process = get_task_ids_from_db(conn, group_id_for_batch)

        print(f"\nExecuting {len(task_ids_to_process)} tasks...")

        # Process each task
        for i, task_id_val in enumerate(task_ids_to_process):
            print(f"\nProcessing task {i + 1}/{len(task_ids_to_process)}:")
            success_status, exec_duration, error_detail = execute_etendo_task(str(task_id_val), effective_etendo_url)

            status_message = "Success" if success_status else "Failure"
            task_execution_details.append({
                'id': task_id_val,
                'status': status_message,
                'duration': exec_duration,
                'error': error_detail
            })

            if success_status:
                successful_task_count += 1
            else:
                failed_task_count += 1
            print(f"  Result: {status_message}, Duration: {exec_duration:.2f}s")
    else:
        print("\nSkipping task execution (--skip_exec specified)")
        # Simulate details for the report
        for i in range(num_tasks_created):
            task_execution_details.append({
                'id': f"(task {i+1} - not executed)",
                'status': "Skipped",
                'duration': 0.0,
                'error': "Execution skipped by user"
            })

    # Count final records in the specified table using the same custom query
    records_after_execution = count_db_records(conn, table_to_monitor, query_template_for_count)
    print(f"Records in {table_to_monitor} after: {records_after_execution if records_after_execution != -1 else 'Error'}")

    delta_records = -1
    if records_before_execution != -1 and records_after_execution != -1:
        delta_records = records_after_execution - records_before_execution

    # Close connection
    conn.close()

    total_script_run_time = time.time() - script_start_time

    # Prepare report data
    report_data_dict = {
        'total_tasks_found': num_tasks_created, # This is tasks created from CSV
        'successful_tasks': successful_task_count,
        'failed_tasks': failed_task_count,
        'total_script_duration': total_script_run_time,
        'table_name': table_to_monitor,
        'records_before': records_before_execution,
        'records_after': records_after_execution,
        'records_created': delta_records,
        'task_details': task_execution_details,
        'query_template': query_template_for_count
    }

    # Generate HTML report
    generate_html_report(report_data_dict)

    # Final summary
    print("\n--- FINAL SUMMARY ---")
    print(f"Tasks created from CSV: {num_tasks_created}")
    if not args.skip_exec:
        print(f"Tasks executed: {successful_task_count + failed_task_count}")
        print(f"Successful tasks: {successful_task_count}")
        print(f"Failed tasks: {failed_task_count}")
    else:
        print(f"Task execution skipped.")
    print(f"Monitored table: {table_to_monitor}")
    print(f"Count query used: {query_template_for_count.format(table_to_monitor)}")
    print(f"Records before: {records_before_execution if records_before_execution != -1 else 'Error'}")
    print(f"Records after: {records_after_execution if records_after_execution != -1 else 'Error'}")
    print(f"Net records created/modified: {delta_records if delta_records != -1 else 'N/A'}")
    print(f"Total script time: {total_script_run_time:.2f} seconds")
    print(f"Script finished. [{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}]")

    # if delta_records is diferent from tasks created, system exit 1
    if delta_records != num_tasks_created:
        print("Error: Number of records created/modified does not match the number of tasks created.")
        exit(1)



if __name__ == "__main__":
    main()
```

## Archivo: `execute.py`

```typescript
import argparse
import json
import os
import sys
import time
from copy import deepcopy
from typing import List, Dict, Any

import requests

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
    tool_to_openai_function, prepare_report_data,
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
    ls_client_eval = Client() # Renamed to avoid conflict if another Client is used
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


def send_evaluation_to_supabase(data_payload: dict):
    """
    Envía los datos de la evaluación a la función de Supabase.
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
        try: print(f"Respuesta de Supabase: {response.json()}")
        except requests.exceptions.JSONDecodeError: print(f"Respuesta de Supabase (no JSON): {response.text}")
    except requests.exceptions.HTTPError as e:
        print(f"Error HTTP al enviar datos a Supabase: {e}. Respuesta: {e.response.text if e.response else 'N/A'}")
    except requests.exceptions.RequestException as e:
        print(f"Error de red/petición al enviar datos a Supabase: {e}")


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
```

## Archivo: `gen_variants.py`

```typescript
import pandas as pd
import os
import re
import argparse
import traceback
from typing import List, Dict, Any, Tuple
from collections import Counter
import json  # For saving stats in a structured way
import numpy as np  # For numerical operations in selection

# Langchain imports - Updated for newer versions
try:
    from langchain_openai import ChatOpenAI
except ImportError:
    print("CRITICAL ERROR: langchain-openai not found. Please install it: pip install langchain-openai")
    print(
        "Attempting to use deprecated ChatOpenAI from langchain_community.chat_models as a fallback (might not work long-term).")
    try:
        from langchain_community.chat_models import ChatOpenAI  # Fallback for older setups
    except ImportError:
        print("CRITICAL ERROR: No ChatOpenAI found. Please install langchain-openai.")
        exit(1)  # Exit if no ChatOpenAI can be imported

from langchain.prompts import ChatPromptTemplate, HumanMessagePromptTemplate
from langchain_core.messages import SystemMessage
from langchain_core.output_parsers import StrOutputParser  # To get string output directly

# NLTK for lexical diversity (optional, install if using)
try:
    from nltk.tokenize import word_tokenize
    from nltk.util import ngrams

    NLTK_AVAILABLE = True
except ImportError:
    NLTK_AVAILABLE = False
    print("Warning: NLTK not found. Lexical diversity metrics (distinct n-grams) will be skipped.")
    print("To enable them, run: pip install nltk")

# Levenshtein for edit distance (optional, install if using)
try:
    import Levenshtein

    LEVENSHTEIN_AVAILABLE = True
except ImportError:
    LEVENSHTEIN_AVAILABLE = False
    print("Warning: python-Levenshtein not found. Levenshtein distance metric will be skipped.")
    print("To enable it, run: pip install python-Levenshtein")

# SentenceTransformers for semantic diversity (optional, install if using)
try:
    from sentence_transformers import SentenceTransformer
    from sklearn.metrics.pairwise import cosine_distances

    SENTENCE_TRANSFORMERS_AVAILABLE = True
    SBERT_MODEL_NAME = 'paraphrase-multilingual-MiniLM-L12-v2'
    SBERT_MODEL = None
except ImportError:
    SENTENCE_TRANSFORMERS_AVAILABLE = False
    SBERT_MODEL = None
    print("Warning: sentence-transformers or scikit-learn not found. Semantic diversity metrics will be skipped.")
    print("To enable them, run: pip install sentence-transformers scikit-learn")

# --- Configuration ---
DEFAULT_LLM_MODEL = "gpt-4.1-mini"
DEFAULT_LLM_TEMPERATURE = 0.75
DEFAULT_NUM_TEMPLATES_FINAL = 20  # Default number of DIVERSE templates to keep
DEFAULT_OUTPUT_FILE = "instantiated_texts.txt"
DIVERSIFICATION_MULTIPLIER = 3  # Generate N * multiplier initial templates


# --- Diversity Measurement Functions ---
def load_sbert_model():
    global SBERT_MODEL
    if SENTENCE_TRANSFORMERS_AVAILABLE and SBERT_MODEL is None:
        try:
            print(f"Loading SentenceTransformer model '{SBERT_MODEL_NAME}'... (This may take a moment on first run)")
            SBERT_MODEL = SentenceTransformer(SBERT_MODEL_NAME)
            print(f"SentenceTransformer model '{SBERT_MODEL_NAME}' loaded successfully.")
        except Exception as e:
            print(f"Error loading SentenceTransformer model '{SBERT_MODEL_NAME}': {e}")
            SBERT_MODEL = None
    return SBERT_MODEL


def calculate_distinct_ngrams(list_of_texts: List[str], n: int = 1) -> Dict[str, Any]:
    if not NLTK_AVAILABLE or not list_of_texts:
        return {"distinct_count": 0, "total_count": 0, "ratio": 0.0,
                "status": "Skipped (NLTK not available or no texts)"}

    all_ngrams_list = []
    try:
        for text in list_of_texts:
            tokens = word_tokenize(text.lower())
            if len(tokens) >= n:
                current_ngrams = list(ngrams(tokens, n))
                all_ngrams_list.extend(current_ngrams)
    except Exception as e:
        if "punkt" in str(e).lower():
            print("\nNLTK 'punkt' tokenizer resource not found. Please download it by running:")
            print("import nltk; nltk.download('punkt')")
        return {"distinct_count": 0, "total_count": 0, "ratio": 0.0, "status": f"NLTK error: {e}"}

    if not all_ngrams_list:
        return {"distinct_count": 0, "total_count": 0, "ratio": 0.0, "status": "Success (no n-grams found)"}

    total_ngrams_count = len(all_ngrams_list)
    distinct_ngrams_count = len(set(all_ngrams_list))
    ratio = distinct_ngrams_count / total_ngrams_count if total_ngrams_count > 0 else 0.0
    return {"distinct_count": distinct_ngrams_count, "total_count": total_ngrams_count, "ratio": ratio,
            "status": "Success"}


def average_levenshtein_distance_pairwise(list_of_texts: List[str]) -> Dict[str, Any]:
    if not LEVENSHTEIN_AVAILABLE:
        return {"average_normalized_distance": 0.0, "status": "Skipped (python-Levenshtein not available)"}
    if len(list_of_texts) < 2:
        return {"average_normalized_distance": 0.0, "status": "Skipped (need at least 2 texts)"}

    distances = []
    for i in range(len(list_of_texts)):
        for j in range(i + 1, len(list_of_texts)):
            distance = Levenshtein.distance(list_of_texts[i], list_of_texts[j])
            normalized_distance = distance / max(len(list_of_texts[i]), len(list_of_texts[j]), 1)
            distances.append(normalized_distance)

    avg_dist = sum(distances) / len(distances) if distances else 0.0
    return {"average_normalized_distance": avg_dist, "status": "Success"}


def get_embeddings(list_of_texts: List[str]) -> Any:
    sbert_model = load_sbert_model()
    if sbert_model is None or not list_of_texts:
        return None
    try:
        return sbert_model.encode(list_of_texts, show_progress_bar=False)
    except Exception as e:
        print(f"Error generating embeddings: {e}")
        return None


def average_cosine_distance_pairwise_semantic(list_of_texts: List[str]) -> Dict[str, Any]:
    sbert_model = load_sbert_model()
    if sbert_model is None:
        return {"average_cosine_distance": 0.0, "status": "Skipped (SentenceTransformer model not loaded)"}
    if len(list_of_texts) < 2:
        return {"average_cosine_distance": 0.0, "status": "Skipped (need at least 2 texts)"}

    embeddings = get_embeddings(list_of_texts)
    if embeddings is None or (isinstance(embeddings, list) and not embeddings) or embeddings.shape[0] < 2:
        return {"average_cosine_distance": 0.0, "status": "Skipped (could not generate enough valid embeddings)"}

    try:
        distances_matrix = cosine_distances(embeddings)
        pairwise_distances = []
        for i in range(len(embeddings)):
            for j in range(i + 1, len(embeddings)):
                pairwise_distances.append(distances_matrix[i, j])
        avg_dist = sum(pairwise_distances) / len(pairwise_distances) if pairwise_distances else 0.0
        return {"average_cosine_distance": avg_dist, "status": "Success"}
    except Exception as e:
        print(f"Error calculating cosine distances: {e}")
        return {"average_cosine_distance": 0.0, "status": f"Error: {e}"}


def report_and_collect_diversity_metrics(templates: List[str]) -> Dict[str, Any]:
    metrics_summary = {"num_templates_analyzed": len(templates)}

    print("\n--- Diversity Metrics for Generated Templates ---")
    if not templates or len(templates) < 2:
        print("Not enough templates (need at least 2) to calculate pairwise diversity.")
        metrics_summary["status"] = "Not enough templates for pairwise diversity."
        return metrics_summary

    metrics_summary["lexical_diversity_nltk"] = {}
    if NLTK_AVAILABLE:
        print("\nLexical Diversity (NLTK):")
        for n_val in [1, 2]:
            ngram_key = f"distinct_{n_val}_grams"
            metrics_ngram = calculate_distinct_ngrams(templates, n=n_val)
            metrics_summary["lexical_diversity_nltk"][ngram_key] = metrics_ngram
            if metrics_ngram.get("status", "").startswith("Error") or metrics_ngram.get("status", "").startswith(
                    "Skipped"):
                print(f"  Distinct-{n_val} grams: {metrics_ngram['status']}")
            else:
                print(
                    f"  Distinct-{n_val} grams: {metrics_ngram['distinct_count']} unique of {metrics_ngram['total_count']} total (Ratio: {metrics_ngram['ratio']:.3f})")
    else:
        print("\nLexical Diversity (NLTK): Skipped (NLTK not available).")
        metrics_summary["lexical_diversity_nltk"]["status"] = "Skipped (NLTK not available)"

    metrics_summary["levenshtein_distance"] = {}
    if LEVENSHTEIN_AVAILABLE:
        lev_metrics = average_levenshtein_distance_pairwise(templates)
        metrics_summary["levenshtein_distance"] = lev_metrics
        print(
            f"\nAverage Normalized Levenshtein Distance (Pairwise): {lev_metrics.get('average_normalized_distance', 0.0):.3f} ({lev_metrics.get('status', '')})")
    else:
        print("\nAverage Normalized Levenshtein Distance: Skipped (python-Levenshtein not available).")
        metrics_summary["levenshtein_distance"]["status"] = "Skipped (python-Levenshtein not available)"

    metrics_summary["semantic_diversity_sbert"] = {}
    if SENTENCE_TRANSFORMERS_AVAILABLE:
        print(f"\nSemantic Diversity (Sentence Transformers - Model: {SBERT_MODEL_NAME}):")
        if load_sbert_model() is not None:
            cosine_metrics = average_cosine_distance_pairwise_semantic(templates)
            metrics_summary["semantic_diversity_sbert"] = cosine_metrics
            print(
                f"  Average Cosine Distance (Pairwise Semantic): {cosine_metrics.get('average_cosine_distance', 0.0):.3f} (Higher is more diverse) ({cosine_metrics.get('status', '')})")
        else:
            print("  Skipped (SentenceTransformer model could not be loaded).")
            metrics_summary["semantic_diversity_sbert"]["status"] = "Skipped (SBERT model not loaded)"
    else:
        print("\nSemantic Diversity: Skipped (sentence-transformers or scikit-learn not available).")
        metrics_summary["semantic_diversity_sbert"]["status"] = "Skipped (sentence-transformers not available)"

    print("---------------------------------------------")
    return metrics_summary


def save_diversity_stats(stats_data: Dict[str, Any], output_stat_file: str):
    try:
        with open(output_stat_file, "w", encoding="utf-8") as f_stat:
            json.dump(stats_data, f_stat, indent=4)
        print(f"\nDiversity statistics saved to: {output_stat_file}")
    except Exception as e:
        print(f"CRITICAL ERROR: Could not save diversity statistics to '{output_stat_file}': {e}")
        traceback.print_exc()


# --- Template Selection Logic ---
def select_most_diverse_templates(
        all_templates: List[str],
        num_to_select: int
) -> List[str]:
    """
    Selects a subset of templates that are most semantically diverse.
    Uses a greedy approach based on maximizing minimum cosine distance to already selected templates.
    """
    if not all_templates:
        print("Warning: No templates provided for diverse selection.")
        return []
    if len(all_templates) <= num_to_select:
        print(
            f"Number of generated templates ({len(all_templates)}) is less than or equal to requested ({num_to_select}). Returning all generated templates.")
        return all_templates

    sbert_model = load_sbert_model()
    if not sbert_model or not SENTENCE_TRANSFORMERS_AVAILABLE:
        print(
            "Warning: Semantic diversity model not available. Cannot perform diverse selection. Returning the first N templates.")
        return all_templates[:num_to_select]

    print(f"\n--- Selecting {num_to_select} most diverse templates from {len(all_templates)} candidates ---")

    embeddings = get_embeddings(all_templates)
    if embeddings is None or len(embeddings) < num_to_select:  # Check if embeddings is None or not enough embeddings
        print("Warning: Could not generate enough embeddings for diverse selection. Returning the first N templates.")
        return all_templates[:num_to_select]

    num_candidates = embeddings.shape[0]
    selected_indices = []

    # Start by selecting the first template
    selected_indices.append(0)

    # Calculate all pairwise cosine distances (1 - similarity)
    # distance_matrix[i, j] is the distance between template i and template j
    try:
        distance_matrix = cosine_distances(embeddings)
    except Exception as e:
        print(f"Error computing distance matrix for diverse selection: {e}. Returning first N templates.")
        return all_templates[:num_to_select]

    while len(selected_indices) < num_to_select:
        best_next_idx = -1
        max_min_dist_to_selected_set = -1

        for i in range(num_candidates):
            if i in selected_indices:
                continue

            # Calculate the minimum distance from candidate 'i' to all already selected templates
            current_min_dist = np.min(distance_matrix[i, selected_indices])

            if current_min_dist > max_min_dist_to_selected_set:
                max_min_dist_to_selected_set = current_min_dist
                best_next_idx = i

        if best_next_idx != -1:
            selected_indices.append(best_next_idx)
        else:
            # Should not happen if num_to_select <= num_candidates,
            # but as a fallback if all remaining candidates have 0 distance (are identical)
            print("Warning: Could not find more distinct templates to select. Returning current selection.")
            break

    selected_templates = [all_templates[i] for i in selected_indices]
    print(f"Selected {len(selected_templates)} diverse templates.")
    return selected_templates


# --- LLM-Powered Template Generation ---
def generate_templates_via_llm(
        task_description: str,
        placeholder_names: List[str],
        num_templates_to_generate_final: int,  # This is the N for final selection
        diversification_multiplier: int,
        model_name: str = DEFAULT_LLM_MODEL,
        temperature: float = DEFAULT_LLM_TEMPERATURE
) -> tuple[List[str], Dict[str, Any]]:
    if not os.getenv("OPENAI_API_KEY"):
        print("CRITICAL ERROR: The OPENAI_API_KEY environment variable is not set.")
        return [], {"status": "OPENAI_API_KEY not set"}
    try:
        llm = ChatOpenAI(model_name=model_name, temperature=temperature)
    except Exception as e:
        print(f"CRITICAL ERROR: Failed to initialize LLM '{model_name}': {e}")
        traceback.print_exc()
        return [], {"status": f"LLM initialization error: {e}"}

    num_initial_templates_to_request = num_templates_to_generate_final * diversification_multiplier
    print(
        f"Requesting {num_initial_templates_to_request} initial templates from AI (target final: {num_templates_to_generate_final}).")

    placeholders_for_prompt = ", ".join([f"{{{{{name}}}}}" for name in placeholder_names])
    if not placeholder_names:
        placeholders_for_prompt = "(No specific placeholders provided, generate generic templates for the task)"

    system_message_content = (
        "You are a highly creative and expert AI assistant specializing in generating flexible and diverse text templates. "
        "Your templates are designed to be easily populated with tabular data (like from a CSV) "
        "to form complete and coherent text instances for various tasks."
    )

    human_template_str = (
        "I need your help to generate {num_initial_templates} distinct text templates. "  # Updated variable name
        "These templates will be used for the following task: '{task_description}'.\n\n"
        "The templates should be able to incorporate data fields. The available data fields, "
        "which you must use as placeholders in the double curly brace format (e.g., {{{{FieldName}}}}), are:\n"
        "{list_placeholders_str}\n\n"
        "**Fundamental Guidelines for Template Generation:**\n"
        "1.  **Placeholder Format:** STRICTLY use the double curly brace format for placeholders (e.g., {{{{ProductName}}}}). DO NOT use single braces.\n"
        "2.  **Allowed Placeholders:** Only use the placeholder names provided in the list above. DO NOT invent new placeholders. If no specific placeholders were provided, create templates that are general for the task and might not need data merging.\n"
        "3.  **Creative Diversity:** This is key! Generate templates that are very diverse from each other. Vary:\n"
        "    * The overall sentence structure and information order.\n"
        "    * The tone (e.g., formal, direct, technical, slightly informal if appropriate for the task).\n"
        "    * The placeholders used (if provided): not all templates must use all placeholders. Some can be concise, others more detailed.\n"
        "    * The language can be English or Spanish, randomly.\n"
        "    * Introductory or concluding phrases.\n"
        "4.  **Clear Intent:** All templates must clearly reflect the intent of the task: '{task_description}'. If it implies an action (e.g., 'create', 'update', 'query'), the template should be a clear instruction or statement for it.\n"
        "5.  **One Template Per Line:** Return each generated template on a new line. Do not include numbering, bullet points, or any other introductory/concluding text in your response—only the templates themselves.\n\n"
        "Please generate {num_initial_templates} templates following these guidelines.\n\n"  # Updated variable name
        "Generated Templates:"
    )

    prompt_chat = ChatPromptTemplate.from_messages([
        SystemMessage(content=system_message_content),
        HumanMessagePromptTemplate.from_template(human_template_str)
    ])

    chain = prompt_chat | llm | StrOutputParser()

    try:
        print(
            f"\n--- Requesting {num_initial_templates_to_request} initial templates from AI for task: '{task_description}' ---")
        if placeholder_names:
            print(f"Available placeholders for AI: {placeholder_names}")
        else:
            print("No specific CSV placeholders provided to AI; expecting generic templates for the task.")

        invoke_input = {
            "num_initial_templates": str(num_initial_templates_to_request),  # Updated key
            "task_description": task_description,
            "list_placeholders_str": placeholders_for_prompt
        }
        llm_response_content = chain.invoke(invoke_input)

        raw_generated_templates = [t.strip() for t in llm_response_content.split('\n') if t.strip()]

        print(f"\n--- {len(raw_generated_templates)} Raw Templates received from AI ---")

        validated_templates = []
        placeholder_pattern = re.compile(r"\{\{([\w\s.-]+?)\}\}")

        for i, template_str in enumerate(raw_generated_templates):
            found_placeholders = set(placeholder_pattern.findall(template_str))
            is_valid = True

            if not found_placeholders and placeholder_names:
                print(
                    f"Warning: Template {i + 1} does not seem to use any placeholders, though some were expected: '{template_str[:100]}...'")

            for ph_found in found_placeholders:
                if ph_found not in placeholder_names:
                    print(
                        f"Warning: Template {i + 1} uses a DISALLOWED placeholder ('{ph_found}'). Placeholder should be one of {placeholder_names}. Discarding template: '{template_str[:100]}...'")
                    is_valid = False
                    break
            if is_valid:
                validated_templates.append(template_str)

        print(f"\n--- {len(validated_templates)} Initial Validated and Processed Templates: ---")
        # for t_idx, t_text in enumerate(validated_templates): # Optional: print all initial validated
        #      print(f"Initial Template {t_idx+1}: {t_text}")

        # Select the most diverse N templates from the validated ones
        final_selected_templates = select_most_diverse_templates(validated_templates, num_templates_to_generate_final)

        print(f"\n--- {len(final_selected_templates)} Final Selected Diverse Templates: ---")
        for t_idx, t_text in enumerate(final_selected_templates):
            print(f"Selected Template {t_idx + 1}: {t_text}")

        template_diversity_stats = {}
        if final_selected_templates:
            template_diversity_stats = report_and_collect_diversity_metrics(final_selected_templates)
        else:
            print("No final templates selected to report diversity on.")
            template_diversity_stats = {"status": "No final templates selected."}
        print("-----------------------------------\n")

        return final_selected_templates, template_diversity_stats

    except Exception as e:
        print(f"CRITICAL ERROR during LLM chain execution for template generation: {e}")
        traceback.print_exc()
        return [], {"status": f"LLM chain execution error: {e}"}


# --- CSV Data Instantiation ---
def instantiate_templates_with_csv_data(
        csv_path: str,
        ai_generated_templates: List[str],
        placeholder_column_names: List[str]
) -> List[str]:
    try:
        df_data = pd.read_csv(csv_path, dtype=str)
        df_data = df_data.fillna('')
        print(f"\nSuccessfully loaded {len(df_data)} records from '{csv_path}'.")
    except FileNotFoundError:
        print(f"CRITICAL ERROR: CSV file '{csv_path}' not found.")
        return []
    except Exception as e:
        print(f"CRITICAL ERROR loading CSV '{csv_path}': {e}")
        traceback.print_exc()
        return []

    if not ai_generated_templates:
        print("Warning: No AI-generated templates provided for instantiation. Returning empty list.")
        return []

    final_instantiated_texts = []
    warned_missing_cols = set()

    print(f"Instantiating {len(ai_generated_templates)} templates with CSV data...")
    for index, data_row in df_data.iterrows():
        for template_str in ai_generated_templates:
            current_instance = template_str
            data_for_this_row: Dict[str, Any] = {}

            for csv_col_name in placeholder_column_names:
                if csv_col_name not in df_data.columns:
                    if csv_col_name not in warned_missing_cols:
                        print(
                            f"Warning: CSV column '{csv_col_name}' (expected for placeholder '{{{{{csv_col_name}}}}}') not found in '{csv_path}'. Using empty string for this placeholder.")
                        warned_missing_cols.add(csv_col_name)
                    data_for_this_row[csv_col_name] = ""
                else:
                    data_for_this_row[csv_col_name] = str(data_row[csv_col_name])

            try:
                for placeholder_name, value in data_for_this_row.items():
                    current_instance = current_instance.replace(f"{{{{{placeholder_name}}}}}", value)
                final_instantiated_texts.append(current_instance)
            except Exception as ex:
                print(f"CRITICAL ERROR during template instantiation for CSV row {index}: {ex}")
                print(f"  Original Template: {template_str}")
                print(f"  Row Data (partial): {dict(list(data_for_this_row.items())[:3])}...")
                traceback.print_exc()
            # if final_instantiated_texts reach 300 elements, break to avoid memory issues
    if len(final_instantiated_texts) >= 50:
        print("Warning: Too many instantiated texts generated. Limiting to 50 for performance.")
        final_instantiated_texts = final_instantiated_texts[:50]
    return final_instantiated_texts


# --- Argument Parsing and Main Execution ---
def main():
    global NLTK_AVAILABLE, LEVENSHTEIN_AVAILABLE, SENTENCE_TRANSFORMERS_AVAILABLE, DIVERSIFICATION_MULTIPLIER
    parser = argparse.ArgumentParser(
        description="Generate text instances by combining AI-generated templates with CSV data, including diversity metrics for templates.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    parser.add_argument(
        "csv_file", type=str, help="Path to the input CSV file."
    )
    parser.add_argument(
        "task", type=str,
        help="Description of the task for which AI will generate templates (e.g., 'draft customer support email responses')."
    )
    parser.add_argument(
        "--columns", type=str, nargs='*',
        help="Space-separated list of CSV column names to be used as placeholders by the AI (e.g., ProductName UserEmail OrderID). If comma-separated, enclose in quotes."
    )
    parser.add_argument(
        "--num_templates", type=int, default=DEFAULT_NUM_TEMPLATES_FINAL,
        help="Final number of diverse templates to select and use."  # Help text updated
    )
    parser.add_argument(
        "--output", type=str, default=DEFAULT_OUTPUT_FILE,
        help="Path to save the final instantiated text instances. Statistics will be saved to a .stat file with the same base name."
    )
    parser.add_argument(
        "--model", type=str, default=DEFAULT_LLM_MODEL, help="Name of the LLM model to use for template generation."
    )
    parser.add_argument(
        "--temp", type=float, default=DEFAULT_LLM_TEMPERATURE,
        help="Temperature for LLM generation (0.0 to 2.0). Higher values mean more randomness/creativity."
    )
    parser.add_argument(
        "--diversification_multiplier", type=int, default=DIVERSIFICATION_MULTIPLIER,
        help="Multiplier for initial template generation (final_num * multiplier = initial_request)."
    )
    parser.add_argument(
        "--skip_nltk", action="store_true", help="Skip NLTK-based lexical diversity metrics (distinct n-grams)."
    )
    parser.add_argument(
        "--skip_levenshtein", action="store_true", help="Skip Levenshtein distance metric."
    )
    parser.add_argument(
        "--skip_semantic", action="store_true", help="Skip SentenceTransformer-based semantic diversity metrics."
    )

    args = parser.parse_args()

    if args.skip_nltk:
        NLTK_AVAILABLE = False
        print("User chose to skip NLTK metrics.")
    if args.skip_levenshtein:
        LEVENSHTEIN_AVAILABLE = False
        print("User chose to skip Levenshtein metrics.")
    if args.skip_semantic:
        SENTENCE_TRANSFORMERS_AVAILABLE = False
        print("User chose to skip Semantic metrics.")

    DIVERSIFICATION_MULTIPLIER = args.diversification_multiplier  # Allow override from command line

    if not os.getenv("OPENAI_API_KEY"):
        print("CRITICAL ERROR: The OPENAI_API_KEY environment variable must be set before running this script.")
        print("Example: export OPENAI_API_KEY='your_api_key_here'")
        return

    print(f"--- Starting Generic Template Generation Script ---")
    print(f"Using LLM Model: {args.model} with Temperature: {args.temp}")
    print(f"Task Description for AI: '{args.task}'")
    print(f"Final number of diverse templates to select: {args.num_templates}")
    print(f"Diversification multiplier for initial generation: {DIVERSIFICATION_MULTIPLIER}")

    placeholder_cols = []
    if args.columns:
        if len(args.columns) == 1 and ',' in args.columns[0]:
            placeholder_cols = [col.strip() for col in args.columns[0].split(',')]
        else:
            placeholder_cols = [col.strip() for col in args.columns]

    if not placeholder_cols:
        print(
            "Note: No specific CSV columns provided for placeholders. AI will be asked to generate generic templates for the task.")
    else:
        print(f"CSV columns to be used as placeholders by AI: {placeholder_cols}")

    if not os.path.exists(args.csv_file):
        print(f"Warning: CSV file '{args.csv_file}' not found. Creating a dummy CSV for demonstration purposes.")
        dummy_cols_for_csv = placeholder_cols if placeholder_cols else ['FieldA', 'FieldB', 'Description']
        if not dummy_cols_for_csv:
            dummy_cols_for_csv = ['SampleData']
        dummy_data: Dict[str, List[Any]] = {col: [] for col in dummy_cols_for_csv}
        for i in range(1, 4):
            for col in dummy_cols_for_csv:
                dummy_data[col].append(f"{col}_Value{i}")
        try:
            pd.DataFrame(dummy_data).to_csv(args.csv_file, index=False)
            print(f"Dummy CSV file '{args.csv_file}' created with columns: {dummy_cols_for_csv}.")
        except Exception as e_csv:
            print(f"CRITICAL ERROR: Could not create dummy CSV file: {e_csv}")
            return

    # generate_templates_via_llm now returns a tuple: (templates_list, stats_dict)
    ai_templates, diversity_stats_results = generate_templates_via_llm(
        task_description=args.task,
        placeholder_names=placeholder_cols,
        num_templates_to_generate_final=args.num_templates,
        diversification_multiplier=DIVERSIFICATION_MULTIPLIER,
        model_name=args.model,
        temperature=args.temp
    )

    base_output_name, _ = os.path.splitext(args.output)
    stat_file_path = base_output_name + ".stat"
    save_diversity_stats(diversity_stats_results, stat_file_path)

    if ai_templates:
        final_texts = instantiate_templates_with_csv_data(
            csv_path=args.csv_file,
            ai_generated_templates=ai_templates,
            placeholder_column_names=placeholder_cols
        )

        if final_texts:
            print(f"\n--- {len(final_texts)} Final Text Instances Generated (Showing first 5) ---")
            for i, text_instance in enumerate(final_texts[:5]):
                print(f"Instance {i + 1}:\n{text_instance}\n" + "-" * 30)

            try:
                with open(args.output, "w", encoding="utf-8") as f_out:
                    for text_to_save in final_texts:
                        f_out.write(text_to_save + "\n<END_OF_INSTANCE>\n\n")
                print(f"\nAll ({len(final_texts)}) instantiated texts have been saved to: {args.output}")
            except Exception as e:
                print(f"CRITICAL ERROR: Could not save instantiated texts to '{args.output}': {e}")
                traceback.print_exc()
        else:
            print("No final text instances were generated from the templates and CSV data.")
    else:
        print("AI did not generate any templates. Cannot proceed with data instantiation.")

    print(f"--- Script Finished ---")


if __name__ == "__main__":
    main()

```

## Archivo: `schemas.py`

```typescript
import time
from typing import List, Optional

from pydantic import BaseModel


# Define the schema for the conversation
class Message(BaseModel):
    """
    Represents a single message in a conversation.

    Attributes:
        role (str): The role of the message sender (e.g., "AI", "USER", "Tool").
        content (str): The content of the message.
        tool_call_id (Optional[str]): The ID of the tool call, if applicable. Defaults to None.
        tool_calls (Optional[List[dict]]): A list of tool calls, if applicable. Defaults to None.
    """

    role: str  # "AI", "USER", "Tool"
    content: str
    tool_call_id: Optional[str] = None  # ID of the tool call, if applicable
    tool_calls: Optional[List[dict]] = None  # List of tool calls, if applicable


class Conversation(BaseModel):
    """
    Represents a conversation consisting of multiple messages and an expected response.

    Attributes:
        run_id (Optional[str]): The ID of the LangSmith run. Defaults to None.
        messages (List[Message]): A list of messages in the conversation.
        expected_response (Message): The expected response for the conversation.
    """

    run_id: Optional[str] = None  # ID of the LangSmith run
    messages: List[Message]
    expected_response: Message
    considerations: Optional[str] = None  # Consideration for the evaluator.
    # Creation date of the conversation YYYY-MM-DD-HH:MM:SS
    creation_date: Optional[str] = time.strftime("%Y-%m-%d-%H:%M:%S")

```

## Archivo: `test_supervisor_output.py`

```typescript
import pytest
from copilot.core.agent.langgraph_agent import setup_graph
from copilot.core.schemas import GraphQuestionSchema
from langsmith import Client, wrappers
from openai import OpenAI
from pydantic import BaseModel, Field

grafo = {
    "conversation_id": "ASAAASDADASDASD",
    "assistants": [
        {
            "name": "Node2Emojis",
            "type": "multimodel",
            "description": "Add emojis to a text and traduce it to english. The "
            'recommended input is "Process the following text: <TEXT>"',
            "assistant_id": "AC6C184726B149C28064FE4E8AC86D0B",
            "temperature": 1,
            "tools": [],
            "provider": "openai",
            "model": "gpt-4o",
            "kb_vectordb_id": "KB_AC6C184726B149C28064FE4E8AC86D0B",
            "system_prompt": "Recibes texto en cualquier idioma y devuelves exactamente"
            " la traduccion al ingles, con muchos emojis. No puedes"
            " realizar otra tarea.\n",
        },
        {
            "name": "NODE1Text",
            "type": "langchain",
            "description": "Spanish stories generator",
            "assistant_id": "3C4D54A938E64DBCBCF1757EB71B54DC",
            "temperature": 1,
            "tools": [],
            "provider": "openai",
            "model": "gpt-4o",
            "kb_vectordb_id": "KB_3C4D54A938E64DBCBCF1757EB71B54DC",
            "system_prompt": "Eres un asistente que solo genera cuentos de 3 actos de"
            " menos de 10 palabras. Solo en Español y sin emojis.\n",
        },
    ],
    "system_prompt": "Resuelves peticiones dividiendo la tarea en subtareas entre los"
    " miembros de tu equipo.\n\nCuando decidas que el flujo termina,"
    " debes dar una respuesta final con lo que se hizo y lo que fallo."
    " No omitas informacion con esa respuesta final.",
    "temperature": 0.1,
    "graph": {"stages": [{"name": "stage1", "assistants": ["Node2Emojis", "NODE1Text"]}]},
    "question": "What is the capital of France?",
    "local_file_ids": [],
    "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
    "history": [],
}

client = Client()
openai_client = wrappers.wrap_openai(OpenAI())

# Create inputs and reference outputs
examples = [{"input": "perros y gatos", "output": " Habia una vez un gato y un perro y fueron felices."}]
# field impuyt in examples
inputs = [{"question": elem["input"]} for elem in examples]
outputs = [{"output": elem["output"]} for elem in examples]

SYSTEM_PROMPT1 = """
Resuelves peticiones dividiendo la tarea en subtareas entre los miembros de tu equipo.

Cuando decidas que el flujo termina, debes dar una respuesta final con lo que se hizo y
 lo que fallo. No omitas informacion con esa respuesta final.

"""


# Define the application logic you want to evaluate inside a target function
def target(inputs: dict) -> dict:
    # set question to grafo
    grafo["question"] = inputs["question"]
    # convert graph dict to GraphQuestionSchema
    question = GraphQuestionSchema.model_validate(grafo)
    graph, conv = setup_graph(question=question, memory=None)

    response = graph.invoke(inputs["question"], thread_id=question.conversation_id)

    return response


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
        description="Boolean that indicates whether the response is accurate relative to the reference answer"
    )


# Define LLM judge that grades the accuracy of the response relative to reference output
def accuracy(outputs: dict, reference_outputs: dict) -> bool:
    content = (
        context.replace("{instructions}", reference_outputs["instructions"])
        .replace("{next}", reference_outputs["next"])
        .replace("{next_generated}", outputs["next"])
        .replace("{generated_instructions}", outputs["instructions"])
    )
    response = openai_client.beta.chat.completions.parse(
        model="gpt-4o",
        messages=[{"role": "system", "content": instructions}, {"role": "user", "content": content}],
        response_format=Grade,
    )
    return response.choices[0].message.parsed.score


@pytest.fixture
def dataset():
    return client.read_dataset(dataset_id="f835b05c-257f-447f-b973-2a41f34eb698")


@pytest.fixture
def examples_dataset(dataset):
    for ex in client.list_examples(dataset_id=dataset.id):
        client.delete_example(ex.id)
    client.create_examples(inputs=inputs, outputs=outputs, dataset_id=dataset.id)
    return dataset


def test_evaluation(examples_dataset):
    experiment_results = client.evaluate(
        target,
        data=examples_dataset.name,
        evaluators=[accuracy],
        experiment_prefix="first-eval-in-langgraph",
        max_concurrency=2,
        num_repetitions=3,
    )
    assert experiment_results is not None

```

## Archivo: `utils.py`

```typescript
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
    if not (results_obj and hasattr(results_obj, '_results') and
            isinstance(results_obj._results, list) and results_obj._results):
        # Return a structure indicating an error or empty data
        return {"error": "Invalid or empty 'results_obj' structure.", "table_items": [], "avg_score": None}

    table_items = []
    error_occurred = False
    error_message = ""

    # Process results
    for result_item in results_obj._results:
        try:
            # Assuming evaluation_results might be a list or a single dict
            evaluation_results_data = result_item.get('evaluation_results', {}).get('results', [])
            if not isinstance(evaluation_results_data, list): # Handle if 'results' is not a list
                evaluation_results_data = [evaluation_results_data] if evaluation_results_data else []


            child_runs_data = result_item.get('run', {}).child_runs or []

            # Heuristic: Try to match eval results to child runs or inputs
            # This part might need adjustment based on the exact structure of results_obj
            num_items_to_process = max(len(evaluation_results_data), len(child_runs_data))
            if num_items_to_process == 0 and result_item.get('run'): # Case where there are no child runs but a main run
                 num_items_to_process = 1


            for i in range(num_items_to_process):
                score = -1
                eval_comment_content = "N/A"
                input_comment_content = "N/A"
                output_data = "N/A"

                # Get score and eval_comment from evaluation_results
                if i < len(evaluation_results_data) and evaluation_results_data[i]:
                    eval_result = evaluation_results_data[i]
                    score = getattr(eval_result, 'score', -1)
                    eval_comment_content = getattr(eval_result, 'comment', 'N/A')

                # Get input_comment from child_runs or main run inputs
                current_run_for_input = child_runs_data[i] if i < len(child_runs_data) else result_item.get('run')
                if current_run_for_input:
                    inputs = getattr(current_run_for_input, 'inputs', {})
                    if isinstance(inputs, dict) and 'messages' in inputs:
                        messages = inputs['messages']
                        # Try to find user message
                        user_message = next((msg.get('kwargs', {}).get('content') for msg in messages if isinstance(msg, dict) and msg.get('kwargs', {}).get('type') == 'human'), None)
                        if user_message:
                            input_comment_content = user_message
                        elif messages: # Fallback to stringifying messages
                             input_comment_content = json.dumps(messages)
                        else:
                            input_comment_content = json.dumps(inputs) # Fallback if no messages
                    elif isinstance(inputs, dict):
                        input_comment_content = json.dumps(inputs)
                    else:
                        input_comment_content = str(inputs)

                # Get output_data from child_runs or main run outputs
                current_run_for_output = child_runs_data[i] if i < len(child_runs_data) else result_item.get('run')
                if current_run_for_output:
                    outputs = getattr(current_run_for_output, 'outputs', {})
                    if isinstance(outputs, dict) and 'generations' in outputs:
                         # Try to extract AI message content
                        generations = outputs['generations']
                        ai_message = next((gen.get('message', {}).get('kwargs', {}).get('content') for gen in generations if isinstance(gen, dict) and gen.get('message')), None)
                        if ai_message:
                            output_data = ai_message
                        else: # Fallback to stringifying outputs
                            output_data = json.dumps(outputs)
                    elif isinstance(outputs, dict):
                        output_data = json.dumps(outputs)
                    else:
                        output_data = str(outputs)


                table_items.append({
                    'comment': input_comment_content,
                    'score': score,
                    'output': output_data,
                    'eval_comment': eval_comment_content
                })

        except Exception as e:
            error_message = f"Error processing result_item: {str(e)}. Item: {json.dumps(result_item, default=str, indent=2)[:500]}..." # Log part of the item
            error_occurred = True
            # Add an error placeholder to table_items to acknowledge the item
            table_items.append({
                'comment': f"Error processing item: {str(e)}",
                'score': -1,
                'output': "Error",
                'eval_comment': "Error processing"
            })
            # Decide whether to break or continue: for now, continue to see other items if possible
            # break

    if error_occurred and not error_message: # If individual items had errors but no global one
        error_message = "Errors occurred while processing some evaluation items."


    valid_scores = [item['score'] for item in table_items if isinstance(item.get('score'), (int, float)) and item['score'] != -1]
    avg_score = None
    if valid_scores:
        avg_score = sum(valid_scores) / len(valid_scores)

    return {"table_items": table_items, "avg_score": avg_score, "error": error_message if error_message else None}


def get_score_tailwind_classes(score_value):
    """Returns Tailwind CSS classes for score badges based on EvalDash style."""
    if score_value is None or score_value == -1: # Error or N/A
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
    if report_data.get("error") and not report_data.get("table_items"): # Only show global error if no items
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
        if avg_score >= 0.9: score_text_color_class = "text-green-600 dark:text-green-400"
        elif avg_score >= 0.7: score_text_color_class = "text-yellow-500 dark:text-yellow-400"
        elif avg_score >= 0.5: score_text_color_class = "text-orange-500 dark:text-orange-400"
        else: score_text_color_class = "text-red-500 dark:text-red-400"

        avg_score_html = f"""
        <div class="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6 my-6 text-center">
            <h2 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-1 uppercase">Score Promedio Global</h2>
            <p class="text-4xl font-bold {score_text_color_class}">{(avg_score * 100):.1f}%</p>
        </div>
        """

    if not table_items and not avg_score_html and not error_message_html: # If truly no data at all
         return """
         <div class="bg-white dark:bg-gray-800 rounded-lg shadow-md p-8 text-center my-6">
            <h2 class="text-xl font-semibold text-gray-900 dark:text-white mb-2">No se encontraron resultados de evaluación</h2>
            <p class="text-gray-600 dark:text-gray-400">No hay datos para mostrar en el reporte.</p>
        </div>
        """


    table_html_rows = []
    for item in table_items:
        score = item.get('score', -1)
        score_val_str = f"{(score * 100):.1f}%" if isinstance(score, (int, float)) and score != -1 else "Error"
        score_classes = get_score_tailwind_classes(score)

        table_html_rows.append(f"""
      <tr class="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
        <td class="px-6 py-4 text-sm text-gray-800 dark:text-gray-200">{shorten(item.get('comment', 'N/A'))}</td>
        <td class="px-6 py-4 text-sm text-gray-800 dark:text-gray-200">{shorten(item.get('output', 'N/A'))}</td>
        <td class="px-6 py-4 whitespace-nowrap text-center">
            <span class="{score_classes}">{score_val_str}</span>
        </td>
        <td class="px-6 py-4 text-sm text-gray-800 dark:text-gray-200">{shorten(item.get('eval_comment', 'N/A'))}</td>
      </tr>
""")

    html_string = error_message_html + avg_score_html + """
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
                """ + "\n".join(table_html_rows) + """
                </tbody>
            </table>
        </div>
    </div>"""
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
        f.write(f"""
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
    """)
    print(f"HTML report generated: {os.path.abspath(html_file_path)}")
    return html_file_path, report_timestamp # Return the path and timestamp

```

