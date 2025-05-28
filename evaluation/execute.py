"""
Etendo Task Execution Script

This script automates the execution of Etendo tasks retrieved from a PostgreSQL database.
It processes each task by calling an Etendo API endpoint, tracks task execution status,
monitors record creation in any specified table, and generates an HTML report with execution results.

Dependencies:
    - psycopg2: PostgreSQL database adapter
    - requests: HTTP library for API calls
    - dotenv: For environment variable loading

Usage:
    Run the script directly with optional arguments:
    python bulk_tasks_eval.py [--envfile ENV_FILE] [--etendo_url ETENDO_URL] [--dataset DATASET_FILE] [--table TABLE_NAME]
"""

import argparse
import os
import time
import uuid
from datetime import datetime

import psycopg2
import requests
from dotenv import load_dotenv
from psycopg2 import sql
from utils import (
    calc_md5,
    generate_html_report,
    get_agent_config,
    get_tools_for_agent,
    save_conversation_from_run,
    tool_to_openai_function,
    validate_dataset_folder,
)


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
AUTH_TOKEN = 'YWRtaW46YWRtaW4='

# Default table to monitor
DEFAULT_TABLE = 'm_product'

# Default task configuration
DEFAULT_CLIENT_ID = '23C59575B9CF467C9620760EB255B389'
DEFAULT_ORG_ID = '0'
DEFAULT_IS_ACTIVE = 'Y'
DEFAULT_USER_ID = '100'
DEFAULT_STATUS = 'D0FCC72902F84486A890B70C1EB10C9C'
DEFAULT_TASK_TYPE_ID = '6F0F3D5470B44A73822EA2CF3175690C'
DEFAULT_AGENT_ID = '25AEC648805544A9B7A644667C9E7D41'

# HTTP headers for API requests
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
        psycopg2.connection: Database connection object or None if connection fails
    """
    try:
        return psycopg2.connect(**config)
    except Exception as e:
        print(f"Database connection error: {e}")
        return None


def get_task_ids_from_db(conn):
    """
    Retrieves task IDs from the 'etask_task' table.

    Args:
        conn: Database connection object

    Returns:
        list: List of task IDs or empty list if retrieval fails
    """
    ids = []
    if not conn:
        return ids
    try:
        cur = conn.cursor()
        query = sql.SQL("SELECT {} FROM {}").format(
            sql.Identifier("etask_task_id"),
            sql.Identifier("etask_task")
        )
        cur.execute(query)
        ids = [row[0] for row in cur.fetchall()]
        cur.close()
        print(f"{len(ids)} tasks found.")
    except Exception as e:
        print(f"Error querying task IDs: {e}")
    return ids


def count_db_records(conn, table_name):
    """
    Counts the number of records in a specified database table.

    Args:
        conn: Database connection object
        table_name (str): Name of the table to count records in

    Returns:
        int: Number of records or -1 if counting fails
    """
    if not conn:
        return -1
    try:
        cur = conn.cursor()
        query = sql.SQL("SELECT COUNT(*) FROM {}").format(sql.Identifier(table_name))
        cur.execute(query)
        count = cur.fetchone()[0]
        cur.close()
        return count
    except Exception as e:
        print(f"Error counting records in {table_name}: {e}")
        return -1


def create_tasks_from_dataset(conn, dataset_file):
    """
    Creates tasks in the database from a dataset file.

    Args:
        conn: Database connection object
        dataset_file (str): Path to the dataset file containing task requests

    Returns:
        int: Number of tasks created or -1 if creation fails
    """
    if not conn:
        return -1

    # Check if file exists
    if not os.path.exists(dataset_file):
        print(f"Dataset file not found: {dataset_file}")
        return -1

    # Read the dataset file
    try:
        with open(dataset_file, 'r', encoding='utf-8') as f:
            requests_data = f.readlines()
    except Exception as e:
        print(f"Error reading dataset file: {e}")
        return -1

    # Filter out empty lines
    requests_data = [req.strip() for req in requests_data if req.strip()]

    # Generate a group ID for this batch
    group_id = str(uuid.uuid4())

    # Insert tasks into the database
    tasks_created = 0
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]

    try:
        cur = conn.cursor()
        for request in requests_data:
            task_id = str(uuid.uuid4()).replace('-', '').upper()

            insert_query = """
            INSERT INTO etask_task (
                etask_task_id, ad_client_id, ad_org_id, isactive, created, createdby, 
                updated, updatedby, status, assigned_user, etask_task_type_id, 
                em_etcop_question, em_etcop_response, em_etcop_agentid, 
                em_etcop_bulkadd, em_etcop_exec, em_etcop_group
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """

            cur.execute(insert_query, (
                task_id, DEFAULT_CLIENT_ID, DEFAULT_ORG_ID, DEFAULT_IS_ACTIVE,
                now, DEFAULT_USER_ID, now, DEFAULT_USER_ID,
                DEFAULT_STATUS, None, DEFAULT_TASK_TYPE_ID,
                request, None, DEFAULT_AGENT_ID,
                'Y', 'N', group_id
            ))

            tasks_created += 1

        conn.commit()
        cur.close()
        print(f"{tasks_created} tasks created with group ID: {group_id}")
        return tasks_created
    except Exception as e:
        conn.rollback()
        print(f"Error creating tasks: {e}")
        return -1


def execute_etendo_task(task_id, etendo_url):
    """
    Executes a task in Etendo by making an API call.

    Args:
        task_id (str): ID of the task to execute
        etendo_url (str): Base URL for Etendo API

    Returns:
        tuple: (success_flag, execution_time, error_message)
            - success_flag (bool): True if execution was successful
            - execution_time (float): Time taken in seconds
            - error_message (str): Error message if execution failed, None otherwise
    """
    start_time = time.time()
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
        response.raise_for_status()
        print(f"  Response: {response.status_code}")
        return True, round(time.time() - start_time, 2), None
    except Exception as e:
        error_msg = f"Error: {str(e)}"
        print(f"  {error_msg}")
        return False, round(time.time() - start_time, 2), error_msg


def generate_html_report(data):
    """
    Generates an HTML report with task execution results.

    Args:
        data (dict): Dictionary containing report data with the following keys:
            - total_tasks_found: Number of tasks found
            - successful_tasks: Number of tasks executed successfully
            - failed_tasks: Number of tasks that failed
            - total_script_duration: Total script execution time
            - table_name: Name of the monitored table
            - records_before: Record count before execution
            - records_after: Record count after execution
            - records_created: Number of records created
            - task_details: List of dictionaries with task execution details
    """
    now = datetime.now()
    filename = f"etendo_task_report_{now.strftime('%Y%m%d_%H%M%S')}.html"

    html = f"""<!DOCTYPE html>
    <html>
    <head>
        <title>Etendo Task Execution Report</title>
        <style>
            body {{ font-family: Arial; margin: 20px; background-color: #f4f4f4; }}
            .container {{ background-color: #fff; padding: 20px; border-radius: 8px; }}
            table {{ width: 100%; border-collapse: collapse; margin-top: 20px; }}
            th, td {{ border: 1px solid #ddd; padding: 8px; text-align: left; }}
            th {{ background-color: #007bff; color: white; }}
            .success {{ color: green; }}
            .failure {{ color: red; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Etendo Task Execution Report</h1>
            <p><strong>Date:</strong> {now.strftime("%Y-%m-%d %H:%M:%S")}</p>

            <h2>Summary</h2>
            <p>Tasks found: {data['total_tasks_found']}</p>
            <p>Successful tasks: <span class="success">{data['successful_tasks']}</span></p>
            <p>Failed tasks: <span class="failure">{data['failed_tasks']}</span></p>
            <p>Total time: {data['total_script_duration']:.2f} seconds</p>

            <h2>Records in table: {data['table_name']}</h2>
            <p>Initial count: {data['records_before']}</p>
            <p>Final count: {data['records_after']}</p>
            <p>Records created: <strong>{data['records_created']}</strong></p>

            <h2>Details</h2>
            <table>
                <tr><th>Task ID</th><th>Status</th><th>Duration (s)</th><th>Error</th></tr>
    """

    for task in data['task_details']:
        status_class = "success" if task['status'] == "Success" else "failure"
        error_msg = task['error'] if task['error'] else "N/A"
        html += f"""<tr>
            <td>{task['id']}</td>
            <td class="{status_class}">{task['status']}</td>
            <td>{task['duration']:.2f}</td>
            <td>{error_msg}</td>
        </tr>"""

    html += """
            </table>
        </div>
    </body>
    </html>"""

    try:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(html)
        print(f"\nHTML report generated: {filename}")
    except Exception as e:
        print(f"Error generating HTML report: {e}")


def parse_arguments():
    """
    Parse command line arguments for the script.

    Returns:
        argparse.Namespace: Parsed arguments
    """
    parser = argparse.ArgumentParser(description="Process Etendo tasks from database.")
    parser.add_argument("--envfile", help="Environment file", default=None)
    parser.add_argument("--etendo_url", help="Etendo base URL", default=ETENDO_BASE_URL)
    parser.add_argument("--dataset", help="Dataset file with task requests to create", default=None)
    parser.add_argument("--table", help="Database table to monitor record count", default=DEFAULT_TABLE)
    parser.add_argument("--user", help="The username for authentication", default=None)
    parser.add_argument("--password", help="The password for authentication", default=None)
    parser.add_argument("--save", help="The run ID to extract and save the conversation", default=None)
    parser.add_argument("--agent_id", help="The unique identifier of the agent whose conversations are being saved", default=None)

    # Database connection parameters (optional)
    parser.add_argument("--dbname", help="Database name", default=None)
    parser.add_argument("--dbuser", help="Database user", default=None)
    parser.add_argument("--dbpassword", help="Database password", default=None)
    parser.add_argument("--dbhost", help="Database host", default=None)
    parser.add_argument("--dbport", help="Database port", default=None)

    return parser.parse_args()


def load_config(args):
    """
    Load configuration from environment file and command line arguments.

    Args:
        args (argparse.Namespace): Command line arguments

    Returns:
        tuple: (db_config, etendo_url)
    """
    db_config = DB_CONFIG.copy()
    etendo_url = args.etendo_url

    # Load from environment file if provided
    if args.envfile:
        print(f"Loading environment variables from {args.envfile}")
        load_dotenv(args.envfile, verbose=True)

        # Update from environment variables using the correct property names
        if os.getenv('bbdd.sid'):
            db_config['dbname'] = os.getenv('bbdd.sid')
        if os.getenv('bbdd.user'):
            db_config['user'] = os.getenv('bbdd.user')
        if os.getenv('bbdd.password'):
            db_config['password'] = os.getenv('bbdd.password')
        if os.getenv('bbdd.host'):
            db_config['host'] = os.getenv('bbdd.host')
        if os.getenv('ETENDO_BASE_URL'):
            etendo_url = os.getenv('ETENDO_BASE_URL')

    # Command line arguments override env file
    if args.dbname:
        db_config['dbname'] = args.dbname
    if args.dbuser:
        db_config['user'] = args.dbuser
    if args.dbpassword:
        db_config['password'] = args.dbpassword
    if args.dbhost:
        db_config['host'] = args.dbhost
    if args.dbport:
        db_config['port'] = args.dbport
    if args.etendo_url:
        etendo_url = args.etendo_url

    return db_config, etendo_url


def main():
    """
    Main function that orchestrates the entire task execution process:
    1. Parses command line arguments
    2. Loads configuration from environment file and arguments
    3. Connects to the database
    4. Creates tasks from dataset if provided
    5. Counts records in specified table before execution
    6. Retrieves and processes tasks
    7. Counts records in specified table after execution
    8. Generates an HTML report
    9. Outputs execution summary
    """
    start_time = time.time()
    print(f"Starting script... [{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}]")

    # Parse arguments and load configuration
    args = parse_arguments()

    # If a save_run_id is provided, extract and save the conversation
    if args.save:
        print("Save ID detected.")
        config_agent = get_agent_config(args.agent_id, args.etendo_url, None, args.user, args.password)

        save_conversation_from_run(
            args.agent_id, args.save, config_agent.get("system_prompt"), base_path=args.dataset
        )
        print("Conversation.json saved.")
        return None, None

    db_config, etendo_url = load_config(args)

    # Get the table to monitor
    table_to_monitor = args.table

    print(f"Using database: {db_config['host']}:{db_config['port']}/{db_config['dbname']}")
    print(f"Using Etendo URL: {etendo_url}")
    print(f"Monitoring table: {table_to_monitor}")

    # Connect to database
    conn = get_db_connection(db_config)
    if not conn:
        print("Could not connect to the database. Exiting.")
        return

    # Create tasks from dataset if provided
    if args.dataset:
        print(f"Creating tasks from dataset: {args.dataset}")
        tasks_created = create_tasks_from_dataset(conn, args.dataset)
        if tasks_created <= 0:
            print("No tasks were created from the dataset. Exiting.")
            conn.close()
            return
        print(f"Successfully created {tasks_created} tasks from the dataset.")

    # Count initial records in the specified table
    records_before = count_db_records(conn, table_to_monitor)
    print(f"Records in {table_to_monitor} before: {records_before if records_before != -1 else 'Error'}")

    # Get tasks and process them
    tasks = get_task_ids_from_db(conn)
    details = []
    successes = 0
    failures = 0

    for i, task_id in enumerate(tasks):
        print(f"\nProcessing task {i + 1}/{len(tasks)}:")
        success, duration, error = execute_etendo_task(str(task_id), etendo_url)

        status = "Success" if success else "Failure"
        details.append({
            'id': task_id,
            'status': status,
            'duration': duration,
            'error': error
        })

        if success:
            successes += 1
        else:
            failures += 1
        print(f"  Result: {status}, Duration: {duration:.2f}s")

    # Count final records in the specified table
    records_after = count_db_records(conn, table_to_monitor)
    print(f"Records in {table_to_monitor} after: {records_after if records_after != -1 else 'Error'}")

    records_created = -1
    if records_before != -1 and records_after != -1:
        records_created = records_after - records_before

    # Close connection
    conn.close()

    total_duration = time.time() - start_time

    # Prepare report data
    report_data = {
        'total_tasks_found': len(tasks),
        'successful_tasks': successes,
        'failed_tasks': failures,
        'total_script_duration': total_duration,
        'table_name': table_to_monitor,
        'records_before': records_before,
        'records_after': records_after,
        'records_created': records_created,
        'task_details': details
    }

    # Generate HTML report
    generate_html_report(report_data)

    # Final summary
    print("\n--- FINAL SUMMARY ---")
    print(f"Tasks found: {len(tasks)}")
    print(f"Successful tasks: {successes}")
    print(f"Failed tasks: {failures}")
    print(f"Table monitored: {table_to_monitor}")
    print(f"Records before: {records_before}")
    print(f"Records after: {records_after}")
    print(f"Records created: {records_created}")
    print(f"Total time: {total_duration:.2f} seconds")
    print(f"Script finished. [{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}]")


if __name__ == "__main__":
    main()