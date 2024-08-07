import os
import requests
from dotenv import load_dotenv
from config import db_config
from typing import Dict, Type, Optional
from copilot.core import utils
import json 

load_dotenv()

def _get_headers(access_token: Optional[str]) -> Dict:
    headers = {}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers

def get_access_token():
    
    url = "http://localhost:8080/etendo/sws/login"
    print("URL Lista")
    payload = {
        "username": os.getenv("USERNAME"),
        "password": os.getenv("PASSWORD")
    }
    print(f"payload: {payload}")
    
    response = requests.post(url, json=payload)
    if response.ok:
        token = response.json().get("token")
        if token:
            return token
        else:
            return None
    else:
        print(f"Error getting token {response.text}")
        return None

def exec_sql(query: str, security_check: bool = True):
    access_token = get_access_token()
    if access_token:
        url = utils.read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")
        headers = _get_headers(access_token)
        endpoint = "/webhooks/?name=DBQueryExec"
        body_params = {
            "Query": query,
            "SecurityCheck": security_check
        }
        json_data = json.dumps(body_params)
        post_result = requests.post(url=(url + endpoint), data=json_data, headers=headers)
        if post_result.ok:
            try:
                return json.loads(post_result.text)
            except json.JSONDecodeError:
                return {"error": "Invalid JSON response"}
        else:
            return {"error": post_result.text}
    else:
        return {"error": "Error getting token"}

name_model = os.getenv("NAME_MODEL")

sql_query = f"""
SELECT 
prompt AS prompt,
retrieval AS retrieval,
ad_column_identifier_std('etcop_openai_model', etcop_openai_model_id) as model,
name AS name,
code_interpreter AS code_interpreter,
openai_id_assistant AS assistant_id,
description AS description
FROM etcop_app
WHERE name= '{name_model}'
;
"""

results = exec_sql(sql_query, security_check=False)

if 'error' not in results:
    raw_data = results.get('data', '[]')
    raw_columns = results.get('columns', '[]')
    
    data = json.loads(raw_data)
    columns = json.loads(raw_columns)

    if data and columns:
        first_result = dict(zip(columns, data[0]))
        prompt = first_result.get('prompt')
        retrieval = first_result.get('retrieval')
        model = first_result.get('model')
        name = first_result.get('name')
        code_interpreter = first_result.get('code_interpreter')
        openai_id_assistant = first_result.get('assistant_id')
        description = first_result.get('description')
        
        print("Prompt:", prompt)
        print("Retrieval:", retrieval)
        print("Model:", model)
        print("Name:", name)
        print("Code Interpreter:", code_interpreter)
        print("OpenAI ID Assistant:", openai_id_assistant)
        print("Description:", description)
    else:
        print("No data found")
else:
    print(f"Error: {results['error']}")

print(results)
