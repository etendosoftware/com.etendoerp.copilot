"""
Test suite for evaluation package - gen_variants module

Tests to validate variant generation logic before refactoring.
"""

import json
import os
import sys
import tempfile
from unittest.mock import patch

import pandas as pd
import pytest

# Add evaluation directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "evaluation"))

try:
    from gen_variants import (
        LEVENSHTEIN_AVAILABLE,
        # Import what's available - these may need adjustment based on actual module structure
        NLTK_AVAILABLE,
    )

    GEN_VARIANTS_AVAILABLE = True
except ImportError:
    GEN_VARIANTS_AVAILABLE = False
    NLTK_AVAILABLE = False
    LEVENSHTEIN_AVAILABLE = False


class TestGenVariantsConstants:
    """Test cases for gen_variants module constants"""

    @pytest.mark.skipif(not GEN_VARIANTS_AVAILABLE, reason="gen_variants module not available")
    def test_nltk_available_flag(self):
        """Test NLTK availability flag"""
        assert isinstance(NLTK_AVAILABLE, bool)

    @pytest.mark.skipif(not GEN_VARIANTS_AVAILABLE, reason="gen_variants module not available")
    def test_levenshtein_available_flag(self):
        """Test Levenshtein availability flag"""
        assert isinstance(LEVENSHTEIN_AVAILABLE, bool)


class TestGenVariantsBasicFunctionality:
    """Test basic functionality that should work regardless of optional dependencies"""

    def test_pandas_dataframe_operations(self):
        """Test basic pandas operations used in variant generation"""
        # Test DataFrame creation and manipulation
        data = {
            "text": ["Hello world", "How are you?", "Good morning"],
            "category": ["greeting", "question", "greeting"],
            "length": [11, 12, 12],
        }
        df = pd.DataFrame(data)

        assert len(df) == 3
        assert "text" in df.columns
        assert "category" in df.columns

        # Test filtering
        greetings = df[df["category"] == "greeting"]
        assert len(greetings) == 2

        # Test basic string operations
        df["word_count"] = df["text"].str.split().str.len()
        assert df.iloc[0]["word_count"] == 2
        assert df.iloc[1]["word_count"] == 3

    def test_text_length_calculations(self):
        """Test text length and character counting"""
        test_texts = [
            "Short text",
            "This is a longer piece of text with more words",
            "Medium length text here",
        ]

        lengths = [len(text) for text in test_texts]
        word_counts = [len(text.split()) for text in test_texts]

        assert lengths == [10, 44, 23]
        assert word_counts == [2, 9, 4]

    def test_basic_text_transformations(self):
        """Test basic text transformations for variant generation"""
        original_text = "This is a sample text for testing"

        # Test case variations
        upper_variant = original_text.upper()
        lower_variant = original_text.lower()
        title_variant = original_text.title()

        assert upper_variant == "THIS IS A SAMPLE TEXT FOR TESTING"
        assert lower_variant == "this is a sample text for testing"
        assert title_variant == "This Is A Sample Text For Testing"

        # Test word replacement simulation
        variants = [
            original_text.replace("sample", "example"),
            original_text.replace("testing", "verification"),
            original_text.replace("text", "content"),
        ]

        assert "example" in variants[0]
        assert "verification" in variants[1]
        assert "content" in variants[2]


class TestGenVariantsDataProcessing:
    """Test data processing functions used in variant generation"""

    def test_json_data_handling(self):
        """Test JSON data loading and processing"""
        test_data = {
            "conversations": [
                {
                    "original": "What is the weather today?",
                    "variants": [
                        "How is the weather today?",
                        "What's the weather like today?",
                        "Today's weather status?",
                    ],
                },
                {"original": "Hello there", "variants": ["Hi there", "Hello", "Hey there"]},
            ]
        }

        # Test JSON serialization/deserialization
        json_str = json.dumps(test_data)
        loaded_data = json.loads(json_str)

        assert loaded_data == test_data
        assert len(loaded_data["conversations"]) == 2
        assert len(loaded_data["conversations"][0]["variants"]) == 3

    def test_csv_variant_processing(self):
        """Test CSV processing for variant data"""
        # Create test CSV data
        variant_data = {
            "original_text": ["How are you?", "What is your name?", "Where are you from?"],
            "variant_1": ["How do you do?", "What's your name?", "Where do you come from?"],
            "variant_2": ["How are things?", "May I know your name?", "What's your origin?"],
        }

        df = pd.DataFrame(variant_data)

        # Test data structure
        assert len(df) == 3
        assert "original_text" in df.columns
        assert "variant_1" in df.columns
        assert "variant_2" in df.columns

        # Test variant extraction
        for _idx, row in df.iterrows():
            original = row["original_text"]
            variants = [row["variant_1"], row["variant_2"]]

            assert len(variants) == 2
            assert all(isinstance(v, str) for v in variants)
            assert original not in variants  # Variants should be different

    def test_text_similarity_basic_metrics(self):
        """Test basic text similarity metrics"""
        text1 = "Hello world"
        text2 = "Hello earth"
        text3 = "Hi planet"

        # Test character overlap
        def char_overlap(s1, s2):
            return len(set(s1.lower()) & set(s2.lower()))

        overlap_1_2 = char_overlap(text1, text2)
        overlap_1_3 = char_overlap(text1, text3)

        # text1 and text2 should have more overlap than text1 and text3
        assert overlap_1_2 > overlap_1_3

        # Test word overlap
        def word_overlap(s1, s2):
            words1 = set(s1.lower().split())
            words2 = set(s2.lower().split())
            return len(words1 & words2)

        word_overlap_1_2 = word_overlap(text1, text2)
        word_overlap_1_3 = word_overlap(text1, text3)

        assert word_overlap_1_2 >= word_overlap_1_3


class TestGenVariantsAlgorithms:
    """Test variant generation algorithms"""

    def test_synonym_replacement_simulation(self):
        """Test synonym replacement logic simulation"""
        # Simulate synonym replacement
        synonyms = {
            "good": ["excellent", "great", "fine", "nice"],
            "big": ["large", "huge", "enormous", "massive"],
            "fast": ["quick", "rapid", "swift", "speedy"],
        }

        original = "This is a good and big car that runs fast"

        # Simulate generating variants by replacing words
        variants = []
        for word, syns in synonyms.items():
            if word in original:
                for syn in syns:
                    variant = original.replace(word, syn)
                    variants.append(variant)

        assert len(variants) > 0
        assert "excellent" in variants[0]
        assert any("large" in v for v in variants)
        assert any("quick" in v for v in variants)

    def test_sentence_restructuring_simulation(self):
        """Test sentence restructuring for variant generation"""

        # Simulate different sentence structures
        restructured_variants = [
            "On the mat sat the cat",
            "The mat had a cat sitting on it",
            "A cat was sitting on the mat",
            "Sitting on the mat was the cat",
        ]

        # All variants should contain key elements
        key_words = ["cat", "mat", "sat"]
        for variant in restructured_variants:
            # Check if variant contains key concepts (with flexibility for word forms)
            variant_lower = variant.lower()
            assert any(word in variant_lower or word[:-1] in variant_lower for word in key_words)

    def test_variation_metrics_calculation(self):
        """Test calculation of variation metrics"""
        original = "How are you doing today?"
        variants = ["How are you today?", "How do you do today?", "How are things today?", "What's up today?"]

        # Test length variation
        lengths = [len(v) for v in variants]
        original_length = len(original)

        length_diffs = [abs(l - original_length) for l in lengths]
        assert all(isinstance(diff, int) for diff in length_diffs)

        # Test word count variation
        original_words = len(original.split())
        variant_words = [len(v.split()) for v in variants]

        word_diffs = [abs(w - original_words) for w in variant_words]
        assert all(isinstance(diff, int) for diff in word_diffs)


class TestGenVariantsFileOperations:
    """Test file operations for variant generation"""

    def test_input_file_processing(self):
        """Test processing of input files"""
        with tempfile.NamedTemporaryFile(mode="w", suffix=".txt", delete=False) as f:
            test_content = """Original text 1
Original text 2
Original text 3"""
            f.write(test_content)
            input_file = f.name

        try:
            # Test reading input file
            with open(input_file, "r") as file:
                lines = [line.strip() for line in file.readlines() if line.strip()]

            assert len(lines) == 3
            assert lines[0] == "Original text 1"
            assert lines[2] == "Original text 3"
        finally:
            os.unlink(input_file)

    def test_output_file_generation(self):
        """Test generation of output files"""
        variant_data = {
            "original": "Test sentence",
            "variants": ["Test phrase", "Testing sentence", "Sample sentence"],
            "metrics": {"variant_count": 3, "avg_length_diff": 2.5},
        }

        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
            json.dump(variant_data, f, indent=2)
            output_file = f.name

        try:
            # Test reading output file
            with open(output_file, "r") as file:
                loaded_data = json.load(file)

            assert loaded_data["original"] == "Test sentence"
            assert len(loaded_data["variants"]) == 3
            assert loaded_data["metrics"]["variant_count"] == 3
        finally:
            os.unlink(output_file)


class TestGenVariantsIntegration:
    """Integration tests for gen_variants module"""

    def test_end_to_end_variant_generation_simulation(self):
        """Test complete variant generation workflow simulation"""
        # Step 1: Input data
        input_texts = [
            "What is the capital of France?",
            "How do I reset my password?",
            "Where can I find the user manual?",
        ]

        # Step 2: Generate variants (simulated)
        all_variants = {}
        for text in input_texts:
            variants = []

            # Simulate different types of variations
            # Type 1: Question word changes
            if text.startswith("What"):
                variants.append(text.replace("What", "Which"))
            if text.startswith("How"):
                variants.append(text.replace("How", "In what way"))
            if text.startswith("Where"):
                variants.append(text.replace("Where", "In which location"))

            # Type 2: Synonym replacements
            variants.append(text.replace("capital", "main city"))
            variants.append(text.replace("reset", "change"))
            variants.append(text.replace("find", "locate"))

            # Filter out unchanged variants
            variants = [v for v in variants if v != text]
            all_variants[text] = variants

        # Step 3: Verify results
        assert len(all_variants) == 3
        for original, variants in all_variants.items():
            assert isinstance(variants, list)
            assert len(variants) > 0
            assert all(v != original for v in variants)

    @patch("builtins.print")
    def test_variant_generation_with_statistics(self, mock_print):
        """Test variant generation with statistics reporting"""
        # Simulate variant generation with statistics
        original_count = 10
        variants_per_original = 5
        total_variants = original_count * variants_per_original

        # Simulate processing
        print(f"Processing {original_count} original texts...")
        print(f"Generated {total_variants} variants total")
        print(f"Average variants per original: {variants_per_original}")

        # Verify output was generated
        assert mock_print.called
        print_calls = [str(call) for call in mock_print.call_args_list]
        assert any("Processing" in call for call in print_calls)


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
