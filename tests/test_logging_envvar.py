import os
from unittest.mock import Mock, mock_open, patch

import pytest
from copilot.baseutils.logging_envvar import (
    copilot_debug,
    copilot_debug_curl,
    copilot_debug_custom,
    copilot_debug_event,
    copilot_info,
    empty_folder,
    is_debug_enabled,
    is_docker,
    read_optional_env_var,
    read_optional_env_var_bool,
    read_optional_env_var_float,
    read_optional_env_var_int,
)


def test_env_var_uppercase(monkeypatch):
    monkeypatch.setenv("MY_ENV_VAR", "value_upper")
    assert read_optional_env_var("my.env.var", "default") == "value_upper"
    monkeypatch.delenv("MY_ENV_VAR", raising=False)


def test_env_var_dot(monkeypatch):
    monkeypatch.setenv("my.env.var", "value_dot")
    assert read_optional_env_var("my.env.var", "default") == "value_dot"
    monkeypatch.delenv("my.env.var", raising=False)


def test_env_var_both(monkeypatch):
    monkeypatch.setenv("MY_ENV_VAR", "value_upper")
    monkeypatch.setenv("my.env.var", "value_dot")
    assert read_optional_env_var("my.env.var", "default") == "value_upper"
    monkeypatch.delenv("MY_ENV_VAR", raising=False)
    monkeypatch.delenv("my.env.var", raising=False)


def test_env_var_none(monkeypatch):
    monkeypatch.delenv("MY_ENV_VAR", raising=False)
    monkeypatch.delenv("my.env.var", raising=False)
    assert read_optional_env_var("my.env.var", "default") == "default"


def test_env_var_uppercase_arg(monkeypatch):
    monkeypatch.setenv("MY_ENV_VAR", "value_upper")
    assert read_optional_env_var("MY_ENV_VAR", "default") == "value_upper"
    monkeypatch.delenv("MY_ENV_VAR", raising=False)


def test_env_var_uppercase_arg_none(monkeypatch):
    monkeypatch.delenv("MY_ENV_VAR", raising=False)
    assert read_optional_env_var("MY_ENV_VAR", "default") == "default"


def test_env_var_ignores_empty_values(monkeypatch):
    monkeypatch.setenv("MY_ENV_VAR", "")
    monkeypatch.setenv("my.env.var", "")

    assert read_optional_env_var("my.env.var", "default") == "default"


def test_read_optional_env_var_int_and_float(monkeypatch):
    monkeypatch.setenv("COPILOT_NUMBER", "7")
    monkeypatch.setenv("COPILOT_FLOAT", "3.5")

    assert read_optional_env_var_int("copilot.number", 1) == 7
    assert read_optional_env_var_float("copilot.float", 1.0) == 3.5


def test_read_optional_env_var_bool_accepts_truthy_and_falsy_values(monkeypatch):
    monkeypatch.setenv("COPILOT_FLAG", "yes")
    assert read_optional_env_var_bool("copilot.flag", False) is True

    monkeypatch.setenv("COPILOT_FLAG", "0")
    assert read_optional_env_var_bool("copilot.flag", True) is False


def test_read_optional_env_var_bool_rejects_invalid_value(monkeypatch):
    monkeypatch.setenv("COPILOT_FLAG", "maybe")

    with pytest.raises(ValueError, match="Invalid boolean value"):
        read_optional_env_var_bool("copilot.flag", False)


def test_is_debug_enabled_reads_uppercase_or_dotted_env_vars(monkeypatch):
    monkeypatch.setenv("COPILOT_DEBUG", "true")
    assert is_debug_enabled() is True

    monkeypatch.setenv("COPILOT_DEBUG", "false")
    monkeypatch.setenv("copilot.debug", "1")
    assert is_debug_enabled() is True

    monkeypatch.setenv("copilot.debug", "no")
    assert is_debug_enabled() is False


def test_debug_helpers_print_only_when_debug_enabled(monkeypatch, capsys):
    monkeypatch.setenv("COPILOT_DEBUG", "true")

    copilot_debug("debug message")
    copilot_debug_custom("custom message", "")

    captured = capsys.readouterr().out
    assert "debug message" in captured
    assert "custom message" in captured


def test_copilot_debug_curl_uses_curlify_when_debug_enabled(monkeypatch):
    monkeypatch.setenv("COPILOT_DEBUG", "true")
    request = Mock()

    with patch("copilot.baseutils.logging_envvar.curlify.to_curl", return_value="curl example") as to_curl:
        with patch("copilot.baseutils.logging_envvar.copilot_debug") as debug:
            copilot_debug_curl(request)

    to_curl.assert_called_once_with(request)
    debug.assert_called_once_with("cUrl command: curl example")


def test_copilot_debug_event_requires_debug_and_event_flags(monkeypatch, capsys):
    monkeypatch.setenv("COPILOT_DEBUG", "true")
    monkeypatch.setenv("COPILOT_DEBUG_EVENT", "true")

    copilot_debug_event("event message")

    assert "event message" in capsys.readouterr().out


def test_copilot_info_prints_when_info_mode_is_enabled(monkeypatch, capsys):
    monkeypatch.setenv("COPILOT_INFO", "true")
    monkeypatch.setenv("COPILOT_DEBUG", "false")

    copilot_info("info message")

    assert "info message" in capsys.readouterr().out


def test_is_docker_true_when_docker_env_var_is_enabled(monkeypatch):
    monkeypatch.setenv("DOCKER_COM_ETENDOERP_COPILOT", "true")

    assert is_docker() is True


def test_is_docker_true_when_current_directory_is_app(monkeypatch):
    monkeypatch.setenv("DOCKER_COM_ETENDOERP_COPILOT", "false")
    monkeypatch.setattr(os, "getcwd", lambda: "/app/copilot")
    monkeypatch.setattr(os.path, "exists", lambda path: False)
    monkeypatch.setattr("socket.gethostname", lambda: "not-docker-host")

    assert is_docker() is True


def test_is_docker_true_when_cgroup_mentions_container(monkeypatch):
    monkeypatch.setenv("DOCKER_COM_ETENDOERP_COPILOT", "false")
    monkeypatch.setattr(os, "getcwd", lambda: "/workspace")
    monkeypatch.setattr(os.path, "exists", lambda path: False)
    monkeypatch.setattr("builtins.open", mock_open(read_data="0::/docker/container-id"))

    assert is_docker() is True


def test_empty_folder_removes_files_links_and_directories(tmp_path):
    folder = tmp_path / "folder"
    folder.mkdir()
    (folder / "file.txt").write_text("content")
    nested = folder / "nested"
    nested.mkdir()
    (nested / "child.txt").write_text("child")
    target = tmp_path / "target.txt"
    target.write_text("target")
    (folder / "link.txt").symlink_to(target)

    empty_folder(str(folder))

    assert list(folder.iterdir()) == []


def test_empty_folder_reports_invalid_directory(capsys):
    empty_folder("/path/that/does/not/exist")

    assert "is not a valid directory" in capsys.readouterr().out
