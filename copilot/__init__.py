from dotenv import load_dotenv

load_dotenv(".env")

from .app import create_app

app = create_app(__name__)
