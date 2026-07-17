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
from synthetic_tabular_data_generator.algorithms.llm_text_only_paraphrase_synthesis import (
    LlmTextOnlyParaphraseSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import SynthesizerOperationError


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "summary", "type": "TEXT"},
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
                },
                "sampling": {
                    "num_samples": 1,
                    "temperature": 0.2,
                    "top_p": 0.9,
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


def test_llm_text_only_paraphrase_synthesis_retries_verbatim_copy_and_returns_rewrite(monkeypatch):
    _set_shared_llm_env(monkeypatch)
    post_attempts = {"count": 0}

    dataset = pd.DataFrame(
        {
            "summary": ["Patient stable after treatment and discharge tomorrow."],
        }
    )

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            post_attempts["count"] += 1
            prompt = kwargs["json"]["prompt"]
            assert "You rewrite the TEXT value of a table row without losing information." in prompt
            assert "Always write the output TEXT in the same language as the source TEXT. Do not translate it" in prompt
            assert "Rewrite each sentence with substantially different wording and sentence structure." in prompt
            assert "Prefer changing active/passive voice, clause order, and sentence openings." in prompt
            assert '"summary": "Patient stable after treatment and discharge tomorrow."' in prompt
            if post_attempts["count"] == 1:
                return _DummyResponse(
                    {
                        "response": json.dumps(
                            {
                                "row": {
                                    "summary": "Patient stable after treatment and discharge tomorrow.",
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
                                "summary": "The patient is stable after treatment and is expected to be discharged tomorrow.",
                            }
                        }
                    )
                }
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTextOnlyParaphraseSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert post_attempts["count"] == 2
    assert sample.to_dict(orient="records") == [
        {
            "summary": "The patient is stable after treatment and is expected to be discharged tomorrow.",
        }
    ]


def test_llm_text_only_paraphrase_synthesis_marks_failed_text_after_invalid_responses(monkeypatch):
    _set_shared_llm_env(monkeypatch)
    logs = []

    dataset = pd.DataFrame(
        {
            "summary": ["Patient stable after treatment."],
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

    synthesizer = LlmTextOnlyParaphraseSynthesisSynthesizer()
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
        }
    ]
    assert any("[LLM_TEXT_REWRITE]" in entry for entry in logs)


def test_llm_text_only_paraphrase_synthesis_rejects_non_text_columns():
    synthesizer = LlmTextOnlyParaphraseSynthesisSynthesizer()

    with pytest.raises(SynthesizerOperationError, match="only supports TEXT columns"):
        synthesizer.initialize_attribute_configuration(
            {
                "configurations": [
                    {"index": 0, "name": "age", "type": "INTEGER"},
                    {"index": 1, "name": "summary", "type": "TEXT"},
                ]
            }
        )


def test_llm_text_only_paraphrase_synthesis_rejects_multiple_text_columns():
    synthesizer = LlmTextOnlyParaphraseSynthesisSynthesizer()

    with pytest.raises(SynthesizerOperationError, match="requires exactly one TEXT column"):
        synthesizer.initialize_attribute_configuration(
            {
                "configurations": [
                    {"index": 0, "name": "summary", "type": "TEXT"},
                    {"index": 1, "name": "recommendation", "type": "TEXT"},
                ]
            }
        )


def test_llm_text_only_paraphrase_synthesis_rejects_header_like_placeholder_input(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    dataset = pd.DataFrame(
        {
            "summary": ["dokument_text"],
        }
    )

    synthesizer = LlmTextOnlyParaphraseSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    monkeypatch.setattr(synthesizer, "_initialize_llm_backend", lambda **_kwargs: None)
    synthesizer.initialize_synthesizer()

    with pytest.raises(SynthesizerOperationError, match="column-header or placeholder values"):
        synthesizer.fit()


def test_llm_text_only_paraphrase_synthesis_skips_header_like_first_row_before_prompting(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    dataset = pd.DataFrame(
        {
            "summary": [
                "dokument_text",
                "Patient stable after treatment and discharge tomorrow.",
            ],
        }
    )

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert '"summary": "dokument_text"' not in prompt
            assert '"summary": "Patient stable after treatment and discharge tomorrow."' in prompt
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {
                            "row": {
                                "summary": "The patient is stable after treatment and is expected to be discharged tomorrow.",
                            }
                        }
                    )
                }
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTextOnlyParaphraseSynthesisSynthesizer()
    config = _algorithm_config()
    config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 1
    synthesizer.initialize_anonymization_configuration(config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {
            "summary": "The patient is stable after treatment and is expected to be discharged tomorrow.",
        }
    ]
