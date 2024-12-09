from copilot.core.exceptions import ToolException
from copilot.core.threadcontext import ThreadContext
from copilot.core.utils import copilot_debug, read_optional_env_var


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


def get_etendo_host():
    """
    Retrieves the Etendo host from the environment variables or returns a default value.

    Returns:
    str: The Etendo host URL.
    """
    return read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")


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
