import base64
import hashlib
import os
import shutil
import tempfile
import time
import zipfile

import chromadb
import pymupdf
from chromadb import Settings
from copilot.baseutils.logging_envvar import (
    copilot_debug,
    is_debug_enabled,
    read_optional_env_var,
)
from copilot.core.schemas import SplitterConfig
from copilot.core.utils.models import get_proxy_url
from langchain.text_splitter import CharacterTextSplitter, MarkdownTextSplitter
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings
from langchain_text_splitters import (
    Language,
    RecursiveCharacterTextSplitter,
    RecursiveJsonSplitter,
)

ALLOWED_EXTENSIONS = ["pdf", "txt", "md", "markdown", "java", "js", "py", "xml", "json"]
IMAGE_EXTENSIONS = ["png", "jpg", "jpeg", "bmp", "tiff", "gif", "webp"]

LANGCHAIN_DEFAULT_COLLECTION_NAME = "langchain"
IMAGES_COLLECTION_NAME = "IMAGES"


def get_embedding():
    """
    Creates and returns an OpenAI embeddings instance configured for the copilot.

    Returns:
        OpenAIEmbeddings: An instance of OpenAIEmbeddings configured with:
            - disallowed_special: Empty tuple (allows all special characters)
            - show_progress_bar: True (displays progress during embedding generation)
            - base_url: Proxy URL from configuration

    Notes:
        The base URL is retrieved using get_proxy_url() to route requests through the configured proxy.
    """
    return OpenAIEmbeddings(disallowed_special=(), show_progress_bar=True, base_url=get_proxy_url())


def get_vector_db_path(vector_db_id):
    """
    Retrieves or constructs the file system path for a vector database.

    Args:
        vector_db_id (str): Unique identifier for the vector database.

    Returns:
        str: Full path to the vector database file (with .db extension).

    Notes:
        - In Docker environments (when /app exists), uses /app/vectordbs as the base directory.
        - In local environments, uses ./vectordbs as the base directory.
        - Creates the vectordbs directory if it doesn't exist.
        - The returned path follows the pattern: <base_folder>/<vector_db_id>.db
    """
    try:
        cwd = os.getcwd()
    except Exception:
        cwd = "unknown"
    copilot_debug(f"Retrieving vector db path for {vector_db_id}, the current working directory is {cwd}")
    # check if exists /app
    if os.path.exists("/app"):
        vectordb_folder = "/app/vectordbs"
    else:
        vectordb_folder = "./vectordbs"
    if not os.path.exists(vectordb_folder):
        os.makedirs(vectordb_folder, exist_ok=True)
    return vectordb_folder + "/" + vector_db_id + ".db"


def get_chroma_settings(db_path=None):
    """
    Creates and configures ChromaDB settings for persistent storage.

    Args:
        db_path (str, optional): Path to the database directory. If None, ChromaDB uses its default location.

    Returns:
        Settings: Configured ChromaDB Settings object with:
            - persist_directory: Set to db_path if provided
            - is_persistent: True (enables data persistence)
            - allow_reset: True (allows database reset operations)

    Notes:
        These settings ensure that vector data is stored persistently on disk and can be reset if needed.
    """
    settings = Settings()
    if db_path is not None:
        settings.persist_directory = db_path
    settings.is_persistent = True
    settings.allow_reset = True
    return settings


def handle_zip_file(zip_file_path, chroma_client, splitter_config: SplitterConfig):
    """
    Extracts and indexes all supported files from a ZIP archive into ChromaDB.

    Args:
        zip_file_path (str): Path to the ZIP file to process.
        chroma_client: ChromaDB client instance for storing indexed documents.
        splitter_config (SplitterConfig): Configuration for text splitting during indexing.

    Returns:
        list[Document]: List of all Document objects created from files in the ZIP archive.

    Notes:
        - Extracts the ZIP file to a temporary directory.
        - Recursively walks through all subdirectories.
        - Only processes files with extensions in ALLOWED_EXTENSIONS.
        - Silently skips files that fail to process (logs error but continues).
        - Cleans up the temporary directory after processing.
    """
    temp_dir = tempfile.mkdtemp()
    acum_texts = []
    with zipfile.ZipFile(zip_file_path, "r") as zip_ref:
        zip_ref.extractall(temp_dir)
    # walk through the directory and index the files
    for root, _dirs, files in os.walk(temp_dir):
        for file in files:
            file_path = os.path.join(root, file)
            ext = file.split(".")[-1].lower()
            if ext in ALLOWED_EXTENSIONS:
                try:
                    copilot_debug(f"Processing file {file_path}")
                    acum_texts.extend(index_file(ext, file_path, chroma_client, splitter_config))
                except Exception as e:
                    copilot_debug(f"Error processing file {file_path}: {e}")
    # Remove the entire temporary directory
    shutil.rmtree(temp_dir)
    return acum_texts


def load_chroma_collection_from_path(db_path):
    """
    Loads a Chroma collection from the specified database path.

    Args:
        db_path (str): The path to the database directory where the Chroma collection is stored.

    Returns:
        Collection: The Chroma collection object loaded from the database.

    Steps:
        1. Initializes a Chroma client with the provided database path.
        2. Retrieves or creates a collection with the default name defined by `LANGCHAIN_DEFAULT_COLLECTION_NAME`.
    """
    # Initialize the Chroma client with the database path
    client = chromadb.Client(chromadb.config.Settings(persist_directory=db_path))

    # Load the collection by its name
    collection = client.get_or_create_collection(LANGCHAIN_DEFAULT_COLLECTION_NAME)
    return collection


def hash_splitter_config(config: SplitterConfig) -> str:
    """
    Generates a unique hash for a given SplitterConfig object.

    Args:
        config (SplitterConfig): The configuration object to be hashed.

    Returns:
        str: A SHA-256 hash of the JSON representation of the configuration.

    Notes:
        - The JSON representation of the configuration is serialized using the `model_dump_json` method,
          which excludes unset fields.
        - The resulting hash can be used to identify changes in the configuration.
    """
    json_repr = config.model_dump_json(exclude_unset=True)
    return hashlib.sha256(json_repr.encode("utf-8")).hexdigest()


def index_file(ext, item_path, chroma_client, splitter_config: SplitterConfig):
    """
    Indexes a file into ChromaDB collection, handling duplicates and text splitting.

    Args:
        ext (str): File extension (e.g., 'pdf', 'txt', 'md').
        item_path (str): Full path to the file to index.
        chroma_client: ChromaDB client instance for database operations.
        splitter_config (SplitterConfig): Configuration for text splitting behavior.

    Returns:
        list[Document]: List of Document objects ready to be added to the vector store.
                       Returns empty list if file already exists in the collection.

    Notes:
        - Calculates file MD5 hash (including splitter config hash) for duplicate detection.
        - If file already exists (same MD5), unmarks it from purge and returns empty list.
        - If file is new, processes it and optionally splits into chunks based on config.
        - All indexed documents are marked with 'purge': False to prevent deletion.
    """
    # Process the file and get its content and MD5
    file_content, md5 = process_file(item_path, ext)
    # If there is a splitter config, we need to add the config to the md5, so
    # we can identify changes in the config, to reindex the file.
    if splitter_config is not None:
        md5 = md5 + hash_splitter_config(splitter_config)

    collection = chroma_client.get_or_create_collection(LANGCHAIN_DEFAULT_COLLECTION_NAME)
    copilot_debug(f"Collection id {collection.id}")
    # Search for the document based on the MD5 hash
    result = collection.get(where={"md5": md5})

    if result and len(result["ids"]) > 0:
        # If the document with the same MD5 exists, unmark it for purge
        copilot_debug(f"The file with md5 {md5} is already indexed. Marking 'purge' as False.")
        collection.update(ids=result["ids"], metadatas=[{"purge": False} for _ in result["metadatas"]])
        return []
    else:
        # If the document with this MD5 doesn't exist, add it as a new document
        document = Document(page_content=file_content, metadata={"md5": md5, "purge": False})
        text_splitter = get_text_splitter(ext, splitter_config)

        # Split the document and add it to the collection
        copilot_debug(f"File with md5 {md5} added to index with 'purge': False.")
        documents: list[Document] = [document]
        if text_splitter and not splitter_config.skip_splitting:
            documents = text_splitter.split_documents(documents)

    return documents


def get_text_splitter(ext, splitter_config: SplitterConfig):
    """
    Returns an appropriate text splitter based on the file extension and optional parameters.

    Args:
        ext (str): The file extension indicating the type of file (e.g., 'md', 'txt', 'pdf', 'json', 'java', 'js', 'py').
        splitter_config (SplitterConfig): Configuration object containing optional parameters such as
            max_chunk_size and chunk_overlap.

    Returns:
        TextSplitter: An instance of a text splitter class appropriate for the given file extension.

    Raises:
        ValueError: If the file extension is unsupported.

    Notes:
        - For Markdown files ('md', 'markdown'), a `MarkdownTextSplitter` is returned.
        - For plain text, PDF, and XML files ('txt', 'pdf', 'xml'), a `CharacterTextSplitter` is returned,
          with optional customization for chunk size and overlap.
        - For JSON files ('json'), a `RecursiveJsonSplitter` is returned.
        - For programming languages like Java, JavaScript, and Python ('java', 'js', 'py'),
          a `RecursiveCharacterTextSplitter` is returned, configured for the respective language.
    """
    if ext in ["md", "markdown"]:
        return MarkdownTextSplitter()
    elif ext in ["txt", "pdf", "xml"]:
        return get_plain_text_splitter(splitter_config)
    elif ext in ["json"]:
        splitter = RecursiveJsonSplitter()
        if splitter_config is not None:
            if splitter_config.max_chunk_size is not None:
                splitter.max_chunk_size = splitter_config.max_chunk_size
        return splitter
    elif ext in ["java"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.JAVA)
    elif ext in ["js"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.JS)
    elif ext in ["py"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.PYTHON)
    else:
        raise ValueError(f"Unsupported file extension: {ext}")


def get_plain_text_splitter(splitter_config):
    """
    Creates and configures a plain text splitter based on the provided configuration.

    Args:
        splitter_config (SplitterConfig): Configuration object containing optional parameters such as
            max_chunk_size and chunk_overlap.

    Returns:
        CharacterTextSplitter: An instance of `CharacterTextSplitter` configured with the specified
        chunk size and overlap, if provided.

    Notes:
        - The `max_chunk_size` parameter determines the maximum size of each text chunk.
        - The `chunk_overlap` parameter specifies the number of overlapping characters between chunks.
    """
    splitter = CharacterTextSplitter()
    if splitter_config is not None:
        if splitter_config.max_chunk_size is not None:
            splitter._chunk_size = splitter_config.max_chunk_size
        if splitter_config.chunk_overlap is not None:
            splitter._chunk_overlap = splitter_config.chunk_overlap
    return splitter


def process_file(file_path, ext):
    """
    Processes a file based on its extension and calculates its SHA-256 hash.

    Args:
        file_path (str): The path to the file to be processed.
        ext (str): The file extension indicating the type of file (e.g., 'pdf', 'txt').

    Returns:
        tuple: A tuple containing:
            - str: The processed content of the file. For PDFs, the extracted text is returned.
                   For other file types, the content is decoded as a UTF-8 string.
            - str: The SHA-256 hash of the file in hexadecimal format.

    Steps:
        1. Calculates the SHA-256 hash of the file using its path.
        2. Reads the file in binary mode.
        3. If the file is a PDF, processes it to extract text content.
        4. For other file types, decodes the binary data as a UTF-8 string.
    """
    sha256 = calculate_sha256_from_file_path(file_path)
    copilot_debug(f"Processing file {file_path} with sha256 {sha256}")
    # Get the document name, which is the file path.
    with open(file_path, "rb") as file:
        file_data = file.read()

    if ext == "pdf":
        return process_pdf(file_data), sha256
    else:
        return file_data.decode("utf-8"), sha256


def calculate_sha256_from_file_path(file_path, chunk_size=4096):
    """
    Calculate the SHA-256 hash of a file given its path.

    :param file_path: Path of the file.
    :param chunk_size: Size of the blocks that will be read. The default value is 4096 bytes.
    :return: SHA-256 hash of the file in hexadecimal format.
    """
    sha256_hash = hashlib.sha256()

    # Open the file in binary mode
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(chunk_size), b""):
            sha256_hash.update(byte_block)

    return sha256_hash.hexdigest()


def process_pdf(pdf_data):
    """
    Processes a PDF file from its binary data and extracts its text content.

    Args:
        pdf_data (bytes): The binary data of the PDF file.

    Returns:
        str: The extracted text content from the PDF file.

    Steps:
        1. Creates a temporary PDF file in the `/tmp` directory with a unique name.
        2. Writes the binary PDF data to the temporary file.
        3. Opens the temporary PDF file using the `pymupdf` library.
        4. Iterates through each page of the PDF and extracts its text content.
        5. Returns the concatenated text content of all pages.
    """
    temp_pdf = "/tmp/temp" + str(round(time.time())) + ".pdf"
    with open(temp_pdf, "wb") as f:
        f.write(pdf_data)
    doc = pymupdf.open(temp_pdf)
    content = ""
    for page in doc:
        content += page.get_text()
    return content


def image_to_base64(image_path):
    """
    Converts an image file to base64 encoding.

    Args:
        image_path (str): Path to the image file.

    Returns:
        str: Base64 encoded string of the image.
    """
    with open(image_path, "rb") as image_file:
        image_binary_data = image_file.read()
        base64_encoded = base64.b64encode(image_binary_data).decode("utf-8")
        return base64_encoded


def calculate_file_md5(file_path, chunk_size=4096):
    """
    Calculates the MD5 hash of a file.

    Args:
        file_path (str): Path to the file.
        chunk_size (int): Size of chunks to read (default: 4096 bytes).

    Returns:
        str: MD5 hash as hexadecimal string.
    """
    md5_hash = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(chunk_size), b""):
            md5_hash.update(chunk)
    return md5_hash.hexdigest()


def index_image_file(image_path, chroma_client, collection_name, agent_id=None):
    """
    Indexes an image file into ChromaDB using CLIP embeddings.

    Args:
        image_path (str): Path to the image file.
        chroma_client: ChromaDB client instance.
        collection_name (str): Name of the collection to store the image.
        agent_id (str, optional): Agent ID for metadata tracking.

    Returns:
        dict: Result with status information.
    """
    try:
        from fastembed import ImageEmbedding

        # Calculate MD5 of the file
        file_md5 = calculate_file_md5(image_path)
        filename = os.path.basename(image_path)

        # Get or create collection
        collection = chroma_client.get_or_create_collection(collection_name)

        # Check if image already exists (by MD5)
        result = collection.get(where={"md5": file_md5})

        if result and len(result["ids"]) > 0:
            # Image already exists, unmark from purge
            copilot_debug(
                f"Image {filename} with MD5 {file_md5[:8]}... already indexed. Unmarking from purge."
            )
            collection.update(
                ids=result["ids"], metadatas=[{"purge": False, "md5": file_md5} for _ in result["ids"]]
            )
            return {"status": "exists", "md5": file_md5, "filename": filename}

        # Load CLIP model for embeddings
        copilot_debug(f"Loading CLIP model for image {filename}...")
        clip_model = ImageEmbedding(model_name="Qdrant/clip-ViT-B-32-vision")

        # Generate embedding
        embedding = list(clip_model.embed([image_path]))[0].tolist()

        # Generate unique ID
        file_id = os.path.splitext(filename)[0]
        existing_ids = collection.get()["ids"]
        if file_id in existing_ids:
            file_id = f"{file_id}_{file_md5[:8]}"

        # Convert image to base64 for storage
        img_b64 = image_to_base64(image_path)

        # Build metadata (convert path to string for ChromaDB compatibility)
        metadata = {
            "path": str(image_path),
            "md5": file_md5,
            "filename": filename,
            "purge": False,
        }
        if agent_id:
            metadata["agent_id"] = agent_id

        # Add to collection
        collection.add(
            embeddings=[embedding],
            ids=[file_id],
            metadatas=[metadata],
            documents=[img_b64],  # Store base64 in documents field
        )

        copilot_debug(f"✓ Added image {filename} to collection {collection_name}")
        return {"status": "added", "md5": file_md5, "filename": filename, "id": file_id}

    except ImportError as e:
        error_msg = f"Required libraries not available for image indexing: {e}"
        copilot_debug(error_msg)
        return {"status": "error", "message": error_msg}
    except Exception as e:
        error_msg = f"Error indexing image {image_path}: {e}"
        copilot_debug(error_msg)
        return {"status": "error", "message": error_msg}


def get_image_collection_name(agent_id):
    """
    Gets the collection name for image references of a specific agent.

    Args:
        agent_id (str): The agent identifier.

    Returns:
        str: Collection name for the agent's image references.
    """
    return f"AGENT_{agent_id}_IMAGES"


def find_similar_reference(
    first_image_path,
    agent_id,
    similarity_threshold=None,
    ignore_env_threshold: bool = False,
):
    """
    Finds the most similar reference image in the agent's ChromaDB vector database.

    Args:
        first_image_path (str): Path to the first page/image of the document to process.
        agent_id (str): Agent identifier to get the correct database and collection.
        similarity_threshold (float, optional): Minimum similarity score to accept a reference.
                            ChromaDB uses distance metrics, so lower distance = higher similarity.
                            Default is None (use env var or no threshold).
                            Recommended: 0.15-0.30 for L2 distance.

    Returns:
        Tuple of (reference_image_path, image_base64_or_None):
        - reference_image_path: Path to the reference image file, or None if not found
        - image_base64_or_None: Base64 string if stored in ChromaDB, None otherwise
    """
    try:
        from fastembed import ImageEmbedding

        if not agent_id:
            copilot_debug("No agent_id provided, cannot search for reference")
            return None, None

        # Get database path from agent_id and use IMAGES collection for image references
        db_path = get_vector_db_path("KB_" + agent_id)
        collection_name = IMAGES_COLLECTION_NAME

        similarity_threshold = get_sim_threshold_with_ignore(
            similarity_threshold, ignore_env=ignore_env_threshold
        )

        copilot_debug(f"Searching for reference in agent {agent_id}, collection: {collection_name}")
        if similarity_threshold is not None:
            copilot_debug(f"Using similarity threshold: {similarity_threshold}")

        # Load CLIP model for embeddings
        clip_model = ImageEmbedding(model_name="Qdrant/clip-ViT-B-32-vision")

        # Open the query image
        query_embedding = list(clip_model.embed([first_image_path]))[0].tolist()

        # Connect to ChromaDB
        chroma_client = chromadb.Client(settings=get_chroma_settings(db_path))

        try:
            reference_collection = chroma_client.get_collection(collection_name)
        except Exception as e:
            copilot_debug(f"Reference collection '{collection_name}' not found: {e}")
            return None, None

        # Query for most similar reference
        results = reference_collection.query(query_embeddings=[query_embedding], n_results=1)

        return filter_by_distance(results, similarity_threshold)

    except ImportError as e:
        copilot_debug(f"Required libraries not available for reference search: {e}")
        return None, None
    except Exception as e:
        copilot_debug(f"Error finding similar reference: {e}")
        return None, None


def filter_by_distance(results, similarity_threshold):
    """
    Filters search results based on similarity distance threshold.

    Args:
        results (dict): ChromaDB query results containing ids, distances, metadatas, and documents.
        similarity_threshold (float, optional): Maximum acceptable L2 distance for similarity match.
                                               Lower values mean stricter matching.

    Returns:
        tuple: (reference_image_path, reference_image_base64) or (None, None) if no match.
            - reference_image_path (str): File path to the reference image.
            - reference_image_base64 (str): Base64 encoded image data if stored in ChromaDB.

    Notes:
        - Uses L2 distance metric where lower values indicate higher similarity.
        - If threshold is set and distance exceeds it, returns (None, None).
        - Logs detailed information about the matching process.
    """
    debug_metadata(results)
    if not results["ids"] or not results["ids"][0]:
        copilot_debug("No reference found in database.")
        return None, None

    # Get distance/similarity score
    distance = results["distances"][0][0] if results.get("distances") and results["distances"][0] else None
    copilot_debug(f"Distance from reference search: {results.get('distances', 'N/A')}")
    ref_metadata = results["metadatas"][0][0]
    reference_image_path = ref_metadata.get("path")
    # Get base64 from documents instead of metadata
    reference_image_base64 = (
        results["documents"][0][0] if results.get("documents") and results["documents"][0] else None
    )

    # Check if distance is available and threshold is set
    if similarity_threshold is not None and distance is not None:
        # For L2 distance: lower is better (more similar)
        # Threshold represents maximum acceptable distance
        if distance > similarity_threshold:
            copilot_debug(
                f"Reference found but similarity too low: distance={distance:.4f} > threshold={similarity_threshold}. "
                f"Reference: {reference_image_path}"
            )
            copilot_debug("No suitable reference found, will use standard OCR without reference")
            return None, None
        else:
            copilot_debug(
                f"Using similar reference: {reference_image_path} "
                f"(distance={distance:.4f}, threshold={similarity_threshold})"
            )
    else:
        copilot_debug(f"Using most similar reference: {reference_image_path}")
        if distance is not None:
            copilot_debug(f"Similarity distance: {distance:.4f}")

    log_image_origin(reference_image_base64)

    return reference_image_path, reference_image_base64


def log_image_origin(reference_image_base64):
    """
    Logs the storage location of a reference image for debugging purposes.

    Args:
        reference_image_base64 (str, optional): Base64 encoded image data, or None if not stored in database.

    Notes:
        - If base64 data is present, logs that image was retrieved from ChromaDB.
        - If base64 data is None, logs that image will be read from disk.
    """
    # Log if image is stored in ChromaDB
    if reference_image_base64:
        copilot_debug("Reference image retrieved from ChromaDB (base64 available)")
    else:
        copilot_debug("Reference image will be read from disk")


def debug_metadata(results):
    """
    Logs detailed metadata from ChromaDB search results for debugging.

    Args:
        results (dict): ChromaDB query results containing ids and metadatas.

    Notes:
        - Only logs when debug mode is enabled.
        - Iterates through all result IDs and their associated metadata.
        - Useful for troubleshooting vector search operations.
    """
    copilot_debug("Reference search results:")
    if is_debug_enabled() and results["ids"] and results["ids"][0]:
        for i, result_id in enumerate(results["ids"][0]):
            copilot_debug(f"  ID: {result_id}")
            if results["metadatas"] and results["metadatas"][0]:
                copilot_debug(f"  Metadata: {results['metadatas'][0][i]}")


def get_sim_threshold(similarity_threshold):
    """
    Retrieves similarity threshold from environment variable if not explicitly provided.

    Args:
        similarity_threshold (float, optional): Explicitly provided threshold value.

    Returns:
        float or None: Similarity threshold value, or None if not set or invalid.

    Notes:
        - If similarity_threshold is provided, returns it unchanged.
        - If None, attempts to read from COPILOT_REFERENCE_SIMILARITY_THRESHOLD env var.
        - Validates that the environment variable contains a valid float value.
        - Returns None if the environment variable contains an invalid value.
    """
    return get_sim_threshold_with_ignore(similarity_threshold, ignore_env=False)


def get_sim_threshold_with_ignore(similarity_threshold, ignore_env: bool = False):
    """
    Retrieves similarity threshold from environment variable if not explicitly provided,
    unless ignore_env is True.

    Args:
        similarity_threshold (float|None): Explicit threshold provided.
        ignore_env (bool): If True, do not read environment variable and keep similarity_threshold as-is.

    Returns:
        float or None: similarity threshold or None.
    """
    # If caller wants to ignore environment variable, just return provided value
    if ignore_env:
        return similarity_threshold

    # Get similarity threshold from env var if not provided
    if similarity_threshold is None:
        threshold_str = read_optional_env_var("COPILOT_REFERENCE_SIMILARITY_THRESHOLD", None)
        if threshold_str:
            try:
                similarity_threshold = float(threshold_str)
            except ValueError:
                copilot_debug(f"Invalid similarity threshold value: {threshold_str}, ignoring")
                similarity_threshold = None
    return similarity_threshold
