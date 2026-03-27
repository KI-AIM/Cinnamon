import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_attribute_to_text import (  # noqa: E402
    LlmAttributeToTextSynthesizer,
)


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "gender", "type": "STRING"},
            {"index": 2, "name": "doctor_letter", "type": "TEXT"},
        ]
    }


def _algorithm_config() -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {
                    "provider": "ollama",
                    "model_name": "llama3.1:8b",
                    "base_url": "http://127.0.0.1:11434",
                    "endpoint_path": "/api/generate",
                    "healthcheck_path": "/api/tags",
                    "temperature": 0.2,
                    "top_p": 0.9,
                    "max_tokens": 512,
                    "target_text_column": "doctor_letter",
                    "user_prompt": "Schreibe mir ein Arztbrief basierend auf den Daten.",
                },
                "model_fitting": {
                    "max_retries": 2,
                    "timeout_seconds": 5,
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


def _dataset_without_target_text() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [80, 65],
            "gender": ["F", "M"],
            "doctor_letter": [pd.NA, pd.NA],
        }
    )


def test_llm_attribute_to_text_generates_only_target_text_and_keeps_rows(monkeypatch):
    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert "Target TEXT column: doctor_letter" in prompt
            assert "User instruction: Schreibe mir ein Arztbrief basierend auf den Daten." in prompt
            assert "Immutable attributes JSON (must not be changed):" in prompt
            assert "Return ONLY valid JSON in this exact format:" in prompt

            if '"age": 80' in prompt:
                return _DummyResponse({"response": json.dumps({"text": "Arztbrief fuer Patientin, Alter 80."})})
            return _DummyResponse({"response": json.dumps({"text": "Arztbrief fuer Patient, Alter 65."})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    source = _dataset_without_target_text()
    synthesizer = LlmAttributeToTextSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(source)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    assert len(result) == len(source)
    assert result["age"].tolist() == source["age"].tolist()
    assert result["gender"].tolist() == source["gender"].tolist()
    assert "doctor_letter" in result.columns
    assert result["doctor_letter"].tolist() == [
        "Arztbrief fuer Patientin, Alter 80.",
        "Arztbrief fuer Patient, Alter 65.",
    ]
