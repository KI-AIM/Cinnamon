import json
import sys
from pathlib import Path

import pandas as pd
import pytest
import requests

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from data_processing.utils import FAILED_TEXT_GENERATION, MISSING_VALUE_STRING
from synthetic_tabular_data_generator.algorithms.llm_text_only_semantic_variation_synthesis import (
    LlmTextOnlySemanticVariationSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import SynthesizerOperationError


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "summary", "type": "TEXT"},
            {"index": 1, "name": "recommendation", "type": "TEXT"},
        ]
    }


def _algorithm_config() -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "llm_profile": {
                    "llm_profile": "Test Profile",
                },
                "model_parameter": {},
                "model_fitting": {
                    "user_prompt_domain_context": "German discharge summaries.",
                    "required_attributes": [
                        {
                            "name": "Blutdruck",
                            "description": "Nur Hinweise auf hohen Blutdruck einschliessen.",
                        }
                    ],
                },
                "sampling": {
                    "num_samples": 2,
                    "temperature": 0.8,
                    "top_p": 0.95,
                },
            }
        }
    }


def _set_shared_llm_env(monkeypatch) -> None:
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_IDS", "test-profile")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_NAME", "Test Profile")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_PROVIDER", "ollama")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_MODEL_NAME", "llama3.1:8b")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_BASE_URL", "http://127.0.0.1:11434")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_ENDPOINT_PATH", "/api/generate")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_HEALTHCHECK_PATH", "/api/tags")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_API_KEY", "")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_TIMEOUT_SECONDS", "5")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_MAX_RETRIES", "2")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_VERIFY_SSL", "true")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_MAX_TOKENS", "1024")


class _DummyResponse:
    def __init__(self, payload: dict, status_code: int = 200):
        self._payload = payload
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            error = requests.exceptions.HTTPError(f"HTTP {self.status_code}")
            error.response = self
            raise error
        return None

    def json(self):
        return self._payload


def test_llm_text_only_semantic_variation_synthesis_generates_related_fictional_rows(monkeypatch):
    _set_shared_llm_env(monkeypatch)
    post_attempts = {"count": 0}

    dataset = pd.DataFrame(
        {
            "summary": ["Patient stable after treatment and discharge tomorrow."],
            "recommendation": ["Follow-up in two weeks."],
        }
    )

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            post_attempts["count"] += 1
            prompt = kwargs["json"]["prompt"]
            assert "You generate new TEXT values for a fictional patient based on a source table row." in prompt
            assert "- Blutdruck: Nur Hinweise auf hohen Blutdruck einschliessen." in prompt
            assert '"summary": "Patient stable after treatment and discharge tomorrow."' in prompt
            assert '"recommendation": "Follow-up in two weeks."' in prompt
            if post_attempts["count"] == 1:
                return _DummyResponse(
                    {
                        "response": json.dumps(
                            {
                                "row": {
                                    "summary": "A clinically stable patient is planned for discharge later this week after an uncomplicated recovery.",
                                    "recommendation": "Outpatient reassessment within ten days is advised.",
                                }
                            }
                        )
                    }
                )
            return _DummyResponse(
                {
                        "response": json.dumps(
                            {
                                "row": {
                                    "summary": "Symptoms improved under therapy, and the fictional patient can likely leave the ward soon despite elevated Blutdruck values.",
                                    "recommendation": "Primary care follow-up should be arranged for the next one to two weeks because the Blutdruck should be rechecked.",
                                }
                            }
                        )
                    }
                )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTextOnlySemanticVariationSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert post_attempts["count"] == 3
    assert len(sample) == 2
    assert sample.to_dict(orient="records") == [
        {
            "summary": "Symptoms improved under therapy, and the fictional patient can likely leave the ward soon despite elevated Blutdruck values.",
            "recommendation": "Primary care follow-up should be arranged for the next one to two weeks because the Blutdruck should be rechecked.",
        },
        {
            "summary": "Symptoms improved under therapy, and the fictional patient can likely leave the ward soon despite elevated Blutdruck values.",
            "recommendation": "Primary care follow-up should be arranged for the next one to two weeks because the Blutdruck should be rechecked.",
        },
    ]


def test_llm_text_only_semantic_variation_synthesis_marks_failed_text_after_invalid_responses(monkeypatch):
    _set_shared_llm_env(monkeypatch)
    logs = []

    dataset = pd.DataFrame(
        {
            "summary": ["Patient stable after treatment."],
            "recommendation": [MISSING_VALUE_STRING],
        }
    )

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            return _DummyResponse({"response": "not valid json"})
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    monkeypatch.setattr("builtins.print", lambda message: logs.append(message))

    synthesizer = LlmTextOnlySemanticVariationSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {
            "summary": FAILED_TEXT_GENERATION,
            "recommendation": MISSING_VALUE_STRING,
        },
        {
            "summary": FAILED_TEXT_GENERATION,
            "recommendation": MISSING_VALUE_STRING,
        },
    ]
    assert any("[LLM_TEXT_REWRITE]" in entry for entry in logs)


def test_llm_text_only_semantic_variation_synthesis_rejects_non_text_columns():
    synthesizer = LlmTextOnlySemanticVariationSynthesisSynthesizer()

    with pytest.raises(SynthesizerOperationError, match="only supports TEXT columns"):
        synthesizer.initialize_attribute_configuration(
            {
                "configurations": [
                    {"index": 0, "name": "age", "type": "INTEGER"},
                    {"index": 1, "name": "summary", "type": "TEXT"},
                ]
            }
        )
