import json

import curlify
import requests
from copilot.baseutils.logging_envvar import copilot_debug


def token_not_none(headers, token, url, endpoint):
    """This function builds the headers for the API request, ensuring that the token is set correctly.
    If the token is "ETENDO_TOKEN", it retrieves the token from the etendo_utils module.
    If the token is None or empty, but its recognized as a Etendo Classic request, add the token from etendo_utils.
    """
    from copilot.core.utils import etendo_utils

    if token and token == "ETENDO_TOKEN":
        token = etendo_utils.get_etendo_token()
        headers["Authorization"] = etendo_utils.normalize_etendo_token(token)
        return

    if token is None or token == "":
        etendo_host = etendo_utils.get_etendo_host()
        if url.lower().startswith(etendo_host) and endpoint.startswith("/sws/"):
            # Uses the Secure Web Services (SWS) layer, which requires a token
            token = etendo_utils.get_etendo_token()
            headers["Authorization"] = etendo_utils.normalize_etendo_token(token)


def do_request(
    method: str,
    url: str,
    endpoint: str,
    body_params: dict = None,
    path_params: dict = None,
    query_params: dict = None,
    token: str = None,
):
    """
    This function performs an HTTP request, handling path and query parameters.

    Parameters:
    method (str): The HTTP method to be used (GET, POST, PUT).
    url (str): The base URL of the API.
    endpoint (str): The API endpoint. Can include path parameters like /users/{id}.
    body_params (dict, optional): The body parameters for the API request. Defaults to None.
    path_params (dict, optional): A dictionary of values to replace in the endpoint.
                                 e.g., {'id': 123} for endpoint /users/{id}.
    query_params (dict, optional): A dictionary of query parameters to append to the URL.
                                   e.g., {'limit': 10, 'page': 1}.
    token (str, optional): The authentication token. Defaults to None.

    Returns:
    requests.Response: The response object from the request.

    Raises:
    ValueError: If the HTTP method is not supported.
    """
    if not url:
        return {"error": "url is required"}
    if not endpoint:
        return {"error": "endpoint is required"}
    if not method:
        return {"error": "method is required"}

    headers = {}
    headers["Content-Type"] = "application/json; charset=utf-8"
    token_not_none(headers, token, url, endpoint)

    final_endpoint = endpoint
    if path_params:
        for key, value in path_params.items():
            final_endpoint = final_endpoint.replace(f"{{{key}}}", str(value))

    if body_params and "@BASE64" in str(body_params):
        from copilot.core.toolgen.openapi_tool_gen import replace_base64_filepaths

        body_params = replace_base64_filepaths(body_params)

    upper_method = method.upper()

    if upper_method == "GET":
        copilot_debug("GET method")
        copilot_debug("url: " + url + final_endpoint)
        copilot_debug("headers: " + str(headers))
        copilot_debug("query_params: " + str(query_params))

        api_response = requests.get(url=url + final_endpoint, headers=headers, params=query_params)
        copilot_debug("response text: " + api_response.text)

    elif upper_method in ["POST", "PUT"]:
        copilot_debug(f"{upper_method} method")
        copilot_debug("url: " + url + final_endpoint)
        copilot_debug("body_params: " + str(body_params))
        copilot_debug("query_params: " + str(query_params))

        if upper_method == "PUT":
            # Serialize body_params to JSON manually with UTF-8 encoding
            json_data = json.dumps(body_params, ensure_ascii=False).encode("utf-8")
            copilot_debug(f"JSON payload size (bytes): {len(json_data)}")
            copilot_debug(f"JSON payload preview: {json_data[:200]}...")
            api_response = requests.put(
                url=url + final_endpoint, data=json_data, headers=headers, params=query_params
            )
        else:  # POST
            # Serialize body_params to JSON manually with UTF-8 encoding
            json_data = json.dumps(body_params, ensure_ascii=False).encode("utf-8")
            copilot_debug(f"JSON payload size (bytes): {len(json_data)}")
            copilot_debug(f"JSON payload preview: {json_data[:200]}...")
            api_response = requests.post(
                url=url + final_endpoint, data=json_data, headers=headers, params=query_params
            )

        copilot_debug("headers: " + str(api_response.request.headers))
        copilot_debug("----CURL----")
        copilot_debug(curlify.to_curl(api_response.request))
        copilot_debug("----Response----")
        copilot_debug(str(str(api_response.content)))
        copilot_debug("--------")

    else:
        raise ValueError(f"Method {method} not supported")

    # Safely decode response content with fallback handling
    try:
        content_text = api_response.content.decode("utf-8")
    except UnicodeDecodeError as e:
        copilot_debug(f"UTF-8 decode failed: {e}")
        # Try with detected encoding or fallback to latin-1 with error replacement
        encoding = api_response.encoding or "latin-1"
        try:
            content_text = api_response.content.decode(encoding, errors="replace")
            copilot_debug(f"Decoded with {encoding} encoding using error replacement")
        except Exception as fallback_error:
            copilot_debug(f"Fallback decode failed: {fallback_error}")
            # Last resort: return base64 encoded content with metadata
            import base64

            content_text = base64.b64encode(api_response.content).decode("ascii")
            copilot_debug("Content encoded as base64 due to decode failures")
            return {
                "status_code": api_response.status_code,
                "content": content_text,
                "content_type": api_response.headers.get("Content-Type", "unknown"),
                "encoding_error": str(e),
                "is_base64": True,
            }

    return {"status_code": api_response.status_code, "content": content_text}
