import os
import re
import xml.dom.minidom
import xml.etree.ElementTree as ET
import copy
import pycountry
import openai
from transformers import GPT2Tokenizer
from copilot.core.tool_wrapper import ToolWrapper


class TranslatorTool(ToolWrapper):
    name = "translator_tool"
    description = "This is a tool that receives a relative path and directly translates the content of XML from one language to another, specified within the xml"
    inputs = ["question"]
    outputs = ["translated_files_paths"]

    def __call__(self, relative_path,*args, **kwargs):
        self.tokenizer = GPT2Tokenizer.from_pretrained("gpt2")
        self.language = "Spanish"
        self.business_requirement = "Human Resources"

        translated_files_paths = []
        script_directory = os.path.dirname(os.path.abspath(__file__)) 
        first_level_up = os.path.dirname(script_directory)  # Esto te lleva una carpeta hacia arriba (a "core")
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
            language_attr = root.attrib.get('language', 'es_ES')
            target_language = self.get_language_name(language_attr)
            if not target_language:
                target_language = "English"

            self.prompt = f"""
            ---
            Translate the English text contained within the "original" XML property into {target_language} and place this translation as the value within the XML element itself, leaving the "original" attribute intact. Here is an example for your reference:

            <value column="Name" isTrl="N" original="Current Salary Grade.">Grado de salario actual.</value>

            The objective is to generate an XML output identical to the input, except that the text within the second "original" node should be translated, leaving all other elements and attributes untouched.
            Considerations:

            The XML content that you're translating pertains to a {self.business_requirement} software component. In cases where a word or phrase might have multiple valid translations, choose the translation that best aligns with the {self.business_requirement} context.
            """

            if not root.findall(".//value[@original]"):
                return
            translated_text = ""

            base_language = root.attrib.get("baseLanguage", "en_US")
            language = root.attrib.get("language", "es_ES")
            table = root.attrib.get("table", "")
            version = root.attrib.get("version")
            
            for child in root:
                new_child = copy.deepcopy(child) 

                values = child.findall('value')
                for value in values:
                    if value.get('isTrl', 'N') == 'Y':
                        continue

                    value_prompt = f"{self.prompt}\n{ET.tostring(value).decode()}"
                    messages = [{"role": "system", "content": value_prompt}]
                    response = openai.ChatCompletion.create(
                        model="gpt-4", messages=messages, max_tokens=2000, temperature=0
                    )

                    translation = response["choices"][0]["message"]["content"].strip()
                    translated_value = ET.fromstring(translation)
                    
                    corresponding_value_in_new_child = new_child.find(f"value[@column='{value.get('column')}']")
                    if corresponding_value_in_new_child is not None:
                        corresponding_value_in_new_child.text = translated_value.text
                        corresponding_value_in_new_child.set("isTrl", "Y" if translated_value.text else "N")


                translated_text += f"{ET.tostring(new_child).decode()}\n\n"

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

            with open(filepath, "w", encoding='utf-8') as file:
                file.write(f'{first_line}\n')
                file.write(ET.tostring(formatted_root, encoding='unicode'))
            
        return f"Successfully translated file {filepath}."
 

