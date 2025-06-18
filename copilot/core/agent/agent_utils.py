import base64
from pathlib import Path
from typing import Dict, List, Tuple, Union


def process_local_files(local_file_ids: Union[str, List[str]]) -> Tuple[List[Dict], List[str]]:
    """Process local file IDs, returning image payloads and a list of other file paths."""
    image_payloads = []
    other_file_paths = []

    if local_file_ids:
        # Split paths into a list
        if isinstance(local_file_ids, str):
            file_paths = local_file_ids.split(",")
        elif isinstance(local_file_ids, list) and len(local_file_ids) == 1 and "," in local_file_ids[0]:
            file_paths = local_file_ids[0].split(",")
        else:
            file_paths = local_file_ids

        # Define supported image formats
        supported_image_formats = {
            "JPEG": "image/jpeg",
            "JPG": "image/jpeg",
            "PNG": "image/png",
            "WEBP": "image/webp",
            "GIF": "image/gif",
        }

        for file_path in [path.strip() for path in file_paths]:
            if Path(file_path).is_file():
                mime = None
                for ext, mime_type in supported_image_formats.items():
                    if file_path.lower().endswith(ext.lower()):
                        mime = mime_type
                        break

                if mime:  # Image format
                    with open(file_path, "rb") as image_file:
                        img_b64 = base64.b64encode(image_file.read()).decode("utf-8")
                        image_payloads.append(
                            {
                                "type": "image_url",
                                "image_url": {"url": f"data:{mime};base64,{img_b64}", "detail": "high"},
                            }
                        )
                else:  # Non-image format (PDF, TXT, etc.)
                    other_file_paths.append(file_path)
            else:
                print(f"Skipping: {file_path} does not exist or is not a file")

    return image_payloads, other_file_paths
