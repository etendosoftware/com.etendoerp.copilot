from copilot.core.threadcontext import ThreadContext


def read_etendo_token() -> str:
    extra_info = ThreadContext.get_data('extra_info')
    if not extra_info or not extra_info.get('auth', {}).get('ETENDO_TOKEN'):
        raise Exception(
            "No access token provided, to work with Etendo, an access token is required."
            "Make sure that the Webservices are enabled to the user role and the WS are configured for"
            " the Entity."
        )
    return extra_info.get('auth').get('ETENDO_TOKEN')
