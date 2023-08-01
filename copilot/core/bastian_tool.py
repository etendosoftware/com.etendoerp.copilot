"""This is a tool which knows a lot about Etendo ERP. It takes a question and returns an answer."""
import json
import os
import re
import xml.dom.minidom
import xml.etree.ElementTree as ET

import openai
import requests
from transformers import (
    GPT2Tokenizer,
    Tool,  # pylint: disable=no-name-in-module
)


# pylint: disable=too-few-public-methods
class BastianFetcher(Tool):
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
        url = os.getenv("BASTIAN_URL", "http://localhost:5005") + "/question"

        payload = json.dumps({"question": question})
        headers = {
            "X-API-KEY": "7f2b9a38-f562-40ea-89ce-86a3191f4ed2",
            "Content-Type": "application/json",
        }
        response = requests.request("POST", url, headers=headers, data=payload, timeout=10000)
        response.raise_for_status()
        return response.json()["answer"]


class XMLTranslatorTool(Tool):
    name = "xml_translator_tool"
    description = "This is a tool that directly translate the content of a XML from one language to another, specified inside the xml"
    inputs = ["question"]
    outputs = ["translated_file_path"]

    def __call__(self, question, *args, **kwargs):
        self.tokenizer = GPT2Tokenizer.from_pretrained("gpt2")
        self.language = "Spanish"
        self.business_requirement = "Human Resources"
        self.prompt = f"""
        ---
        Translate the English text contained within the "original" XML property into {self.language} and place this translation as the value within the XML element itself, leaving the "original" attribute intact. Here is an example for your reference:

        <value column="Name" isTrl="N" original="Current Salary Grade.">Grado de salario actual.</value>

        The objective is to generate an XML output identical to the input, except that the text within the second "original" node should be translated, leaving all other elements and attributes untouched.
        Considerations:

        The XML content that you're translating pertains to a {self.business_requirement} software component. In cases where a word or phrase might have multiple valid translations, choose the translation that best aligns with the {self.business_requirement} context.
        """
        translated_file_path = self.translate_xml_file(question)
        return translated_file_path

    def split_xml_into_segments(self, content, max_tokens):
        root = ET.fromstring(content)
        segments = []
        current_segment = ET.Element(root.tag)

        for child in root:
            if ET.tostring(current_segment).strip().decode() == f"<{root.tag}></{root.tag}>":
                continue

            if len(ET.tostring(current_segment).strip().decode()) + len(ET.tostring(child)) + 2 <= max_tokens:
                current_segment.append(child)
            else:
                segments.append(ET.tostring(current_segment).strip().decode())
                current_segment = ET.Element(root.tag)
                current_segment.append(child)

        if current_segment:
            segments.append(ET.tostring(current_segment).strip().decode())

        return segments

    def translate_xml_file(self, filepath):
        with open(filepath, "r") as file:
            first_line = file.readline().strip()
            content = file.read()
            root = ET.fromstring(content)
            if not root.findall(".//value[@original]"):
                return
            translated_text = ""

            base_language = root.attrib.get("baseLanguage", "en_US")
            language = root.attrib.get("language", "es_ES")
            table = root.attrib.get("table", "")
            version = root.attrib.get("version")

            for child in root:
                segment = ET.tostring(child).decode()
                segment_prompt = f"{self.prompt}\n{segment}"
                messages = [{"role": "system", "content": segment_prompt}]
                response = openai.ChatCompletion.create(
                    model="gpt-4", messages=messages, max_tokens=2000, temperature=0
                )

                translation = response["choices"][0]["message"]["content"].strip()

                translated_element = ET.fromstring(translation)
                values = translated_element.findall("value")
                original_values = child.findall("value")

                for i in range(len(values)):
                    original_text = original_values[i].text
                    if original_text:
                        original_values[i].text = values[i].text
                        is_trl = "Y" if values[i].text else "N"
                        original_values[i].set("isTrl", is_trl)

                translated_text += f"{translation}\n\n"

            translated_text = f'<compiereTrl baseLanguage="{base_language}" language="{language}" table="{table}" version="{version}">\n{translated_text}</compiereTrl>'
            dom = xml.dom.minidom.parseString(translated_text)
            formatted_text = dom.toxml()
            formatted_text = re.sub("\n\\s*\n", "\n", formatted_text)
            formatted_root = ET.fromstring(formatted_text)

            for child in formatted_root:
                for value in child.findall("value"):
                    original = value.get("original")
                    is_trl = "Y" if original and original.strip() else "N"
                    value.set("isTrl", is_trl)
                child.set(
                    "trl", "Y" if any(value.get("isTrl") == "Y" for value in child.findall("value")) else "N"
                )

            with open(filepath, "w", encoding="utf-8") as file:
                file.write(f"{first_line}\n")
                file.write(ET.tostring(formatted_root, encoding="unicode"))

            return filepath
