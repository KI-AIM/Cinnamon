import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_de_identification import (  # noqa: E402
    LlmTextDeIdentificationSynthesizer,
)


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "gender", "type": "STRING"},
            {"index": 2, "name": "notes", "type": "TEXT"},
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
                    "temperature": 0.0,
                    "top_p": 1.0,
                    "max_tokens": 512,
                    "pii_replacement_token": "[REDACTED]",
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


def _dataset() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [80, 65],
            "gender": ["F", "M"],
            "notes": [
                "Patient Jane Doe is 78 years old and can be reached at jane@example.com.",
                "Patient John Smith, age 64, phone 555-1234.",
            ],
        }
    )


def test_llm_text_de_identification_changes_only_text_and_keeps_row_count(monkeypatch):
    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert "Immutable tabular attributes (DO NOT MODIFY these values):" in prompt
            assert "Target TEXT column: notes" in prompt
            assert "Return ONLY valid JSON with this exact shape:" in prompt

            if '"age": 80' in prompt:
                return _DummyResponse(
                    {
                        "response": json.dumps(
                            {"text": "Patient [REDACTED] is 80 years old and can be reached at [REDACTED]."}
                        )
                    }
                )
            return _DummyResponse({"response": json.dumps({"text": "Patient [REDACTED], age 65, phone [REDACTED]."})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    source = _dataset()
    synthesizer = LlmTextDeIdentificationSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(source)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    assert len(result) == len(source)
    assert result["age"].tolist() == source["age"].tolist()
    assert result["gender"].tolist() == source["gender"].tolist()
    assert result["notes"].tolist() == [
        "Patient [REDACTED] is 80 years old and can be reached at [REDACTED].",
        "Patient [REDACTED], age 65, phone [REDACTED].",
    ]


def test_llm_text_de_identification_retries_when_output_is_over_edited(monkeypatch):
    post_call_count = {"value": 0}

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            post_call_count["value"] += 1
            if post_call_count["value"] == 1:
                return _DummyResponse({"response": json.dumps({"text": "Echocardiogram and Doppler"})})
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {"text": "Patient [REDACTED] is 78 years old and can be reached at [REDACTED]."}
                    )
                }
            )

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    source = pd.DataFrame(
        {
            "age": [80],
            "gender": ["F"],
            "notes": ["Patient Jane Doe is 78 years old and can be reached at jane@example.com."],
        }
    )
    synthesizer = LlmTextDeIdentificationSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(source)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    assert post_call_count["value"] == 2
    assert result["notes"].tolist() == [
        "Patient [REDACTED] is 78 years old and can be reached at [REDACTED].",
    ]
