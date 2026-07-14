import json
import sys
from pathlib import Path

import pandas as pd
import requests

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_text_only_embedding_nearest_neighbor_synthesis import (
    LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer,
)


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
                "model_parameter": {
                    "embedding_provider": "bm25",
                    "few_shot_examples": 1,
                    "similarity_function": "sparse_cosine",
                    "exclude_self_match": True,
                },
                "model_fitting": {
                    "user_prompt_domain_context": "German discharge summaries.",
                },
                "sampling": {
                    "num_samples": 1,
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


def test_llm_text_only_embedding_nearest_neighbor_synthesis_uses_bm25_reference_examples(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    dataset = pd.DataFrame(
        {
            "summary": ["Blood pressure remained elevated after therapy and follow-up was arranged."],
        }
    )
    reference_dataset = pd.DataFrame(
        {
            "summary": [
                "Blood pressure remained elevated after therapy and follow-up was arranged.",
                "Hypertension follow-up was scheduled after persistently elevated blood pressure.",
                "The patient enjoyed gardening and adopted a new cat.",
            ],
        }
    )

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert "NEAREST-NEIGHBOR REFERENCE EXAMPLES" in prompt
            assert "Information:\n" in prompt
            assert "Treat the source row and reference texts as pools of domain, content, and style signals" in prompt
            assert "Preserve only the medical domain, document type, language, tone, approximate length" in prompt
            assert "must differ substantially from the source in at least five of these areas" in prompt
            assert "Do not preserve the source case's narrative blueprint" in prompt
            assert "The document date must not precede the described discharge date." in prompt
            assert "internally check: 1. chronology, 2. diagnosis and ICD consistency" in prompt
            assert "Keep a missing TEXT value as '__MISSING_VALUE__'." in prompt
            assert "Use the source row as the main clinical basis" not in prompt
            assert "Change at least three relevant case aspects" not in prompt
            assert "Hypertension follow-up was scheduled after persistently elevated blood pressure." in prompt
            assert "The patient enjoyed gardening and adopted a new cat." not in prompt
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {
                            "row": {
                                "summary": "Persistent hypertension required follow-up planning after therapy.",
                            }
                        }
                    )
                }
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(reference_dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {
            "summary": "Persistent hypertension required follow-up planning after therapy.",
        }
    ]
