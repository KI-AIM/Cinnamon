import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from data_processing.utils import FAILED_TEXT_GENERATION, MISSING_VALUE_STRING
from synthetic_tabular_data_generator.algorithms.llm_nearest_neighbor_few_shot_text_synthesis import (
    LlmNearestNeighborFewShotTextSynthesisSynthesizer,
)


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "group", "type": "STRING"},
            {"index": 2, "name": "notes", "type": "TEXT"},
        ]
    }


def _algorithm_config() -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {
                    "profile_rows": 1000,
                    "few_shot_rows": 2,
                    "similarity_strategy": "Random",
                },
                "model_fitting": {
                    "user_prompt_domain_context": "German clinical discharge summaries.",
                    "allow_structured_corrections": True,
                },
                "sampling": {
                    "num_samples": 2,
                    "temperature": 0.3,
                    "top_p": 0.9,
                },
            }
        }
    }


def _set_shared_llm_env(monkeypatch) -> None:
    monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "ollama")
    monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "llama3.1:8b")
    monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://127.0.0.1:11434")
    monkeypatch.setenv("CINNAMON_LLM_ENDPOINT_PATH", "/api/generate")
    monkeypatch.setenv("CINNAMON_LLM_HEALTHCHECK_PATH", "/api/tags")
    monkeypatch.setenv("CINNAMON_LLM_API_KEY", "")
    monkeypatch.setenv("CINNAMON_LLM_TIMEOUT_SECONDS", "5")
    monkeypatch.setenv("CINNAMON_LLM_MAX_RETRIES", "2")
    monkeypatch.setenv("CINNAMON_LLM_VERIFY_SSL", "true")
    monkeypatch.setenv("CINNAMON_LLM_TEMPERATURE", "0.3")
    monkeypatch.setenv("CINNAMON_LLM_TOP_P", "0.9")
    monkeypatch.setenv("CINNAMON_LLM_MAX_TOKENS", "1024")


class _DummyResponse:
    def __init__(self, payload: dict):
        self._payload = payload
        self.status_code = 200

    def raise_for_status(self):
        return None

    def json(self):
        return self._payload


def _synthetic_input() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [999, 50],
            "group": ["A", "B"],
            "notes": [MISSING_VALUE_STRING, MISSING_VALUE_STRING],
        }
    )


def _original_input() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [40, 52, 61],
            "group": ["A", "B", "A"],
            "notes": [
                "Patient stable after treatment.",
                "Requires follow-up in two weeks.",
                "No acute findings and good recovery.",
            ],
        }
    )


def test_llm_nearest_neighbor_few_shot_text_synthesis_generates_text_and_can_correct_structured_values(monkeypatch):
    call_count = {"post": 0}
    _set_shared_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            call_count["post"] += 1
            prompt = kwargs["json"]["prompt"]
            assert "Current synthetic row:" in prompt
            assert "Generate realistic values for TEXT columns: notes" in prompt
            if call_count["post"] == 1:
                return _DummyResponse({"response": json.dumps({"row": {"age": 45, "group": "A", "notes": "Stable clinical status."}})})
            return _DummyResponse({"response": json.dumps({"row": {"age": 51, "group": "B", "notes": "Follow-up appointment recommended."}})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmNearestNeighborFewShotTextSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_synthetic_input())
    synthesizer.initialize_reference_dataset(_original_input())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 2
    assert sample["notes"].tolist() == [
        "Stable clinical status.",
        "Follow-up appointment recommended.",
    ]
    assert sample["age"].tolist() == [45, 51]
    assert sample["group"].tolist() == ["A", "B"]


def test_llm_nearest_neighbor_few_shot_text_synthesis_marks_failed_text_after_invalid_responses(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            return _DummyResponse({"response": "no valid json"})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmNearestNeighborFewShotTextSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_synthetic_input())
    synthesizer.initialize_reference_dataset(_original_input())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample["age"].tolist() == [999, 50]
    assert sample["group"].tolist() == ["A", "B"]
    assert sample["notes"].tolist() == [FAILED_TEXT_GENERATION, FAILED_TEXT_GENERATION]


def test_llm_nearest_neighbor_few_shot_text_synthesis_reports_sampling_remaining_time_via_callback(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            return _DummyResponse({"response": json.dumps({"row": {"age": 45, "group": "A", "notes": "Stable clinical status."}})})
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config()
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 1

    synthesizer = LlmNearestNeighborFewShotTextSynthesisSynthesizer()
    updates = []
    synthesizer.set_progress_callback(lambda step, remaining_time: updates.append((step, remaining_time)))
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_synthetic_input())
    synthesizer.initialize_reference_dataset(_original_input())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    synthesizer.sample()

    assert updates[-1] == ("sampling", 0)


def test_llm_nearest_neighbor_few_shot_text_synthesis_uses_structured_attribute_neighbors(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    synthetic_input = pd.DataFrame(
        {
            "age": [53],
            "group": ["B"],
            "notes": [MISSING_VALUE_STRING],
        }
    )

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert '"age": 52' in prompt
            assert '"group": "B"' in prompt
            assert '"notes": "Requires follow-up in two weeks."' in prompt
            return _DummyResponse({"response": json.dumps({"row": {"age": 45, "group": "A", "notes": "Stable clinical status."}})})
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config()
    algorithm_config["synthetization_configuration"]["algorithm"]["model_parameter"]["similarity_strategy"] = "Attributes"
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 1

    synthesizer = LlmNearestNeighborFewShotTextSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(synthetic_input)
    synthesizer.initialize_reference_dataset(_original_input())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 1
    assert sample["notes"].iloc[0] == "Stable clinical status."
