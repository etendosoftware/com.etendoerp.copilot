from dotenv import load_dotenv

load_dotenv(".env")

from .app import create_app  # noqa: E402

app = create_app(__name__)
