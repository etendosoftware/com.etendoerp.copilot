"""
Specialized utilities for template generation and diversity analysis

This module contains classes to handle template generation, diversity metrics,
and variant processing with reduced cognitive complexity.
"""

import re
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import pandas as pd

from .common_utils import EvaluationLogger, FileHandler

# Optional dependency handling
try:
    from nltk.tokenize import word_tokenize
    from nltk.util import ngrams

    NLTK_AVAILABLE = True
except ImportError:
    NLTK_AVAILABLE = False
    word_tokenize = None
    ngrams = None

try:
    import Levenshtein

    LEVENSHTEIN_AVAILABLE = True
except ImportError:
    LEVENSHTEIN_AVAILABLE = False
    Levenshtein = None

try:
    from sentence_transformers import SentenceTransformer
    from sklearn.metrics.pairwise import cosine_distances

    SENTENCE_TRANSFORMERS_AVAILABLE = True
except ImportError:
    SENTENCE_TRANSFORMERS_AVAILABLE = False
    SentenceTransformer = None
    cosine_distances = None


class DiversityAnalyzer:
    """Handles diversity metrics calculation for templates"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("DIVERSITY")
        self.sbert_model = None
        self.sbert_model_name = "paraphrase-multilingual-MiniLM-L12-v2"

    def load_sbert_model(self) -> Optional[Any]:
        """Load SentenceTransformer model if available"""
        if not SENTENCE_TRANSFORMERS_AVAILABLE:
            return None

        if self.sbert_model is None:
            try:
                self.logger.info(f"Loading SentenceTransformer model '{self.sbert_model_name}'...")
                self.sbert_model = SentenceTransformer(self.sbert_model_name)
                self.logger.info("SentenceTransformer model loaded successfully")
            except Exception as e:
                self.logger.error("Error loading SentenceTransformer model", e)
                self.sbert_model = None

        return self.sbert_model

    def calculate_distinct_ngrams(self, texts: List[str], n: int = 1) -> Dict[str, Any]:
        """Calculate distinct n-grams ratio"""
        if not NLTK_AVAILABLE or not texts:
            return {
                "distinct_count": 0,
                "total_count": 0,
                "ratio": 0.0,
                "status": "Skipped (NLTK not available or no texts)",
            }

        try:
            all_ngrams = self._extract_ngrams_from_texts(texts, n)
            return self._calculate_ngram_stats(all_ngrams)
        except Exception as e:
            return {"distinct_count": 0, "total_count": 0, "ratio": 0.0, "status": f"Error: {str(e)}"}

    def _extract_ngrams_from_texts(self, texts: List[str], n: int) -> List[tuple]:
        """Extract n-grams from all texts"""
        all_ngrams = []
        for text in texts:
            tokens = word_tokenize(text.lower())
            if len(tokens) >= n:
                text_ngrams = list(ngrams(tokens, n))
                all_ngrams.extend(text_ngrams)
        return all_ngrams

    def _calculate_ngram_stats(self, all_ngrams: List[tuple]) -> Dict[str, Any]:
        """Calculate statistics for n-grams"""
        if not all_ngrams:
            return {
                "distinct_count": 0,
                "total_count": 0,
                "ratio": 0.0,
                "status": "Success (no n-grams found)",
            }

        total_count = len(all_ngrams)
        distinct_count = len(set(all_ngrams))
        ratio = distinct_count / total_count if total_count > 0 else 0.0

        return {
            "distinct_count": distinct_count,
            "total_count": total_count,
            "ratio": ratio,
            "status": "Success",
        }

    def calculate_levenshtein_distance(self, texts: List[str]) -> Dict[str, Any]:
        """Calculate average Levenshtein distance between texts"""
        if not LEVENSHTEIN_AVAILABLE:
            return {
                "average_normalized_distance": 0.0,
                "status": "Skipped (python-Levenshtein not available)",
            }

        if len(texts) < 2:
            return {"average_normalized_distance": 0.0, "status": "Skipped (need at least 2 texts)"}

        distances = self._calculate_pairwise_levenshtein(texts)
        avg_distance = sum(distances) / len(distances) if distances else 0.0

        return {"average_normalized_distance": avg_distance, "status": "Success"}

    def _calculate_pairwise_levenshtein(self, texts: List[str]) -> List[float]:
        """Calculate pairwise Levenshtein distances"""
        distances = []
        for i in range(len(texts)):
            for j in range(i + 1, len(texts)):
                distance = Levenshtein.distance(texts[i], texts[j])
                max_length = max(len(texts[i]), len(texts[j]), 1)
                normalized_distance = distance / max_length
                distances.append(normalized_distance)
        return distances

    def calculate_semantic_diversity(self, texts: List[str]) -> Dict[str, Any]:
        """Calculate semantic diversity using cosine distances"""
        model = self.load_sbert_model()
        if model is None:
            return {
                "average_cosine_distance": 0.0,
                "status": "Skipped (SentenceTransformer model not loaded)",
            }

        if len(texts) < 2:
            return {"average_cosine_distance": 0.0, "status": "Skipped (need at least 2 texts)"}

        embeddings = self._get_embeddings(texts)
        if embeddings is None or len(embeddings) < 2:
            return {
                "average_cosine_distance": 0.0,
                "status": "Skipped (could not generate enough valid embeddings)",
            }

        try:
            distances = self._calculate_cosine_distances(embeddings)
            return {"average_cosine_distance": distances, "status": "Success"}
        except Exception as e:
            return {"average_cosine_distance": 0.0, "status": f"Error: {str(e)}"}

    def _get_embeddings(self, texts: List[str]) -> Optional[np.ndarray]:
        """Get embeddings for texts"""
        try:
            return self.sbert_model.encode(texts, show_progress_bar=False)
        except Exception as e:
            self.logger.error("Error generating embeddings", e)
            return None

    def _calculate_cosine_distances(self, embeddings: np.ndarray) -> float:
        """Calculate average pairwise cosine distances"""
        distances_matrix = cosine_distances(embeddings)
        pairwise_distances = []

        for i in range(len(embeddings)):
            for j in range(i + 1, len(embeddings)):
                pairwise_distances.append(distances_matrix[i, j])

        return sum(pairwise_distances) / len(pairwise_distances) if pairwise_distances else 0.0

    def analyze_diversity(self, templates: List[str]) -> Dict[str, Any]:
        """Comprehensive diversity analysis"""
        if not templates or len(templates) < 2:
            return {
                "num_templates_analyzed": len(templates),
                "status": "Not enough templates for pairwise diversity.",
            }

        metrics = {"num_templates_analyzed": len(templates)}

        # Lexical diversity
        metrics["lexical_diversity_nltk"] = self._analyze_lexical_diversity(templates)

        # Levenshtein distance
        metrics["levenshtein_distance"] = self.calculate_levenshtein_distance(templates)

        # Semantic diversity
        metrics["semantic_diversity_sbert"] = self.calculate_semantic_diversity(templates)

        return metrics

    def _analyze_lexical_diversity(self, templates: List[str]) -> Dict[str, Any]:
        """Analyze lexical diversity using n-grams"""
        if not NLTK_AVAILABLE:
            return {"status": "Skipped (NLTK not available)"}

        lexical_metrics = {}
        for n in [1, 2]:
            ngram_key = f"distinct_{n}_grams"
            lexical_metrics[ngram_key] = self.calculate_distinct_ngrams(templates, n=n)

        return lexical_metrics


class TemplateSelector:
    """Handles template selection based on diversity"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("SELECTOR")
        self.analyzer = DiversityAnalyzer(logger)

    def select_diverse_templates(self, templates: List[str], num_to_select: int) -> List[str]:
        """Select most diverse templates using semantic analysis"""
        if not templates:
            self.logger.warning("No templates provided for diverse selection")
            return []

        if len(templates) <= num_to_select:
            self.logger.info(f"Returning all {len(templates)} templates (requested {num_to_select})")
            return templates

        model = self.analyzer.load_sbert_model()
        if not model or not SENTENCE_TRANSFORMERS_AVAILABLE:
            self.logger.warning("Semantic diversity model not available. Returning first N templates")
            return templates[:num_to_select]

        return self._perform_diverse_selection(templates, num_to_select)

    def _perform_diverse_selection(self, templates: List[str], num_to_select: int) -> List[str]:
        """Perform greedy diverse selection"""
        self.logger.info(f"Selecting {num_to_select} most diverse templates from {len(templates)} candidates")

        embeddings = self.analyzer._get_embeddings(templates)
        if embeddings is None or len(embeddings) < num_to_select:
            self.logger.warning("Could not generate enough embeddings. Returning first N templates")
            return templates[:num_to_select]

        try:
            selected_indices = self._greedy_diverse_selection(embeddings, num_to_select)
            selected_templates = [templates[i] for i in selected_indices]
            self.logger.info(f"Selected {len(selected_templates)} diverse templates")
            return selected_templates
        except Exception as e:
            self.logger.error("Error in diverse selection", e)
            return templates[:num_to_select]

    def _greedy_diverse_selection(self, embeddings: np.ndarray, num_to_select: int) -> List[int]:
        """Greedy algorithm for diverse template selection"""
        distance_matrix = cosine_distances(embeddings)
        selected_indices = [0]  # Start with first template

        while len(selected_indices) < num_to_select:
            best_next_idx = self._find_most_diverse_candidate(
                distance_matrix, selected_indices, len(embeddings)
            )

            if best_next_idx == -1:
                self.logger.warning("Could not find more distinct templates")
                break

            selected_indices.append(best_next_idx)

        return selected_indices

    def _find_most_diverse_candidate(
        self, distance_matrix: np.ndarray, selected_indices: List[int], num_candidates: int
    ) -> int:
        """Find the most diverse candidate from remaining templates"""
        best_idx = -1
        max_min_distance = -1

        for i in range(num_candidates):
            if i in selected_indices:
                continue

            min_distance = np.min(distance_matrix[i, selected_indices])
            if min_distance > max_min_distance:
                max_min_distance = min_distance
                best_idx = i

        return best_idx


class TemplateGenerator:
    """Handles LLM-based template generation"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("GENERATOR")
        self.selector = TemplateSelector(logger)

    def generate_templates(
        self,
        task_description: str,
        placeholder_names: List[str],
        num_final_templates: int,
        multiplier: int,
        model_name: str,
        temperature: float,
    ) -> Tuple[List[str], Dict[str, Any]]:
        """Generate diverse templates using LLM"""
        # Validate OpenAI API key
        if not self._validate_openai_key():
            return [], {"status": "OPENAI_API_KEY not set"}

        # Initialize LLM
        llm = self._initialize_llm(model_name, temperature)
        if llm is None:
            return [], {"status": "LLM initialization error"}

        # Generate initial templates
        num_initial = num_final_templates * multiplier
        raw_templates = self._request_templates_from_llm(
            llm, task_description, placeholder_names, num_initial
        )

        if not raw_templates:
            return [], {"status": "No templates generated by LLM"}

        # Validate and filter templates
        validated_templates = self._validate_templates(raw_templates, placeholder_names)

        # Select diverse subset
        final_templates = self.selector.select_diverse_templates(validated_templates, num_final_templates)

        # Analyze diversity
        diversity_stats = self.selector.analyzer.analyze_diversity(final_templates)

        return final_templates, diversity_stats

    def _validate_openai_key(self) -> bool:
        """Validate OpenAI API key is set"""
        import os

        if not os.getenv("OPENAI_API_KEY"):
            self.logger.error("OPENAI_API_KEY environment variable is not set")
            return False
        return True

    def _initialize_llm(self, model_name: str, temperature: float) -> Optional[Any]:
        """Initialize LLM with error handling"""
        try:
            from langchain_openai import ChatOpenAI

            return ChatOpenAI(model_name=model_name, temperature=temperature)
        except Exception as e:
            self.logger.error(f"Failed to initialize LLM '{model_name}'", e)
            return None

    def _request_templates_from_llm(
        self, llm: Any, task_description: str, placeholder_names: List[str], num_templates: int
    ) -> List[str]:
        """Request templates from LLM"""
        try:
            prompt = self._build_prompt(task_description, placeholder_names, num_templates)
            response = llm.invoke(prompt)

            if hasattr(response, "content"):
                content = response.content
            else:
                content = str(response)

            return [t.strip() for t in content.split("\n") if t.strip()]
        except Exception as e:
            self.logger.error("Error during LLM template generation", e)
            return []

    def _build_prompt(self, task_description: str, placeholder_names: List[str], num_templates: int) -> str:
        """Build prompt for template generation"""
        placeholders_str = ", ".join([f"{{{{{name}}}}}" for name in placeholder_names])
        if not placeholder_names:
            placeholders_str = "(No specific placeholders provided)"

        return f"""Generate {num_templates} distinct text templates for the task: '{task_description}'.

Available placeholders: {placeholders_str}

Guidelines:
1. Use double curly braces for placeholders: {{{{FieldName}}}}
2. Only use provided placeholder names
3. Create diverse templates with varied structure, tone, and complexity
4. Language can be English or Spanish
5. Each template on a new line, no numbering

Generated Templates:"""

    def _validate_templates(self, raw_templates: List[str], placeholder_names: List[str]) -> List[str]:
        """Validate and filter templates"""
        validated = []
        placeholder_pattern = re.compile(r"\{\{([\w\s.-]+?)\}\}")

        for i, template in enumerate(raw_templates):
            if self._is_template_valid(template, placeholder_names, placeholder_pattern):
                validated.append(template)
            else:
                self.logger.warning(f"Template {i+1} failed validation: {template[:100]}...")

        self.logger.info(f"Validated {len(validated)} templates from {len(raw_templates)} generated")
        return validated

    def _is_template_valid(self, template: str, placeholder_names: List[str], pattern: re.Pattern) -> bool:
        """Check if template is valid"""
        found_placeholders = set(pattern.findall(template))

        # Check for disallowed placeholders
        for placeholder in found_placeholders:
            if placeholder not in placeholder_names:
                return False

        return True


class CSVTemplateProcessor:
    """Handles CSV data processing and template instantiation"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("CSV_PROCESSOR")
        self.file_handler = FileHandler(logger)

    def instantiate_templates(
        self, csv_path: str, templates: List[str], column_names: List[str], max_instances: int = 50
    ) -> List[str]:
        """Instantiate templates with CSV data"""
        # Load CSV data
        df = self.file_handler.load_csv(csv_path, dtype=str)
        if df is None:
            return []

        df = df.fillna("")

        if not templates:
            self.logger.warning("No templates provided for instantiation")
            return []

        # Process instantiation
        instances = self._process_instantiation(df, templates, column_names, max_instances)

        self.logger.info(f"Generated {len(instances)} instantiated texts")
        return instances

    def _process_instantiation(
        self, df: pd.DataFrame, templates: List[str], column_names: List[str], max_instances: int
    ) -> List[str]:
        """Process template instantiation with data"""
        instances = []
        warned_columns = set()

        for index, row in df.iterrows():
            if len(instances) >= max_instances:
                self.logger.warning(f"Limiting to {max_instances} instances for performance")
                break

            for template in templates:
                instance = self._instantiate_single_template(
                    template, row, column_names, df.columns, warned_columns, index
                )
                if instance:
                    instances.append(instance)

        return instances

    def _instantiate_single_template(
        self,
        template: str,
        row: pd.Series,
        column_names: List[str],
        df_columns: pd.Index,
        warned_columns: set,
        row_index: int,
    ) -> Optional[str]:
        """Instantiate a single template with row data"""
        try:
            data_dict = self._build_data_dict(row, column_names, df_columns, warned_columns)

            # Replace placeholders
            instance = template
            for placeholder, value in data_dict.items():
                instance = instance.replace(f"{{{{{placeholder}}}}}", str(value))

            return instance
        except Exception as e:
            self.logger.error(f"Error instantiating template for row {row_index}", e)
            return None

    def _build_data_dict(
        self, row: pd.Series, column_names: List[str], df_columns: pd.Index, warned_columns: set
    ) -> Dict[str, str]:
        """Build data dictionary for template substitution"""
        data_dict = {}

        for col_name in column_names:
            if col_name not in df_columns:
                if col_name not in warned_columns:
                    self.logger.warning(f"CSV column '{col_name}' not found. Using empty string")
                    warned_columns.add(col_name)
                data_dict[col_name] = ""
            else:
                data_dict[col_name] = str(row[col_name])

        return data_dict
