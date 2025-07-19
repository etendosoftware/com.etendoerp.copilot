import argparse
import json
import os
import traceback
from typing import Any, Dict, List

import pandas as pd

# Import common utilities
from common_utils import EvaluationLogger

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


def report_and_collect_diversity_metrics(templates: List[str]) -> Dict[str, Any]:
    """Calculate and report diversity metrics using specialized analyzer"""
    from .template_utils import DiversityAnalyzer

    logger = EvaluationLogger("DIVERSITY")
    analyzer = DiversityAnalyzer(logger)

    diversity_stats = analyzer.analyze_diversity(templates)

    # Print results in a user-friendly format
    _print_diversity_results(diversity_stats)

    return diversity_stats


def _print_diversity_results(diversity_stats: Dict[str, Any]) -> None:
    """Print diversity statistics in a readable format"""
    print("\n--- Diversity Analysis Results ---")

    num_analyzed = diversity_stats.get("num_templates_analyzed", 0)
    print(f"Templates analyzed: {num_analyzed}")

    if "status" in diversity_stats:
        print(f"Status: {diversity_stats['status']}")
        return

    # Print lexical diversity
    lexical = diversity_stats.get("lexical_diversity_nltk", {})
    if lexical.get("status") != "Skipped (NLTK not available)":
        _print_lexical_diversity(lexical)

    # Print Levenshtein distance
    levenshtein = diversity_stats.get("levenshtein_distance", {})
    if levenshtein.get("status") == "Success":
        distance = levenshtein.get("average_normalized_distance", 0.0)
        print(f"Average Levenshtein distance: {distance:.3f}")

    # Print semantic diversity
    semantic = diversity_stats.get("semantic_diversity_sbert", {})
    if semantic.get("status") == "Success":
        distance = semantic.get("average_cosine_distance", 0.0)
        print(f"Average semantic diversity (cosine): {distance:.3f}")

    print("-----------------------------------")


def _print_lexical_diversity(lexical_metrics: Dict[str, Any]) -> None:
    """Print lexical diversity metrics"""
    for key, value in lexical_metrics.items():
        if key.startswith("distinct_") and isinstance(value, dict):
            if value.get("status") == "Success":
                ratio = value.get("ratio", 0.0)
                total = value.get("total_count", 0)
                print(f"{key.replace('_', ' ').title()}: {ratio:.3f} ({total} total)")


def save_diversity_stats(stats_data: Dict[str, Any], output_stat_file: str):
    try:
        with open(output_stat_file, "w", encoding="utf-8") as f_stat:
            json.dump(stats_data, f_stat, indent=4)
        print(f"\nDiversity statistics saved to: {output_stat_file}")
    except Exception as e:
        print(f"CRITICAL ERROR: Could not save diversity statistics to '{output_stat_file}': {e}")
        traceback.print_exc()


# --- Template Selection Logic ---
def select_most_diverse_templates(all_templates: List[str], num_to_select: int) -> List[str]:
    """Select most diverse templates using specialized selector"""
    from .template_utils import TemplateSelector

    logger = EvaluationLogger("SELECTOR")
    selector = TemplateSelector(logger)

    return selector.select_diverse_templates(all_templates, num_to_select)


# --- LLM-Powered Template Generation ---
def generate_templates_via_llm(
    task_description: str,
    placeholder_names: List[str],
    num_templates_to_generate_final: int,  # This is the N for final selection
    diversification_multiplier: int,
    model_name: str = DEFAULT_LLM_MODEL,
    temperature: float = DEFAULT_LLM_TEMPERATURE,
) -> tuple[List[str], Dict[str, Any]]:
    """Generate diverse text templates using LLM with simplified logic"""
    from .template_utils import TemplateGenerator

    logger = EvaluationLogger("GEN_TEMPLATES")
    generator = TemplateGenerator(logger)

    return generator.generate_templates(
        task_description=task_description,
        placeholder_names=placeholder_names,
        num_final_templates=num_templates_to_generate_final,
        multiplier=diversification_multiplier,
        model_name=model_name,
        temperature=temperature,
    )


# --- CSV Data Instantiation ---
def instantiate_templates_with_csv_data(
    csv_path: str, ai_generated_templates: List[str], placeholder_column_names: List[str]
) -> List[str]:
    """Instantiate templates with CSV data using specialized utility"""
    from .template_utils import CSVTemplateProcessor

    logger = EvaluationLogger("CSV_INSTANTIATION")
    processor = CSVTemplateProcessor(logger)

    # Limit instances for performance (can be made configurable)
    MAX_INSTANCES = 50

    return processor.instantiate_templates(
        csv_path=csv_path,
        templates=ai_generated_templates,
        column_names=placeholder_column_names,
        max_instances=MAX_INSTANCES,
    )


# --- Argument Parsing and Main Execution ---
def main():
    """Main entry point with simplified orchestration"""
    # Parse arguments
    args = _parse_arguments()

    # Configure availability flags
    _configure_availability_flags(args)

    # Validate environment
    if not _validate_environment():
        return

    # Log configuration
    _log_configuration(args)

    # Process columns
    placeholder_cols = _process_placeholder_columns(args.columns)

    # Ensure CSV exists
    if not _ensure_csv_exists(args.csv_file, placeholder_cols):
        return

    # Generate templates and get stats
    ai_templates, diversity_stats = _generate_templates_and_stats(args, placeholder_cols)

    # Save diversity statistics
    _save_statistics(args.output, diversity_stats)

    # Process templates and save results
    _process_and_save_results(args, ai_templates, placeholder_cols)

    print("--- Script Finished ---")


def _parse_arguments():
    """Parse command line arguments"""
    parser = argparse.ArgumentParser(
        description="Generate text instances by combining AI-generated templates with CSV data",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("csv_file", type=str, help="Path to the input CSV file.")
    parser.add_argument("task", type=str, help="Description of the task for AI template generation.")
    parser.add_argument("--columns", type=str, nargs="*", help="CSV column names to use as placeholders")
    parser.add_argument(
        "--num_templates", type=int, default=DEFAULT_NUM_TEMPLATES_FINAL, help="Number of templates"
    )
    parser.add_argument("--output", type=str, default=DEFAULT_OUTPUT_FILE, help="Output file path")
    parser.add_argument("--model", type=str, default=DEFAULT_LLM_MODEL, help="LLM model name")
    parser.add_argument("--temp", type=float, default=DEFAULT_LLM_TEMPERATURE, help="LLM temperature")
    parser.add_argument(
        "--diversification_multiplier",
        type=int,
        default=DIVERSIFICATION_MULTIPLIER,
        help="Generation multiplier",
    )
    parser.add_argument("--skip_nltk", action="store_true", help="Skip NLTK metrics")
    parser.add_argument("--skip_levenshtein", action="store_true", help="Skip Levenshtein metrics")
    parser.add_argument("--skip_semantic", action="store_true", help="Skip semantic metrics")

    return parser.parse_args()


def _configure_availability_flags(args):
    """Configure global availability flags based on arguments"""
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

    DIVERSIFICATION_MULTIPLIER = args.diversification_multiplier


def _validate_environment() -> bool:
    """Validate required environment variables"""
    if not os.getenv("OPENAI_API_KEY"):
        print("CRITICAL ERROR: The OPENAI_API_KEY environment variable must be set.")
        print("Example: export OPENAI_API_KEY='your_api_key_here'")
        return False
    return True


def _log_configuration(args):
    """Log current configuration"""
    print("--- Starting Generic Template Generation Script ---")
    print(f"Using LLM Model: {args.model} with Temperature: {args.temp}")
    print(f"Task Description for AI: '{args.task}'")
    print(f"Final number of diverse templates to select: {args.num_templates}")
    print(f"Diversification multiplier for initial generation: {DIVERSIFICATION_MULTIPLIER}")


def _process_placeholder_columns(columns_arg):
    """Process and normalize placeholder columns"""
    placeholder_cols = []
    if columns_arg:
        if len(columns_arg) == 1 and "," in columns_arg[0]:
            placeholder_cols = [col.strip() for col in columns_arg[0].split(",")]
        else:
            placeholder_cols = [col.strip() for col in columns_arg]

    if not placeholder_cols:
        print("Note: No specific CSV columns provided for placeholders.")
    else:
        print(f"CSV columns to be used as placeholders by AI: {placeholder_cols}")

    return placeholder_cols


def _ensure_csv_exists(csv_file: str, placeholder_cols: List[str]) -> bool:
    """Ensure CSV file exists, create dummy if needed"""
    if os.path.exists(csv_file):
        return True

    print(f"Warning: CSV file '{csv_file}' not found. Creating dummy CSV.")

    dummy_cols = placeholder_cols if placeholder_cols else ["FieldA", "FieldB", "Description"]
    if not dummy_cols:
        dummy_cols = ["SampleData"]

    try:
        dummy_data = {col: [f"{col}_Value{i}" for i in range(1, 4)] for col in dummy_cols}
        pd.DataFrame(dummy_data).to_csv(csv_file, index=False)
        print(f"Dummy CSV file '{csv_file}' created with columns: {dummy_cols}.")
        return True
    except Exception as e:
        print(f"CRITICAL ERROR: Could not create dummy CSV file: {e}")
        return False


def _generate_templates_and_stats(args, placeholder_cols):
    """Generate templates and return with diversity stats"""
    return generate_templates_via_llm(
        task_description=args.task,
        placeholder_names=placeholder_cols,
        num_templates_to_generate_final=args.num_templates,
        diversification_multiplier=DIVERSIFICATION_MULTIPLIER,
        model_name=args.model,
        temperature=args.temp,
    )


def _save_statistics(output_path: str, diversity_stats):
    """Save diversity statistics to file"""
    base_output_name, _ = os.path.splitext(output_path)
    stat_file_path = base_output_name + ".stat"
    save_diversity_stats(diversity_stats, stat_file_path)


def _process_and_save_results(args, ai_templates, placeholder_cols):
    """Process templates with CSV data and save results"""
    if not ai_templates:
        print("AI did not generate any templates. Cannot proceed with data instantiation.")
        return

    final_texts = instantiate_templates_with_csv_data(
        csv_path=args.csv_file,
        ai_generated_templates=ai_templates,
        placeholder_column_names=placeholder_cols,
    )

    if not final_texts:
        print("No final text instances were generated from the templates and CSV data.")
        return

    # Show preview
    print(f"\n--- {len(final_texts)} Final Text Instances Generated (Showing first 5) ---")
    for i, text_instance in enumerate(final_texts[:5]):
        print(f"Instance {i + 1}:\n{text_instance}\n" + "-" * 30)

    # Save to file
    try:
        with open(args.output, "w", encoding="utf-8") as f_out:
            for text_to_save in final_texts:
                f_out.write(text_to_save + "\n<END_OF_INSTANCE>\n\n")
        print(f"\nAll ({len(final_texts)}) instantiated texts have been saved to: {args.output}")
    except Exception as e:
        print(f"CRITICAL ERROR: Could not save instantiated texts to '{args.output}': {e}")
        traceback.print_exc()


if __name__ == "__main__":
    main()
