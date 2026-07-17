import json
import sys
from pathlib import Path

import pandas as pd
import pytest
import requests

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_text_only_indirect_identifier_rewrite_synthesis import (
    LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import SynthesizerOperationError


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "summary", "type": "TEXT"},
        ]
    }


def _algorithm_config(*, num_samples: int = 1) -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "llm_profile": {
                    "llm_profile": "Test Profile",
                },
                "model_parameter": {},
                "model_fitting": {
                    "indirect_identifier_level": "medium",
                },
                "sampling": {
                    "num_samples": num_samples,
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


def test_llm_text_only_indirect_identifier_rewrite_synthesis_rewrites_with_selected_level(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    dataset = pd.DataFrame(
        {
            "summary": [
                "Unsere Patientin [NAME], die mit [ALTER] Jahren die aelteste Person mit Leukaemie in unserer Klinik war, stellte sich zur weiteren Therapieplanung vor.",
            ],
        }
    )

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert "You are an expert clinical de-identification rewriter." in prompt
            assert "Rewrite the TEXT value of one clinical table row into a safer version with lower re-identification risk." in prompt
            assert "Always write the output TEXT in the same language as the source TEXT. Do not translate it" in prompt
            assert "selected anonymization level: MEDIUM" in prompt
            assert "Rewrite the non-missing TEXT field in fluent clinical language." in prompt
            assert "Preserve the clinical meaning, but reduce direct and indirect identifiability." in prompt
            assert "PHI/direct identifiers must always be removed or replaced, regardless of the selected indirect identifier level." in prompt
            assert "PHI categories and required actions (always apply these rules):" in prompt
            assert "NAME: Names, initials, aliases, usernames, or handles" in prompt
            assert "Action: Redact detected values of this category by replacing the original span with [NAME]." in prompt
            assert "Indirect identifier handling rules (apply in all levels):" in prompt
            assert "Indirect identifier categories and required actions (MEDIUM):" in prompt
            assert "APPEARANCE: Mention of a person’s (also infant’s) weight, height" in prompt
            assert "Generalize or replace appearance details with broader non-identifying values while preserving medical relevance" in prompt
            assert "TIME: Mentions of age or time-related information" in prompt
            assert "Generalize or replace time information with broader but still structured intervals or phases while preserving clinical sequence." in prompt
            assert "Use different wording and sentence structure where possible." in prompt
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {
                            "row": {
                                "summary": "Eine Patientin im hoeheren Lebensalter stellte sich bei einer haematologischen Tumorerkrankung zur weiteren Therapieplanung vor.",
                            }
                        }
                    )
                }
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {
            "summary": "Eine Patientin im hoeheren Lebensalter stellte sich bei einer haematologischen Tumorerkrankung zur weiteren Therapieplanung vor.",
        }
    ]


def test_llm_text_only_indirect_identifier_rewrite_synthesis_allows_sampling_fewer_rows_than_dataset(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    dataset = pd.DataFrame(
        {
            "summary": [
                "Text eins.",
                "Text zwei.",
            ],
        }
    )

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert '"summary": "Text eins."' in prompt
            assert '"summary": "Text zwei."' not in prompt
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {
                            "row": {
                                "summary": "Umformulierter Text eins.",
                            }
                        }
                    )
                }
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config(num_samples=1))
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {
            "summary": "Umformulierter Text eins.",
        }
    ]


def test_llm_text_only_indirect_identifier_rewrite_synthesis_uses_low_level_prompt_rules(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    dataset = pd.DataFrame(
        {
            "summary": [
                "Patient wurde am 03.04.2024 im Klinikum vorgestellt und erhielt 5 mg eines Medikaments.",
            ],
        }
    )

    config = _algorithm_config()
    config["synthetization_configuration"]["algorithm"]["model_fitting"]["indirect_identifier_level"] = "low"

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert "selected anonymization level: LOW" in prompt
            assert "Preserve the clinical meaning and as much clinically relevant detail as possible." not in prompt
            assert "PHI categories and required actions (always apply these rules):" in prompt
            assert "Action: Redact detected values of this category by replacing the original span with [NAME]." in prompt
            assert "Indirect identifier categories and required actions (LOW):" in prompt
            assert "Keep clinically relevant appearance information." in prompt
            assert "Keep the clinically relevant event and outcome." in prompt
            assert "Lightly coarsen or replace exact ages, days, dates, times, and medication or laboratory timestamps" in prompt
            assert "For LOW level, prioritize clinical fidelity and information preservation over broad generalization." in prompt
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {
                            "row": {
                                "summary": "Der Patient wurde am 03.04.2024 in einer Klinik vorgestellt und erhielt 5 mg eines Medikaments.",
                            }
                        }
                    )
                }
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {
            "summary": "Der Patient wurde am 03.04.2024 in einer Klinik vorgestellt und erhielt 5 mg eines Medikaments.",
        },
    ]


def test_llm_text_only_indirect_identifier_rewrite_synthesis_uses_high_level_prompt_rules(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    dataset = pd.DataFrame(
        {
            "summary": [
                "Patient wurde mit einer sehr seltenen Diagnose am 03.04.2024 im Universitaetsklinikum vorgestellt.",
            ],
        }
    )

    config = _algorithm_config()
    config["synthetization_configuration"]["algorithm"]["model_fitting"]["indirect_identifier_level"] = "high"

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert "You are an expert clinical de-identification rewriter." in prompt
            assert "Rewrite the TEXT value of one clinical table row into a safer version with lower re-identification risk." in prompt
            assert "selected anonymization level: HIGH" in prompt
            assert "Preserve the broad clinical message, but strongly reduce direct and indirect identifiability." in prompt
            assert "PHI categories and required actions (always apply these rules):" in prompt
            assert "Action: Redact detected values of this category by replacing the original span with [NAME]." in prompt
            assert "Indirect identifier categories and required actions (HIGH):" in prompt
            assert "Strongly abstract or replace distinctive appearance details with plausible non-identifying alternatives." in prompt
            assert "Strongly abstract or replace distinctive event narratives with less specific but clinically coherent alternatives." in prompt
            assert "Actively obscure rare or unique clinical combinations by replacing them with plausible broader categories" in prompt
            assert "Strongly simplify, abstract, or replace distinctive care pathways and transfer patterns with generic care phases" in prompt
            assert "prioritize privacy over fine-grained detail" in prompt
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {
                            "row": {
                                "summary": "Eine Person mit einer seltenen Erkrankung wurde in einem spezialisierten Zentrum behandelt.",
                            }
                        }
                    )
                }
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {
            "summary": "Eine Person mit einer seltenen Erkrankung wurde in einem spezialisierten Zentrum behandelt.",
        },
    ]


def test_llm_text_only_indirect_identifier_rewrite_synthesis_rejects_unknown_level(monkeypatch):
    _set_shared_llm_env(monkeypatch)
    synthesizer = LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer()
    config = _algorithm_config()
    config["synthetization_configuration"]["algorithm"]["model_fitting"]["indirect_identifier_level"] = "extreme"

    with pytest.raises(
        SynthesizerOperationError,
        match="indirect_identifier_level must be one of: low, medium, high",
    ):
        synthesizer.initialize_anonymization_configuration(config)


def test_llm_text_only_indirect_identifier_rewrite_synthesis_rejects_header_like_placeholder_input(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    dataset = pd.DataFrame(
        {
            "summary": ["dokument_text"],
        }
    )

    synthesizer = LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(dataset)
    monkeypatch.setattr(synthesizer, "_initialize_llm_backend", lambda **_kwargs: None)
    synthesizer.initialize_synthesizer()

    with pytest.raises(SynthesizerOperationError, match="column-header or placeholder values"):
        synthesizer.fit()
