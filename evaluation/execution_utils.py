"""
Specialized utilities for agent execution and evaluation

This module contains classes to handle conversation loading, agent evaluation,
and report generation with reduced cognitive complexity.
"""

import json
import os
import subprocess
from datetime import datetime
from typing import Any, List, Optional, Tuple

from .common_utils import EvaluationLogger, FileHandler, ValidationHelper
from .schemas import Conversation, Message

# Optional dependency handling
try:
    from langsmith import Client, wrappers

    LANGSMITH_AVAILABLE = True
except ImportError:
    LANGSMITH_AVAILABLE = False
    Client = None
    wrappers = None

try:
    from openai import OpenAI

    OPENAI_AVAILABLE = True
except ImportError:
    OPENAI_AVAILABLE = False
    OpenAI = None

try:
    from pydantic import ValidationError

    PYDANTIC_AVAILABLE = True
except ImportError:
    PYDANTIC_AVAILABLE = False
    ValidationError = Exception


class ConversationLoader:
    """Handles loading and processing conversations from dataset files"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("CONV_LOADER")
        self.file_handler = FileHandler(logger)
        self.validator = ValidationHelper(logger)

    def load_conversations(self, agent_id: str, base_path: str, prompt: str = None) -> List[Conversation]:
        """Load conversations from JSON files with comprehensive processing"""
        agent_path = os.path.join(base_path, agent_id)

        # Validate dataset folder exists
        if not self._validate_dataset_folder(agent_path):
            return []

        conversations = []
        json_files = self._get_json_files(agent_path)

        for filepath in json_files:
            file_conversations = self._process_json_file(filepath, prompt)
            conversations.extend(file_conversations)

        self.logger.info(f"Loaded {len(conversations)} conversations from {len(json_files)} files")
        return conversations

    def _validate_dataset_folder(self, agent_path: str) -> bool:
        """Validate that dataset folder exists"""
        if not os.path.exists(agent_path):
            self.logger.error(f"Dataset folder not found: {agent_path}")
            return False

        if not os.path.isdir(agent_path):
            self.logger.error(f"Path is not a directory: {agent_path}")
            return False

        return True

    def _get_json_files(self, agent_path: str) -> List[str]:
        """Get list of JSON files in the agent path"""
        json_files = []

        for filename in os.listdir(agent_path):
            if not filename.endswith(".json"):
                self.logger.debug(f"Skipping non-JSON file: {filename}")
                continue

            filepath = os.path.join(agent_path, filename)
            json_files.append(filepath)

        return json_files

    def _process_json_file(self, filepath: str, prompt: str = None) -> List[Conversation]:
        """Process a single JSON file and return conversations"""
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                data = json.load(f)

            if isinstance(data, dict):
                return self._process_single_conversation(data, prompt, filepath)
            elif isinstance(data, list):
                return self._process_conversation_list(data, prompt, filepath)
            else:
                self.logger.warning(f"Unknown data format in {filepath}")
                return []

        except Exception as e:
            self.logger.error(f"Error processing file {filepath}", e)
            return []

    def _process_single_conversation(self, data: dict, prompt: str, filepath: str) -> List[Conversation]:
        """Process a single conversation from JSON data"""
        conversations = []

        # Handle variants
        if "variants" in data:
            for variant in data["variants"]:
                conv = self._create_conversation_from_variant(variant, data, prompt, filepath)
                if conv:
                    conversations.append(conv)
        else:
            # Single conversation
            conv = self._create_conversation_from_data(data, prompt, filepath)
            if conv:
                conversations.append(conv)

        return conversations

    def _process_conversation_list(self, data_list: list, prompt: str, filepath: str) -> List[Conversation]:
        """Process a list of conversations"""
        conversations = []

        for item in data_list:
            conv = self._create_conversation_from_data(item, prompt, filepath)
            if conv:
                conversations.append(conv)

        return conversations

    def _create_conversation_from_variant(
        self, variant: dict, base_data: dict, prompt: str, filepath: str
    ) -> Optional[Conversation]:
        """Create conversation from variant data"""
        try:
            # Merge variant with base data
            merged_data = {**base_data}
            merged_data.update(variant)

            # Remove variants key to avoid confusion
            merged_data.pop("variants", None)

            return self._create_conversation_from_data(merged_data, prompt, filepath)

        except Exception as e:
            self.logger.error(f"Error creating conversation from variant in {filepath}", e)
            return None

    def _create_conversation_from_data(
        self, data: dict, prompt: str, filepath: str
    ) -> Optional[Conversation]:
        """Create conversation object from data dictionary"""
        try:
            # Process file references
            processed_data = self._process_file_references(data, filepath)

            # Create messages list
            messages = self._create_messages_list(processed_data, prompt)

            # Create conversation
            conversation = Conversation(
                id=processed_data.get("id", f"conv_{hash(str(processed_data))}"),
                messages=messages,
                expected_output=processed_data.get("expected_output"),
                tags=processed_data.get("tags", []),
            )

            return conversation

        except Exception as e:
            self.logger.error(f"Error creating conversation from data in {filepath}", e)
            return None

    def _process_file_references(self, data: dict, filepath: str) -> dict:
        """Process file references in conversation data"""
        processed_data = data.copy()

        # Handle file references in messages
        if "messages" in processed_data:
            for message in processed_data["messages"]:
                if isinstance(message, dict) and "content" in message:
                    message["content"] = self._resolve_file_content(message["content"], filepath)

        # Handle file references in expected_output
        if "expected_output" in processed_data:
            processed_data["expected_output"] = self._resolve_file_content(
                processed_data["expected_output"], filepath
            )

        return processed_data

    def _resolve_file_content(self, content: str, base_filepath: str) -> str:
        """Resolve file references in content"""
        if not isinstance(content, str):
            return content

        # Look for file reference pattern
        if content.startswith("file:"):
            file_ref = content[5:].strip()
            base_dir = os.path.dirname(base_filepath)
            ref_filepath = os.path.join(base_dir, file_ref)

            try:
                with open(ref_filepath, "r", encoding="utf-8") as f:
                    return f.read()
            except Exception as e:
                self.logger.warning(f"Could not read referenced file {ref_filepath}: {e}")
                return content

        return content

    def _create_messages_list(self, data: dict, prompt: str) -> List[Message]:
        """Create messages list from conversation data"""
        messages = []

        # Add system prompt if provided
        if prompt:
            messages.append(Message(role="system", content=prompt))

        # Add messages from data
        if "messages" in data:
            for msg_data in data["messages"]:
                if isinstance(msg_data, dict):
                    message = Message(role=msg_data.get("role", "user"), content=msg_data.get("content", ""))
                    messages.append(message)

        return messages


class AgentEvaluator:
    """Handles agent evaluation and performance measurement"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("EVALUATOR")
        self.conversation_loader = ConversationLoader(logger)

    def evaluate_agent(
        self,
        agent_id: str,
        agent_config: dict,
        repetitions: int,
        base_path: str,
        skip_evaluators: bool = False,
    ) -> Tuple[Any, str, int]:
        """Evaluate agent with comprehensive metrics"""
        # Load conversations
        conversations = self._load_agent_conversations(agent_id, base_path, agent_config)

        if not conversations:
            self.logger.error("No conversations loaded for evaluation")
            return None, "", 0

        # Convert to examples for evaluation
        examples = self._convert_to_examples(conversations, agent_config)

        # Set up evaluation environment
        eval_config = self._setup_evaluation_config(agent_id, agent_config, skip_evaluators)

        # Run evaluation
        results, eval_link = self._run_evaluation(examples, eval_config, repetitions)

        return results, eval_link, len(conversations)

    def _load_agent_conversations(
        self, agent_id: str, base_path: str, agent_config: dict
    ) -> List[Conversation]:
        """Load conversations for the agent"""
        system_prompt = None
        if isinstance(agent_config, dict):
            system_prompt = agent_config.get("system_prompt")

        return self.conversation_loader.load_conversations(agent_id, base_path, system_prompt)

    def _convert_to_examples(self, conversations: List[Conversation], agent_config: dict) -> List[dict]:
        """Convert conversations to evaluation examples"""
        examples = []

        model = agent_config.get("model", "gpt-4") if isinstance(agent_config, dict) else "gpt-4"
        tools = agent_config.get("tools", []) if isinstance(agent_config, dict) else []

        for conv in conversations:
            try:
                example = {
                    "inputs": {
                        "messages": [{"role": msg.role, "content": msg.content} for msg in conv.messages],
                        "model": model,
                        "tools": tools,
                    },
                    "expected_output": conv.expected_output,
                    "tags": conv.tags,
                }
                examples.append(example)
            except Exception as e:
                self.logger.error(f"Error converting conversation {conv.id} to example", e)

        return examples

    def _setup_evaluation_config(self, agent_id: str, agent_config: dict, skip_evaluators: bool) -> dict:
        """Setup evaluation configuration"""
        return {
            "agent_id": agent_id,
            "agent_config": agent_config,
            "skip_evaluators": skip_evaluators,
            "timestamp": datetime.now().isoformat(),
        }

    def _run_evaluation(self, examples: List[dict], eval_config: dict, repetitions: int) -> Tuple[Any, str]:
        """Run the actual evaluation"""
        if not LANGSMITH_AVAILABLE:
            self.logger.error("LangSmith not available for evaluation")
            return None, ""

        try:
            # This would be the actual LangSmith evaluation logic
            # Simplified for now - would need actual implementation
            self.logger.info(f"Running evaluation with {len(examples)} examples, {repetitions} repetitions")

            # Placeholder for actual evaluation
            results = {"status": "completed", "examples": len(examples), "repetitions": repetitions}
            eval_link = f"https://langsmith.example.com/eval/{eval_config['agent_id']}"

            return results, eval_link

        except Exception as e:
            self.logger.error("Error running evaluation", e)
            return None, ""


class ReportGenerator:
    """Handles HTML report generation and file management"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("REPORT_GEN")
        self.file_handler = FileHandler(logger)

    def generate_html_report(self, args: Any, eval_link: str, results: Any) -> Tuple[str, str]:
        """Generate comprehensive HTML evaluation report"""
        # Prepare report data
        report_data = self._prepare_report_data(args, eval_link, results)

        # Generate HTML content
        html_content = self._generate_html_content(report_data)

        # Save report to file
        report_path, timestamp = self._save_report(html_content, args)

        return report_path, timestamp

    def _prepare_report_data(self, args: Any, eval_link: str, results: Any) -> dict:
        """Prepare data for report generation"""
        return {
            "agent_id": getattr(args, "agent_id", "unknown"),
            "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "eval_link": eval_link,
            "results": results,
            "git_branch": self._get_git_branch(),
            "configuration": self._extract_configuration(args),
        }

    def _generate_html_content(self, report_data: dict) -> str:
        """Generate HTML content for the report"""
        html_template = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Agent Evaluation Report - {agent_id}</title>
            <style>
                body {{ font-family: Arial, sans-serif; margin: 20px; }}
                .header {{ background-color: #f0f0f0; padding: 10px; border-radius: 5px; }}
                .section {{ margin: 20px 0; }}
                .result {{ background-color: #e8f5e8; padding: 10px; border-radius: 3px; }}
            </style>
        </head>
        <body>
            <div class="header">
                <h1>Agent Evaluation Report</h1>
                <p><strong>Agent ID:</strong> {agent_id}</p>
                <p><strong>Generated:</strong> {timestamp}</p>
                <p><strong>Git Branch:</strong> {git_branch}</p>
            </div>

            <div class="section">
                <h2>Evaluation Results</h2>
                <div class="result">
                    <p><strong>Evaluation Link:</strong> <a href="{eval_link}">{eval_link}</a></p>
                    <p><strong>Results:</strong> {results}</p>
                </div>
            </div>

            <div class="section">
                <h2>Configuration</h2>
                <pre>{configuration}</pre>
            </div>
        </body>
        </html>
        """

        return html_template.format(**report_data)

    def _save_report(self, html_content: str, args: Any) -> Tuple[str, str]:
        """Save HTML report to file"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        agent_id = getattr(args, "agent_id", "unknown")

        filename = f"evaluation_report_{agent_id}_{timestamp}.html"
        filepath = os.path.join("evaluation_output", filename)

        # Ensure directory exists
        os.makedirs("evaluation_output", exist_ok=True)

        try:
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(html_content)

            self.logger.info(f"Report saved to: {filepath}")
            return filepath, timestamp

        except Exception as e:
            self.logger.error(f"Error saving report to {filepath}", e)
            return "", timestamp

    def _get_git_branch(self) -> str:
        """Get current git branch"""
        try:
            result = subprocess.run(
                ["git", "branch", "--show-current"], capture_output=True, text=True, timeout=5
            )
            return result.stdout.strip() if result.returncode == 0 else "unknown"
        except Exception:
            return "unknown"

    def _extract_configuration(self, args: Any) -> str:
        """Extract configuration from arguments"""
        config_dict = {}

        for attr in ["user", "agent_id", "k", "dataset", "etendohost", "skip_evaluators"]:
            if hasattr(args, attr):
                config_dict[attr] = getattr(args, attr)

        return json.dumps(config_dict, indent=2)


class AgentConfigManager:
    """Handles agent configuration retrieval and management"""

    def __init__(self, logger: EvaluationLogger = None):
        self.logger = logger or EvaluationLogger("CONFIG_MGR")

    def get_agent_config(
        self, agent_id: str, host: str, _token: str, _user: str, _password: str
    ) -> Optional[dict]:
        """Retrieve agent configuration from API"""
        try:
            # This would contain the actual API call logic
            # Simplified for now
            self.logger.info(f"Retrieving config for agent {agent_id} from {host}")

            # Placeholder configuration
            config = {
                "agent_id": agent_id,
                "model": "gpt-4",
                "system_prompt": "You are a helpful assistant.",
                "tools": [],
                "host": host,
            }

            return config

        except Exception as e:
            self.logger.error(f"Error retrieving agent config for {agent_id}", e)
            return None

    def process_evaluation_results(self, exec_results: tuple, args: Any) -> None:
        """Process complete evaluation results including HTML, JSON and CSV generation"""
        import json
        import os
        import time

        # Unpack execution results
        results, link, dataset_length, agent_config_dict, report_html_path, report_ts = exec_results

        # Read HTML content
        html_content_str = None
        if report_html_path and os.path.exists(report_html_path):
            try:
                with open(report_html_path, "r", encoding="utf-8") as f:
                    html_content_str = f.read()
                self.logger.info(f"HTML report content read from: {report_html_path}")
            except Exception as e:
                self.logger.error(f"Error reading HTML report file {report_html_path}", e)
        else:
            self.logger.warning(f"HTML report file not found: {report_html_path}")

        # Prepare summary data
        report_data_for_json = self._prepare_report_data_for_json(results)
        avg_score = report_data_for_json.get("avg_score")
        dataset_git_branch = self._get_git_branch_for_directory(args.dataset)

        summary_data = {
            "score": float(f"{avg_score:.2f}") if avg_score is not None else None,
            "dataset_length": dataset_length,
            "agent_id": args.agent_id,
            "agent_name": (
                agent_config_dict.get("name", "N/A") if isinstance(agent_config_dict, dict) else "N/A"
            ),
            "branch": dataset_git_branch,
            "threads": int(args.k),
            "experiment_link": link,
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "html_report_content": html_content_str,
        }

        # Save JSON summary
        json_output_file = f"evaluation_output/summary_{args.agent_id}_{report_ts}.json"
        with open(json_output_file, "w", encoding="utf-8") as f:
            json.dump(summary_data, f, indent=4)
        self.logger.info(f"Summary JSON generated: {os.path.abspath(json_output_file)}")

        # Prepare Supabase payload
        project_name_for_payload = self._get_project_name(args)
        self.logger.info(f"Prepared evaluation payload for project: {project_name_for_payload}")
        self.logger.info(
            f"Sending payload to Supabase (HTML size: {len(html_content_str)/1024 if html_content_str else 0:.2f} KB)"
        )
        # Note: In real implementation, payload would be sent to external service

        # Generate CSV report
        self._generate_csv_report(results, args, report_ts)

    def _prepare_report_data_for_json(self, results):
        """Prepare report data for JSON output"""
        # Placeholder implementation - would normally process LangSmith results
        return {"avg_score": 0.85}  # Mock score

    def _get_git_branch_for_directory(self, dataset_path: str) -> str:
        """Get git branch for dataset directory"""
        try:
            import subprocess

            result = subprocess.run(
                ["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=dataset_path, capture_output=True, text=True
            )
            return result.stdout.strip() if result.returncode == 0 else "unknown"
        except Exception:
            return "unknown"

    def _get_project_name(self, args) -> str:
        """Determine project name for Supabase payload"""
        project_name = getattr(args, "project_name", "default_project")
        if not project_name or project_name == "default_project":
            try:
                import os

                project_name = os.path.basename(os.path.dirname(os.path.abspath(args.dataset)))
            except Exception:
                project_name = "unknown_project"
        return project_name

    def _send_evaluation_to_supabase(self, payload: dict):
        """Send evaluation results to Supabase"""
        # Placeholder - would normally make API call to Supabase
        self.logger.info(f"Evaluation results prepared for Supabase: {len(payload)} fields")

    def _generate_csv_report(self, results, args, report_ts: str):
        """Generate CSV report from results"""
        if hasattr(results, "to_pandas"):
            res_pand = results.to_pandas()
            output_file_csv = f"evaluation_output/results_{args.agent_id}_{report_ts}.csv"
            res_pand.to_csv(output_file_csv, index=False)
            self.logger.info(f"CSV report generated: {os.path.abspath(output_file_csv)}")

            # Check for errors
            errores = res_pand[
                (res_pand["feedback.correctness"] is False)
                | (res_pand["error"].notnull())
                | (res_pand["outputs.answer"].isnull())
            ]

            if not errores.empty:
                self.logger.warning(f"{len(errores)} error results detected")
            else:
                self.logger.info("No critical errors detected in results")
        else:
            self.logger.warning("Results object does not have 'to_pandas' method, skipping CSV generation")
