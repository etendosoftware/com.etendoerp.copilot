"""This module contains the RetrievalTool class.

This is a tool which look up for documents extracts from a vector
database, requires another tool which elaborates an answer.
"""

import json

import requests

from .tool_wrapper import ToolWrapper


class RetrievalTool(ToolWrapper):
    """A tool for fetching answers to questions about Etendo ERP.

    This tool sends a question to a local server running the Etendo ERP
    question-answering system and returns the answer provided by the system.

    Attributes:
        name (str): The name of the tool.
        description (str): A brief description of the tool.
        inputs (List[str]): The names of the input arguments for the tool.
    """

    name = "retrieval_tool"
    description = (
        "This is a tool which look up for documents extracts from "
        "a vector database, requires another tool which elaborates an answer."
    )

    inputs = ["question"]
    outputs = ["answer"]

    def __call__(self, question, *args, **kwargs):
        url = "http://localhost:8085/query"

        payload = json.dumps({"queries": [{"query": question, "top_k": 5}]})
        headers = {
            "Authorization": "Bearer rzf9wcg53cy3r815pw29",
            "Content-Type": "application/json",
        }

        response = requests.request("POST", url, headers=headers, data=payload, timeout=10000)
        response.raise_for_status()
        response_data = response.json()
        doc_id = response_data["results"][0]["results"][0]["id"].split("_")[0]

        url = f"http://localhost:8092/ETDOC_Document/{doc_id}"

        response = requests.request("GET", url, headers=headers, data=payload, timeout=10000)

        content = response.json()["content"]
        return content
