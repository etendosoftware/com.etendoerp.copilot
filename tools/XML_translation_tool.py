import os
import re
import xml.dom.minidom
import xml.etree.ElementTree as ET
import copy
import pycountry
import openai
from transformers import GPT2Tokenizer
from copilot.core.tool_wrapper import ToolWrapper
import time
from dotenv import load_dotenv

load_dotenv()

class XML_translation_tool(ToolWrapper):
    name = "XML_translation_tool"
    description = "This is a tool that receives a relative path and directly translates the content of XML from one language to another, specified within the xml"
    inputs = ["question"]
    outputs = ["translated_files_paths"]

    def __call__(self, relative_path,*args, **kwargs):
        self.tokenizer = GPT2Tokenizer.from_pretrained("gpt2")
        self.language = "Spanish"
        self.business_requirement = os.getenv("BUSINESS_TOPIC", "ERP")
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
        model = os.getenv("OPENAI_MODEL", "gpt-3.5-turbo")
        with open(filepath, "r") as file:
            first_line = file.readline().strip()
            content = file.read()
            root = ET.fromstring(content)

            language_attr = root.attrib.get('language', 'es_ES')
            target_language = self.get_language_name(language_attr)
            if not target_language:
                target_language = "English"
            value_elements = []
            for child in root:
                is_trl = 'N'
                values = child.findall('value')
                for value in values:
                    if value.get('isTrl', 'N') == 'Y':
                        continue
                    original_text = value.get('original', '').strip()
                    if not original_text:
                        continue
                    value_elements.append(value)
                    is_trl = 'Y'
                child.attrib['trl'] = is_trl

            if not value_elements:
                return None

            original_texts = [value.text for value in value_elements if value.text and value.text.strip()]
            prompt_texts = "\n".join(original_texts)
            self.prompt = f"""
            ---
            Translate the following texts from English to {target_language} in the context of {self.business_requirement} software:
            {prompt_texts}
            """

            messages = [{"role": "system", "content": self.prompt}]
            response = openai.ChatCompletion.create(
                model=model,
                messages=messages,
                max_tokens=2000,
                temperature=0
            )

            translations = response["choices"][0]["message"]["content"].strip().split('\n')
            
            for i, value in enumerate(value_elements):
                if i < len(translations):  
                    value.text = translations[i].strip()
                    value.set('isTrl', 'Y')
                
            translated_text = ET.tostring(root).decode()

            with open(filepath, "w", encoding='utf-8') as file:
                file.write(f'{first_line}\n')
                file.write(translated_text)

            return f"Successfully translated file {filepath}."
