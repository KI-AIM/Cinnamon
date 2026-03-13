import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_tabular import LlmTabularSynthesizer


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "height", "type": "DECIMAL"},
            {"index": 2, "name": "risk", "type": "BOOLEAN"},
            {"index": 3, "name": "group", "type": "STRING"},
        ]
    }


def _algorithm_config(provider: str = "ollama") -> dict:
    endpoint_path = "/api/generate" if provider == "ollama" else "/v1/chat/completions"
    healthcheck_path = "/api/tags" if provider == "ollama" else "/v1/models"
    base_url = "http://127.0.0.1:11434" if provider == "ollama" else "http://gpu.example.org:7086"
    model_name = "llama3.1:8b" if provider == "ollama" else "gpt-test"

    return {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {
                    "provider": provider,
                    "model_name": model_name,
                    "base_url": base_url,
                    "endpoint_path": endpoint_path,
                    "healthcheck_path": healthcheck_path,
                    "temperature": 0.2,
                    "top_p": 0.8,
                    "max_tokens": 512,
                },
                "model_fitting": {
                    "profile_rows": 50,
                    "few_shot_rows": 2,
                    "max_retries": 2,
                    "timeout_seconds": 5,
                },
                "sampling": {
                    "num_samples": 3,
                },
            }
        }
    }


class _DummyResponse:
    def __init__(self, payload: dict):
        self._payload = payload
        self.status_code = 200

    def raise_for_status(self):
        return None

    def json(self):
        return self._payload


def _dataset() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [28, 35, 44],
            "height": [168.0, 176.3, 182.1],
            "risk": [False, True, True],
            "group": ["A", "B", "A"],
        }
    )


def test_llm_tabular_synthesizer_generates_requested_rows_via_ollama(monkeypatch):
    call_counter = {"count": 0}

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            call_counter["count"] += 1
            if call_counter["count"] == 1:
                rows = [
                    {"age": 31, "height": 171.5, "risk": True, "group": "A"},
                    {"age": 47, "height": 183.2, "risk": False, "group": "B"},
                ]
            else:
                rows = [
                    {"age": 39, "height": 175.0, "risk": True, "group": "C"},
                ]
            return _DummyResponse({"response": json.dumps({"rows": rows})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config(provider="ollama"))
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert list(sample.columns) == ["age", "height", "risk", "group"]
    assert len(sample) == 3
    assert sample["age"].between(28, 44).all()
    assert sample["height"].between(168.0, 182.1).all()
    assert sample["risk"].isin([True, False]).all()


def test_llm_tabular_synthesizer_generates_requested_rows_via_openai_compatible(monkeypatch):
    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/v1/models"):
            return _DummyResponse({"data": [{"id": "gpt-test"}]})

        if method == "POST" and url.endswith("/v1/chat/completions"):
            content = json.dumps(
                {
                    "rows": [
                        {"age": 29, "height": 170.5, "risk": False, "group": "A"},
                        {"age": 36, "height": 177.5, "risk": True, "group": "B"},
                        {"age": 41, "height": 180.2, "risk": True, "group": "C"},
                    ]
                }
            )
            return _DummyResponse({"choices": [{"message": {"content": content}}]})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config(provider="openai_compatible"))
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 3
    assert sample["age"].tolist() == [29, 36, 41]
    assert sample["group"].tolist() == ["A", "B", "C"]


def test_llm_tabular_synthesizer_maps_positional_column_names(monkeypatch):
    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            rows = [
                {"column_a": 31, "column_b": 171.5, "column_c": True, "column_d": "A"},
                {"column_a": 42, "column_b": 178.0, "column_c": False, "column_d": "B"},
            ]
            return _DummyResponse({"response": json.dumps({"rows": rows})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config(provider="ollama")
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 2

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 2
    assert set(sample["age"].tolist()).issubset({31, 42})
    assert set(sample["group"].tolist()).issubset({"A", "B"})
