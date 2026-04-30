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
