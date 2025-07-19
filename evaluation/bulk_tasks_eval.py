"""
Etendo Task Creation & Execution Script

This script automates the complete process of creating and executing tasks in Etendo:
1. Creates tasks by combining data from a CSV with text templates
2. Executes the created tasks by calling an Etendo API
3. Monitors changes in database tables
4. Generates an HTML report with the results
5. Sends evaluation results to a Supabase backend

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
import subprocess
import time
import traceback
import uuid
from datetime import datetime

import pandas as pd
import psycopg2
import requests

# Import refactored utilities
from common_utils import EvaluationLogger
from copilot.core.utils import read_optional_env_var
from dotenv import load_dotenv
from psycopg2 import sql

# Default database connection configuration
DB_CONFIG = {
    "dbname": "copilot_test",
    "user": "postgres",
    "password": "syspass",
    "host": "localhost",
    "port": "5432",
}

# Default Etendo API configuration
ETENDO_BASE_URL = "http://localhost:8080/etendo"
ETENDO_API_PATH = "/org.openbravo.client.kernel"
PROCESS_ID = "7260F458FA2E43A7968E25E4B5242E60"
WINDOW_ID = "172C8727CDB74C948A207A1405CE445B"
ACTION = "com.etendoerp.copilot.process.ExecTask"
AUTH_TOKEN = "YWRtaW46YWRtaW4="  # admin:admin

# Default table to monitor
DEFAULT_TABLE = "m_product"

# Default SQL query template
DEFAULT_QUERY = "SELECT COUNT(*) FROM {}"

# Default task configuration
DEFAULT_CLIENT_ID = "23C59575B9CF467C9620760EB255B389"
DEFAULT_ORG_ID = "0"
DEFAULT_IS_ACTIVE = "Y"
DEFAULT_USER_ID = "100"
DEFAULT_STATUS = "D0FCC72902F84486A890B70C1EB10C9C"  # Default status for new tasks
DEFAULT_TASK_TYPE_ID = "A83E397389DB42559B2D7719A442168F"

# Status of tasks to be executed
TASKS_STATUS = "D0FCC72902F84486A890B70C1EB10C9C"  # Status indicating tasks ready for execution

# HTTP Headers for API requests
HEADERS = {"Content-Type": "application/json;charset=UTF-8", "Authorization": "Basic " + AUTH_TOKEN}


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
        with open(template_file, "r", encoding="utf-8") as f:
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


def create_tasks_from_templates_and_csv(agent_id, conn, df, templates):
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
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

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

                cur.execute(
                    insert_query,
                    (
                        DEFAULT_CLIENT_ID,
                        DEFAULT_ORG_ID,
                        DEFAULT_IS_ACTIVE,
                        now,
                        DEFAULT_USER_ID,
                        now,
                        DEFAULT_USER_ID,
                        DEFAULT_STATUS,
                        None,
                        DEFAULT_TASK_TYPE_ID,
                        task_text,
                        None,
                        agent_id,
                        "Y",
                        "N",
                        group_id,
                    ),
                )

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
                sql.Identifier("etask_task_id"), sql.Identifier("etask_task")
            )
            cur.execute(query, (TASKS_STATUS, group_id))
        else:
            query = sql.SQL("SELECT {} FROM {} WHERE status = %s").format(
                sql.Identifier("etask_task_id"), sql.Identifier("etask_task")
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
    payload = {"recordIds": [task_id], "_buttonValue": "DONE", "_params": {}, "_entityName": "ETASK_Task"}

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


def generate_html_report(data, write_to_file=True):
    """
    Generates an HTML report string with task execution results.
    Optionally writes the report to a file.

    Args:
        data (dict): Dictionary with report data.
        write_to_file (bool): If True, writes the HTML to a file.

    Returns:
        str: The generated HTML content as a string.
    """
    now = datetime.now()
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

    for task in data["task_details"]:
        # Determine status class
        if task["status"] == "Success":
            status_class = "success"
        elif task["status"] == "Failure":
            status_class = "failure"
        else:
            status_class = "skipped"

        error_msg_display = task["error"] if task["error"] else "N/A"
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

    if write_to_file:
        try:
            # create directories if they don't exist
            os.makedirs("evaluation_output", exist_ok=True)
            with open(filename, "w", encoding="utf-8") as f:
                f.write(html_content)
            print(f"\nHTML report generated: {filename}")
        except Exception as e:
            print(f"Error generating HTML report file: {e}")

    return html_content


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
    parser.add_argument("--agent_id", help="Agent ID", default=None)
    parser.add_argument("--project_name", help="Project name", default="default_project")
    parser.add_argument("--table", help="Database table to monitor record count", default=DEFAULT_TABLE)
    parser.add_argument(
        "--query",
        help="Custom SQL query to count records (use {} as placeholder for table name)",
        default=DEFAULT_QUERY,
    )
    parser.add_argument(
        "--max_tasks", type=int, help="Maximum number of tasks to process from CSV", default=1000
    )
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
        current_db_config["dbname"] = read_optional_env_var("bbdd.sid", "etendo")
        current_db_config["user"] = read_optional_env_var("bbdd.user", "tad")
        current_db_config["password"] = read_optional_env_var("bbdd.password", "tad")
        current_db_config["host"] = read_optional_env_var("bbdd.host", "localhost")
        current_db_config["port"] = read_optional_env_var("bbdd.port", "5432")
        if os.getenv("ETENDO_BASE_URL"):
            current_etendo_url = os.getenv("ETENDO_BASE_URL")

    # Command-line arguments override environment file and defaults
    if args.dbname:
        current_db_config["dbname"] = args.dbname
    if args.dbuser:
        current_db_config["user"] = args.dbuser
    if args.dbpassword:
        current_db_config["password"] = args.dbpassword
    if args.dbhost:
        current_db_config["host"] = args.dbhost
    if args.dbport:
        current_db_config["port"] = args.dbport

    if args.etendo_url != ETENDO_BASE_URL:
        current_etendo_url = args.etendo_url
    elif os.getenv("ETENDO_BASE_URL"):
        current_etendo_url = os.getenv("ETENDO_BASE_URL")

    return current_db_config, current_etendo_url


def send_evaluation_to_supabase(data_payload: dict):
    """Envía los datos de la evaluación a la función de Supabase."""
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


def _setup_execution_environment(args) -> tuple:
    """Setup execution environment and validate configuration"""
    logger = EvaluationLogger("BULK_TASKS")
    logger.info("Setting up execution environment")

    effective_db_config, effective_etendo_url = load_config(args)

    logger.info(
        f"Database: {effective_db_config['host']}:{effective_db_config['port']}/{effective_db_config['dbname']}"
    )
    logger.info(f"Etendo URL: {effective_etendo_url}")
    logger.info(f"Monitoring table: {args.table}")
    logger.info(f"Maximum task limit: {args.max_tasks}")

    return logger, effective_db_config, effective_etendo_url


def _setup_project_context(args, conn):
    """Setup project context including agent and branch information"""
    project_name = args.project_name
    if not project_name or project_name == "default_project":
        try:
            project_name = os.path.basename(os.path.dirname(os.path.abspath(os.getcwd() + "/../")))
        except Exception:
            project_name = "unknown_project"

    agent_id = args.agent_id or os.path.basename(os.getcwd())
    agent_name = get_agent_name(agent_id, conn)
    branch = get_git_branch_for_directory(os.getcwd())

    return project_name, agent_id, agent_name, branch


def _load_and_validate_data(args, logger):
    """Load and validate templates and CSV data"""
    templates_list = load_templates(args.templates)
    if not templates_list:
        logger.error("No templates loaded")
        return None, None

    df_csv_data = load_csv_data(args.csv)
    if df_csv_data is None:
        logger.error("No CSV data loaded")
        return None, None

    # Limit data if necessary
    if len(df_csv_data) > args.max_tasks:
        logger.info(f"Limiting to {args.max_tasks} rows from CSV (out of {len(df_csv_data)} total)")
        df_csv_data = df_csv_data.head(args.max_tasks)

    return templates_list, df_csv_data


def _execute_tasks(args, task_ids_to_process, effective_etendo_url, logger):
    """Execute Etendo tasks and collect results"""
    task_execution_details = []
    successful_task_count = 0
    failed_task_count = 0

    if not args.skip_exec:
        logger.info(f"Executing {len(task_ids_to_process)} tasks")
        for i, task_id_val in enumerate(task_ids_to_process):
            logger.info(f"Processing task {i + 1}/{len(task_ids_to_process)}")
            success_status, exec_duration, error_detail = execute_etendo_task(
                str(task_id_val), effective_etendo_url
            )
            status_message = "Success" if success_status else "Failure"
            task_execution_details.append(
                {
                    "id": task_id_val,
                    "status": status_message,
                    "duration": exec_duration,
                    "error": error_detail,
                }
            )
            if success_status:
                successful_task_count += 1
            else:
                failed_task_count += 1
    else:
        logger.info("Skipping task execution (--skip_exec specified)")
        for i in range(len(task_ids_to_process)):
            task_execution_details.append(
                {
                    "id": f"(task {i+1} - not executed)",
                    "status": "Skipped",
                    "duration": 0.0,
                    "error": "Execution skipped by user",
                }
            )

    return task_execution_details, successful_task_count, failed_task_count


def _generate_report_and_send_results(args, report_data_dict, logger):
    """Generate HTML report and send results to Supabase"""
    html_report_content_str = generate_html_report(report_data_dict, write_to_file=True)

    # Calculate score
    score = 0.0
    if report_data_dict["total_tasks_found"] > 0 and not args.skip_exec:
        score = report_data_dict["records_created"] / report_data_dict["total_tasks_found"]

    # Send to Supabase
    evaluation_data = {
        "project": args.project_name,
        "agent": args.agent_id,
        "branch": get_git_branch_for_directory(os.getcwd()),
        "score": score,
        "dataset_size": report_data_dict["total_tasks_found"],
        "experiment": "bulk_tasks",
        "html_report_content": html_report_content_str,
    }

    logger.info("Sending evaluation data to Supabase")
    send_evaluation_to_supabase(evaluation_data)

    return html_report_content_str


def main():
    """Main function that orchestrates the entire task execution process using modular
    components."""
    script_start_time = time.time()

    # Parse arguments and setup environment
    args = parse_arguments()
    logger, effective_db_config, effective_etendo_url = _setup_execution_environment(args)

    # Connect to database
    conn = get_db_connection(effective_db_config)
    if not conn:
        logger.error("Could not connect to the database")
        return

    try:
        # Setup project context
        _, agent_id, _, _ = _setup_project_context(args, conn)
        args.agent_id = agent_id  # Update args with resolved agent_id

        # Load and validate data
        templates_list, df_csv_data = _load_and_validate_data(args, logger)
        if not templates_list or df_csv_data is None:
            return

        # Count records before execution
        records_before_execution = count_db_records(conn, args.table, args.query)
        logger.info(f"Records in {args.table} before: {records_before_execution}")

        # Create tasks
        group_id_for_batch, num_tasks_created = create_tasks_from_templates_and_csv(
            agent_id, conn, df_csv_data, templates_list
        )

        if num_tasks_created <= 0:
            logger.error("No tasks were created")
            return

        # Execute tasks
        task_ids_to_process = get_task_ids_from_db(conn, group_id_for_batch)
        task_execution_details, successful_task_count, failed_task_count = _execute_tasks(
            args, task_ids_to_process, effective_etendo_url, logger
        )

        # Count records after execution
        records_after_execution = count_db_records(conn, args.table, args.query)
        logger.info(f"Records in {args.table} after: {records_after_execution}")

        # Calculate delta
        delta_records = -1
        if records_before_execution != -1 and records_after_execution != -1:
            delta_records = records_after_execution - records_before_execution

        # Prepare report data
        total_script_run_time = time.time() - script_start_time
        report_data_dict = {
            "total_tasks_found": num_tasks_created,
            "successful_tasks": successful_task_count,
            "failed_tasks": failed_task_count,
            "total_script_duration": total_script_run_time,
            "table_name": args.table,
            "records_before": records_before_execution,
            "records_after": records_after_execution,
            "records_created": delta_records,
            "task_details": task_execution_details,
            "query_template": args.query,
        }

        # Generate report and send results
        _generate_report_and_send_results(args, report_data_dict, logger)

        # Display summary
        _display_execution_summary(report_data_dict, logger)

    finally:
        conn.close()


def _display_execution_summary(report_data_dict, logger):
    """Display execution summary"""
    logger.info("=== EXECUTION SUMMARY ===")
    logger.info(f"Total tasks: {report_data_dict['total_tasks_found']}")
    logger.info(f"Successful: {report_data_dict['successful_tasks']}")
    logger.info(f"Failed: {report_data_dict['failed_tasks']}")
    logger.info(f"Records created: {report_data_dict['records_created']}")
    logger.info(f"Total duration: {report_data_dict['total_script_duration']:.2f}s")


def get_agent_name(agent_id, conn):
    agent_name = "N/A"
    if agent_id:
        # Get agent name from the database if agent_id is provided
        sql = "SELECT name FROM etcop_app WHERE etcop_app_id = %s"
        try:
            cur = conn.cursor()
            cur.execute(sql, (agent_id,))
            result = cur.fetchone()
            if result:
                agent_name = result[0]
                print(f"Using agent name from DB: {agent_name}")
            else:
                print(f"No agent found with ID: {agent_id}. Using default agent name.")
            cur.close()
        except Exception as e:
            print(f"Error fetching agent name from DB: {e}")
            agent_name = None
    return agent_name


if __name__ == "__main__":
    main()
