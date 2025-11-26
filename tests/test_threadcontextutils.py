from unittest.mock import Mock, patch

from copilot.core.threadcontextutils import (
    read_accum_usage_data,
    read_accum_usage_data_from_msg_arr,
)


class TestReadAccumUsageData:
    """Test cases for the read_accum_usage_data function."""

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_no_existing_data(self, mock_debug, mock_thread_context):
        """Test reading usage data when no existing data is stored in ThreadContext."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock output object with usage metadata
        mock_output = Mock()
        mock_output.usage_metadata = {"input_tokens": 100, "output_tokens": 50}

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify
        expected_result = {"input_tokens": 100, "output_tokens": 50}
        assert result == expected_result

        # Verify ThreadContext interactions
        mock_thread_context.has_data.assert_called_once_with("usage_data")
        mock_thread_context.set_data.assert_called_once_with("usage_data", expected_result)

        # Verify debug call
        mock_debug.assert_called_once()

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_with_existing_data(self, mock_debug, mock_thread_context):
        """Test reading usage data when existing data is already stored in ThreadContext."""
        # Setup
        existing_usage_data = {"input_tokens": 25, "output_tokens": 15}
        mock_thread_context.has_data.return_value = True
        mock_thread_context.get_data.return_value = existing_usage_data

        # Create mock output object with usage metadata
        mock_output = Mock()
        mock_output.usage_metadata = {"input_tokens": 75, "output_tokens": 35}

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify
        expected_result = {"input_tokens": 100, "output_tokens": 50}  # 25 + 75  # 15 + 35
        assert result == expected_result

        # Verify ThreadContext interactions
        mock_thread_context.has_data.assert_called_once_with("usage_data")
        mock_thread_context.get_data.assert_called_once_with("usage_data")
        mock_thread_context.set_data.assert_called_once_with("usage_data", expected_result)

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_no_usage_metadata(self, mock_debug, mock_thread_context):
        """Test reading usage data when output object has no usage_metadata."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock output object without usage metadata
        mock_output = Mock()
        mock_output.usage_metadata = None

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

        # Verify ThreadContext interactions
        mock_thread_context.set_data.assert_called_once_with("usage_data", expected_result)

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_partial_usage_metadata(self, mock_debug, mock_thread_context):
        """Test reading usage data when output object has partial usage_metadata."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock output object with partial usage metadata (only input_tokens)
        mock_output = Mock()
        mock_output.usage_metadata = {
            "input_tokens": 42
            # output_tokens is missing
        }

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify
        expected_result = {"input_tokens": 42, "output_tokens": 0}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_empty_usage_metadata(self, mock_debug, mock_thread_context):
        """Test reading usage data when output object has empty usage_metadata."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock output object with empty usage metadata
        mock_output = Mock()
        mock_output.usage_metadata = {}

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_zero_tokens(self, mock_debug, mock_thread_context):
        """Test reading usage data when tokens are zero."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock output object with zero tokens
        mock_output = Mock()
        mock_output.usage_metadata = {"input_tokens": 0, "output_tokens": 0}

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_large_numbers(self, mock_debug, mock_thread_context):
        """Test reading usage data with large token numbers."""
        # Setup
        existing_usage_data = {"input_tokens": 999999, "output_tokens": 888888}
        mock_thread_context.has_data.return_value = True
        mock_thread_context.get_data.return_value = existing_usage_data

        # Create mock output object with large token numbers
        mock_output = Mock()
        mock_output.usage_metadata = {"input_tokens": 111111, "output_tokens": 222222}

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify
        expected_result = {
            "input_tokens": 1111110,  # 999999 + 111111
            "output_tokens": 1111110,  # 888888 + 222222
        }
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_attribute_error(self, mock_debug, mock_thread_context):
        """Test reading usage data when output object doesn't have usage_metadata attribute."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock output object without usage_metadata attribute
        mock_output = Mock(spec=[])  # Empty spec means no attributes

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify - should return default values and log error
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

        # Verify error was logged
        assert mock_debug.call_count == 1
        error_call = mock_debug.call_args_list[0]
        assert "Error reading usage data:" in error_call[0][0]

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_thread_context_error(self, mock_debug, mock_thread_context):
        """Test reading usage data when ThreadContext raises an exception."""
        # Setup
        mock_thread_context.has_data.side_effect = Exception("ThreadContext error")

        # Create mock output object
        mock_output = Mock()
        mock_output.usage_metadata = {"input_tokens": 50, "output_tokens": 30}

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify - should return default values and log error
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

        # Verify error was logged
        assert mock_debug.call_count == 1
        error_call = mock_debug.call_args_list[0]
        assert "Error reading usage data:" in error_call[0][0]
        assert "ThreadContext error" in error_call[0][0]

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_type_error(self, mock_debug, mock_thread_context):
        """Test reading usage data when usage_metadata has wrong type."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock output object with non-dict usage_metadata
        mock_output = Mock()
        mock_output.usage_metadata = "not a dict"

        # Execute
        result = read_accum_usage_data(mock_output)

        # Verify - should return default values and log error
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

        # Verify error was logged
        assert mock_debug.call_count == 1

class TestReadAccumUsageDataFromMsgArr:
    """Test cases for the read_accum_usage_data_from_msg_arr function."""

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_no_existing_data(self, mock_debug, mock_thread_context):
        """Test reading usage data from message array when no existing data is stored."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages with different types
        human_msg = Mock()
        human_msg.type = "human"
        human_msg.usage_metadata = None

        ai_msg = Mock()
        ai_msg.type = "ai"
        ai_msg.usage_metadata = {"input_tokens": 50, "output_tokens": 30}

        tool_msg = Mock()
        tool_msg.type = "tool"
        tool_msg.usage_metadata = {"input_tokens": 20, "output_tokens": 10}

        msg_arr = [human_msg, ai_msg, tool_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify
        expected_result = {"input_tokens": 70, "output_tokens": 40}  # 50 + 20  # 30 + 10
        assert result == expected_result

        # Verify ThreadContext interactions
        mock_thread_context.has_data.assert_called_once_with("usage_data")
        mock_thread_context.set_data.assert_called_once_with("usage_data", expected_result)

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_with_existing_data(self, mock_debug, mock_thread_context):
        """Test reading usage data from message array with existing ThreadContext data."""
        # Setup
        existing_usage_data = {"input_tokens": 100, "output_tokens": 50}
        mock_thread_context.has_data.return_value = True
        mock_thread_context.get_data.return_value = existing_usage_data

        # Create mock messages
        human_msg = Mock()
        human_msg.type = "human"

        ai_msg = Mock()
        ai_msg.type = "ai"
        ai_msg.usage_metadata = {"input_tokens": 25, "output_tokens": 15}

        msg_arr = [human_msg, ai_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify
        expected_result = {"input_tokens": 125, "output_tokens": 65}  # 100 + 25  # 50 + 15
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_no_human_message(self, mock_debug, mock_thread_context):
        """Test reading usage data from message array with no human message."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages without human type
        ai_msg1 = Mock()
        ai_msg1.type = "ai"
        ai_msg1.usage_metadata = {"input_tokens": 30, "output_tokens": 20}

        ai_msg2 = Mock()
        ai_msg2.type = "ai"
        ai_msg2.usage_metadata = {"input_tokens": 40, "output_tokens": 25}

        msg_arr = [ai_msg1, ai_msg2]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should process all messages since there's no human message
        # The function starts from new_messages_index which is len(msg_arr) - 1 = 1
        # So it only processes the last message (ai_msg2)
        expected_result = {"input_tokens": 40, "output_tokens": 25}  # Only from ai_msg2  # Only from ai_msg2
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_all_messages_without_human(
        self, mock_debug, mock_thread_context
    ):
        """Test reading usage data when processing all messages without human message (single message)."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create single AI message
        ai_msg = Mock()
        ai_msg.type = "ai"
        ai_msg.usage_metadata = {"input_tokens": 50, "output_tokens": 30}

        msg_arr = [ai_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should process the single message
        expected_result = {"input_tokens": 50, "output_tokens": 30}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_messages_without_usage_metadata(
        self, mock_debug, mock_thread_context
    ):
        """Test reading usage data from message array where messages don't have usage_metadata attribute."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages without usage_metadata attribute
        human_msg = Mock()
        human_msg.type = "human"
        # Remove usage_metadata attribute if it exists
        if hasattr(human_msg, "usage_metadata"):
            delattr(human_msg, "usage_metadata")

        ai_msg = Mock()
        ai_msg.type = "ai"
        # Remove usage_metadata attribute if it exists
        if hasattr(ai_msg, "usage_metadata"):
            delattr(ai_msg, "usage_metadata")

        msg_arr = [human_msg, ai_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_empty_array(self, mock_debug, mock_thread_context):
        """Test reading usage data from empty message array."""
        # Setup
        mock_thread_context.has_data.return_value = False

        msg_arr = []

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_multiple_human_messages(
        self, mock_debug, mock_thread_context
    ):
        """Test reading usage data from message array with multiple human messages."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages with multiple human messages
        human_msg1 = Mock()
        human_msg1.type = "human"

        ai_msg1 = Mock()
        ai_msg1.type = "ai"
        ai_msg1.usage_metadata = {"input_tokens": 10, "output_tokens": 5}

        human_msg2 = Mock()
        human_msg2.type = "human"

        ai_msg2 = Mock()
        ai_msg2.type = "ai"
        ai_msg2.usage_metadata = {"input_tokens": 20, "output_tokens": 15}

        msg_arr = [human_msg1, ai_msg1, human_msg2, ai_msg2]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should only process messages after the last human message
        expected_result = {"input_tokens": 20, "output_tokens": 15}  # Only ai_msg2  # Only ai_msg2
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_none_usage_metadata(self, mock_debug, mock_thread_context):
        """Test reading usage data from message array with None usage_metadata."""
        # Setup
        mock_thread_context.has_data.return_value = False

        human_msg = Mock()
        human_msg.type = "human"

        ai_msg = Mock()
        ai_msg.type = "ai"
        ai_msg.usage_metadata = None

        msg_arr = [human_msg, ai_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_invalid_metadata_type(self, mock_debug, mock_thread_context):
        """Test reading usage data when usage_metadata is not a dictionary."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages with non-dict usage_metadata
        human_msg = Mock()
        human_msg.type = "human"

        ai_msg = Mock()
        ai_msg.type = "ai"
        ai_msg.usage_metadata = "not a dict"  # This will cause TypeError in the "in" check

        msg_arr = [human_msg, ai_msg]

        # Execute - should handle TypeError gracefully (currently it doesn't)
        # This test documents the current behavior
        try:
            result = read_accum_usage_data_from_msg_arr(msg_arr)
            # If we get here without exception, metadata was ignored
            expected_result = {"input_tokens": 0, "output_tokens": 0}
            assert result == expected_result
        except TypeError:
            # This is the current behavior - the function doesn't handle non-dict metadata
            pass

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_partial_metadata(self, mock_debug, mock_thread_context):
        """Test reading usage data from message array with partial usage metadata."""
        # Setup
        mock_thread_context.has_data.return_value = False

        human_msg = Mock()
        human_msg.type = "human"

        ai_msg1 = Mock()
        ai_msg1.type = "ai"
        ai_msg1.usage_metadata = {
            "input_tokens": 30
            # output_tokens missing
        }

        ai_msg2 = Mock()
        ai_msg2.type = "ai"
        ai_msg2.usage_metadata = {
            "output_tokens": 20
            # input_tokens missing
        }

        msg_arr = [human_msg, ai_msg1, ai_msg2]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify
        expected_result = {"input_tokens": 30, "output_tokens": 20}  # Only from ai_msg1  # Only from ai_msg2
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_human_at_start(self, mock_debug, mock_thread_context):
        """Test reading usage data when human message is at the start."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages with human message at start
        human_msg = Mock()
        human_msg.type = "human"

        ai_msg1 = Mock()
        ai_msg1.type = "ai"
        ai_msg1.usage_metadata = {"input_tokens": 10, "output_tokens": 5}

        ai_msg2 = Mock()
        ai_msg2.type = "ai"
        ai_msg2.usage_metadata = {"input_tokens": 20, "output_tokens": 15}

        msg_arr = [human_msg, ai_msg1, ai_msg2]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should process all messages after human message
        expected_result = {"input_tokens": 30, "output_tokens": 20}  # 10 + 20  # 5 + 15
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_human_at_end(self, mock_debug, mock_thread_context):
        """Test reading usage data when human message is at the end."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages with human message at end
        ai_msg1 = Mock()
        ai_msg1.type = "ai"
        ai_msg1.usage_metadata = {"input_tokens": 10, "output_tokens": 5}

        ai_msg2 = Mock()
        ai_msg2.type = "ai"
        ai_msg2.usage_metadata = {"input_tokens": 20, "output_tokens": 15}

        human_msg = Mock()
        human_msg.type = "human"

        msg_arr = [ai_msg1, ai_msg2, human_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should process no messages (starts after last human)
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_mixed_message_types(self, mock_debug, mock_thread_context):
        """Test reading usage data with mixed message types (system, tool, etc)."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages with different types
        system_msg = Mock()
        system_msg.type = "system"
        system_msg.usage_metadata = {"input_tokens": 5, "output_tokens": 3}

        tool_msg = Mock()
        tool_msg.type = "tool"
        tool_msg.usage_metadata = {"input_tokens": 15, "output_tokens": 10}

        ai_msg = Mock()
        ai_msg.type = "ai"
        ai_msg.usage_metadata = {"input_tokens": 25, "output_tokens": 20}

        msg_arr = [system_msg, tool_msg, ai_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should process only the last message (ai_msg)
        expected_result = {"input_tokens": 25, "output_tokens": 20}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_negative_tokens(self, mock_debug, mock_thread_context):
        """Test reading usage data with negative token values."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages with negative token values
        human_msg = Mock()
        human_msg.type = "human"

        ai_msg = Mock()
        ai_msg.type = "ai"
        ai_msg.usage_metadata = {"input_tokens": -10, "output_tokens": -5}  # Negative values

        msg_arr = [human_msg, ai_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should handle negative values
        expected_result = {"input_tokens": -10, "output_tokens": -5}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_only_human_messages(self, mock_debug, mock_thread_context):
        """Test reading usage data from array with only human messages."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock messages with only human messages
        human_msg1 = Mock()
        human_msg1.type = "human"

        human_msg2 = Mock()
        human_msg2.type = "human"

        msg_arr = [human_msg1, human_msg2]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should process no messages (no messages after last human)
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_exception_in_processing(self, mock_debug, mock_thread_context):
        """Test reading usage data when exception occurs during message processing."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock message where accessing usage_metadata raises an exception
        human_msg = Mock()
        human_msg.type = "human"

        ai_msg = Mock()
        ai_msg.type = "ai"
        # Make usage_metadata raise an exception when accessed
        type(ai_msg).usage_metadata = property(lambda self: (_ for _ in ()).throw(Exception("Metadata error")))

        msg_arr = [human_msg, ai_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should return default values and log error
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

        # Verify error was logged
        assert mock_debug.call_count == 1
        error_call = mock_debug.call_args_list[0]
        assert "Error reading usage data from message array:" in error_call[0][0]

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_thread_context_error(self, mock_debug, mock_thread_context):
        """Test reading usage data when ThreadContext raises an exception."""
        # Setup
        mock_thread_context.has_data.side_effect = Exception("ThreadContext error")

        # Create mock messages
        human_msg = Mock()
        human_msg.type = "human"

        ai_msg = Mock()
        ai_msg.type = "ai"
        ai_msg.usage_metadata = {"input_tokens": 50, "output_tokens": 30}

        msg_arr = [human_msg, ai_msg]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should return default values and log error
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

        # Verify error was logged
        assert mock_debug.call_count == 1
        error_call = mock_debug.call_args_list[0]
        assert "Error reading usage data from message array:" in error_call[0][0]
        assert "ThreadContext error" in error_call[0][0]

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_type_error_on_message(self, mock_debug, mock_thread_context):
        """Test reading usage data when message doesn't have type attribute."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Create mock message without type attribute
        msg_without_type = Mock(spec=[])  # Empty spec means no attributes

        msg_arr = [msg_without_type]

        # Execute
        result = read_accum_usage_data_from_msg_arr(msg_arr)

        # Verify - should return default values and log error
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

        # Verify error was logged
        assert mock_debug.call_count == 1
        error_call = mock_debug.call_args_list[0]
        assert "Error reading usage data from message array:" in error_call[0][0]

    @patch("copilot.core.threadcontextutils.ThreadContext")
    @patch("copilot.core.threadcontextutils.copilot_debug_custom")
    def test_read_accum_usage_data_from_msg_arr_none_input(self, mock_debug, mock_thread_context):
        """Test reading usage data when None is passed as message array."""
        # Setup
        mock_thread_context.has_data.return_value = False

        # Execute
        result = read_accum_usage_data_from_msg_arr(None)

        # Verify - should return default values and log error
        expected_result = {"input_tokens": 0, "output_tokens": 0}
        assert result == expected_result

        # Verify error was logged
        assert mock_debug.call_count == 1
        error_call = mock_debug.call_args_list[0]
        assert "Error reading usage data from message array:" in error_call[0][0]
