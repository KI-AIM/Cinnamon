import json
import sys
from pathlib import Path

import pandas as pd
import pytest

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


SYNTHESIZERS = [
    LlmMixedDataParaphraseSynthesisSynthesizer,
    LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer,
    LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer,
]


def _initialize(monkeypatch, cls, dataset, reference, configs, responses, model_params=None):
    _set_llm_env(monkeypatch)
    prompts = []
    responses = iter(responses)

    def fake_request(method, url, **kwargs):
        if method == "GET":
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        prompts.append(kwargs["json"]["prompt"])
        return _DummyResponse({"response": json.dumps({"row": next(responses)})})

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    synthesizer = cls()
    synthesizer.initialize_anonymization_configuration({
        "synthetization_configuration": {"algorithm": {
            "llm_profile": {"llm_profile": "Test Profile"},
            "model_parameter": model_params or {},
            "model_fitting": {"indirect_identifier_level": "high"},
            # A stale text-stage count must never resample or truncate synthetic structured rows.
            "sampling": {"num_samples": 99, "temperature": 0.2, "top_p": 0.9},
        }},
    })
    synthesizer.initialize_attribute_configuration({"configurations": configs})
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_reference_dataset(reference)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()
    return synthesizer, prompts


@pytest.mark.parametrize("cls", SYNTHESIZERS)
def test_mixed_methods_preserve_ground_truth_and_generate_missing_text(monkeypatch, cls):
    dataset = pd.DataFrame({
        "age": pd.Series([80, 42], dtype="Int64"),
        "group": ["A", "B"],
        "note": [None, "note"],
    })
    reference = pd.DataFrame([{"age": 83, "group": "C", "note": "Gardening was discussed."}])
    configs = [{"name": "age", "type": "INTEGER"}, {"name": "group", "type": "STRING"},
               {"name": "note", "type": "TEXT"}]
    texts = ["Gardening was discussed. age: 80; group: A.", "Routine visit. age: 42; group: B."]
    synthesizer, prompts = _initialize(monkeypatch, cls, dataset, reference, configs,
                                      [{"age": 999, "group": "wrong", "note": text} for text in texts])
    result = synthesizer.sample()
    pd.testing.assert_frame_equal(result[["age", "group"]], dataset[["age", "group"]])
    assert result["note"].tolist() == texts
    assert len(prompts) == 2
    assert all('"age"' in prompt and "STRUCTURED GROUND TRUTH" in prompt for prompt in prompts)
    assert all("Additional information absent from the structured schema" in prompt for prompt in prompts)
    assert "Gardening was discussed." in prompts[0]
    if cls is LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer:
        assert "Selected anonymization level: HIGH" in prompts[0]
        assert "ONLY to additional reference details absent from the ground truth" in prompts[0]
        assert "NAME: Person names" in prompts[0]


def test_mixed_retries_incomplete_text_without_returning_it(monkeypatch):
    dataset = pd.DataFrame([{"age": 8, "note": None}])
    configs = [{"name": "age", "type": "INTEGER"}, {"name": "note", "type": "TEXT"}]
    synth, prompts = _initialize(monkeypatch, SYNTHESIZERS[0], dataset, dataset, configs,
                                [{"note": "age: 80"}, {"note": "Follow-up. age: 8."}])
    assert synth.sample()["note"].tolist() == ["Follow-up. age: 8."]
    assert len(prompts) == 2


def test_mixed_fails_if_llm_never_covers_all_facts(monkeypatch):
    dataset = pd.DataFrame([{"age": 8, "note": None}])
    configs = [{"name": "age", "type": "INTEGER"}, {"name": "note", "type": "TEXT"}]
    synth, _ = _initialize(monkeypatch, SYNTHESIZERS[0], dataset, dataset, configs,
                           [{"note": "No facts."}, {"note": "age: 8.5"}])
    with pytest.raises(RuntimeError, match="ground truth"):
        synth.sample()


def test_nearest_neighbors_query_synthetic_attributes_without_source_text(monkeypatch):
    configs = [{"name": "age", "type": "INTEGER"},
               {"name": "visit_date", "type": "DATE", "configurations": [{"dateFormatter": "dd.MM.yyyy"}]},
               {"name": "note", "type": "TEXT"}]
    dataset = pd.DataFrame([{"age": 80, "visit_date": "02.01.2024", "note": None}])
    reference = pd.DataFrame([
        {"age": 20, "visit_date": "01.01.2010", "note": "Hypertension treatment."},
        {"age": 80, "visit_date": "02.01.2024", "note": "Gardening was discussed."},
    ])
    synth, prompts = _initialize(monkeypatch, SYNTHESIZERS[2], dataset, reference, configs,
        [{"note": "Gardening. age: 80; visit_date: 02.01.2024."}],
        {"few_shot_examples": 1, "text_similarity_weight": 0, "structured_similarity_weight": 1})
    result = synth.sample()
    assert len(result) == 1
    assert result["visit_date"].tolist() == ["02.01.2024"]
    assert "Gardening was discussed." in prompts[0]
    assert "Hypertension treatment." not in prompts[0]


def test_embedding_query_uses_structured_facts(monkeypatch):
    dataset = pd.DataFrame([{"diagnosis": "Asthma", "note": None}])
    reference = pd.DataFrame([{"diagnosis": "Asthma", "note": "Asthma follow-up."},
                              {"diagnosis": "Fracture", "note": "Fracture follow-up."}])
    configs = [{"name": "diagnosis", "type": "STRING"}, {"name": "note", "type": "TEXT"}]
    synth, prompts = _initialize(monkeypatch, SYNTHESIZERS[2], dataset, reference, configs,
        [{"note": "Routine review. diagnosis: Asthma."}],
        {"few_shot_examples": 1, "text_similarity_weight": 1, "structured_similarity_weight": 0})
    synth.sample()
    assert "Asthma follow-up." in prompts[0]
    assert "Fracture follow-up." not in prompts[0]


def test_ground_truth_preserves_missing_false_zero_dates_and_decimals(monkeypatch):
    dataset = pd.DataFrame([{"age": 0, "active": False, "weight": 65.125,
                             "visit_date": "02.01.2024", "unknown": pd.NA, "note": None}])
    configs = [{"name": name, "type": kind} for name, kind in
               [("age", "INTEGER"), ("active", "BOOLEAN"), ("weight", "DECIMAL"),
                ("visit_date", "DATE"), ("unknown", "INTEGER"), ("note", "TEXT")]]
    configs[3]["configurations"] = [{"dateFormatter": "dd.MM.yyyy"}]
    text = "age: 0; active: false; weight: 65.125; visit_date: 02.01.2024."
    synth, prompts = _initialize(monkeypatch, SYNTHESIZERS[0], dataset, dataset, configs, [{"note": text}])
    result = synth.sample()
    pd.testing.assert_frame_equal(result.drop(columns="note"), dataset.drop(columns="note"))
    assert result["note"].tolist() == [text]
    assert '"unknown": null' in prompts[0]
