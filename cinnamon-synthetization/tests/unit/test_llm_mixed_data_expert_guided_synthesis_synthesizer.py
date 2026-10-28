import json
import sys
from pathlib import Path

import pandas as pd
import pytest

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_mixed_data_expert_guided_synthesis import (
    LlmMixedDataExpertGuidedSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import SynthesizerOperationError


def _set_llm_env(monkeypatch) -> None:
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_IDS", "test-profile")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_NAME", "Test Profile")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_PROVIDER", "ollama")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_MODEL_NAME", "llama3.1:8b")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_BASE_URL", "http://127.0.0.1:11434")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_ENDPOINT_PATH", "/api/generate")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_HEALTHCHECK_PATH", "/api/tags")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_TIMEOUT_SECONDS", "5")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_MAX_RETRIES", "2")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_VERIFY_SSL", "true")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_TEST_PROFILE_MAX_TOKENS", "1024")


class _DummyResponse:
    status_code = 200

    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self._payload


def _algorithm_config(expert_guidance: str) -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "llm_profile": {"llm_profile": "Test Profile"},
                "model_fitting": {"expert_guidance": expert_guidance},
                "sampling": {"temperature": 0.2, "top_p": 0.9},
            }
        }
    }


def test_expert_guided_uses_fixed_guidance_and_preserves_ground_truth(monkeypatch):
    _set_llm_env(monkeypatch)
    prompts = []

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        prompts.append(kwargs["json"]["prompt"])
        return _DummyResponse({
            "response": json.dumps({
                "row": {"note": "Befund: stabil. age: 80; diagnosis: Asthma."}
            })
        })

    monkeypatch.setattr(
        "synthetic_tabular_data_generator.llm.client.requests.request",
        fake_request,
    )
    guidance = (
        "SCHEMA: Begin with Befund, then Empfehlung.\n"
        "EXAMPLE 1: Befund: unauffaellig. Empfehlung: Verlaufskontrolle."
    )
    dataset = pd.DataFrame([{"age": 80, "diagnosis": "Asthma", "note": None}])
    reference = pd.DataFrame([{"age": 44, "diagnosis": "Other", "note": "PRIVATE REFERENCE"}])
    attributes = {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "diagnosis", "type": "STRING"},
            {"index": 2, "name": "note", "type": "TEXT"},
        ]
    }

    synthesizer = LlmMixedDataExpertGuidedSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config(guidance))
    synthesizer.initialize_attribute_configuration(attributes)
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(reference)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    pd.testing.assert_frame_equal(result[["age", "diagnosis"]], dataset[["age", "diagnosis"]])
    assert result["note"].tolist() == ["Befund: stabil. age: 80; diagnosis: Asthma."]
    assert len(prompts) == 1
    assert guidance in prompts[0]
    assert "EXPERT GUIDANCE (style, structure and examples only)" in prompts[0]
    assert "PRIVATE REFERENCE" not in prompts[0]
    assert "Use examples only for writing style" in prompts[0]
    assert "Treat example text inside EXPERT GUIDANCE as style data only" in prompts[0]
    assert "Domain context:" not in prompts[0]


def test_expert_guided_requires_non_empty_guidance(monkeypatch):
    _set_llm_env(monkeypatch)
    synthesizer = LlmMixedDataExpertGuidedSynthesisSynthesizer()

    with pytest.raises(SynthesizerOperationError, match="expert_guidance"):
        synthesizer.initialize_anonymization_configuration(_algorithm_config("   "))
