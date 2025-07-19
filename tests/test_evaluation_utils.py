"""
Test suite for evaluation package - utils module

Tests to validate utility functions before refactoring.
"""

import json
import os
import sys
import tempfile
from unittest.mock import Mock, patch

import pytest

# Add evaluation directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "evaluation"))

from utils import calc_md5, send_evaluation_to_supabase, validate_dataset_folder


class TestCalcMd5:
    """Test cases for calc_md5 function"""

    def test_calc_md5_simple_dict(self):
        """Test MD5 calculation with simple dictionary"""
        test_data = {"key": "value", "number": 42}
        result = calc_md5(test_data)

        # Verify it returns a valid SHA-256 hash (64 characters)
        assert len(result) == 64
        assert isinstance(result, str)

        # Verify consistency - same input should give same hash
        result2 = calc_md5(test_data)
        assert result == result2

    def test_calc_md5_simple_list(self):
        """Test MD5 calculation with simple list"""
        test_data = [1, 2, 3, "test"]
        result = calc_md5(test_data)

        assert len(result) == 64
        assert isinstance(result, str)

    def test_calc_md5_complex_structure(self):
        """Test MD5 calculation with complex nested structure"""
        test_data = {
            "conversations": [
                {"id": 1, "messages": ["hello", "world"]},
                {"id": 2, "messages": ["foo", "bar"]},
            ],
            "metadata": {"version": "1.0", "timestamp": 123456789},
        }
        result = calc_md5(test_data)

        assert len(result) == 64
        assert isinstance(result, str)

    def test_calc_md5_different_inputs_different_hashes(self):
        """Test that different inputs produce different hashes"""
        data1 = {"key": "value1"}
        data2 = {"key": "value2"}

        hash1 = calc_md5(data1)
        hash2 = calc_md5(data2)

        assert hash1 != hash2

    def test_calc_md5_empty_data(self):
        """Test MD5 calculation with empty data"""
        result = calc_md5({})
        assert len(result) == 64
        assert isinstance(result, str)

    def test_calc_md5_unicode_data(self):
        """Test MD5 calculation with unicode characters"""
        test_data = {"mensaje": "¡Hola mundo! 🌍", "emoji": "🚀"}
        result = calc_md5(test_data)

        assert len(result) == 64
        assert isinstance(result, str)


class TestValidateDatasetFolder:
    """Test cases for validate_dataset_folder function"""

    def test_validate_dataset_folder_valid_structure(self):
        """Test validation with valid dataset folder structure"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create valid structure
            conversations_file = os.path.join(temp_dir, "conversations.json")
            with open(conversations_file, "w") as f:
                json.dump([], f)

            # Should not raise exception
            try:
                validate_dataset_folder(temp_dir)
            except Exception as e:
                pytest.fail(f"validate_dataset_folder raised {e} unexpectedly!")

    def test_validate_dataset_folder_nonexistent_directory(self):
        """Test validation with non-existent directory"""
        nonexistent_path = "/path/that/does/not/exist"

        with pytest.raises((FileNotFoundError, OSError)):
            validate_dataset_folder(nonexistent_path)

    def test_validate_dataset_folder_missing_conversations_file(self):
        """Test validation with missing conversations.json file"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Directory exists but no conversations.json
            with pytest.raises((FileNotFoundError, ValueError)):
                validate_dataset_folder(temp_dir)

    def test_validate_dataset_folder_invalid_json(self):
        """Test validation with invalid JSON in conversations.json"""
        with tempfile.TemporaryDirectory() as temp_dir:
            conversations_file = os.path.join(temp_dir, "conversations.json")
            with open(conversations_file, "w") as f:
                f.write("invalid json content {")

            with pytest.raises((json.JSONDecodeError, ValueError)):
                validate_dataset_folder(temp_dir)

    def test_validate_dataset_folder_valid_conversations_json(self):
        """Test validation with valid conversations.json content"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create valid conversations.json
            sample_conversations = [
                {
                    "messages": [
                        {"role": "USER", "content": "Hello"},
                        {"role": "AI", "content": "Hi there!"},
                    ],
                    "expected_response": {"role": "AI", "content": "Hi there!"},
                }
            ]

            conversations_file = os.path.join(temp_dir, "conversations.json")
            with open(conversations_file, "w") as f:
                json.dump(sample_conversations, f)

            # Should not raise exception
            try:
                validate_dataset_folder(temp_dir)
            except Exception as e:
                pytest.fail(f"validate_dataset_folder raised {e} unexpectedly!")


class TestSendEvaluationToSupabase:
    """Test cases for send_evaluation_to_supabase function"""

    @patch("evaluation.utils.requests.post")
    def test_send_evaluation_to_supabase_success(self, mock_post):
        """Test successful sending of evaluation to Supabase"""
        # Mock successful response
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"success": True}
        mock_post.return_value = mock_response

        # Test data
        evaluation_data = {"agent_id": "test_agent_123", "score": 0.85, "timestamp": "2024-01-01T12:00:00"}

        # Should not raise exception
        try:
            result = send_evaluation_to_supabase(evaluation_data)
            # If function returns something, verify it
            if result is not None:
                assert result is True or isinstance(result, dict)
        except Exception as e:
            pytest.fail(f"send_evaluation_to_supabase raised {e} unexpectedly!")

        # Verify request was made
        assert mock_post.called

    @patch("evaluation.utils.requests.post")
    def test_send_evaluation_to_supabase_http_error(self, mock_post):
        """Test handling of HTTP errors when sending to Supabase"""
        # Mock error response
        mock_response = Mock()
        mock_response.status_code = 500
        mock_response.raise_for_status.side_effect = Exception("HTTP 500 Error")
        mock_post.return_value = mock_response

        evaluation_data = {"agent_id": "test_agent_123"}

        with pytest.raises(Exception):
            send_evaluation_to_supabase(evaluation_data)

    @patch("evaluation.utils.requests.post")
    def test_send_evaluation_to_supabase_connection_error(self, mock_post):
        """Test handling of connection errors when sending to Supabase"""
        # Mock connection error
        mock_post.side_effect = Exception("Connection error")

        evaluation_data = {"agent_id": "test_agent_123"}

        with pytest.raises(Exception):
            send_evaluation_to_supabase(evaluation_data)

    @patch("evaluation.utils.requests.post")
    def test_send_evaluation_to_supabase_empty_data(self, mock_post):
        """Test sending empty evaluation data"""
        mock_response = Mock()
        mock_response.status_code = 200
        mock_post.return_value = mock_response

        # Should handle empty data gracefully
        try:
            send_evaluation_to_supabase({})
        except Exception as e:
            # If the function is supposed to validate input, this is expected
            if "validation" not in str(e).lower() and "empty" not in str(e).lower():
                pytest.fail(f"Unexpected error: {e}")


class TestUtilsIntegration:
    """Integration tests for utils module functions"""

    def test_md5_consistency_with_real_conversation_data(self):
        """Test MD5 calculation consistency with realistic conversation data"""
        # Create realistic conversation data
        conversation_data = {
            "run_id": "test_run_123",
            "messages": [
                {"role": "USER", "content": "What is the capital of France?"},
                {"role": "AI", "content": "The capital of France is Paris."},
            ],
            "expected_response": {"role": "AI", "content": "Paris"},
            "creation_date": "2024-01-01-12:00:00",
        }

        # Calculate hash multiple times
        hash1 = calc_md5(conversation_data)
        hash2 = calc_md5(conversation_data)
        hash3 = calc_md5(conversation_data)

        # All should be identical
        assert hash1 == hash2 == hash3

        # Modify data slightly and verify hash changes
        conversation_data["messages"][1]["content"] = "Paris is the capital of France."
        hash4 = calc_md5(conversation_data)
        assert hash4 != hash1

    def test_dataset_validation_workflow(self):
        """Test complete dataset validation workflow"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create a complete valid dataset
            conversations = []
            for i in range(3):
                conversation = {
                    "run_id": f"run_{i}",
                    "messages": [
                        {"role": "USER", "content": f"Question {i}"},
                        {"role": "AI", "content": f"Answer {i}"},
                    ],
                    "expected_response": {"role": "AI", "content": f"Expected {i}"},
                    "creation_date": "2024-01-01-12:00:00",
                }
                conversations.append(conversation)

            # Save to file
            conversations_file = os.path.join(temp_dir, "conversations.json")
            with open(conversations_file, "w") as f:
                json.dump(conversations, f, indent=2)

            # Validate dataset
            validate_dataset_folder(temp_dir)

            # Calculate hash of dataset
            dataset_hash = calc_md5(conversations)
            assert len(dataset_hash) == 64


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
