import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.algorithms.llm_nearest_neighbor_knowledge_grounded_text_synthesis import (
    LlmNearestNeighborKnowledgeGroundedTextSynthesisSynthesizer,
)


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "group", "type": "STRING"},
            {"index": 2, "name": "notes", "type": "TEXT"},
        ]
    }


def _algorithm_config() -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {
                    "few_shot_rows": 2,
                    "similarity_strategy": "Attributes",
                    "knowledge_source_type": "NOT_IMPLEMENTED",
                },
                "model_fitting": {
                    "user_prompt_domain_context": "German clinical discharge summaries.",
                    "allow_structured_corrections": True,
                },
                "sampling": {
                    "num_samples": 1,
                    "temperature": 0.3,
                    "top_p": 0.9,
                },
            }
        }
    }


def _set_shared_llm_env(monkeypatch) -> None:
    monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "ollama")
    monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "llama3.1:8b")
    monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://127.0.0.1:11434")
    monkeypatch.setenv("CINNAMON_LLM_ENDPOINT_PATH", "/api/generate")
    monkeypatch.setenv("CINNAMON_LLM_HEALTHCHECK_PATH", "/api/tags")
    monkeypatch.setenv("CINNAMON_LLM_API_KEY", "")
    monkeypatch.setenv("CINNAMON_LLM_TIMEOUT_SECONDS", "5")
    monkeypatch.setenv("CINNAMON_LLM_MAX_RETRIES", "2")
    monkeypatch.setenv("CINNAMON_LLM_VERIFY_SSL", "true")
    monkeypatch.setenv("CINNAMON_LLM_TEMPERATURE", "0.3")
    monkeypatch.setenv("CINNAMON_LLM_TOP_P", "0.9")
    monkeypatch.setenv("CINNAMON_LLM_MAX_TOKENS", "1024")


class _DummyResponse:
    def __init__(self, payload: dict):
        self._payload = payload
        self.status_code = 200

    def raise_for_status(self):
        return None

    def json(self):
        return self._payload


def _synthetic_input() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [67],
            "group": ["A"],
            "notes": [MISSING_VALUE_STRING],
        }
    )


def _original_input() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [40, 52, 61],
            "group": ["A", "B", "A"],
            "notes": [
                "Patient stable after treatment.",
                "Requires follow-up in two weeks.",
                "No acute findings and good recovery.",
            ],
        }
    )


def test_llm_nearest_neighbor_knowledge_grounded_text_synthesis_runs_without_knowledge_chunks(monkeypatch):
    _set_shared_llm_env(monkeypatch)
    post_count = {"count": 0}

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            post_count["count"] += 1
            prompt = kwargs["json"]["prompt"]
            assert "SYNTHETIC EXAMPLE" in prompt
            if "You repair the non-TEXT fields of a synthetic table row." in prompt:
                assert "Knowledge grounding" not in prompt
                assert "MOST SIMILAR REFERENCE ROW" in prompt
                assert "NEIGHBORING REFERENCE ROWS" in prompt
                assert '"notes": "No acute findings and good recovery."' not in prompt
                assert '"age": 67' in prompt
                assert '"group": "A"' in prompt
                return _DummyResponse(
                    {"response": json.dumps({"row": {"age": 61, "group": "A", "notes": MISSING_VALUE_STRING}})}
                )

            assert "MOST SIMILAR EXAMPLE" in prompt
            assert "NEIGHBORING EXAMPLES" in prompt
            assert '"notes": "No acute findings and good recovery."' in prompt
            assert '"age": 61' in prompt
            assert '"group": "A"' in prompt
            assert f'"notes": "{MISSING_VALUE_STRING}"' not in prompt
            return _DummyResponse(
                {"response": json.dumps({"row": {"age": 67, "group": "A", "notes": "Stable discharge with follow-up in one week."}})}
            )

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmNearestNeighborKnowledgeGroundedTextSynthesisSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_synthetic_input())
    synthesizer.initialize_reference_dataset(_original_input())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 1
    assert post_count["count"] == 2
    assert sample["notes"].iloc[0] == "Stable discharge with follow-up in one week."
