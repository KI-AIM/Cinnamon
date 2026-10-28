from pathlib import Path
from types import SimpleNamespace

import pytest

from synthetic_tabular_data_generator.llm.client import (
    DEFAULT_OPENAI_SYSTEM_PROMPT,
    LlmClient,
    LlmClientConfig,
)
from synthetic_tabular_data_generator.llm.prompt_logger import PromptLogger, log_llm_interaction


@pytest.fixture(autouse=True)
def logging_environment(monkeypatch, tmp_path):
    monkeypatch.delenv("CINNAMON_LLM_ENABLE_PROMPT_LOGGING", raising=False)
    monkeypatch.setenv("CINNAMON_LLM_LOG_DIR", str(tmp_path / "logs"))
    PromptLogger.reset()
    yield
    PromptLogger.reset()


def client_for(provider, body):
    config = LlmClientConfig(
        provider=provider, model_name="test-model", base_url="http://unused",
        endpoint_path="/generate", healthcheck_path="/health", api_key="PRIVATE_API_KEY",
        temperature=0.2, top_p=0.9, max_tokens=100, timeout_seconds=1,
        max_retries=1, verify_ssl=True,
    )
    client = LlmClient(config)
    client._request = lambda *args, **kwargs: SimpleNamespace(json=lambda: body)
    return client


@pytest.mark.parametrize("setting", [None, "false", "0", "invalid", ""])
def test_disabled_logging_creates_no_directories(monkeypatch, tmp_path, setting):
    if setting is not None:
        monkeypatch.setenv("CINNAMON_LLM_ENABLE_PROMPT_LOGGING", setting)
    logger = PromptLogger()
    logger.initialize("session")
    assert logger.log_prompt_and_response("PRIVATE_PROMPT", "PRIVATE_RESPONSE") is None
    assert log_llm_interaction("PRIVATE_PROMPT", "PRIVATE_RESPONSE", session_key="other") is None
    client = client_for("ollama", {"response": "response"})
    client.set_session_key("client-session")
    assert client.generate_text("PRIVATE_PROMPT") == "response"
    assert not (tmp_path / "logs").exists()


@pytest.mark.parametrize("provider,body", [
    ("ollama", {"response": "response"}),
    ("openai_compatible", {"choices": [{"message": {"content": "response"}}]}),
    ("openai_compatible", {"choices": [{"text": "response"}]}),
])
def test_each_response_path_logs_once_to_configured_directory(monkeypatch, tmp_path, provider, body):
    monkeypatch.setenv("CINNAMON_LLM_ENABLE_PROMPT_LOGGING", " TrUe ")
    client = client_for(provider, body)
    client.set_session_key("session")
    assert client.generate_text("prompt") == "response"
    files = list((tmp_path / "logs" / "session").glob("*.txt"))
    assert len(files) == 1
    content = files[0].read_text()
    assert "USER PROMPT:\n" + "-" * 40 + "\nprompt" in content
    assert "LLM RESPONSE:\n" + "-" * 40 + "\nresponse" in content
    assert "model: test-model" in content
    assert "PRIVATE_API_KEY" not in content
    if provider == "openai_compatible":
        assert DEFAULT_OPENAI_SYSTEM_PROMPT in content
    monkeypatch.setenv("CINNAMON_LLM_ENABLE_PROMPT_LOGGING", "false")
    assert client.generate_text("DO_NOT_LOG") == "response"
    assert list((tmp_path / "logs" / "session").glob("*.txt")) == files
    assert files[0].read_text() == content


def test_client_sessions_do_not_share_logger(monkeypatch, tmp_path):
    monkeypatch.setenv("CINNAMON_LLM_ENABLE_PROMPT_LOGGING", "true")
    first = client_for("ollama", {"response": "first"})
    second = client_for("ollama", {"response": "second"})
    first.set_session_key("first")
    second.set_session_key("second")
    first.generate_text("first prompt", system_prompt="first system")
    second.generate_text("second prompt")
    first_log = next((tmp_path / "logs" / "first").glob("*.txt")).read_text()
    assert "first prompt" in first_log
    assert "first system" in first_log
    assert "second prompt" not in first_log


def test_default_directory_when_no_override(monkeypatch, tmp_path):
    from synthetic_tabular_data_generator.llm import prompt_logger
    monkeypatch.setenv("CINNAMON_LLM_ENABLE_PROMPT_LOGGING", "true")
    monkeypatch.delenv("CINNAMON_LLM_LOG_DIR")
    monkeypatch.setattr(prompt_logger, "__file__", str(tmp_path / "package" / "llm" / "prompt_logger.py"))
    logger = PromptLogger()
    logger.initialize("session")
    path = logger.log_prompt_and_response("prompt", "response")
    assert Path(path).parent == tmp_path / "outputs" / "prompts" / "session"


@pytest.mark.parametrize("session", ["..", "../escape", "/absolute", "nested/name", "nested\\name"])
def test_session_cannot_escape_log_directory(monkeypatch, tmp_path, session):
    monkeypatch.setenv("CINNAMON_LLM_ENABLE_PROMPT_LOGGING", "true")
    with pytest.raises(ValueError, match="single directory name"):
        PromptLogger().initialize(session)
    assert not (tmp_path / "logs").exists()
