import json
import sys
from pathlib import Path

import pandas as pd
import pytest

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_tabular import LlmTabularSynthesizer
from synthetic_tabular_data_generator.tabular_data_synthesizer import SynthesizerOperationError


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "height", "type": "DECIMAL"},
            {"index": 2, "name": "risk", "type": "BOOLEAN"},
            {"index": 3, "name": "group", "type": "STRING"},
        ]
    }


def _algorithm_config(*, profile_rows=2, num_samples=3) -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "llm_profile": {"llm_profile": "Test Profile"},
                "model_parameter": {"profile_rows": profile_rows},
                "model_fitting": {"user_prompt_domain_context": "Clinical registry data."},
                "sampling": {
                    "num_samples": num_samples,
                    "temperature": 0.3,
                    "top_p": 0.9,
                },
            }
        }
    }


def _dataset() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [28, 35, 44],
            "height": [168.0, 176.3, 182.1],
            "risk": [False, True, True],
            "group": ["A", "B", "A"],
        }
    )


def _set_llm_env(monkeypatch, provider="ollama") -> None:
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_IDS", "test-profile")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_NAME", "Test Profile")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_PROVIDER", provider)
    monkeypatch.setenv(
        "CINNAMON_LLM_PROFILE_TEST_PROFILE_MODEL_NAME",
        "llama3.1:8b" if provider == "ollama" else "gpt-test",
    )
    monkeypatch.setenv(
        "CINNAMON_LLM_PROFILE_TEST_PROFILE_BASE_URL",
        "http://127.0.0.1:11434" if provider == "ollama" else "http://gpu.example.org:7086",
    )
    monkeypatch.setenv(
        "CINNAMON_LLM_PROFILE_TEST_PROFILE_ENDPOINT_PATH",
        "/api/generate" if provider == "ollama" else "/v1/chat/completions",
    )
    monkeypatch.setenv(
        "CINNAMON_LLM_PROFILE_TEST_PROFILE_HEALTHCHECK_PATH",
        "/api/tags" if provider == "ollama" else "/v1/models",
    )
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_TIMEOUT_SECONDS", "5")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_MAX_RETRIES", "2")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_VERIFY_SSL", "true")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_MAX_TOKENS", "1024")


class _DummyResponse:
    def __init__(self, payload: dict):
        self.payload = payload
        self.status_code = 200

    def raise_for_status(self):
        return None

    def json(self):
        return self.payload


def _initialize_synthesizer(config: dict) -> LlmTabularSynthesizer:
    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()
    return synthesizer


def test_llm_tabular_generates_structured_rows_and_includes_profiles(monkeypatch):
    _set_llm_env(monkeypatch)
    prompts = []

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        prompts.append(kwargs["json"]["prompt"])
        index = len(prompts)
        return _DummyResponse(
            {
                "response": json.dumps(
                    {
                        "rows": [
                            {"age": 30 + index, "height": 170.0 + index, "risk": True, "group": "A"}
                        ]
                    }
                )
            }
        )

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    sample = _initialize_synthesizer(_algorithm_config()).sample()

    assert len(sample) == 3
    assert list(sample.columns) == ["age", "height", "risk", "group"]
    assert len(prompts) == 3
    assert "only structured tabular data" in prompts[0]
    assert "calculated from 2 of 3 input rows" in prompts[0]
    assert "Statistical column profiles:" in prompts[0]
    assert "- age (INTEGER): min=" in prompts[0]
    assert '"rows": [{"age": "<value>"' in prompts[0]
    assert "Generate exactly 1 row." in prompts[0]
    assert "REFERENCE EXAMPLES" not in prompts[0]


def test_llm_tabular_supports_openai_compatible(monkeypatch):
    _set_llm_env(monkeypatch, provider="openai_compatible")
    calls = {"count": 0}

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"data": [{"id": "gpt-test"}]})
        calls["count"] += 1
        content = json.dumps(
            {"rows": [{"age": 29, "height": 169.5, "risk": False, "group": "B"}]}
        )
        return _DummyResponse({"choices": [{"message": {"content": content}}]})

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    sample = _initialize_synthesizer(_algorithm_config(num_samples=1)).sample()

    assert calls["count"] == 1
    assert sample.to_dict(orient="records") == [
        {"age": 29, "height": 169.5, "risk": False, "group": "B"}
    ]


def test_llm_tabular_rejects_text_columns():
    synthesizer = LlmTabularSynthesizer()
    with pytest.raises(SynthesizerOperationError, match="only supports structured data"):
        synthesizer.initialize_attribute_configuration(
            {"configurations": [{"index": 0, "name": "notes", "type": "TEXT"}]}
        )


def test_llm_tabular_caps_profile_rows_at_dataset_size(monkeypatch):
    _set_llm_env(monkeypatch)
    monkeypatch.setattr(
        "synthetic_tabular_data_generator.llm.client.requests.request",
        lambda method, url, **kwargs: _DummyResponse({"models": [{"name": "llama3.1:8b"}]}),
    )
    synthesizer = _initialize_synthesizer(_algorithm_config(profile_rows=999, num_samples=1))

    assert synthesizer._requested_profile_rows == 999
    assert synthesizer._profile_rows_used == len(_dataset())
    assert "calculated from 3 of 3 input rows" in synthesizer._generation_prompt_prefix


def test_llm_tabular_uses_all_rows_for_unresolved_dataset_placeholder(monkeypatch):
    _set_llm_env(monkeypatch)
    monkeypatch.setattr(
        "synthetic_tabular_data_generator.llm.client.requests.request",
        lambda method, url, **kwargs: _DummyResponse({"models": [{"name": "llama3.1:8b"}]}),
    )
    synthesizer = _initialize_synthesizer(
        _algorithm_config(profile_rows="$dataset.original.numberRows", num_samples=1)
    )

    assert synthesizer._profile_rows_used == len(_dataset())


def test_llm_tabular_rejects_non_positive_profile_rows(monkeypatch):
    _set_llm_env(monkeypatch)
    synthesizer = LlmTabularSynthesizer()
    with pytest.raises(SynthesizerOperationError, match="profile_rows must be greater than 0"):
        synthesizer.initialize_anonymization_configuration(_algorithm_config(profile_rows=0))


def test_llm_tabular_maps_positional_column_names(monkeypatch):
    _set_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        return _DummyResponse(
            {
                "response": json.dumps(
                    {"rows": [{"column_a": 31, "column_b": 171.0, "column_c": True, "column_d": "A"}]}
                )
            }
        )

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    sample = _initialize_synthesizer(_algorithm_config(num_samples=1)).sample()

    assert sample.to_dict(orient="records") == [
        {"age": 31, "height": 171.0, "risk": True, "group": "A"}
    ]


def test_llm_tabular_reports_sampling_completion(monkeypatch):
    _set_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        return _DummyResponse(
            {"response": json.dumps({"rows": [{"age": 30, "height": 170.0, "risk": True, "group": "A"}]})}
        )

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    synthesizer = LlmTabularSynthesizer()
    updates = []
    synthesizer.set_progress_callback(lambda step, remaining: updates.append((step, remaining)))
    synthesizer.initialize_anonymization_configuration(_algorithm_config(num_samples=1))
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()
    synthesizer.sample()

    assert updates[-1] == ("sampling", 0)


def test_llm_tabular_logs_invalid_response_diagnostics(monkeypatch):
    _set_llm_env(monkeypatch)
    logs = []

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        return _DummyResponse({"response": json.dumps({"rows": [{"age": "age"}]})})

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    monkeypatch.setattr("builtins.print", lambda message: logs.append(message))
    synthesizer = _initialize_synthesizer(_algorithm_config(num_samples=1))

    with pytest.raises(SynthesizerOperationError):
        synthesizer.sample()

    assert any("[LLM_TABULAR_STRUCTURED_GENERATION]" in message for message in logs)
    assert any("unusable_rows=" in message for message in logs)
