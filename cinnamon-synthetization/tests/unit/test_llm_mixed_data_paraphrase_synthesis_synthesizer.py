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
from synthetic_tabular_data_generator.algorithms.llm_mixed_data_indirect_identifier_rewrite_synthesis import (
    LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.algorithms.llm_mixed_data_embedding_nearest_neighbor_synthesis import (
    LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer,
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


def test_mixed_indirect_identifier_rewrite_then_aligns_structured_values(monkeypatch):
    _set_llm_env(monkeypatch)
    prompts = []

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        prompt = kwargs["json"]["prompt"]
        prompts.append(prompt)
        if len(prompts) == 1:
            return _DummyResponse(
                {"response": json.dumps({"row": {"note": "Der etwa 80-jährige Patient wurde entlassen."}})}
            )
        return _DummyResponse(
            {"response": json.dumps({"row": {"age": 80, "note": "must be ignored"}})}
        )

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    attribute_config = {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "note", "type": "TEXT"},
        ]
    }
    algorithm_config = {
        "synthetization_configuration": {
            "algorithm": {
                "llm_profile": {"llm_profile": "Test Profile"},
                "model_parameter": {"profile_rows": 100},
                "model_fitting": {"indirect_identifier_level": "high"},
                "sampling": {"num_samples": 1, "temperature": 0.2, "top_p": 0.9},
            }
        }
    }
    dataset = pd.DataFrame([{"age": 83, "note": "Der 83-jährige Max Mustermann wurde entlassen."}])
    reference = pd.DataFrame([{"age": 80, "note": "Referenztext"}])

    synthesizer = LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(attribute_config)
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(reference)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert sample.to_dict(orient="records") == [
        {"age": 80, "note": "Der etwa 80-jährige Patient wurde entlassen."}
    ]
    assert len(prompts) == 2
    assert "expert clinical de-identification rewriter" in prompts[0]
    assert "selected anonymization level: HIGH" in prompts[0]
    assert '"age"' not in prompts[0]
    assert "Statistical profiles were calculated from 1 of 1 reference rows." in prompts[1]
    assert '"note": "Der etwa 80-jährige Patient wurde entlassen."' in prompts[1]


def test_mixed_embedding_combines_structured_similarity_and_generates_extra_rows(monkeypatch):
    _set_llm_env(monkeypatch)
    prompts = []

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        prompt = kwargs["json"]["prompt"]
        prompts.append(prompt)
        if "SOURCE ROW" in prompt:
            assert '"age"' not in prompt.split("SOURCE ROW", 1)[1].split("NEAREST-NEIGHBOR", 1)[0]
            assert "Gardening was discussed during an otherwise routine visit." in prompt
            assert "Hypertension required urgent medication adjustment." not in prompt
            return _DummyResponse(
                {"response": json.dumps({"row": {"note": "A new clinically plausible report."}})}
            )
        return _DummyResponse(
            {"response": json.dumps({"row": {"age": 80, "visit_date": "02.01.2024", "note": "ignored"}})}
        )

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    date_config = {
        "index": 1,
        "name": "visit_date",
        "type": "DATE",
        "configurations": [{"dateFormatter": "dd.MM.yyyy"}],
    }
    attribute_config = {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            date_config,
            {"index": 2, "name": "note", "type": "TEXT"},
        ]
    }
    algorithm_config = {
        "synthetization_configuration": {
            "algorithm": {
                "llm_profile": {"llm_profile": "Test Profile"},
                "model_parameter": {
                    "profile_rows": 99,
                    "few_shot_examples": 1,
                    "embedding_provider": "bm25",
                    "similarity_function": "sparse_cosine",
                    "exclude_self_match": True,
                    "text_similarity_weight": 0.0,
                    "structured_similarity_weight": 1.0,
                },
                "model_fitting": {},
                "sampling": {"num_samples": 3, "temperature": 0.8, "top_p": 0.95},
            }
        }
    }
    source_date = int(pd.Timestamp("2024-01-02").timestamp())
    old_date = int(pd.Timestamp("2010-01-01").timestamp())
    dataset = pd.DataFrame([{"age": 80, "visit_date": source_date, "note": "Hypertension follow-up."}])
    reference = pd.DataFrame(
        [
            {
                "age": 20,
                "visit_date": old_date,
                "note": "Hypertension required urgent medication adjustment.",
            },
            {
                "age": 80,
                "visit_date": source_date,
                "note": "Gardening was discussed during an otherwise routine visit.",
            },
        ]
    )

    synthesizer = LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(attribute_config)
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(reference)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 3
    assert sample["age"].tolist() == [80, 80, 80]
    assert sample["note"].tolist() == ["A new clinically plausible report."] * 3
    assert len(prompts) == 6
    assert all(
        "Statistical profiles were calculated from 2 of 2 reference rows." in prompt
        for prompt in prompts
        if "ROW WITH REWRITTEN TEXT" in prompt
    )
    assert synthesizer._normalize_structured_similarity_value(date_config, "02.01.2024") == source_date
