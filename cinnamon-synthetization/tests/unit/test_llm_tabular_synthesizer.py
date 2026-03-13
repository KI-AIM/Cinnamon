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
            prompt = kwargs["json"]["prompt"]
            assert "Generate exactly 1 rows." in prompt

            rows = [
                {"age": 30 + call_counter["count"], "height": 170.0 + call_counter["count"], "risk": True, "group": "A"},
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
    assert call_counter["count"] == 3
    assert sample["age"].between(28, 44).all()
    assert sample["height"].between(168.0, 182.1).all()
    assert sample["risk"].isin([True, False]).all()


def test_llm_tabular_synthesizer_generates_requested_rows_via_openai_compatible(monkeypatch):
    call_counter = {"count": 0}

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/v1/models"):
            return _DummyResponse({"data": [{"id": "gpt-test"}]})

        if method == "POST" and url.endswith("/v1/chat/completions"):
            call_counter["count"] += 1
            prompt = kwargs["json"]["messages"][1]["content"]
            assert "Generate exactly 1 rows." in prompt
            content = json.dumps(
                {
                    "rows": [
                        {
                            "age": 28 + call_counter["count"],
                            "height": 169.5 + call_counter["count"],
                            "risk": call_counter["count"] % 2 == 0,
                            "group": chr(ord("A") + call_counter["count"] - 1),
                        },
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
    assert call_counter["count"] == 3
    assert sample["age"].tolist() == [29, 30, 31]
    assert sample["group"].tolist() == ["A", "B", "C"]


def test_llm_tabular_synthesizer_maps_positional_column_names(monkeypatch):
    call_counter = {"count": 0}

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            call_counter["count"] += 1
            rows = [
                {
                    "column_a": 30 + call_counter["count"],
                    "column_b": 170.5 + call_counter["count"],
                    "column_c": call_counter["count"] % 2 == 1,
                    "column_d": chr(ord("A") + call_counter["count"] - 1),
                },
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
    assert call_counter["count"] == 2
    assert sample["age"].tolist() == [31, 32]
    assert sample["group"].tolist() == ["A", "B"]
