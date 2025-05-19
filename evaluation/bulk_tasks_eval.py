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