import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.algorithms.llm_tabular import LlmTabularSynthesizer


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "height", "type": "DECIMAL"},
            {"index": 2, "name": "risk", "type": "BOOLEAN"},
            {"index": 3, "name": "group", "type": "STRING"},
        ]
    }


def _algorithm_config(provider: str = "ollama") -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {},
                "model_fitting": {
                    "profile_rows": 50,
                    "few_shot_rows": 2,
                },
                "sampling": {
                    "num_samples": 3,
                },
            }
        }
    }


def _set_shared_llm_env(monkeypatch, provider: str) -> None:
    endpoint_path = "/api/generate" if provider == "ollama" else "/v1/chat/completions"
    healthcheck_path = "/api/tags" if provider == "ollama" else "/v1/models"
    base_url = "http://127.0.0.1:11434" if provider == "ollama" else "http://gpu.example.org:7086"
    model_name = "llama3.1:8b" if provider == "ollama" else "gpt-test"

    monkeypatch.setenv("CINNAMON_LLM_PROVIDER", provider)
    monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", model_name)
    monkeypatch.setenv("CINNAMON_LLM_BASE_URL", base_url)
    monkeypatch.setenv("CINNAMON_LLM_ENDPOINT_PATH", endpoint_path)
    monkeypatch.setenv("CINNAMON_LLM_HEALTHCHECK_PATH", healthcheck_path)
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


def _dataset() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [28, 35, 44],
            "height": [168.0, 176.3, 182.1],
            "risk": [False, True, True],
            "group": ["A", "B", "A"],
        }
    )


def _attribute_config_with_text() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "group", "type": "STRING"},
            {"index": 2, "name": "notes", "type": "TEXT"},
        ]
    }


def _dataset_with_text() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [28, 35, 44],
            "group": ["A", "B", "A"],
            "notes": ["stable patient", "high risk patient", "follow-up needed"],
        }
    )


def test_llm_tabular_synthesizer_generates_requested_rows_via_ollama(monkeypatch):
    call_counter = {"count": 0}
    _set_shared_llm_env(monkeypatch, provider="ollama")

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            call_counter["count"] += 1
            prompt = kwargs["json"]["prompt"]
            assert "You generate non-TEXT fields for synthetic tabular rows." in prompt
            assert "GENERATION TASK" in prompt
            assert "Generate exactly 1 row." in prompt

            rows = [
                {"age": 30 + call_counter["count"], "height": 170.0 + call_counter["count"], "risk": True, "group": "A"},
            ]
            return _DummyResponse({"response": json.dumps({"rows": rows})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config(provider="ollama"))
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert list(sample.columns) == ["age", "height", "risk", "group"]
    assert len(sample) == 3
    assert call_counter["count"] == 3
    assert sample["age"].between(28, 44).all()
    assert sample["height"].between(168.0, 182.1).all()
    assert sample["risk"].isin([True, False]).all()


def test_llm_tabular_synthesizer_generates_requested_rows_via_openai_compatible(monkeypatch):
    call_counter = {"count": 0}
    _set_shared_llm_env(monkeypatch, provider="openai_compatible")

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/v1/models"):
            return _DummyResponse({"data": [{"id": "gpt-test"}]})

        if method == "POST" and url.endswith("/v1/chat/completions"):
            call_counter["count"] += 1
            prompt = kwargs["json"]["messages"][1]["content"]
            assert "You generate non-TEXT fields for synthetic tabular rows." in prompt
            assert "GENERATION TASK" in prompt
            assert "Generate exactly 1 row." in prompt
            content = json.dumps(
                {
                    "rows": [
                        {
                            "age": 28 + call_counter["count"],
                            "height": 169.5 + call_counter["count"],
                            "risk": call_counter["count"] % 2 == 0,
                            "group": chr(ord("A") + call_counter["count"] - 1),
                        },
                    ]
                }
            )
            return _DummyResponse({"choices": [{"message": {"content": content}}]})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config(provider="openai_compatible"))
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 3
    assert call_counter["count"] == 3
    assert sample["age"].tolist() == [29, 30, 31]
    assert sample["group"].tolist() == ["A", "B", "C"]


def test_llm_tabular_synthesizer_maps_positional_column_names(monkeypatch):
    call_counter = {"count": 0}
    _set_shared_llm_env(monkeypatch, provider="ollama")

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            call_counter["count"] += 1
            rows = [
                {
                    "column_a": 30 + call_counter["count"],
                    "column_b": 170.5 + call_counter["count"],
                    "column_c": call_counter["count"] % 2 == 1,
                    "column_d": chr(ord("A") + call_counter["count"] - 1),
                },
            ]
            return _DummyResponse({"response": json.dumps({"rows": rows})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config(provider="ollama")
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 2

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 2
    assert call_counter["count"] == 2
    assert sample["age"].tolist() == [31, 32]
    assert sample["group"].tolist() == ["A", "B"]


def test_llm_tabular_synthesizer_preserves_sparse_positional_indices():
    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_attribute_configuration(_attribute_config())

    aligned_row, used_positional_mapping = synthesizer._align_row_to_schema(  # type: ignore[attr-defined]
        {
            "column_b": 176.5,
            "column_d": "B",
        }
    )

    assert used_positional_mapping is True
    assert aligned_row == {
        "age": None,
        "height": 176.5,
        "risk": None,
        "group": "B",
    }


def test_llm_tabular_synthesizer_generates_text_in_single_step(monkeypatch):
    _set_shared_llm_env(monkeypatch, provider="ollama")
    post_count = {"count": 0}

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            post_count["count"] += 1
            prompt = kwargs["json"]["prompt"]
            assert "Domain context: Hospital discharge documentation in German." in prompt
            if "You generate non-TEXT fields for synthetic tabular rows." in prompt:
                assert "REFERENCE EXAMPLES" in prompt
                assert "- notes (TEXT): missing_ratio=" in prompt
                assert "- notes (TEXT): frequent values [" not in prompt
                return _DummyResponse(
                    {
                        "response": json.dumps(
                            {
                                "rows": [
                                    {"age": 33, "group": "A", "notes": MISSING_VALUE_STRING},
                                ]
                            }
                        )
                    }
                )

            assert "You generate TEXT fields for repaired synthetic tabular rows." in prompt
            assert "SYNTHETIC EXAMPLE" in prompt
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {
                            "row": {"age": 99, "group": "Z", "notes": "Patient shows stable recovery."},
                        }
                    )
                }
            )

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config(provider="ollama")
    algorithm_config["synthetization_configuration"]["algorithm"]["model_fitting"]["user_prompt_domain_context"] = (
        "Hospital discharge documentation in German."
    )
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 1

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config_with_text())
    synthesizer.initialize_dataset(_dataset_with_text())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 1
    assert post_count["count"] == 2
    assert sample["notes"].iloc[0] == "Patient shows stable recovery."


def test_llm_tabular_prefers_model_parameter_for_profile_and_few_shot(monkeypatch):
    _set_shared_llm_env(monkeypatch, provider="ollama")

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            return _DummyResponse({"response": json.dumps({"rows": [{"age": 30, "height": 170.0, "risk": True, "group": "A"}]})})
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config(provider="ollama")
    algorithm = algorithm_config["synthetization_configuration"]["algorithm"]
    algorithm["model_parameter"] = {"profile_rows": 2, "few_shot_rows": 1}
    algorithm["model_fitting"]["profile_rows"] = 999
    algorithm["model_fitting"]["few_shot_rows"] = 999
    algorithm["sampling"]["num_samples"] = 1

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    assert synthesizer._fitting_kwargs["profile_rows"] == 2
    assert synthesizer._fitting_kwargs["few_shot_rows"] == 1


def test_llm_tabular_draws_new_few_shot_examples_for_each_prompt(monkeypatch):
    call_counter = {"count": 0}
    _set_shared_llm_env(monkeypatch, provider="ollama")

    prompts = []

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            call_counter["count"] += 1
            prompt = kwargs["json"]["prompt"]
            prompts.append(prompt)
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {
                            "rows": [
                                {"age": 30 + call_counter["count"], "height": 170.0, "risk": True, "group": "A"},
                            ]
                        }
                    )
                }
            )

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config(provider="ollama")
    algorithm_config["synthetization_configuration"]["algorithm"]["model_parameter"]["few_shot_rows"] = 1
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 3

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    draw_counter = {"count": 0}

    def fake_draw_examples():
        draw_counter["count"] += 1
        return [{"age": 100 + draw_counter["count"], "height": 171.0, "risk": True, "group": "X"}]

    synthesizer._draw_few_shot_examples = fake_draw_examples  # type: ignore[assignment]

    sample = synthesizer.sample()

    assert len(sample) == 3
    assert call_counter["count"] == 3
    assert draw_counter["count"] == 3
    assert "REFERENCE EXAMPLES" in prompts[0]
    assert '"age": 101' in prompts[0]
    assert '"age": 102' in prompts[1]
    assert '"age": 103' in prompts[2]


def test_llm_tabular_reports_sampling_remaining_time_via_callback(monkeypatch):
    _set_shared_llm_env(monkeypatch, provider="ollama")

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            return _DummyResponse({"response": json.dumps({"rows": [{"age": 30, "height": 170.0, "risk": True, "group": "A"}]})})
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config(provider="ollama")
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 1

    synthesizer = LlmTabularSynthesizer()
    updates = []
    synthesizer.set_progress_callback(lambda step, remaining_time: updates.append((step, remaining_time)))
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    synthesizer.sample()

    assert updates[-1] == ("sampling", 0)


def test_llm_tabular_caches_generation_prompt_prefix(monkeypatch):
    _set_shared_llm_env(monkeypatch, provider="ollama")

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            return _DummyResponse(
                {"response": json.dumps({"rows": [{"age": 30, "height": 170.0, "risk": True, "group": "A"}]})}
            )
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    algorithm_config = _algorithm_config(provider="ollama")
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 2

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()

    original_builder = synthesizer._build_non_text_generation_prompt_prefix
    build_counter = {"count": 0}

    def counted_builder():
        build_counter["count"] += 1
        return original_builder()

    synthesizer._build_non_text_generation_prompt_prefix = counted_builder  # type: ignore[assignment]

    synthesizer.fit()
    synthesizer.sample()

    assert build_counter["count"] == 1


def test_llm_tabular_logs_diagnostics_for_invalid_generation_attempts(monkeypatch):
    _set_shared_llm_env(monkeypatch, provider="ollama")
    logs = []

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})
        if method == "POST" and url.endswith("/api/generate"):
            return _DummyResponse({"response": json.dumps({"rows": [{"age": "age"}]})})
        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)
    monkeypatch.setattr("builtins.print", lambda message: logs.append(message))

    algorithm_config = _algorithm_config(provider="ollama")
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 1

    synthesizer = LlmTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(_dataset())
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    try:
        synthesizer.sample()
    except RuntimeError:
        pass

    assert any("[LLM_TABULAR_NON_TEXT_GENERATION]" in entry for entry in logs)
    assert any("unusable_rows=" in entry for entry in logs)
