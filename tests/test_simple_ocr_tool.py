import base64
import os
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from pydantic import BaseModel, ValidationError

from tools.SimpleOcrTool import (
    DEFAULT_MODEL,
    DEFAULT_OCR_TEMPERATURE,
    DEFAULT_PROVIDER,
    SIMPLE_OCR_TOOL_ID,
    SUPPORTED_MIME_TYPES,
    SYSTEM_PROMPT,
    SimpleOcrTool,
    SimpleOcrToolInput,
    _call_gemini,
    _call_openai,
    _get_model_config,
    _is_gemini,
    _model_id,
    _read_mime,
    _resolve_file_path,
    _structured_schema_dict,
    _validate_mime,
)


class _FakeSchema(BaseModel):
    value: str


class TestSimpleOcrToolInput(unittest.TestCase):
    def test_valid_input_without_structured_output(self):
        inp = SimpleOcrToolInput(path="/x.pdf", question="Extract")
        self.assertEqual(inp.path, "/x.pdf")
        self.assertEqual(inp.question, "Extract")
        self.assertIsNone(inp.structured_output)

    def test_valid_input_with_structured_output(self):
        inp = SimpleOcrToolInput(
            path="/x.pdf", question="Extract", structured_output="Invoice"
        )
        self.assertEqual(inp.structured_output, "Invoice")

    def test_structured_output_accepts_none_explicitly(self):
        inp = SimpleOcrToolInput(
            path="/x.pdf", question="Extract", structured_output=None
        )
        self.assertIsNone(inp.structured_output)

    def test_missing_required_field_raises(self):
        with self.assertRaises(ValidationError):
            SimpleOcrToolInput(path="/x.pdf")


class TestModuleConstants(unittest.TestCase):
    def test_constants(self):
        self.assertEqual(DEFAULT_MODEL, "gpt-5-mini")
        self.assertEqual(DEFAULT_PROVIDER, "openai")
        self.assertEqual(DEFAULT_OCR_TEMPERATURE, 0.2)
        self.assertEqual(SIMPLE_OCR_TOOL_ID, "BD5CBAAA442F42E289F5C7438E3C2EDF")
        self.assertIn("application/pdf", SUPPORTED_MIME_TYPES)
        self.assertIn("image/png", SUPPORTED_MIME_TYPES)
        self.assertIn("OCR", SYSTEM_PROMPT)


class TestResolveFilePath(unittest.TestCase):
    def test_resolves_existing_path_without_prefix(self):
        with tempfile.NamedTemporaryFile(delete=False) as tmp:
            tmp.write(b"data")
            tmp_path = tmp.name
        try:
            self.assertEqual(_resolve_file_path(tmp_path), tmp_path)
        finally:
            os.unlink(tmp_path)

    @patch("tools.SimpleOcrTool.Path")
    def test_raises_when_file_missing(self, mock_path_cls):
        obj = MagicMock()
        obj.is_file.return_value = False
        mock_path_cls.return_value = obj
        with self.assertRaises(FileNotFoundError):
            _resolve_file_path("/definitely-not-on-disk.pdf")

    def test_joins_prefix_safely_even_with_leading_slash(self):
        """Regression: '/app' + '/x.pdf' must become '/app/x.pdf', not '/appx.pdf'."""
        with patch("tools.SimpleOcrTool.Path") as mock_path_cls:
            candidates = []

            def fake_path(arg):
                candidates.append(arg)
                obj = MagicMock()
                obj.is_file.return_value = False
                return obj

            mock_path_cls.side_effect = fake_path
            with self.assertRaises(FileNotFoundError):
                _resolve_file_path("/file.pdf")

            self.assertIn("/app/file.pdf", candidates)
            self.assertNotIn("/appfile.pdf", candidates)
            self.assertNotIn("/app/file.pdf".replace("/", "", 1), candidates)


class TestReadMime(unittest.TestCase):
    @patch("filetype.guess")
    def test_returns_mime(self, mock_guess):
        mock_guess.return_value = MagicMock(mime="image/png")
        self.assertEqual(_read_mime("/x.png"), "image/png")

    @patch("filetype.guess")
    def test_returns_none_when_unknown(self, mock_guess):
        mock_guess.return_value = None
        self.assertIsNone(_read_mime("/x.bin"))


class TestValidateMime(unittest.TestCase):
    def test_supported_passes(self):
        for mime in SUPPORTED_MIME_TYPES:
            _validate_mime("/x", mime)

    def test_unsupported_raises(self):
        with self.assertRaises(ValueError):
            _validate_mime("/x.txt", "text/plain")


class TestIsGemini(unittest.TestCase):
    def test_gemini(self):
        self.assertTrue(_is_gemini("gemini"))
        self.assertTrue(_is_gemini("google_genai"))

    def test_not_gemini(self):
        self.assertFalse(_is_gemini("openai"))
        self.assertFalse(_is_gemini(""))
        self.assertFalse(_is_gemini(None))


class TestModelId(unittest.TestCase):
    def test_prefixed_when_proxy(self):
        self.assertEqual(
            _model_id("openai", "gpt-5-mini", "https://proxy"),
            "openai/gpt-5-mini",
        )

    def test_not_prefixed_without_proxy(self):
        self.assertEqual(_model_id("openai", "gpt-5-mini", None), "gpt-5-mini")


class TestStructuredSchemaDict(unittest.TestCase):
    def test_none_returns_none(self):
        self.assertIsNone(_structured_schema_dict(None))

    def test_pydantic_model_returns_schema(self):
        schema = _structured_schema_dict(_FakeSchema)
        self.assertIsInstance(schema, dict)
        self.assertIn("properties", schema)

    def test_broken_schema_returns_none(self):
        broken = MagicMock()
        broken.model_json_schema.side_effect = RuntimeError("boom")
        self.assertIsNone(_structured_schema_dict(broken))


class TestGetModelConfig(unittest.TestCase):
    @patch("tools.SimpleOcrTool.get_extra_info")
    def test_returns_configured_values(self, mock_extra):
        mock_extra.return_value = {
            "tool_config": {
                "agent-1": {SIMPLE_OCR_TOOL_ID: {"model": "m", "provider": "gemini"}}
            }
        }
        model, provider = _get_model_config("agent-1")
        self.assertEqual(model, "m")
        self.assertEqual(provider, "gemini")

    @patch("tools.SimpleOcrTool.get_extra_info")
    def test_falls_back_to_defaults_when_no_config(self, mock_extra):
        mock_extra.return_value = {}
        model, provider = _get_model_config("agent-x")
        self.assertEqual(model, DEFAULT_MODEL)
        self.assertEqual(provider, DEFAULT_PROVIDER)

    @patch("tools.SimpleOcrTool.get_extra_info")
    def test_falls_back_when_extra_info_raises(self, mock_extra):
        mock_extra.side_effect = RuntimeError("bad")
        model, provider = _get_model_config("agent-x")
        self.assertEqual(model, DEFAULT_MODEL)
        self.assertEqual(provider, DEFAULT_PROVIDER)

    @patch("tools.SimpleOcrTool.get_extra_info")
    def test_none_extra_info(self, mock_extra):
        mock_extra.return_value = None
        model, provider = _get_model_config("agent-x")
        self.assertEqual(model, DEFAULT_MODEL)
        self.assertEqual(provider, DEFAULT_PROVIDER)


class TestCallOpenAI(unittest.TestCase):
    @patch("tools.SimpleOcrTool._get_client")
    def test_pdf_payload_and_no_temperature_for_gpt5(self, mock_get_client):
        client = MagicMock()
        response = MagicMock(output_text="result")
        client.responses.create.return_value = response
        mock_get_client.return_value = (client, "https://proxy")

        out = _call_openai(
            "gpt-5-mini", "openai", "sys", "q",
            b"pdfbytes", "application/pdf", None,
        )
        self.assertEqual(out, "result")
        kwargs = client.responses.create.call_args.kwargs
        self.assertNotIn("temperature", kwargs)
        self.assertEqual(kwargs["model"], "openai/gpt-5-mini")
        self.assertEqual(kwargs["input"][1]["content"][0]["type"], "input_file")
        b64 = base64.b64encode(b"pdfbytes").decode()
        self.assertIn(b64, kwargs["input"][1]["content"][0]["file_data"])

    @patch("tools.SimpleOcrTool._get_client")
    def test_image_payload_with_temperature_for_non_gpt5(self, mock_get_client):
        client = MagicMock()
        client.responses.create.return_value = MagicMock(output_text="ok")
        mock_get_client.return_value = (client, None)

        _call_openai(
            "gpt-4o-mini", "openai", "sys", "q",
            b"img", "image/png", None,
        )
        kwargs = client.responses.create.call_args.kwargs
        self.assertEqual(kwargs["temperature"], DEFAULT_OCR_TEMPERATURE)
        self.assertEqual(kwargs["model"], "gpt-4o-mini")
        self.assertEqual(kwargs["input"][1]["content"][0]["type"], "input_image")

    @patch("tools.SimpleOcrTool._get_client")
    def test_structured_schema_added(self, mock_get_client):
        client = MagicMock()
        client.responses.create.return_value = MagicMock(output_text="ok")
        mock_get_client.return_value = (client, None)

        _call_openai(
            "gpt-4o-mini", "openai", "sys", "q",
            b"img", "image/png", _FakeSchema,
        )
        kwargs = client.responses.create.call_args.kwargs
        self.assertIn("text", kwargs)
        self.assertEqual(kwargs["text"]["format"]["type"], "json_schema")
        self.assertEqual(kwargs["text"]["format"]["name"], "ocr_output")


class TestCallGemini(unittest.TestCase):
    @patch("tools.SimpleOcrTool._get_client")
    def test_basic_call(self, mock_get_client):
        client = MagicMock()
        msg = MagicMock()
        msg.content = "gem"
        client.chat.completions.create.return_value = MagicMock(
            choices=[MagicMock(message=msg)]
        )
        mock_get_client.return_value = (client, "https://proxy")

        out = _call_gemini(
            "gemini-2.0", "gemini", "sys", "q",
            b"img", "image/png", None,
        )
        self.assertEqual(out, "gem")
        kwargs = client.chat.completions.create.call_args.kwargs
        self.assertEqual(kwargs["model"], "gemini/gemini-2.0")
        self.assertEqual(kwargs["temperature"], DEFAULT_OCR_TEMPERATURE)
        self.assertNotIn("response_format", kwargs)

    @patch("tools.SimpleOcrTool._get_client")
    def test_with_structured_schema(self, mock_get_client):
        client = MagicMock()
        msg = MagicMock()
        msg.content = "gem"
        client.chat.completions.create.return_value = MagicMock(
            choices=[MagicMock(message=msg)]
        )
        mock_get_client.return_value = (client, None)

        _call_gemini(
            "gemini-2.0", "gemini", "sys", "q",
            b"img", "image/png", _FakeSchema,
        )
        kwargs = client.chat.completions.create.call_args.kwargs
        self.assertIn("response_format", kwargs)
        self.assertEqual(
            kwargs["response_format"]["json_schema"]["name"], "ocr_output"
        )


class TestSimpleOcrTool(unittest.TestCase):
    def test_metadata(self):
        tool = SimpleOcrTool()
        self.assertEqual(tool.name, "SimpleOcrTool")
        self.assertEqual(tool.args_schema, SimpleOcrToolInput)
        self.assertIn("PDF", tool.description)

    @patch("tools.SimpleOcrTool._call_openai")
    @patch("tools.SimpleOcrTool._get_model_config")
    @patch("tools.SimpleOcrTool._validate_mime")
    @patch("tools.SimpleOcrTool._read_mime")
    @patch("tools.SimpleOcrTool._resolve_file_path")
    def test_run_openai_path(
        self, mock_resolve, mock_mime, mock_validate, mock_cfg, mock_openai
    ):
        with tempfile.NamedTemporaryFile(delete=False) as tmp:
            tmp.write(b"hello")
            tmp_path = tmp.name
        try:
            mock_resolve.return_value = tmp_path
            mock_mime.return_value = "application/pdf"
            mock_cfg.return_value = ("gpt-5-mini", "openai")
            mock_openai.return_value = "openai-result"

            tool = SimpleOcrTool()
            out = tool.run({"path": "/x.pdf", "question": "q"})

            self.assertEqual(out, "openai-result")
            mock_validate.assert_called_once_with(tmp_path, "application/pdf")
            mock_openai.assert_called_once_with(
                "gpt-5-mini", "openai", SYSTEM_PROMPT, "q",
                b"hello", "application/pdf", None,
            )
        finally:
            os.unlink(tmp_path)

    @patch("tools.SimpleOcrTool._call_gemini")
    @patch("tools.SimpleOcrTool._get_model_config")
    @patch("tools.SimpleOcrTool._validate_mime")
    @patch("tools.SimpleOcrTool._read_mime")
    @patch("tools.SimpleOcrTool._resolve_file_path")
    def test_run_gemini_path(
        self, mock_resolve, mock_mime, mock_validate, mock_cfg, mock_gemini
    ):
        with tempfile.NamedTemporaryFile(delete=False) as tmp:
            tmp.write(b"img")
            tmp_path = tmp.name
        try:
            mock_resolve.return_value = tmp_path
            mock_mime.return_value = "image/png"
            mock_cfg.return_value = ("gemini-2.0", "gemini")
            mock_gemini.return_value = "gem-result"

            tool = SimpleOcrTool()
            out = tool.run({"path": "/x.png", "question": "q"})

            self.assertEqual(out, "gem-result")

        finally:
            os.unlink(tmp_path)

    @patch("tools.SimpleOcrTool.load_schema")
    @patch("tools.SimpleOcrTool._call_openai")
    @patch("tools.SimpleOcrTool._get_model_config")
    @patch("tools.SimpleOcrTool._validate_mime")
    @patch("tools.SimpleOcrTool._read_mime")
    @patch("tools.SimpleOcrTool._resolve_file_path")
    def test_run_with_structured_output(
        self, mock_resolve, mock_mime, mock_validate, mock_cfg,
        mock_openai, mock_load_schema,
    ):
        with tempfile.NamedTemporaryFile(delete=False) as tmp:
            tmp.write(b"x")
            tmp_path = tmp.name
        try:
            mock_resolve.return_value = tmp_path
            mock_mime.return_value = "application/pdf"
            mock_cfg.return_value = ("gpt-5-mini", "openai")
            mock_load_schema.return_value = _FakeSchema
            mock_openai.return_value = "structured"

            tool = SimpleOcrTool()
            out = tool.run(
                {"path": "/x.pdf", "question": "q", "structured_output": "Invoice"}
            )

            self.assertEqual(out, "structured")
            mock_load_schema.assert_called_once_with("Invoice")
            mock_openai.assert_called_once_with(
                "gpt-5-mini", "openai", SYSTEM_PROMPT, "q",
                b"x", "application/pdf", _FakeSchema,
            )
        finally:
            os.unlink(tmp_path)

    @patch("tools.SimpleOcrTool._resolve_file_path")
    def test_run_returns_error_dict_on_exception(self, mock_resolve):
        mock_resolve.side_effect = FileNotFoundError("nope")
        tool = SimpleOcrTool()
        out = tool.run({"path": "/missing.pdf", "question": "q"})
        self.assertIsInstance(out, dict)
        self.assertIn("error", out)
        self.assertIn("nope", out["error"])


if __name__ == "__main__":
    unittest.main()
