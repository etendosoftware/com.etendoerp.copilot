"""This is a tool which knows a lot about Etendo ERP. It takes a question and returns an answer."""
import json
import os
from typing import Final

import requests

from .tool_wrapper import ToolWrapper

BASTIAN: Final[str] = os.getenv("BASTIAN_URL", "http://localhost:5005")


class BastianFetcher(ToolWrapper):
    """A tool for fetching answers to questions about Etendo ERP.

    This tool sends a question to a local server running the Etendo ERP
    question-answering system and returns the answer provided by the system.

    Attributes:
        name (str): The name of the tool.
        description (str): A brief description of the tool.
        inputs (List[str]): The names of the input arguments for the tool.
        outputs (List[str]): The names of the output values for the tool.
    """

    name = "bastian_fetcher"
    description = (
        """This is a tool which knows a lot about Etendo ERP. " "It takes a question and returns an answer."""
    )

    inputs = ["question"]
    outputs = ["answer"]

    def __call__(self, question, *args, **kwargs):
        url = f"{BASTIAN}/question"

        payload = json.dumps({"question": question})
        headers = {
            "X-API-KEY": "7f2b9a38-f562-40ea-89ce-86a3191f4ed2",
            "Content-Type": "application/json",
        }
        response = requests.request("POST", url, headers=headers, data=payload, timeout=10000)
        response.raise_for_status()
        return response.json()["answer"]
