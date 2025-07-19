import argparse
import json  # For saving stats in a structured way
import os
import re
import traceback
from typing import Any, Dict, List

import numpy as np  # For numerical operations in selection
import pandas as pd

# Langchain imports - Updated for newer versions
try:
    from langchain_openai import ChatOpenAI
except ImportError:
    print("CRITICAL ERROR: langchain-openai not found. Please install it: pip install langchain-openai")
    print(
        "Attempting to use deprecated ChatOpenAI from langchain_community.chat_models as a fallback (might not work long-term)."
    )
    try:
        from langchain_community.chat_models import (
            ChatOpenAI,  # Fallback for older setups
        )
    except ImportError:
        print("CRITICAL ERROR: No ChatOpenAI found. Please install langchain-openai.")
        exit(1)  # Exit if no ChatOpenAI can be imported

from langchain.prompts import ChatPromptTemplate, HumanMessagePromptTemplate
from langchain_core.messages import SystemMessage
from langchain_core.output_parsers import (
    StrOutputParser,  # To get string output directly
)

# NLTK for lexical diversity (optional, install if using)
try:
    from nltk.tokenize import word_tokenize
    from nltk.util import ngrams

    NLTK_AVAILABLE = True
except ImportError:
    NLTK_AVAILABLE = False
    print("Warning: NLTK not found. Lexical diversity metrics (distinct n-grams) will be skipped.")
    print("To enable them, run: pip install nltk")

# Levenshtein for edit distance (optional, install if using)
try:
    import Levenshtein

    LEVENSHTEIN_AVAILABLE = True
except ImportError:
    LEVENSHTEIN_AVAILABLE = False
    print("Warning: python-Levenshtein not found. Levenshtein distance metric will be skipped.")
    print("To enable it, run: pip install python-Levenshtein")

# SentenceTransformers for semantic diversity (optional, install if using)
try:
    from sentence_transformers import SentenceTransformer
    from sklearn.metrics.pairwise import cosine_distances

    SENTENCE_TRANSFORMERS_AVAILABLE = True
    SBERT_MODEL_NAME = "paraphrase-multilingual-MiniLM-L12-v2"
    SBERT_MODEL = None
except ImportError:
    SENTENCE_TRANSFORMERS_AVAILABLE = False
    SBERT_MODEL = None
    print(
        "Warning: sentence-transformers or scikit-learn not found. Semantic diversity metrics will be skipped."
    )
    print("To enable them, run: pip install sentence-transformers scikit-learn")

# --- Configuration ---
DEFAULT_LLM_MODEL = "gpt-4.1-mini"
DEFAULT_LLM_TEMPERATURE = 0.75
DEFAULT_NUM_TEMPLATES_FINAL = 20  # Default number of DIVERSE templates to keep
DEFAULT_OUTPUT_FILE = "instantiated_texts.txt"
DIVERSIFICATION_MULTIPLIER = 3  # Generate N * multiplier initial templates


# --- Diversity Measurement Functions ---
def load_sbert_model():
    global SBERT_MODEL
    if SENTENCE_TRANSFORMERS_AVAILABLE and SBERT_MODEL is None:
        try:
            print(
                f"Loading SentenceTransformer model '{SBERT_MODEL_NAME}'... (This may take a moment on first run)"
            )
            SBERT_MODEL = SentenceTransformer(SBERT_MODEL_NAME)
            print(f"SentenceTransformer model '{SBERT_MODEL_NAME}' loaded successfully.")
        except Exception as e:
            print(f"Error loading SentenceTransformer model '{SBERT_MODEL_NAME}': {e}")
            SBERT_MODEL = None
    return SBERT_MODEL


def calculate_distinct_ngrams(list_of_texts: List[str], n: int = 1) -> Dict[str, Any]:
    if not NLTK_AVAILABLE or not list_of_texts:
        return {
            "distinct_count": 0,
            "total_count": 0,
            "ratio": 0.0,
            "status": "Skipped (NLTK not available or no texts)",
        }

    all_ngrams_list = []
    try:
        for text in list_of_texts:
            tokens = word_tokenize(text.lower())
            if len(tokens) >= n:
                current_ngrams = list(ngrams(tokens, n))
                all_ngrams_list.extend(current_ngrams)
    except Exception as e:
        if "punkt" in str(e).lower():
            print("\nNLTK 'punkt' tokenizer resource not found. Please download it by running:")
            print("import nltk; nltk.download('punkt')")
        return {"distinct_count": 0, "total_count": 0, "ratio": 0.0, "status": f"NLTK error: {e}"}

    if not all_ngrams_list:
        return {"distinct_count": 0, "total_count": 0, "ratio": 0.0, "status": "Success (no n-grams found)"}

    total_ngrams_count = len(all_ngrams_list)
    distinct_ngrams_count = len(set(all_ngrams_list))
    ratio = distinct_ngrams_count / total_ngrams_count if total_ngrams_count > 0 else 0.0
    return {
        "distinct_count": distinct_ngrams_count,
        "total_count": total_ngrams_count,
        "ratio": ratio,
        "status": "Success",
    }


def average_levenshtein_distance_pairwise(list_of_texts: List[str]) -> Dict[str, Any]:
    if not LEVENSHTEIN_AVAILABLE:
        return {"average_normalized_distance": 0.0, "status": "Skipped (python-Levenshtein not available)"}
    if len(list_of_texts) < 2:
        return {"average_normalized_distance": 0.0, "status": "Skipped (need at least 2 texts)"}

    distances = []
    for i in range(len(list_of_texts)):
        for j in range(i + 1, len(list_of_texts)):
            distance = Levenshtein.distance(list_of_texts[i], list_of_texts[j])
            normalized_distance = distance / max(len(list_of_texts[i]), len(list_of_texts[j]), 1)
            distances.append(normalized_distance)

    avg_dist = sum(distances) / len(distances) if distances else 0.0
    return {"average_normalized_distance": avg_dist, "status": "Success"}


def get_embeddings(list_of_texts: List[str]) -> Any:
    sbert_model = load_sbert_model()
    if sbert_model is None or not list_of_texts:
        return None
    try:
        return sbert_model.encode(list_of_texts, show_progress_bar=False)
    except Exception as e:
        print(f"Error generating embeddings: {e}")
        return None


def average_cosine_distance_pairwise_semantic(list_of_texts: List[str]) -> Dict[str, Any]:
    sbert_model = load_sbert_model()
    if sbert_model is None:
        return {"average_cosine_distance": 0.0, "status": "Skipped (SentenceTransformer model not loaded)"}
    if len(list_of_texts) < 2:
        return {"average_cosine_distance": 0.0, "status": "Skipped (need at least 2 texts)"}

    embeddings = get_embeddings(list_of_texts)
    if embeddings is None or (isinstance(embeddings, list) and not embeddings) or embeddings.shape[0] < 2:
        return {
            "average_cosine_distance": 0.0,
            "status": "Skipped (could not generate enough valid embeddings)",
        }

    try:
        distances_matrix = cosine_distances(embeddings)
        pairwise_distances = []
        for i in range(len(embeddings)):
            for j in range(i + 1, len(embeddings)):
                pairwise_distances.append(distances_matrix[i, j])
        avg_dist = sum(pairwise_distances) / len(pairwise_distances) if pairwise_distances else 0.0
        return {"average_cosine_distance": avg_dist, "status": "Success"}
    except Exception as e:
        print(f"Error calculating cosine distances: {e}")
        return {"average_cosine_distance": 0.0, "status": f"Error: {e}"}


# --- ADDED HELPER FUNCTIONS ---

def _report_lexical_diversity(templates: List[str], metrics_summary: Dict[str, Any]):
    """Calculates and reports lexical diversity (NLTK-based N-grams)."""
    metrics_summary["lexical_diversity_nltk"] = {}
    if NLTK_AVAILABLE:
        print("\nLexical Diversity (NLTK):")
        for n_val in [1, 2]:
            ngram_key = f"distinct_{n_val}_grams"
            metrics_ngram = calculate_distinct_ngrams(templates, n=n_val)
            metrics_summary["lexical_diversity_nltk"][ngram_key] = metrics_ngram
            if metrics_ngram.get("status", "").startswith("Error") or metrics_ngram.get("status", "").startswith(
                    "Skipped"):
                print(f"  Distinct-{n_val} grams: {metrics_ngram['status']}")
            else:
                print(
                    f"  Distinct-{n_val} grams: {metrics_ngram['distinct_count']} unique of {metrics_ngram['total_count']} total (Ratio: {metrics_ngram['ratio']:.3f})"
                )
    else:
        print("\nLexical Diversity (NLTK): Skipped (NLTK not available).")
        metrics_summary["lexical_diversity_nltk"]["status"] = "Skipped (NLTK not available)"


def _report_levenshtein_diversity(templates: List[str], metrics_summary: Dict[str, Any]):
    """Calculates and reports average normalized Levenshtein distance."""
    metrics_summary["levenshtein_distance"] = {}
    if LEVENSHTEIN_AVAILABLE:
        lev_metrics = average_levenshtein_distance_pairwise(templates)
        metrics_summary["levenshtein_distance"] = lev_metrics
        print(
            f"\nAverage Normalized Levenshtein Distance (Pairwise): {lev_metrics.get('average_normalized_distance', 0.0):.3f} ({lev_metrics.get('status', '')})"
        )
    else:
        print("\nAverage Normalized Levenshtein Distance: Skipped (python-Levenshtein not available).")
        metrics_summary["levenshtein_distance"]["status"] = "Skipped (python-Levenshtein not available)"


def _report_semantic_diversity(templates: List[str], metrics_summary: Dict[str, Any]):
    """Calculates and reports semantic diversity (Sentence Transformers-based cosine distance)."""
    metrics_summary["semantic_diversity_sbert"] = {}
    if SENTENCE_TRANSFORMERS_AVAILABLE:
        print(f"\nSemantic Diversity (Sentence Transformers - Model: {SBERT_MODEL_NAME}):")
        if load_sbert_model() is not None:
            cosine_metrics = average_cosine_distance_pairwise_semantic(templates)
            metrics_summary["semantic_diversity_sbert"] = cosine_metrics
            print(
                f"  Average Cosine Distance (Pairwise Semantic): {cosine_metrics.get('average_cosine_distance', 0.0):.3f} (Higher is more diverse) ({cosine_metrics.get('status', '')})"
            )
        else:
            print("  Skipped (SentenceTransformer model could not be loaded).")
            metrics_summary["semantic_diversity_sbert"]["status"] = "Skipped (SBERT model not loaded)"
    else:
        print("\nSemantic Diversity: Skipped (sentence-transformers or scikit-learn not available).")
        metrics_summary["semantic_diversity_sbert"]["status"] = "Skipped (sentence-transformers not available)"


# --- REFACTORED report_and_collect_diversity_metrics FUNCTION ---

def report_and_collect_diversity_metrics(templates: List[str]) -> Dict[str, Any]:
    """
    Calculates and reports various diversity metrics for a list of templates.

    Args:
        templates (List[str]): A list of template strings to analyze.

    Returns:
        Dict[str, Any]: A dictionary summarizing the calculated metrics and their status.
    """
    metrics_summary = {"num_templates_analyzed": len(templates)}

    print("\n--- Diversity Metrics for Generated Templates ---")

    # Initial check for minimum templates required for pairwise metrics
    if not templates or len(templates) < 2:
        print("Not enough templates (need at least 2) to calculate pairwise diversity.")
        metrics_summary["status"] = "Not enough templates for pairwise diversity."
        return metrics_summary

    # Calculate and report lexical diversity (NLTK)
    _report_lexical_diversity(templates, metrics_summary)

    # Calculate and report Levenshtein distance
    _report_levenshtein_diversity(templates, metrics_summary)

    # Calculate and report semantic diversity (SBERT)
    _report_semantic_diversity(templates, metrics_summary)

    print("---------------------------------------------")
    return metrics_summary


def save_diversity_stats(stats_data: Dict[str, Any], output_stat_file: str):
    try:
        with open(output_stat_file, "w", encoding="utf-8") as f_stat:
            json.dump(stats_data, f_stat, indent=4)
        print(f"\nDiversity statistics saved to: {output_stat_file}")
    except Exception as e:
        print(f"CRITICAL ERROR: Could not save diversity statistics to '{output_stat_file}': {e}")
        traceback.print_exc()


# --- ADDED HELPER FUNCTIONS ---

def _handle_initial_template_checks(
        all_templates: List[str], num_to_select: int
) -> List[str] | None:
    """
    Handles initial checks for template validity and quantity,
    returning early if conditions are met. Returns None if processing should continue,
    otherwise returns a list of templates for early exit.
    """
    if not all_templates:
        print("Warning: No templates provided for diverse selection.")
        return []
    if len(all_templates) <= num_to_select:
        print(
            f"Number of generated templates ({len(all_templates)}) is less than or equal to requested ({num_to_select}). Returning all generated templates."
        )
        return all_templates

    sbert_model = load_sbert_model()
    if not sbert_model or not SENTENCE_TRANSFORMERS_AVAILABLE:
        print(
            "Warning: Semantic diversity model not available. Cannot perform diverse selection. Returning the first N templates."
        )
        return all_templates[:num_to_select]

    return None  # Indicates that processing should continue


def _perform_greedy_selection(
        embeddings: np.ndarray, num_to_select: int, all_templates: List[str]
) -> List[str]:
    """
    Performs the greedy selection of diverse templates based on cosine distance.
    """
    num_candidates = embeddings.shape[0]
    selected_indices = []

    # Start by selecting the first template (common greedy strategy start)
    selected_indices.append(0)

    try:
        distance_matrix = cosine_distances(embeddings)
    except Exception as e:
        print(f"Error computing distance matrix for diverse selection: {e}. Returning first N templates.")
        return all_templates[:num_to_select]

    while len(selected_indices) < num_to_select:
        best_next_idx = -1
        max_min_dist_to_selected_set = -1

        for i in range(num_candidates):
            if i in selected_indices:
                continue

            # Calculate the minimum distance from candidate 'i' to all already selected templates
            current_min_dist = np.min(distance_matrix[i, selected_indices])

            if current_min_dist > max_min_dist_to_selected_set:
                max_min_dist_to_selected_set = current_min_dist
                best_next_idx = i

        if best_next_idx != -1:
            selected_indices.append(best_next_idx)
        else:
            # Fallback for when no more distinct templates can be found (e.g., all remaining are identical)
            print("Warning: Could not find more distinct templates to select. Returning current selection.")
            break

    return [all_templates[i] for i in selected_indices]


# --- REFACTORED select_most_diverse_templates FUNCTION ---

def select_most_diverse_templates(all_templates: List[str], num_to_select: int) -> List[str]:
    """
    Selects a subset of templates that are most semantically diverse using a greedy approach.

    Args:
        all_templates (List[str]): A list of all candidate template strings.
        num_to_select (int): The desired number of diverse templates to select.

    Returns:
        List[str]: A list of the selected diverse template strings.
    """
    # Handle initial checks and early exit conditions
    early_exit_templates = _handle_initial_template_checks(all_templates, num_to_select)
    if early_exit_templates is not None:
        return early_exit_templates

    print(f"\n--- Selecting {num_to_select} most diverse templates from {len(all_templates)} candidates ---")

    # Generate embeddings
    embeddings = get_embeddings(all_templates)
    if embeddings is None or len(embeddings) < num_to_select:
        print(
            "Warning: Could not generate enough embeddings for diverse selection. Returning the first N templates."
        )
        return all_templates[:num_to_select]

    # Perform the greedy selection
    selected_templates = _perform_greedy_selection(embeddings, num_to_select, all_templates)

    print(f"Selected {len(selected_templates)} diverse templates.")
    return selected_templates


# --- ADDED HELPER FUNCTIONS ---

def _initialize_llm(model_name: str, temperature: float) -> ChatOpenAI | None:
    """Initializes the LLM model, handling API key and initialization errors."""
    if not os.getenv("OPENAI_API_KEY"):
        print("CRITICAL ERROR: The OPENAI_API_KEY environment variable is not set.")
        return None
    try:
        llm = ChatOpenAI(model_name=model_name, temperature=temperature)
        return llm
    except Exception as e:
        print(f"CRITICAL ERROR: Failed to initialize LLM '{model_name}': {e}")
        traceback.print_exc()
        return None


def _construct_llm_prompt(
        task_description: str,
        num_initial_templates: int
) -> ChatPromptTemplate:
    """Constructs the ChatPromptTemplate for LLM interaction."""

    system_message_content = (
        "You are a highly creative and expert AI assistant specializing in generating flexible and diverse text templates. "
        "Your templates are designed to be easily populated with tabular data (like from a CSV) "
        "to form complete and coherent text instances for various tasks."
    )

    human_template_str = (
        f"I need your help to generate {num_initial_templates} distinct text templates. "
        f"These templates will be used for the following task: '{task_description}'.\n\n"
        "The templates should be able to incorporate data fields. The available data fields, "
        f"which you must use as placeholders in the double curly brace format (e.g., {{{{FieldName}}}}), are:\n"
        f"{list_placeholders_str}\n\n"
        "**Fundamental Guidelines for Template Generation:**\n"
        "1.  **Placeholder Format:** STRICTLY use the double curly brace format for placeholders (e.g., {{{{ProductName}}}}). DO NOT use single braces.\n"
        "2.  **Allowed Placeholders:** Only use the placeholder names provided in the list above. DO NOT invent new placeholders. If no specific placeholders were provided, create templates that are general for the task and might not need data merging.\n"
        "3.  **Creative Diversity:** This is key! Generate templates that are very diverse from each other. Vary:\n"
        "    * The overall sentence structure and information order.\n"
        "    * The tone (e.g., formal, direct, technical, slightly informal if appropriate for the task).\n"
        "    * The placeholders used (if provided): not all templates must use all placeholders. Some can be concise, others more detailed.\n"
        "    * The language can be English or Spanish, randomly.\n"
        "    * Introductory or concluding phrases.\n"
        f"4.  **Clear Intent:** All templates must clearly reflect the intent of the task: '{task_description}'. If it implies an action (e.g., 'create', 'update', 'query'), the template should be a clear instruction or statement for it.\n"
        "5.  **One Template Per Line:** Return each generated template on a new line. Do not include numbering, bullet points, or any other introductory/concluding text in your response—only the templates themselves.\n\n"
        "Please generate {num_initial_templates} templates following these guidelines.\n\n"
        "Generated Templates:"
    )

    return ChatPromptTemplate.from_messages(
        [
            SystemMessage(content=system_message_content),
            HumanMessagePromptTemplate.from_template(human_template_str),
        ]
    )


def _parse_and_validate_templates(
        raw_templates: List[str], expected_placeholder_names: List[str]
) -> List[str]:
    """
    Parses raw LLM response into a list of templates and validates
    placeholders against a predefined list.
    """
    validated_templates = []
    placeholder_pattern = re.compile(r"\{\{([\w\s.-]+?)\}\}")

    for i, template_str in enumerate(raw_templates):
        found_placeholders = set(placeholder_pattern.findall(template_str))
        is_valid = True

        if not found_placeholders and expected_placeholder_names:
            print(
                f"Warning: Template {i + 1} does not seem to use any placeholders, though some were expected: '{template_str[:100]}...'"
            )

        for ph_found in found_placeholders:
            if ph_found not in expected_placeholder_names:
                print(
                    f"Warning: Template {i + 1} uses a DISALLOWED placeholder ('{ph_found}'). Placeholder should be one of {expected_placeholder_names}. Discarding template: '{template_str[:100]}...'"
                )
                is_valid = False
                break
        if is_valid:
            validated_templates.append(template_str)
    return validated_templates


# --- REFACTORED generate_templates_via_llm FUNCTION ---

def generate_templates_via_llm(
        task_description: str,
        placeholder_names: List[str],
        num_templates_to_generate_final: int,
        diversification_multiplier: int,
        model_name: str = DEFAULT_LLM_MODEL,
        temperature: float = DEFAULT_LLM_TEMPERATURE,
) -> Tuple[List[str], Dict[str, Any]]:
    """
    Generates diverse text templates using an LLM, validates them,
    selects the most diverse, and reports diversity metrics.

    Args:
        task_description (str): Description of the task for which templates are generated.
        placeholder_names (List[str]): List of allowed placeholder names.
        num_templates_to_generate_final (int): The target number of final diverse templates.
        diversification_multiplier (int): Multiplier for initial request to the LLM
                                          to allow for diversity selection.
        model_name (str): The name of the LLM model to use (e.g., "gpt-3.5-turbo").
        temperature (float): The sampling temperature for the LLM.

    Returns:
        Tuple[List[str], Dict[str, Any]]: A tuple containing:
            - A list of the final selected diverse template strings.
            - A dictionary summarizing diversity metrics.
    """
    llm = _initialize_llm(model_name, temperature)
    if llm is None:
        return [], {"status": "LLM initialization failed."}

    num_initial_templates_to_request = num_templates_to_generate_final * diversification_multiplier
    print(
        f"Requesting {num_initial_templates_to_request} initial templates from AI (target final: {num_templates_to_generate_final})."
    )

    # 1. Construct prompt
    prompt_chat = _construct_llm_prompt(
        task_description, num_initial_templates_to_request
    )

    chain = prompt_chat | llm | StrOutputParser()

    try:
        print(
            f"\n--- Requesting {num_initial_templates_to_request} initial templates from AI for task: '{task_description}' ---"
        )
        if placeholder_names:
            print(f"Available placeholders for AI: {placeholder_names}")
        else:
            print("No specific CSV placeholders provided to AI; expecting generic templates for the task.")

        invoke_input = {
            "num_initial_templates": str(num_initial_templates_to_request),
            "task_description": task_description,
            "list_placeholders_str": ", ".join([f"{{{{{name}}}}}" for name in
                                                placeholder_names]) if placeholder_names else "(No specific placeholders provided, generate generic templates for the task)",
        }
        llm_response_content = chain.invoke(invoke_input)

        raw_generated_templates = [t.strip() for t in llm_response_content.split("\n") if t.strip()]
        print(f"\n--- {len(raw_generated_templates)} Raw Templates received from AI ---")

        # 2. Parse and validate templates
        validated_templates = _parse_and_validate_templates(
            raw_generated_templates, placeholder_names
        )
        print(f"\n--- {len(validated_templates)} Initial Validated and Processed Templates: ---")

        # 3. Select the most diverse N templates
        final_selected_templates = select_most_diverse_templates(
            validated_templates, num_templates_to_generate_final
        )
        print(f"\n--- {len(final_selected_templates)} Final Selected Diverse Templates: ---")
        for t_idx, t_text in enumerate(final_selected_templates):
            print(f"Selected Template {t_idx + 1}: {t_text}")

        # 4. Report diversity metrics
        template_diversity_stats = {}
        if final_selected_templates:
            template_diversity_stats = report_and_collect_diversity_metrics(final_selected_templates)
        else:
            print("No final templates selected to report diversity on.")
            template_diversity_stats = {"status": "No final templates selected."}
        print("-----------------------------------\n")

        return final_selected_templates, template_diversity_stats

    except Exception as e:
        print(f"CRITICAL ERROR during LLM chain execution for template generation: {e}")
        traceback.print_exc()
        return [], {"status": f"LLM chain execution error: {e}"}


# --- ADDED HELPER FUNCTIONS ---

def _load_csv_data_safe(csv_path: str) -> pd.DataFrame | None:
    """
    Safely loads a CSV file into a pandas DataFrame, handling FileNotFoundError
    and other exceptions. Fills NaN values with empty strings.
    """
    try:
        df_data = pd.read_csv(csv_path, dtype=str)
        df_data = df_data.fillna("")
        print(f"\nSuccessfully loaded {len(df_data)} records from '{csv_path}'.")
        return df_data
    except FileNotFoundError:
        print(f"CRITICAL ERROR: CSV file '{csv_path}' not found.")
        return None
    except Exception as e:
        print(f"CRITICAL ERROR loading CSV '{csv_path}': {e}")
        traceback.print_exc()
        return None


def _prepare_row_data_for_template(
        data_row: pd.Series, placeholder_column_names: List[str], warned_missing_cols: set, csv_path: str
) -> Dict[str, Any]:
    """
    Prepares a dictionary of data for template instantiation from a single CSV row,
    handling missing columns by using empty strings and printing warnings.
    """
    data_for_this_row: Dict[str, Any] = {}
    for csv_col_name in placeholder_column_names:
        if csv_col_name not in data_row.index:  # Use .index for Series columns
            if csv_col_name not in warned_missing_cols:
                print(
                    f"Warning: CSV column '{csv_col_name}' (expected for placeholder '{{{{{csv_col_name}}}}}') not found in '{csv_path}'. Using empty string for this placeholder."
                )
                warned_missing_cols.add(csv_col_name)
            data_for_this_row[csv_col_name] = ""
        else:
            data_for_this_row[csv_col_name] = str(data_row[csv_col_name])
    return data_for_this_row


def _instantiate_single_template(
        template_str: str, data_for_this_row: Dict[str, Any], row_index: int
) -> str | None:
    """
    Instantiates a single template string with provided data, handling errors during replacement.
    Returns the instantiated string or None if an error occurs.
    """
    current_instance = template_str
    try:
        for placeholder_name, value in data_for_this_row.items():
            current_instance = current_instance.replace(f"{{{{{placeholder_name}}}}}", value)
        return current_instance
    except Exception as ex:
        print(f"CRITICAL ERROR during template instantiation for CSV row {row_index}: {ex}")
        print(f"  Original Template: {template_str}")
        # Print only a few items from data_for_this_row to avoid excessive output
        print(f"  Row Data (partial): {dict(list(data_for_this_row.items())[:3])}...")
        traceback.print_exc()
        return None


# --- REFACTORED instantiate_templates_with_csv_data FUNCTION ---

def instantiate_templates_with_csv_data(
        csv_path: str, ai_generated_templates: List[str], placeholder_column_names: List[str]
) -> List[str]:
    """
    Loads CSV data and instantiates AI-generated templates with the data.

    Args:
        csv_path (str): Path to the CSV file containing data for placeholders.
        ai_generated_templates (List[str]): A list of template strings to instantiate.
        placeholder_column_names (List[str]): A list of column names expected as placeholders.

    Returns:
        List[str]: A list of fully instantiated template strings.
    """
    df_data = _load_csv_data_safe(csv_path)
    if df_data is None:
        return []

    if not ai_generated_templates:
        print("Warning: No AI-generated templates provided for instantiation. Returning empty list.")
        return []

    final_instantiated_texts = []
    warned_missing_cols = set()  # Set to track and warn about missing columns only once

    print(f"Instantiating {len(ai_generated_templates)} templates with CSV data...")
    for index, data_row in df_data.iterrows():
        data_for_this_row = _prepare_row_data_for_template(
            data_row, placeholder_column_names, warned_missing_cols, csv_path
        )

        for template_str in ai_generated_templates:
            instantiated_text = _instantiate_single_template(
                template_str, data_for_this_row, index
            )
            if instantiated_text is not None:
                final_instantiated_texts.append(instantiated_text)

            # Limit output to prevent excessive memory usage / performance issues
            if len(final_instantiated_texts) >= 50:  # Changed from 300 to 50 as per comment feedback
                print("Warning: Too many instantiated texts generated. Limiting to 50 for performance.")
                final_instantiated_texts = final_instantiated_texts[:50]
                return final_instantiated_texts  # Early exit once limit is reached

    return final_instantiated_texts


# --- ADDED HELPER FUNCTIONS ---

def _configure_runtime_flags(args: argparse.Namespace):
    """Configures global flags based on command-line arguments."""
    global NLTK_AVAILABLE, LEVENSHTEIN_AVAILABLE, SENTENCE_TRANSFORMERS_AVAILABLE, DIVERSIFICATION_MULTIPLIER

    if args.skip_nltk:
        NLTK_AVAILABLE = False
        print("User chose to skip NLTK metrics.")
    if args.skip_levenshtein:
        LEVENSHTEIN_AVAILABLE = False
        print("User chose to skip Levenshtein metrics.")
    if args.skip_semantic:
        SENTENCE_TRANSFORMERS_AVAILABLE = False
        print("User chose to skip Semantic metrics.")

    DIVERSIFICATION_MULTIPLIER = args.diversification_multiplier  # Allow override


def _validate_api_key():
    """Checks for the presence of OPENAI_API_KEY environment variable."""
    if not os.getenv("OPENAI_API_KEY"):
        print(
            "CRITICAL ERROR: The OPENAI_API_KEY environment variable must be set before running this script."
        )
        print("Example: export OPENAI_API_KEY='your_api_key_here'")
        return False
    return True


def _prepare_placeholder_columns(args_columns: List[str] | None) -> List[str]:
    """Processes command-line column arguments into a clean list of placeholder names."""
    if not args_columns:
        print(
            "Note: No specific CSV columns provided for placeholders. AI will be asked to generate generic templates for the task."
        )
        return []

    if len(args_columns) == 1 and "," in args_columns[0]:
        placeholder_cols = [col.strip() for col in args_columns[0].split(",")]
    else:
        placeholder_cols = [col.strip() for col in args_columns]

    print(f"CSV columns to be used as placeholders by AI: {placeholder_cols}")
    return placeholder_cols


def _create_dummy_csv_if_missing(csv_file_path: str, placeholder_cols: List[str]):
    """Creates a dummy CSV file if the specified one does not exist."""
    if not os.path.exists(csv_file_path):
        print(
            f"Warning: CSV file '{csv_file_path}' not found. Creating a dummy CSV for demonstration purposes."
        )
        dummy_cols_for_csv = placeholder_cols if placeholder_cols else ["FieldA", "FieldB", "Description"]
        if not dummy_cols_for_csv:  # Fallback if even placeholder_cols is empty
            dummy_cols_for_csv = ["SampleData"]

        dummy_data: Dict[str, List[Any]] = {col: [] for col in dummy_cols_for_csv}
        for i in range(1, 4):  # Create 3 rows of dummy data
            for col in dummy_cols_for_csv:
                dummy_data[col].append(f"{col}_Value{i}")
        try:
            pd.DataFrame(dummy_data).to_csv(csv_file_path, index=False)
            print(f"Dummy CSV file '{csv_file_path}' created with columns: {dummy_cols_for_csv}.")
        except Exception as e_csv:
            print(f"CRITICAL ERROR: Could not create dummy CSV file: {e_csv}")
            # Raising an error here or returning False might be better in production
            # For this example, we'll let main handle the return
            raise


def _save_instantiated_texts(final_texts: List[str], output_file_path: str):
    """Saves the list of instantiated texts to a specified output file."""
    if not final_texts:
        print("No final text instances were generated to save.")
        return

    try:
        with open(output_file_path, "w", encoding="utf-8") as f_out:
            for text_to_save in final_texts:
                f_out.write(text_to_save + "\n<END_OF_INSTANCE>\n\n")
        print(f"\nAll ({len(final_texts)}) instantiated texts have been saved to: {output_file_path}")
    except Exception as e:
        print(f"CRITICAL ERROR: Could not save instantiated texts to '{output_file_path}': {e}")
        traceback.print_exc()


# --- REFACTORED main FUNCTION ---

def main():
    """
    Main function that orchestrates the entire text generation and instantiation process.
    """
    parser = argparse.ArgumentParser(
        description="Generate text instances by combining AI-generated templates with CSV data, including diversity metrics for templates.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    # Define arguments
    parser.add_argument("csv_file", type=str, help="Path to the input CSV file.")
    parser.add_argument("task", type=str, help="Description of the task for which AI will generate templates.")
    parser.add_argument("--columns", type=str, nargs="*",
                        help="Space-separated list of CSV column names to be used as placeholders.")
    parser.add_argument("--num_templates", type=int, default=DEFAULT_NUM_TEMPLATES_FINAL,
                        help="Final number of diverse templates to select and use.")
    parser.add_argument("--output", type=str, default=DEFAULT_OUTPUT_FILE,
                        help="Path to save the final instantiated text instances.")
    parser.add_argument("--model", type=str, default=DEFAULT_LLM_MODEL,
                        help="Name of the LLM model to use for template generation.")
    parser.add_argument("--temp", type=float, default=DEFAULT_LLM_TEMPERATURE, help="Temperature for LLM generation.")
    parser.add_argument("--diversification_multiplier", type=int, default=DIVERSIFICATION_MULTIPLIER,
                        help="Multiplier for initial template generation.")
    parser.add_argument("--skip_nltk", action="store_true", help="Skip NLTK-based lexical diversity metrics.")
    parser.add_argument("--skip_levenshtein", action="store_true", help="Skip Levenshtein distance metric.")
    parser.add_argument("--skip_semantic", action="store_true",
                        help="Skip SentenceTransformer-based semantic diversity metrics.")

    args = parser.parse_args()

    # 1. Configure runtime flags based on args
    _configure_runtime_flags(args)

    # 2. Validate API Key
    if not _validate_api_key():
        return

    print("--- Starting Generic Template Generation Script ---")
    print(f"Using LLM Model: {args.model} with Temperature: {args.temp}")
    print(f"Task Description for AI: '{args.task}'")
    print(f"Final number of diverse templates to select: {args.num_templates}")
    print(f"Diversification multiplier for initial generation: {DIVERSIFICATION_MULTIPLIER}")

    # 3. Prepare placeholder columns
    placeholder_cols = _prepare_placeholder_columns(args.columns)

    # 4. Create dummy CSV if missing
    try:
        _create_dummy_csv_if_missing(args.csv_file, placeholder_cols)
    except Exception:  # Catch the potential error from dummy CSV creation
        return

    # 5. Generate templates via LLM
    ai_templates, diversity_stats_results = generate_templates_via_llm(
        task_description=args.task,
        placeholder_names=placeholder_cols,
        num_templates_to_generate_final=args.num_templates,
        diversification_multiplier=DIVERSIFICATION_MULTIPLIER,
        model_name=args.model,
        temperature=args.temp,
    )

    # 6. Save diversity statistics
    base_output_name, _ = os.path.splitext(args.output)
    stat_file_path = base_output_name + ".stat"
    save_diversity_stats(diversity_stats_results, stat_file_path)

    # 7. Instantiate templates with CSV data and save results
    if ai_templates:
        final_texts = instantiate_templates_with_csv_data(
            csv_path=args.csv_file,
            ai_generated_templates=ai_templates,
            placeholder_column_names=placeholder_cols,
        )
        if final_texts:
            print(f"\n--- {len(final_texts)} Final Text Instances Generated (Showing first 5) ---")
            for i, text_instance in enumerate(final_texts[:5]):
                print(f"Instance {i + 1}:\n{text_instance}\n" + "-" * 30)
            _save_instantiated_texts(final_texts, args.output)
        else:
            print("No final text instances were generated from the templates and CSV data.")
    else:
        print("AI did not generate any templates. Cannot proceed with data instantiation.")

    print("--- Script Finished ---")


if __name__ == '__main__':
    main()
