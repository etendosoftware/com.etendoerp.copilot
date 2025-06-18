import curlify
import requests
from copilot.core.exceptions import ToolException
from copilot.core.threadcontext import ThreadContext
from copilot.core.utils import copilot_debug, copilot_debug_curl, read_optional_env_var


def get_etendo_token():
    """
    Retrieves the Etendo token from the thread context's extra info.

    Raises:
        Exception: If no access token is provided or if the Webservices are not enabled for the user role or if the WS are not configured for the Entity.

    Returns:
        str: The Etendo token.
    """
    extra_info = get_extra_info()
    if (
        extra_info is None
        or extra_info.get("auth") is None
        or extra_info.get("auth").get("ETENDO_TOKEN") is None
    ):
        raise Exception(
            "No access token provided, to work with Etendo, an access token is required."
            "Make sure that the Webservices are enabled to the user role and the WS are configured"
            " for the Entity."
        )
    access_token = extra_info.get("auth").get("ETENDO_TOKEN")
    return access_token


def get_extra_info():
    """
    Retrieves the extra info from the thread context.

    Returns:
        dict: The extra info from the thread context.

    If an error occurs, returns an empty dictionary.
    """
    try:
        return ThreadContext.get_data("extra_info")
    except Exception as e:
        copilot_debug(f"Error getting extra info: {e}")
    return {}


def _get_headers(access_token):
    """
    This method generates headers for an HTTP request.

    Parameters:
    access_token (str, optional): The access token to be included in the headers. If provided, an 'Authorization' field
     is added to the headers with the value 'Bearer {access_token}'.

    Returns:
    dict: A dictionary representing the headers. If an access token is provided, the dictionary includes an
     'Authorization' field.
    """
    headers = {}

    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


def call_etendo(method: str, url: str, endpoint: str, body_params, access_token: str):
    """
    Sends an HTTP request to a specified Etendo endpoint using the given method, URL, endpoint, body parameters, and access token.

    Args:
        method (str): The HTTP method to use ('GET', 'POST', 'PUT', 'DELETE').
        url (str): The base URL of the Etendo server.
        endpoint (str): The specific API endpoint to call.
        body_params (dict or any): The parameters to include in the request body (for POST, PUT, DELETE).
        access_token (str): The access token for authentication.

    Returns:
        dict: The JSON-decoded response from the server if the request is successful.
        dict: A dictionary containing an "error" key with the error message if the request fails.

    Raises:
        ToolException: If an unsupported HTTP method is provided.
    """
    import requests

    headers = _get_headers(access_token)
    import json

    json_data = json.dumps(body_params)
    full_url = url + endpoint
    copilot_debug(f"Calling Webhook(POST): {full_url}")
    if method.upper() == "GET":
        result = requests.get(url=full_url, headers=headers)
    elif method.upper() == "POST":
        result = requests.post(url=full_url, data=json_data, headers=headers)
    elif method.upper() == "PUT":
        result = requests.put(url=full_url, data=json_data, headers=headers)
    elif method.upper() == "DELETE":
        result = requests.delete(url=full_url, data=json_data, headers=headers)
    else:
        raise ToolException(f"Unsupported HTTP method: {method}")
    copilot_debug_curl(result.request)
    if result.ok:
        return json.loads(result.text)
    else:
        copilot_debug(result.text)
        return {"error": result.text}


def get_etendo_host():
    """
    Retrieves the Etendo host from the environment variables or returns a default value.

    Returns:
    str: The Etendo host URL.
    """
    return read_optional_env_var("ETENDO_HOST_DOCKER", "http://host.docker.internal:8080/etendo")


def login_etendo(server_url, client_admin_user, client_admin_password):
    """
    Logs in to the Etendo system.

    Parameters:
    server_url (str): The URL of the Etendo server.
    client_admin_user (str): The username of the client admin user.
    client_admin_password (str): The password of the client admin user.

    Returns:
    str: The access token for the Etendo system.
    """
    import requests

    url = f"{server_url}/sws/login"
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    data = {
        "username": client_admin_user,
        "password": client_admin_password,
    }
    response = requests.post(url, headers=headers, json=data)
    if response.ok:
        return response.json().get("token")
    else:
        raise ToolException(f"Error logging in to Etendo: {response.text}")


def call_webhook(access_token, body_params, url, webhook_name):
    """
    Calls a webhook with the provided parameters.

    Parameters:
    access_token (str): The access token for authorization.
    body_params (dict): The body parameters for the webhook.
    url (str): The base URL for the webhook.
    webhook_name (str): The name of the webhook.

    Returns:
    dict: The response from the webhook call. If the call fails, returns an error message.
    """
    import requests

    headers = _get_headers(access_token)
    endpoint = "/webhooks/?name=" + webhook_name
    import json

    json_data = json.dumps(body_params)
    full_url = url + endpoint
    copilot_debug(f"Calling Webhook(POST): {full_url}")
    post_result = requests.post(url=full_url, data=json_data, headers=headers)
    if post_result.ok:
        return json.loads(post_result.text)
    else:
        copilot_debug(post_result.text)
        return {"error": post_result.text}


def request_to_etendo(
    method,
    payload,
    endpoint,
    etendo_host,
    bearer_token,
) -> requests.Response:
    """
    Sends an HTTP request to the specified Etendo endpoint using the given method, payload, and authentication.

    Args:
        method (str): The HTTP method to use ('GET', 'POST', 'PUT', 'DELETE').
        payload (dict): The JSON payload to send with the request (used for 'POST' and 'PUT' methods).
        endpoint (str): The API endpoint to target (appended to etendo_host).
        etendo_host (str): The base URL of the Etendo host.
        bearer_token (str): The Bearer token for authorization.

    Returns:
        requests.Response: The response object returned by the requests library.

    Raises:
        ToolException: If an invalid HTTP method is provided.
    """
    url = f"{etendo_host}{endpoint}"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {bearer_token}",
    }
    if method.upper() == "GET":
        response = requests.get(url, headers=headers)
    elif method.upper() == "POST":
        response = requests.post(url, headers=headers, json=payload)
    elif method.upper() == "PUT":
        response = requests.put(url, headers=headers, json=payload)
    elif method.upper() == "DELETE":
        response = requests.delete(url, headers=headers)
    else:
        raise ToolException(f"Invalid HTTP method: {method}")
    copilot_debug(curlify.to_curl(response.request))
    return response


def simple_request_to_etendo(method, payload, endpoint) -> requests.Response:
    """
    Sends a simple HTTP request to the Etendo API using the specified method, payload, and endpoint.

    Args:
        method (str): The HTTP method to use for the request (e.g., 'GET', 'POST').
        payload (dict): The data to send in the body of the request.
        endpoint (str): The API endpoint to send the request to.

    Returns:
        requests.Response: The response object returned by the Etendo API.
    """
    return request_to_etendo(
        method,
        payload,
        endpoint,
        etendo_host=get_etendo_host(),
        bearer_token=get_etendo_token(),
    )
