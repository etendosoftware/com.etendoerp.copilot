"""
Common utilities for evaluation package

This module contains shared utilities to reduce code duplication and cognitive complexity
across evaluation scripts (execute.py, bulk_tasks_eval.py, gen_variants.py).
"""

import argparse
import json
import os
import sys
import traceback
from datetime import datetime
from typing import Any, Callable, Dict, List, Optional, Union

import pandas as pd
from dotenv import load_dotenv


class EvaluationLogger:
    """Centralized logging for evaluation scripts"""

    def __init__(self, prefix: str = "EVAL"):
        self.prefix = prefix

    def info(self, message: str):
        """Log info message"""
        print(f"[{self.prefix}] INFO: {message}")

    def warning(self, message: str):
        """Log warning message"""
        print(f"[{self.prefix}] WARNING: {message}")

    def error(self, message: str, exception: Exception = None):
        """Log error message with optional exception"""
        print(f"[{self.prefix}] ERROR: {message}")
        if exception:
            print(f"[{self.prefix}] Exception details: {str(exception)}")
            traceback.print_exc()

    def critical(self, message: str, exception: Exception = None):
        """Log critical error and exit"""
        print(f"[{self.prefix}] CRITICAL ERROR: {message}")
        if exception:
            print(f"[{self.prefix}] Exception details: {str(exception)}")
            traceback.print_exc()
        sys.exit(1)


class ConfigManager:
    """Manages configuration loading and validation"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("CONFIG")
        self._config = {}

    def load_env_file(self, env_file: Optional[str] = None) -> Dict[str, str]:
        """Load environment variables from file"""
        if env_file and os.path.exists(env_file):
            load_dotenv(env_file)
            self.logger.info(f"Loaded environment from: {env_file}")
        elif env_file:
            self.logger.warning(f"Environment file not found: {env_file}")
        else:
            load_dotenv()

        return dict(os.environ)

    def validate_required_env_vars(self, required_vars: List[str]) -> bool:
        """Validate that required environment variables are present"""
        missing_vars = []
        for var in required_vars:
            if not os.getenv(var):
                missing_vars.append(var)

        if missing_vars:
            self.logger.error(f"Missing required environment variables: {missing_vars}")
            return False

        return True

    def get_db_config(self) -> Dict[str, str]:
        """Get database configuration from environment"""
        return {
            "dbname": os.getenv("DB_NAME", "copilot_test"),
            "user": os.getenv("DB_USER", "postgres"),
            "password": os.getenv("DB_PASSWORD", "syspass"),
            "host": os.getenv("DB_HOST", "localhost"),
            "port": os.getenv("DB_PORT", "5432"),
        }


class FileHandler:
    """Handles file operations with error handling"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("FILE")

    def load_csv(self, csv_path: str, **kwargs) -> Optional[pd.DataFrame]:
        """Load CSV file with error handling"""
        try:
            if not os.path.exists(csv_path):
                self.logger.error(f"CSV file not found: {csv_path}")
                return None

            df = pd.read_csv(csv_path, **kwargs)
            self.logger.info(f"Loaded CSV with {len(df)} rows from: {csv_path}")
            return df

        except Exception as e:
            self.logger.error(f"Failed to load CSV file: {csv_path}", e)
            return None

    def load_json(self, json_path: str) -> Optional[Union[Dict, List]]:
        """Load JSON file with error handling"""
        try:
            if not os.path.exists(json_path):
                self.logger.error(f"JSON file not found: {json_path}")
                return None

            with open(json_path, "r", encoding="utf-8") as f:
                data = json.load(f)

            self.logger.info(f"Loaded JSON from: {json_path}")
            return data

        except Exception as e:
            self.logger.error(f"Failed to load JSON file: {json_path}", e)
            return None

    def save_json(self, data: Union[Dict, List], json_path: str, **kwargs) -> bool:
        """Save data to JSON file with error handling"""
        try:
            # Create directory if it doesn't exist
            os.makedirs(os.path.dirname(json_path), exist_ok=True)

            with open(json_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False, **kwargs)

            self.logger.info(f"Saved JSON to: {json_path}")
            return True

        except Exception as e:
            self.logger.error(f"Failed to save JSON file: {json_path}", e)
            return False

    def load_text_lines(self, file_path: str) -> Optional[List[str]]:
        """Load text file as list of lines"""
        try:
            if not os.path.exists(file_path):
                self.logger.error(f"Text file not found: {file_path}")
                return None

            with open(file_path, "r", encoding="utf-8") as f:
                lines = [line.strip() for line in f.readlines() if line.strip()]

            self.logger.info(f"Loaded {len(lines)} lines from: {file_path}")
            return lines

        except Exception as e:
            self.logger.error(f"Failed to load text file: {file_path}", e)
            return None

    def save_text_lines(self, lines: List[str], file_path: str) -> bool:
        """Save list of lines to text file"""
        try:
            os.makedirs(os.path.dirname(file_path), exist_ok=True)

            with open(file_path, "w", encoding="utf-8") as f:
                for line in lines:
                    f.write(line + "\n")

            self.logger.info(f"Saved {len(lines)} lines to: {file_path}")
            return True

        except Exception as e:
            self.logger.error(f"Failed to save text file: {file_path}", e)
            return False


class ArgumentParser:
    """Enhanced argument parser with common options"""

    def __init__(self, description: str):
        self.parser = argparse.ArgumentParser(
            description=description, formatter_class=argparse.ArgumentDefaultsHelpFormatter
        )
        self._add_common_arguments()

    def _add_common_arguments(self):
        """Add common arguments used across evaluation scripts"""
        self.parser.add_argument("--envfile", type=str, help="Path to environment file")
        self.parser.add_argument(
            "--output-dir", type=str, default="./output", help="Output directory for generated files"
        )
        self.parser.add_argument("--verbose", action="store_true", help="Enable verbose logging")
        self.parser.add_argument(
            "--dry-run", action="store_true", help="Perform dry run without actual execution"
        )

    def add_csv_arguments(self):
        """Add CSV-related arguments"""
        self.parser.add_argument("--csv", type=str, required=True, help="Path to input CSV file")
        return self

    def add_template_arguments(self):
        """Add template-related arguments"""
        self.parser.add_argument("--templates", type=str, help="Path to template file")
        self.parser.add_argument(
            "--num-templates", type=int, default=20, help="Number of templates to generate/use"
        )
        return self

    def add_database_arguments(self):
        """Add database-related arguments"""
        self.parser.add_argument("--table", type=str, default="m_product", help="Database table to monitor")
        return self

    def add_etendo_arguments(self):
        """Add Etendo-related arguments"""
        self.parser.add_argument("--etendo-url", type=str, help="Etendo base URL")
        self.parser.add_argument("--user", type=str, help="Etendo username")
        self.parser.add_argument("--password", type=str, help="Etendo password")
        return self

    def add_agent_arguments(self):
        """Add agent-related arguments"""
        self.parser.add_argument("--agent-id", type=str, required=True, help="Agent ID for evaluation")
        self.parser.add_argument("--dataset", type=str, help="Path to dataset directory")
        self.parser.add_argument("-k", type=int, default=5, help="Number of repetitions for evaluation")
        return self

    def parse(self) -> argparse.Namespace:
        """Parse arguments and return namespace"""
        return self.parser.parse_args()


class ValidationHelper:
    """Helper for common validation tasks"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("VALIDATION")

    def validate_file_exists(self, file_path: str, file_type: str = "file") -> bool:
        """Validate that a file exists"""
        if not os.path.exists(file_path):
            self.logger.error(f"{file_type} not found: {file_path}")
            return False
        return True

    def validate_directory_exists(self, dir_path: str, create_if_missing: bool = False) -> bool:
        """Validate that a directory exists, optionally create it"""
        if not os.path.exists(dir_path):
            if create_if_missing:
                try:
                    os.makedirs(dir_path, exist_ok=True)
                    self.logger.info(f"Created directory: {dir_path}")
                    return True
                except Exception as e:
                    self.logger.error(f"Failed to create directory: {dir_path}", e)
                    return False
            else:
                self.logger.error(f"Directory not found: {dir_path}")
                return False
        return True

    def validate_csv_columns(self, df: pd.DataFrame, required_columns: List[str]) -> bool:
        """Validate that CSV has required columns"""
        missing_columns = [col for col in required_columns if col not in df.columns]
        if missing_columns:
            self.logger.error(f"Missing required CSV columns: {missing_columns}")
            return False
        return True

    def validate_positive_integer(self, value: Any, name: str) -> bool:
        """Validate that value is a positive integer"""
        try:
            int_value = int(value)
            if int_value <= 0:
                self.logger.error(f"{name} must be a positive integer, got: {value}")
                return False
            return True
        except (ValueError, TypeError):
            self.logger.error(f"{name} must be an integer, got: {value}")
            return False


class ProgressTracker:
    """Simple progress tracking for long operations"""

    def __init__(self, total: int, description: str = "Processing"):
        self.total = total
        self.current = 0
        self.description = description
        self.start_time = datetime.now()

    def update(self, step: int = 1):
        """Update progress"""
        self.current += step
        percentage = (self.current / self.total) * 100 if self.total > 0 else 0

        elapsed = datetime.now() - self.start_time
        if self.current > 0:
            eta = elapsed * (self.total - self.current) / self.current
            eta_str = str(eta).split(".")[0]  # Remove microseconds
        else:
            eta_str = "Unknown"

        print(
            f"\r{self.description}: {self.current}/{self.total} ({percentage:.1f}%) - ETA: {eta_str}", end=""
        )

        if self.current >= self.total:
            print()  # New line when complete

    def finish(self):
        """Mark progress as finished"""
        elapsed = datetime.now() - self.start_time
        print(f"\n{self.description} completed in {elapsed}")


def safe_execute(func: Callable, *args, logger: EvaluationLogger = None, **kwargs) -> tuple[bool, Any]:
    """
    Safely execute a function with error handling

    Returns:
        tuple[bool, Any]: (success, result) where success indicates if execution succeeded
    """
    if logger is None:
        logger = EvaluationLogger("SAFE_EXEC")

    try:
        result = func(*args, **kwargs)
        return True, result
    except Exception as e:
        logger.error(f"Error executing {func.__name__}", e)
        return False, None


def batch_process(
    items: List[Any], process_func: Callable, batch_size: int = 100, description: str = "Processing"
) -> List[Any]:
    """
    Process items in batches with progress tracking

    Args:
        items: List of items to process
        process_func: Function to apply to each item
        batch_size: Size of each batch
        description: Description for progress tracking

    Returns:
        List of processed results
    """
    results = []
    progress = ProgressTracker(len(items), description)

    for i in range(0, len(items), batch_size):
        batch = items[i : i + batch_size]
        batch_results = []

        for item in batch:
            success, result = safe_execute(process_func, item)
            if success:
                batch_results.append(result)
            progress.update()

        results.extend(batch_results)

    progress.finish()
    return results


def create_output_structure(base_dir: str, subdirs: List[str] = None) -> Dict[str, str]:
    """
    Create standard output directory structure

    Args:
        base_dir: Base output directory
        subdirs: Additional subdirectories to create

    Returns:
        Dict mapping directory names to paths
    """
    default_subdirs = ["reports", "data", "logs", "temp"]
    if subdirs:
        default_subdirs.extend(subdirs)

    paths = {"base": base_dir}

    for subdir in default_subdirs:
        subdir_path = os.path.join(base_dir, subdir)
        os.makedirs(subdir_path, exist_ok=True)
        paths[subdir] = subdir_path

    return paths


# Common constants
DEFAULT_CONFIGS = {
    "CSV_ENCODING": "utf-8",
    "JSON_INDENT": 2,
    "BATCH_SIZE": 100,
    "MAX_RETRIES": 3,
    "TIMEOUT_SECONDS": 300,
}
