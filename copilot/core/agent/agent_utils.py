import base64
from pathlib import Path
from typing import Dict, List, Tuple, Union

from copilot.baseutils.logging_envvar import is_docker


def process_local_files(local_file_ids: Union[str, List[str]]) -> Tuple[List[Dict], List[str]]:
    """Process local file IDs, returning image payloads and a list of other file
    paths."""
    image_payloads = []
    other_file_paths = []

    if not local_file_ids:
        return image_payloads, other_file_paths

    # Split paths into a list
    file_paths = split_file_paths(local_file_ids)

    # Define supported image formats
    supported_image_formats = {
        "JPEG": "image/jpeg",
        "JPG": "image/jpeg",
        "PNG": "image/png",
        "WEBP": "image/webp",
        "GIF": "image/gif",
    }

    for file_path in [path.strip() for path in file_paths]:
        if not Path(file_path).is_file():
            print(f"Skipping: {file_path} does not exist or is not a file")
            continue
        mime = ext_to_mime(file_path, supported_image_formats)
        if mime:  # Image format
            img_payload = get_payload(file_path, mime)
            image_payloads.append(img_payload)
        else:  # Non-image format (PDF, TXT, etc.)
            other_file_paths.append(file_path)

    return image_payloads, other_file_paths


def get_payload(file_path, mime):
    """
    Generates an image payload in base64 format.

    This function reads an image file, encodes it in base64, and creates a payload
    that includes the image URL in `data:` format with the specified MIME type.

    Args:
        file_path (str): The path to the image file.
        mime (str): The MIME type of the image (e.g., 'image/jpeg').

    Returns:
        dict: A dictionary representing the image payload, including the base64-encoded URL.
    """
    with open(file_path, "rb") as image_file:
        img_b64 = base64.b64encode(image_file.read()).decode("utf-8")
        img_payload = {
            "type": "image_url",
            "image_url": {"url": f"data:{mime};base64,{img_b64}", "detail": "high"},
        }
    return img_payload


def ext_to_mime(file_path, supported_image_formats):
    """
    Determines the MIME type of a file based on its extension.

    Args:
        file_path (str): The path to the file whose MIME type is to be determined.
        supported_image_formats (dict): A dictionary mapping file extensions to their corresponding MIME types.

    Returns:
        str or None: The MIME type if the file extension matches a supported format, otherwise None.
    """
    for ext, mime_type in supported_image_formats.items():
        if file_path.lower().endswith(ext.lower()):
            return mime_type
    return None


def split_file_paths(local_file_ids):
    """
    Splits a string or list of file paths into individual file paths.

    Args:
        local_file_ids (Union[str, list]): A string of comma-separated file paths or a list of file paths.

    Returns:
        list: A list of individual file paths.
    """
    if isinstance(local_file_ids, str):
        file_paths = local_file_ids.split(",")
    elif isinstance(local_file_ids, list) and len(local_file_ids) == 1 and "," in local_file_ids[0]:
        file_paths = local_file_ids[0].split(",")
    else:
        file_paths = local_file_ids
    return file_paths


def get_checkpoint_file():
    if is_docker():
        base = "/checkpoints/"
    else:
        base = "./checkpoints/"
    Path(base).mkdir(parents=True, exist_ok=True)
    return base + "checkpoints.sqlite"
