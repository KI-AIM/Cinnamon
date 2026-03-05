import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.ollama_tabular import OllamaTabularSynthesizer


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "height", "type": "DECIMAL"},
            {"index": 2, "name": "risk", "type": "BOOLEAN"},
            {"index": 3, "name": "group", "type": "STRING"},
        ]
    }


def _algorithm_config() -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {
                    "model_name": "llama3.1:8b",
                    "ollama_base_url": "http://127.0.0.1:11434",
                    "temperature": 0.2,
                    "top_p": 0.8,
                },
                "model_fitting": {
                    "profile_rows": 50,
                    "few_shot_rows": 2,
                    "max_retries": 2,
                    "timeout_seconds": 5,
                },
                "sampling": {
                    "num_samples": 3,
                },
            }
        }
    }


class _DummyResponse:
    def __init__(self, payload: dict):
        self._payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self._payload


def test_ollama_tabular_synthesizer_generates_requested_rows(monkeypatch):
    def fake_get(*args, **kwargs):
        return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

    call_counter = {"count": 0}

    def fake_post(*args, **kwargs):
        call_counter["count"] += 1
        if call_counter["count"] == 1:
            rows = [
                {"age": 31, "height": 171.5, "risk": True, "group": "A"},
                {"age": 47, "height": 183.2, "risk": False, "group": "B"},
            ]
        else:
            rows = [
                {"age": 39, "height": 175.0, "risk": True, "group": "C"},
            ]
        return _DummyResponse({"response": json.dumps({"rows": rows})})

    monkeypatch.setattr("synthetic_tabular_data_generator.algorithms.ollama_tabular.requests.get", fake_get)
    monkeypatch.setattr("synthetic_tabular_data_generator.algorithms.ollama_tabular.requests.post", fake_post)

    dataset = pd.DataFrame(
        {
            "age": [28, 35, 44],
            "height": [168.0, 176.3, 182.1],
            "risk": [False, True, True],
            "group": ["A", "B", "A"],
        }
    )

    synthesizer = OllamaTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert list(sample.columns) == ["age", "height", "risk", "group"]
    assert len(sample) == 3
    assert sample["age"].between(28, 44).all()
    assert sample["height"].between(168.0, 182.1).all()
    assert sample["risk"].isin([True, False]).all()


def test_ollama_tabular_synthesizer_maps_positional_column_names(monkeypatch):
    def fake_get(*args, **kwargs):
        return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

    def fake_post(*args, **kwargs):
        rows = [
            {"column_a": 31, "column_b": 171.5, "column_c": True, "column_d": "A"},
            {"column_a": 42, "column_b": 178.0, "column_c": False, "column_d": "B"},
        ]
        return _DummyResponse({"response": json.dumps({"rows": rows})})

    monkeypatch.setattr("synthetic_tabular_data_generator.algorithms.ollama_tabular.requests.get", fake_get)
    monkeypatch.setattr("synthetic_tabular_data_generator.algorithms.ollama_tabular.requests.post", fake_post)

    dataset = pd.DataFrame(
        {
            "age": [28, 35, 44],
            "height": [168.0, 176.3, 182.1],
            "risk": [False, True, True],
            "group": ["A", "B", "A"],
        }
    )

    algorithm_config = _algorithm_config()
    algorithm_config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] = 2

    synthesizer = OllamaTabularSynthesizer()
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(dataset)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    sample = synthesizer.sample()

    assert len(sample) == 2
    assert set(sample["age"].tolist()).issubset({31, 42})
    assert set(sample["group"].tolist()).issubset({"A", "B"})
