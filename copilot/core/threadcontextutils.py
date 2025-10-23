from copilot.baseutils.logging_envvar import copilot_debug_custom
from copilot.core.threadcontext import ThreadContext

RED = "\033[91m"


def read_accum_usage_data(output) -> dict:
    """Reads and accumulates usage data from the output object.
    Returns a tuple of (input_tokens, output_tokens).
    """
    try:
        if ThreadContext.has_data("usage_data") is False:
            usage_data = {"input_tokens": 0, "output_tokens": 0}
        else:
            usage_data = ThreadContext.get_data("usage_data")
        if output.usage_metadata and "input_tokens" in output.usage_metadata:
            usage_data["input_tokens"] += output.usage_metadata["input_tokens"]
        if output.usage_metadata and "output_tokens" in output.usage_metadata:
            usage_data["output_tokens"] += output.usage_metadata["output_tokens"]
        copilot_debug_custom(
            f"Input tokens: {usage_data['input_tokens']}, Output tokens: {usage_data['output_tokens']}, \n Total tokens: {usage_data['input_tokens'] + usage_data['output_tokens']}",
            # RED
            RED,
        )
        # Store usage data in the thread context
        ThreadContext.set_data("usage_data", usage_data)
        return usage_data
    except Exception as e:
        copilot_debug_custom(f"Error reading usage data from output: {e}", RED)
        return {"input_tokens": 0, "output_tokens": 0}


def read_accum_usage_data_from_msg_arr(msg_arr) -> dict:
    """Reads and accumulates usage data from the message array.
    Returns a tuple of (input_tokens, output_tokens).
    """
    try:
        if ThreadContext.has_data("usage_data") is False:
            usage_data = {"input_tokens": 0, "output_tokens": 0}
        else:
            usage_data = ThreadContext.get_data("usage_data")
        # start after the last human message (msg.type == 'human')
        new_messages_index = len(msg_arr) - 1
        for i in range(len(msg_arr) - 1, -1, -1):
            if msg_arr[i].type == "human":
                new_messages_index = i + 1
                break
        for msg in msg_arr[new_messages_index:]:
            if not hasattr(msg, "usage_metadata"):
                continue
            if msg.usage_metadata and "input_tokens" in msg.usage_metadata:
                usage_data["input_tokens"] += msg.usage_metadata["input_tokens"]
            if msg.usage_metadata and "output_tokens" in msg.usage_metadata:
                usage_data["output_tokens"] += msg.usage_metadata["output_tokens"]
        copilot_debug_custom(
            f"Input tokens: {usage_data['input_tokens']}, Output tokens: {usage_data['output_tokens']}, \n Total tokens: {usage_data['input_tokens'] + usage_data['output_tokens']}",
            # RED
            RED,
        )
        # Store usage data in the thread context
        ThreadContext.set_data("usage_data", usage_data)
        return usage_data

    except Exception as e:
        copilot_debug_custom(f"Error reading usage data from message array: {e}", RED)
        return {"input_tokens": 0, "output_tokens": 0}
