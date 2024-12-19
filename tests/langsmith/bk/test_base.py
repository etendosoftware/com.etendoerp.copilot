# my_app/main.py
from langsmith import traceable

@traceable # Optional
def generate_sql(user_query):
    # Replace with your SQL generation logic
    # e.g., my_llm(my_prompt.format(user_query))
    return "SELECT * FROM customers"
