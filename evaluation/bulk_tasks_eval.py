import json
import time
from datetime import datetime

import psycopg2
import requests
from psycopg2 import sql

# --- Configuration ---
DB_CONFIG = {
    'dbname': 'copilot_test',
    'user': 'postgres',
    'password': 'syspass',
    'host': 'localhost',  # or the IP/hostname of your DB server
    'port': '5432'  # your DB server port
}

ETENDO_URL = 'http://localhost:8080/etendo/org.openbravo.client.kernel'
PROCESS_ID = '7260F458FA2E43A7968E25E4B5242E60'
WINDOW_ID = '172C8727CDB74C948A207A1405CE445B'
ACTION = 'com.etendoerp.copilot.process.ExecTask'

# Auth token base64 admin:admin
AUTH_TOKEN = 'YWRtaW46YWRtaW4='  # Change this if you use another username/password

# Headers extracted from your curl command
HEADERS = {
    'Accept': '*/*',
    'Accept-Language': 'es-419,es;q=0.9',
    'Connection': 'keep-alive',
    'Content-Type': 'application/json;charset=UTF-8',
    'Origin': 'http://localhost:8080',
    'Referer': 'http://localhost:8080/etendo/',
    'Sec-Fetch-Dest': 'empty',
    'Sec-Fetch-Mode': 'cors',
    'Sec-Fetch-Site': 'same-origin',
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36',
    # Update if necessary
    'sec-ch-ua': '"Chromium";v="136", "Google Chrome";v="136", "Not.A/Brand";v="99"',  # Update if necessary
    'sec-ch-ua-mobile': '?0',
    'sec-ch-ua-platform': '"macOS"',
    'Authorization': 'Basic ' + AUTH_TOKEN
}


# --- Database Functions ---

def get_db_connection():
    """Establishes and returns a database connection."""
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        return conn
    except Exception as e:
        print(f"Error connecting to the database: {e}")
        return None


def get_task_ids_from_db(conn):
    """Gets all IDs from the etask_task table."""
    ids = []
    if not conn:
        return ids
    try:
        cur = conn.cursor()
        table_name = "etask_task"
        # Assumes the ID column is called 'etask_task_id' or similar. ADJUST IF NECESSARY!
        id_column_name = "etask_task_id"

        query = sql.SQL("SELECT {id_col} FROM {table}").format(
            id_col=sql.Identifier(id_column_name),
            table=sql.Identifier(table_name)
        )
        # query = f"SELECT {id_column_name} FROM {table_name};" # Simpler alternative

        print(
            f"Executing ID query: {cur.mogrify(query).decode('utf-8', 'ignore') if hasattr(cur, 'mogrify') else query}")
        cur.execute(query)
        rows = cur.fetchall()
        for row in rows:
            ids.append(row[0])
        cur.close()
        print(f"{len(ids)} task IDs found.")
    except Exception as e:
        print(f"Error querying task IDs: {e}")
    return ids


def count_db_records(conn, table_name_str):
    """Counts records in a specific table."""
    count = -1  # Default value in case of error
    if not conn:
        return count
    try:
        cur = conn.cursor()
        query = sql.SQL("SELECT COUNT(*) FROM {table}").format(
            table=sql.Identifier(table_name_str)
        )
        # query = f"SELECT COUNT(*) FROM {table_name_str};" # Simpler alternative

        # print(f"Executing count for {table_name_str}: {cur.mogrify(query).decode('utf-8', 'ignore') if hasattr(cur, 'mogrify') else query}")
        cur.execute(query)
        result = cur.fetchone()
        if result:
            count = result[0]
        cur.close()
    except Exception as e:
        print(f"Error counting records in table {table_name_str}: {e}")
    return count


# --- Etendo Task Function ---

def execute_etendo_task(task_id_str):
    """
    Executes the task in Etendo for a specific task ID.
    Returns: (bool: success, float: duration_seconds, str: error_message | None)
    """
    start_time = time.time()
    full_url = f"{ETENDO_URL}?processId={PROCESS_ID}&reportId=null&windowId={WINDOW_ID}&_action={ACTION}"
    payload = {
        "recordIds": [task_id_str],
        "_buttonValue": "DONE",
        "_params": {},
        "_entityName": "ETASK_Task"
    }
    error_message = None
    success = False

    try:
        print(f"  Executing task for ID: {task_id_str}...")
        response = requests.post(full_url, headers=HEADERS, data=json.dumps(payload), timeout=60)  # Increased timeout
        response.raise_for_status()
        print(f"  Response for ID {task_id_str}: {response.status_code}")
        success = True
    except requests.exceptions.HTTPError as http_err:
        error_message = f"HTTP Error: {http_err}. Response: {response.text if 'response' in locals() else 'N/A'}"
        print(f"  {error_message}")
    except requests.exceptions.ConnectionError as conn_err:
        error_message = f"Connection Error: {conn_err}"
        print(f"  {error_message}")
    except requests.exceptions.Timeout as timeout_err:
        error_message = f"Timeout: {timeout_err}"
        print(f"  {error_message}")
    except requests.exceptions.RequestException as req_err:
        error_message = f"Requests Error: {req_err}"
        print(f"  {error_message}")

    end_time = time.time()
    duration = round(end_time - start_time, 2)
    return success, duration, error_message


# --- HTML Report Generation ---

def generate_html_report(report_data):
    """Generates an HTML report from the collected data."""

    now = datetime.now()
    timestamp_str = now.strftime("%Y-%m-%d %H:%M:%S")
    filename = f"etendo_task_report_{now.strftime('%Y%m%d_%H%M%S')}.html"

    html_content = f"""
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Etendo Task Execution Report</title>
            <style>
                body {{ font-family: Arial, sans-serif; margin: 20px; background-color: #f4f4f4; color: #333; }}
                .container {{ background-color: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }}
                h1, h2 {{ color: #333; }}
                table {{ width: 100%; border-collapse: collapse; margin-top: 20px; }}
                th, td {{ border: 1px solid #ddd; padding: 8px; text-align: left; }}
                th {{ background-color: #007bff; color: white; }}
                tr:nth-child(even) {{ background-color: #f2f2f2; }}
                .summary-item {{ margin-bottom: 10px; }}
                .success {{ color: green; }}
                .failure {{ color: red; }}
            </style>
        </head>
        <body>
            <div class="container">
                <h1>Etendo Task Execution Report</h1>
                <p class="summary-item"><strong>Report Date and Time:</strong> {timestamp_str}</p>
    
                <h2>General Summary</h2>
                <p class="summary-item">Total Tasks Found: {report_data['total_tasks_found']}</p>
                <p class="summary-item">Successfully Processed Tasks: <span class="success">{report_data['successful_tasks']}</span></p>
                <p class="summary-item">Failed Tasks: <span class="failure">{report_data['failed_tasks']}</span></p>
                <p class="summary-item">Total Script Execution Time: {report_data['total_script_duration']:.2f} seconds</p>
    
                <h2>Product Count (m_product)</h2>
                <p class="summary-item">Initial Product Count: {report_data['products_before']}</p>
                <p class="summary-item">Final Product Count: {report_data['products_after']}</p>
                <p class="summary-item">Products Created During Process: <strong class="success">{report_data['products_created']}</strong></p>
    
                <h2>Processed Task Details</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Task ID</th>
                            <th>Status</th>
                            <th>Duration (seconds)</th>
                            <th>Error Message</th>
                        </tr>
                    </thead>
                    <tbody>
        """

    for task in report_data['task_details']:
        status_class = "success" if task['status'] == "Success" else "failure"
        error_msg = task['error'] if task['error'] else "N/A"
        html_content += f"""
                        <tr>
                            <td>{task['id']}</td>
                            <td class="{status_class}">{task['status']}</td>
                            <td>{task['duration']:.2f}</td>
                            <td>{error_msg}</td>
                        </tr>
            """

    html_content += """
                    </tbody>
                </table>
            </div>
        </body>
        </html>
        """
    try:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(html_content)
        print(f"\nHTML report generated: {filename}")
    except Exception as e:
        print(f"Error generating HTML report: {e}")


# --- Main Script ---
if __name__ == "__main__":
    script_start_time = time.time()
    print(f"Starting script to execute Etendo tasks... [{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}]")

    db_conn = get_db_connection()
    if not db_conn:
        print("Could not establish database connection. Exiting.")
        exit()

    # 1. Initial product count
    products_before = count_db_records(db_conn, "m_product")
    print(
        f"Number of products (m_product) before the process: {products_before if products_before != -1 else 'Error counting'}")

    # 2. Get task IDs
    task_ids_to_process = get_task_ids_from_db(db_conn)

    task_details_for_report = []
    successful_task_count = 0
    failed_task_count = 0

    if not task_ids_to_process:
        print("No task IDs found for processing.")
    else:
        print(f"{len(task_ids_to_process)} tasks will be processed.")
        for i, task_id in enumerate(task_ids_to_process):
            print(f"\nProcessing task {i + 1}/{len(task_ids_to_process)}:")
            success, duration, error_msg = execute_etendo_task(str(task_id))  # Ensure ID is string

            status_str = "Success" if success else "Failure"
            task_details_for_report.append({
                'id': task_id,
                'status': status_str,
                'duration': duration,
                'error': error_msg
            })
            if success:
                successful_task_count += 1
            else:
                failed_task_count += 1
            print(f"  Result: {status_str}, Duration: {duration:.2f}s")
        print("-" * 40)

    # 3. Final product count
    products_after = count_db_records(db_conn, "m_product")
    print(
        f"Number of products (m_product) after the process: {products_after if products_after != -1 else 'Error counting'}")

    products_created = -1
    if products_before != -1 and products_after != -1:
        products_created = products_after - products_before

    # Close DB connection
    if db_conn:
        db_conn.close()

    script_end_time = time.time()
    total_script_duration = script_end_time - script_start_time

    # 4. Prepare data for the report
    report_data = {
        'total_tasks_found': len(task_ids_to_process),
        'successful_tasks': successful_task_count,
        'failed_tasks': failed_task_count,
        'total_script_duration': total_script_duration,
        'products_before': products_before if products_before != -1 else "Error",
        'products_after': products_after if products_after != -1 else "Error",
        'products_created': products_created if products_created != -1 else "Error",
        'task_details': task_details_for_report
    }

    # 5. Generate HTML report
    generate_html_report(report_data)

    # 6. Final summary in console
    print("\n--- FINAL SUMMARY ---")
    print(f"Tasks found: {len(task_ids_to_process)}")
    print(f"Successfully processed: {successful_task_count}")
    print(f"Failed: {failed_task_count}")
    print(f"Products before: {products_before if products_before != -1 else 'Error'}")
    print(f"Products after: {products_after if products_after != -1 else 'Error'}")
    print(f"Products created: {products_created if products_created != -1 else 'Error'}")
    print(f"Total script time: {total_script_duration:.2f} seconds.")
    print(f"Script finished. [{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}]")
