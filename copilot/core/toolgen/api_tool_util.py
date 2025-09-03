import json

import curlify
import requests
from copilot.baseutils.logging_envvar import copilot_debug


def token_not_none(headers, token, url, endpoint):
    """Ensure the Authorization header is set when a token is required.

    Behavior:
    - If token is the special string "ETENDO_TOKEN", retrieve the real token
      from etendo_utils and set the Authorization header using the
      normalize_etendo_token helper.
    - If token is None or an empty string and the request targets the Etendo
      host under the Secure Web Services path (endpoint starts with "/sws/"),
      retrieve the token from etendo_utils and set the Authorization header.

    Args:
        headers (dict): HTTP headers dictionary that will be modified in-place.
        token (str | None): Token provided by the caller or special marker.
        url (str): Base URL of the target server. Used to detect Etendo host.
        endpoint (str): Endpoint path; used to detect SWS endpoints.

    Returns:
        None: The headers dict is modified in-place. No return value.
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
        api_response = do_post_put(body_params, final_endpoint, headers, query_params, upper_method, url)

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


def do_post_put(body_params, final_endpoint, headers, query_params, upper_method, url):
    """Perform a POST or PUT HTTP request with JSON body.

    This helper sets the appropriate Content-Type header for JSON payloads,
    serializes the provided body parameters into UTF-8 encoded JSON bytes,
    and issues either a POST or PUT request using the requests library.

    Args:
        body_params (dict): The payload to serialize and send in the request body.
        final_endpoint (str): The endpoint path to append to the base URL.
        headers (dict): Dictionary of HTTP headers to include in the request. The
            Content-Type header will be set to 'application/json; charset=utf-8'.
        query_params (dict): Optional query parameters to include in the URL.
        upper_method (str): Uppercase HTTP method name, expected to be 'POST' or 'PUT'.
        url (str): Base URL for the request.

    Returns:
        requests.Response: The Response object returned by the requests call.

    Raises:
        Any exception raised by requests.post or requests.put is propagated to the caller.
    """
    copilot_debug(f"{upper_method} method")
    copilot_debug("url: " + url + final_endpoint)
    copilot_debug("body_params: " + str(body_params))
    copilot_debug("query_params: " + str(query_params))
    headers["Content-Type"] = "application/json; charset=utf-8"
    if upper_method == "PUT":
        json_data = serialize_json(body_params)
        api_response = requests.put(
            url=url + final_endpoint, data=json_data, headers=headers, params=query_params
        )
    else:  # POST
        json_data = serialize_json(body_params)
        api_response = requests.post(
            url=url + final_endpoint, data=json_data, headers=headers, params=query_params
        )
    return api_response


def serialize_json(body_params):
    """Serialize a Python object to UTF-8 encoded JSON bytes.

    This function converts the given Python object (typically a dict) into a
    JSON-formatted bytes object using ensure_ascii=False to preserve Unicode
    characters, then logs the payload size and a short preview.

    Args:
        body_params (Any): The Python object to serialize to JSON.

    Returns:
        bytes: The UTF-8 encoded JSON representation of body_params.

    Raises:
        TypeError: If body_params contains non-serializable objects.
    """
    # Serialize body_params to JSON manually with UTF-8 encoding
    json_data = json.dumps(body_params, ensure_ascii=False).encode("utf-8")
    copilot_debug(f"JSON payload size (bytes): {len(json_data)}")
    copilot_debug(f"JSON payload preview: {json_data[:200]}...")
    return json_data
