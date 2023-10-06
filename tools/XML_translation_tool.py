import os
import re
import xml.dom.minidom
import xml.etree.ElementTree as ET
import pycountry
import openai
from transformers import GPT2Tokenizer
import time

from copilot.core.tool_wrapper import ToolWrapper


class XML_translation_tool(ToolWrapper):
    name = "XML_translation_tool"
    description = "This is a tool that receives a relative path and directly translates the content of XML from one language to another, specified within the xml"
    inputs = ["question"]
    outputs = ["translated_files_paths"]

    def __call__(self, relative_path,*args, **kwargs):
        self.tokenizer = GPT2Tokenizer.from_pretrained("gpt2")
        self.language = "Spanish"
        self.business_requirement = "Human Resources"

        translated_files_paths = []
        script_directory = os.path.dirname(os.path.abspath(__file__)) 
        first_level_up = os.path.dirname(script_directory)
        second_level_up = os.path.dirname(first_level_up)  
        parent_directory = os.path.dirname(second_level_up)
        absolute_path = os.path.join(parent_directory, relative_path)
        reference_data_path = absolute_path
   
        if not os.path.exists(reference_data_path):
            raise ValueError(f"The 'referencedata' directory was not found at {reference_data_path}.")

        for dirpath, dirnames, filenames in os.walk(reference_data_path):
            xml_files = [f for f in filenames if f.endswith(".xml")]
            for xml_file in xml_files:
                filepath = os.path.join(dirpath, xml_file)
                translated_file_path = self.translate_xml_file(filepath)
                if translated_file_path:
                    translated_files_paths.append(translated_file_path)

        return translated_files_paths
    
    def get_language_name(self, iso_code):
        language_part = iso_code.split('_')[0]
        language = pycountry.languages.get(alpha_2=language_part)
        if language:
            return language.name
        return None

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
                
            for child in root:
                needs_translation = any(value.get('isTrl', 'N') == 'N' and value.get('original').strip() 
                                        for value in child.findall('value'))

                if not needs_translation:
                    continue

                for value in child.findall('value'):
                    if value.get('isTrl') == 'N' and value.get('original').strip():
                        original_text = value.get('original').strip()
                        segment_prompt = f"""
                        Translate the following English text into {self.language}:
                        {original_text}

                        The XML content that you're translating pertains to a {self.business_requirement} software component. In cases where a word or phrase might have multiple valid translations, choose the translation that best aligns with the {self.business_requirement} context.
                        """
                        messages = [{"role": "system", "content": segment_prompt}]
                        while True:
                            try:
                                response = openai.ChatCompletion.create(
                                    model="gpt-4", messages=messages, max_tokens=2000, temperature=0
                                )
                                break 
                            except openai.error.RateLimitError:
                                print("Rate limit reached. Retrying in 60 seconds.")
                                time.sleep(60)  

                        translation = response["choices"][0]["message"]["content"].strip()
                        if translation:
                            value.text = translation
                            value.set('isTrl', 'Y')
                            child.set('trl', 'Y')

            with open(filepath, "w", encoding='utf-8') as file:
                file.write(f'{first_line}\n')
                file.write(ET.tostring(root, encoding='unicode'))
                
        return f"Successfully translated file {filepath}."