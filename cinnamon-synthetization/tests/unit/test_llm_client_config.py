import sys
from pathlib import Path

import requests

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.llm.client import LlmClient, load_llm_client_config


def _set_profile_env(
    monkeypatch,
    *,
    profile_id: str = "p1",
    profile_name: str = "Profile A",
    provider: str = "openai_compatible",
    model_name: str | None = None,
    base_url: str | None = None,
    endpoint_path: str | None = None,
    healthcheck_path: str | None = None,
    timeout_seconds: str = "10",
    max_retries: str = "2",
    verify_ssl: str = "true",
    max_tokens: str = "2048",
) -> str:
    if model_name is None:
        model_name = "gpt-test" if provider == "openai_compatible" else "qwen3:8b"
    if base_url is None:
        base_url = "http://gpu.example.org:7086" if provider == "openai_compatible" else "http://127.0.0.1:11434"
    if endpoint_path is None:
        endpoint_path = "/v1/chat/completions" if provider == "openai_compatible" else "/api/generate"
    if healthcheck_path is None:
        healthcheck_path = "/v1/models" if provider == "openai_compatible" else "/api/tags"

    token = profile_id.upper()
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_IDS", profile_id)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_NAME", profile_name)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_PROVIDER", provider)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_MODEL_NAME", model_name)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_BASE_URL", base_url)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_ENDPOINT_PATH", endpoint_path)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_HEALTHCHECK_PATH", healthcheck_path)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_API_KEY", "")
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_TIMEOUT_SECONDS", timeout_seconds)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_MAX_RETRIES", max_retries)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_VERIFY_SSL", verify_ssl)
    monkeypatch.setenv(f"CINNAMON_LLM_PROFILE_{token}_MAX_TOKENS", max_tokens)
    return profile_name


def _algorithm_config(
    *,
    profile_name: str = "Profile A",
    sampling: dict | None = None,
    model_parameter: dict | None = None,
    model_fitting: dict | None = None,
) -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "llm_profile": {
                    "llm_profile": profile_name,
                },
                "model_parameter": model_parameter or {},
                "model_fitting": model_fitting or {},
                "sampling": sampling or {},
            }
        }
    }


def test_load_llm_client_config_uses_profile_max_tokens_as_fallback(monkeypatch):
    profile_name = _set_profile_env(monkeypatch, max_tokens="2048")

    config = load_llm_client_config(
        _algorithm_config(
            profile_name=profile_name,
            sampling={
                "temperature": 0.2,
                "top_p": 0.9,
            },
        )
    )

    assert config.max_tokens == 2048


def test_load_llm_client_config_requires_selected_profile(monkeypatch):
    _set_profile_env(monkeypatch)

    try:
        load_llm_client_config(
            {
                "synthetization_configuration": {
                    "algorithm": {
                        "llm_profile": {},
                        "model_parameter": {},
                        "model_fitting": {},
                        "sampling": {
                            "temperature": 0.2,
                            "top_p": 0.9,
                        },
                    }
                }
            }
        )
        assert False, "Expected ValueError for missing llm_profile selection"
    except ValueError as exc:
        message = str(exc)
        assert "Missing llm_profile selection" in message
        assert "Profile A" in message


def test_load_llm_client_config_requires_explicit_decoding_values(monkeypatch):
    profile_name = _set_profile_env(monkeypatch)

    try:
        load_llm_client_config(_algorithm_config(profile_name=profile_name, sampling={}))
        assert False, "Expected ValueError for missing decoding configuration"
    except ValueError as exc:
        message = str(exc)
        assert "Invalid LLM decoding configuration" in message
        assert "temperature" in message
        assert "top_p" in message
        assert "max_tokens" in message


def test_load_llm_client_config_uses_selected_profile(monkeypatch):
    profile_name = _set_profile_env(
        monkeypatch,
        provider="openai_compatible",
        model_name="model-a",
        base_url="http://profile-a.example.org:8000",
        timeout_seconds="33",
        max_retries="4",
        verify_ssl="false",
        max_tokens="1500",
    )

    config = load_llm_client_config(
        _algorithm_config(
            profile_name=profile_name,
            sampling={
                "temperature": 0.4,
                "top_p": 0.8,
            },
        )
    )

    assert config.provider == "openai_compatible"
    assert config.model_name == "model-a"
    assert config.base_url == "http://profile-a.example.org:8000"
    assert config.endpoint_path == "/v1/chat/completions"
    assert config.healthcheck_path == "/v1/models"
    assert config.timeout_seconds == 33
    assert config.max_retries == 4
    assert config.verify_ssl is False
    assert config.max_tokens == 1500


class _DummyRetryResponse:
    def __init__(self, status_code: int, payload: dict):
        self.status_code = status_code
        self._payload = payload

    def raise_for_status(self):
        if self.status_code >= 400:
            error = requests.exceptions.HTTPError(f"HTTP {self.status_code}")
            error.response = self
            raise error

    def json(self):
        return self._payload


def test_llm_client_retries_on_retryable_http_status(monkeypatch):
    profile_name = _set_profile_env(monkeypatch, max_retries="3", max_tokens="512")

    config = load_llm_client_config(
        _algorithm_config(
            profile_name=profile_name,
            sampling={"temperature": 0.2, "top_p": 0.9},
        )
    )
    client = LlmClient(config)

    call_counter = {"count": 0}
    sleep_calls = []

    def fake_request(method, url, **kwargs):
        del method, url, kwargs
        call_counter["count"] += 1
        if call_counter["count"] < 3:
            return _DummyRetryResponse(503, {})
        return _DummyRetryResponse(200, {"ok": True})

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.time.sleep", lambda seconds: sleep_calls.append(seconds))

    response = client._request("GET", "http://gpu.example.org:7086/v1/models")

    assert response.status_code == 200
    assert call_counter["count"] == 3
    assert len(sleep_calls) == 2


def test_llm_client_does_not_retry_on_non_retryable_http_status(monkeypatch):
    profile_name = _set_profile_env(monkeypatch, max_retries="3", max_tokens="512")

    config = load_llm_client_config(
        _algorithm_config(
            profile_name=profile_name,
            sampling={"temperature": 0.2, "top_p": 0.9},
        )
    )
    client = LlmClient(config)

    call_counter = {"count": 0}
    sleep_calls = []

    def fake_request(method, url, **kwargs):
        del method, url, kwargs
        call_counter["count"] += 1
        return _DummyRetryResponse(400, {})

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.time.sleep", lambda seconds: sleep_calls.append(seconds))

    try:
        client._request("GET", "http://gpu.example.org:7086/v1/models")
        assert False, "Expected HTTPError for non-retryable response"
    except requests.exceptions.HTTPError:
        pass

    assert call_counter["count"] == 1
    assert sleep_calls == []


def test_llm_client_ollama_healthcheck_requires_configured_model(monkeypatch):
    profile_name = _set_profile_env(
        monkeypatch,
        provider="ollama",
        model_name="qwen3:8b",
        base_url="http://127.0.0.1:11434",
        max_tokens="512",
    )

    config = load_llm_client_config(
        _algorithm_config(
            profile_name=profile_name,
            sampling={"temperature": 0.2, "top_p": 0.9},
        )
    )
    client = LlmClient(config)

    def fake_request(method, url, **kwargs):
        del method, url, kwargs
        return _DummyRetryResponse(200, {"models": [{"name": "llama3.1:8b"}]})

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    try:
        client.initialize()
        assert False, "Expected ValueError when configured Ollama model is missing"
    except ValueError as exc:
        message = str(exc)
        assert "qwen3:8b" in message
        assert "llama3.1:8b" in message
