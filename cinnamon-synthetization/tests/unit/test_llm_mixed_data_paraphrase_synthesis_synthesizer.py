import json
import sys
from pathlib import Path

import pandas as pd
import requests

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_mixed_data_paraphrase_synthesis import (
    LlmMixedDataParaphraseSynthesisSynthesizer,
)


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
    def __init__(self, payload: dict):
        self._payload = payload
        self.status_code = 200

    def raise_for_status(self):
        return None

    def json(self):
        return self._payload


def test_mixed_paraphrase_rewrites_text_then_aligns_structured_values(monkeypatch):
    _set_llm_env(monkeypatch)
    prompts = []

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST":
            prompt = kwargs["json"]["prompt"]
            prompts.append(prompt)
            if len(prompts) == 1:
                return _DummyResponse(
                    {"response": json.dumps({"row": {"note": "Der 83-jährige Patient wurde entlassen."}})}
                )
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {"row": {"age": 83, "group": "A", "note": "must be ignored"}}
                    )
                }
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    attribute_config = {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "group", "type": "STRING"},
            {"index": 2, "name": "note", "type": "TEXT"},
        ]
    }
    algorithm_config = {
        "synthetization_configuration": {
            "algorithm": {
                "llm_profile": {"llm_profile": "Test Profile"},
                "model_parameter": {"profile_rows": 50},
                "model_fitting": {},
                "sampling": {"num_samples": 1, "temperature": 0.2, "top_p": 0.9},
            }
        }
    }
    dataset = pd.DataFrame([{"age": 80, "group": "A", "note": "Entlassung des 83-jährigen Patienten."}])
    reference = pd.DataFrame([{"age": 83, "group": "A", "note": "Referenztext"}])

    synthesizer = LlmMixedDataParaphraseSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(attribute_config)
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(reference)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {"age": 83, "group": "A", "note": "Der 83-jährige Patient wurde entlassen."}
    ]
    assert len(prompts) == 2
    assert '"age"' not in prompts[0]
    assert "Statistical profiles were calculated from 1 of 1 reference rows." in prompts[1]
    assert '"age": 80' in prompts[1]
    assert '"note": "Der 83-jährige Patient wurde entlassen."' in prompts[1]
