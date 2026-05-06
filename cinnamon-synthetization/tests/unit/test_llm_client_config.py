import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.llm.client import load_llm_client_config


def test_load_llm_client_config_reads_max_tokens_from_env(monkeypatch):
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
