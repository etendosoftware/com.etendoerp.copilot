import psycopg2
import psycopg2.extras
from collections import defaultdict
from openai import OpenAI
import os 
from dotenv import load_dotenv

load_dotenv()

# Database configuration
db_config = {
    "database": "etendo_2",
    "user": "tad",
    "password": "tad",
    "host": "localhost",
    "port": "5432"
}

# Connect to the database
conn = psycopg2.connect(**db_config)
# Use DictCursor
cur = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)

name_model = os.getenv("NAME_MODEL") 

# Query to get tables with descriptions
sql_query = """
SELECT 
prompt AS prompt,
retrieval AS retrieval,
ad_column_identifier_std('etcop_openai_model', etcop_openai_model_id) as model,
name AS name,
code_interpreter AS code_interpreter,
openai_id_assistant AS assistant_id,
description AS description
FROM etcop_app
WHERE name= %s
;
"""

cur.execute(sql_query, (name_model,))

results = cur.fetchone()  # use fetchone to get only the first row

# Check if results exist
if results:
    prompt = results['prompt']
    retrieval = results['retrieval']
    model = results['model']  # Storing the model name in a variable called 'model'
    name = results['name'] 
    code_interpreter = results['code_interpreter']
    openai_id_assistant = results['assistant_id']
    description = results['description']
else:
    print("No data found")

print(results)


cur.close()
conn.close()
