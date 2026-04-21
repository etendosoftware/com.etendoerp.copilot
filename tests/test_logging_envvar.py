from copilot.baseutils.logging_envvar import read_optional_env_var


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
