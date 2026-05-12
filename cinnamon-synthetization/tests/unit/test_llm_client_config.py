import sys
from pathlib import Path

import requests

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.llm.client import LlmClient, load_llm_client_config


def test_load_llm_client_config_prefers_sampling_max_tokens_over_env(monkeypatch):
    monkeypatch.setenv("CINNAMON_LLM_MAX_TOKENS", "2048")
    monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "openai_compatible")
    monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "gpt-test")
    monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://gpu.example.org:7086")
    monkeypatch.setenv("CINNAMON_LLM_TIMEOUT_SECONDS", "10")
    monkeypatch.setenv("CINNAMON_LLM_MAX_RETRIES", "2")
    monkeypatch.setenv("CINNAMON_LLM_TEMPERATURE", "0.2")
    monkeypatch.setenv("CINNAMON_LLM_TOP_P", "0.9")

    algorithm_config = {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {},
                "model_fitting": {},
                "sampling": {
                    "temperature": 0.2,
                    "top_p": 0.9,
                    "max_tokens": 999999,
                },
            }
        }
    }

    config = load_llm_client_config(algorithm_config)

    assert config.max_tokens == 999999


def test_load_llm_client_config_uses_env_max_tokens_as_fallback(monkeypatch):
    monkeypatch.setenv("CINNAMON_LLM_MAX_TOKENS", "2048")
    monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "openai_compatible")
    monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "gpt-test")
    monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://gpu.example.org:7086")
    monkeypatch.setenv("CINNAMON_LLM_TIMEOUT_SECONDS", "10")
    monkeypatch.setenv("CINNAMON_LLM_MAX_RETRIES", "2")
    monkeypatch.setenv("CINNAMON_LLM_TEMPERATURE", "0.2")
    monkeypatch.setenv("CINNAMON_LLM_TOP_P", "0.9")

    algorithm_config = {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {},
                "model_fitting": {},
                "sampling": {
                    "temperature": 0.2,
                    "top_p": 0.9,
                },
            }
        }
    }

    config = load_llm_client_config(algorithm_config)

    assert config.max_tokens == 2048


def test_load_llm_client_config_requires_explicit_decoding_values(monkeypatch):
    monkeypatch.delenv("CINNAMON_LLM_TEMPERATURE", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_TOP_P", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_MAX_TOKENS", raising=False)
    monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "openai_compatible")
    monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "gpt-test")
    monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://gpu.example.org:7086")
    monkeypatch.setenv("CINNAMON_LLM_TIMEOUT_SECONDS", "10")
    monkeypatch.setenv("CINNAMON_LLM_MAX_RETRIES", "2")

    algorithm_config = {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {},
                "model_fitting": {},
                "sampling": {},
            }
        }
    }

    try:
        load_llm_client_config(algorithm_config)
        assert False, "Expected ValueError for missing decoding configuration"
    except ValueError as exc:
        message = str(exc)
        assert "Invalid LLM decoding configuration" in message
        assert "CINNAMON_LLM_TEMPERATURE" in message
        assert "CINNAMON_LLM_TOP_P" in message
        assert "CINNAMON_LLM_MAX_TOKENS" in message


def test_load_llm_client_config_uses_selected_profile(monkeypatch):
    monkeypatch.delenv("CINNAMON_LLM_PROVIDER", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_MODEL_NAME", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_BASE_URL", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_ENDPOINT_PATH", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_HEALTHCHECK_PATH", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_API_KEY", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_TIMEOUT_SECONDS", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_MAX_RETRIES", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_VERIFY_SSL", raising=False)
    monkeypatch.delenv("CINNAMON_LLM_MAX_TOKENS", raising=False)

    monkeypatch.setenv("CINNAMON_LLM_PROFILE_IDS", "p1,p2")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_NAME", "Profile A")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_PROVIDER", "openai_compatible")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_MODEL_NAME", "model-a")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_BASE_URL", "http://profile-a.example.org:8000")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_ENDPOINT_PATH", "/v1/chat/completions")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_HEALTHCHECK_PATH", "/v1/models")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_API_KEY", "")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_TIMEOUT_SECONDS", "33")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_MAX_RETRIES", "4")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_VERIFY_SSL", "false")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_P1_MAX_TOKENS", "1500")

    monkeypatch.setenv("CINNAMON_LLM_TEMPERATURE", "0.4")
    monkeypatch.setenv("CINNAMON_LLM_TOP_P", "0.8")

    algorithm_config = {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {
                    "llm_profile": "Profile A",
                },
                "model_fitting": {},
                "sampling": {
                    "temperature": 0.4,
                    "top_p": 0.8,
                },
            }
        }
    }

    config = load_llm_client_config(algorithm_config)

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
    monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "openai_compatible")
    monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "gpt-test")
    monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://gpu.example.org:7086")
    monkeypatch.setenv("CINNAMON_LLM_TIMEOUT_SECONDS", "10")
    monkeypatch.setenv("CINNAMON_LLM_MAX_RETRIES", "3")
    monkeypatch.setenv("CINNAMON_LLM_TEMPERATURE", "0.2")
    monkeypatch.setenv("CINNAMON_LLM_TOP_P", "0.9")
    monkeypatch.setenv("CINNAMON_LLM_MAX_TOKENS", "512")

    config = load_llm_client_config(
        {
            "synthetization_configuration": {
                "algorithm": {
                    "model_parameter": {},
                    "model_fitting": {},
                    "sampling": {"temperature": 0.2, "top_p": 0.9},
                }
            }
        }
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
    monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "openai_compatible")
    monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "gpt-test")
    monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://gpu.example.org:7086")
    monkeypatch.setenv("CINNAMON_LLM_TIMEOUT_SECONDS", "10")
    monkeypatch.setenv("CINNAMON_LLM_MAX_RETRIES", "3")
    monkeypatch.setenv("CINNAMON_LLM_TEMPERATURE", "0.2")
    monkeypatch.setenv("CINNAMON_LLM_TOP_P", "0.9")
    monkeypatch.setenv("CINNAMON_LLM_MAX_TOKENS", "512")

    config = load_llm_client_config(
        {
            "synthetization_configuration": {
                "algorithm": {
                    "model_parameter": {},
                    "model_fitting": {},
                    "sampling": {"temperature": 0.2, "top_p": 0.9},
                }
            }
        }
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
