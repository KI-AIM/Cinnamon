from __future__ import annotations

import pandas as pd
import pytest

from cinnamon_text_anonymization.anonymization_service import (
    process_column,
    process_texts,
)
from cinnamon_text_anonymization.inference.inference_model import merge_entities, token_windows


@pytest.mark.parametrize(
    ("length", "window_size", "overlap", "expected"),
    [
        (5, 5, 0, [(0, 5)]),
        (4, 1, 0, [(0, 1), (1, 2), (2, 3), (3, 4)]),
        (7, 4, 1, [(0, 4), (3, 7), (6, 7)]),
        (0, 5, 2, [])
    ]
)
def test_token_windows(length, window_size, overlap, expected):
    assert token_windows(length, window_size, overlap) == expected

def entity(
        start: int,
        end: int,
        label: str,
        score: float,
) -> dict:
    return {
        "start": start,
        "end": end,
        "entity_group": label,
        "score": score,
    }

@pytest.mark.parametrize(
    ("entities", "expected"),
    [
        ([], []),
        (
                [entity(0, 4, "NAME_PATIENT", 0.9)],
                [("NAME_PATIENT", 0.9, 0, 4)],
        ),
        (
                [
                    entity(0, 4, "NAME_PATIENT", 0.9),
                    entity(10, 15, "DATE", 0.8),
                ],
                [
                    ("NAME_PATIENT", 0.9, 0, 4),
                    ("DATE", 0.8, 10, 15),
                ],
        ),
        (
                [
                    entity(0, 5, "NAME_PATIENT", 0.99),
                    entity(0, 10, "NAME_PATIENT", 0.8),
                ],
                [
                    ("NAME_PATIENT", 0.8, 0, 10),
                ],
        ),
        (
                [
                    entity(0, 5, "NAME_PATIENT", 0.8),
                    entity(1, 6, "NAME_PATIENT", 0.95),
                ],
                [
                    ("NAME_PATIENT", 0.95, 1, 6),
                ],
        ),
        (
                [
                    entity(0, 10, "NAME_PATIENT", 0.75),
                    entity(2, 8, "NAME_DOCTOR", 0.95),
                ],
                [
                    ("NAME_DOCTOR", 0.95, 2, 8),
                ],
        ),
    ],
)
def test_merge_entities(entities, expected):
    result = merge_entities(entities, "x" * 100)

    actual = [
        (e["label"], e["score"], e["start"], e["end"])
        for e in result
    ]

    assert actual == expected

class FakeInferenceModel:
    """Small deterministic stand-in for InferenceModel wrapper."""

    def __init__(self, predictions: list[list[dict]] | None = None) -> None:
        self.calls: list[list[str]] = []
        self.predictions = predictions

    def predict(self, texts: list[str]) -> list[list[dict]]:
        self.calls.append(texts.copy())
        if self.predictions is not None:
            return self.predictions

        return [
            [
                {
                    "label": "PERSON",
                    "score": 0.95,
                    "start": 0,
                    "end": len(text),
                }
            ]
            for text in texts
        ]

def test_process_column() -> None:
    data = pd.DataFrame(
        {"text": ["Jane", None, "", "Joe"], "random_column": [1, 2, 3, 4]},
        index=[10, 20, 30, 40],
    )
    result = data.copy(deep=True)
    model = FakeInferenceModel()

    process_column(
        data=data,
        column_position=0,
        result=result,
        ner_model=model,
        confidence_threshold=0.9,
        anonymization_mode="redact",
    )

    assert result.loc[10, "text"] == "[PERSON]"
    assert pd.isna(result.loc[20, "text"])
    assert result.loc[30, "text"] == ""
    assert result.loc[40, "text"] == "[PERSON]"
    assert result["random_column"].tolist() == [1, 2, 3, 4]
    assert model.calls == [["Jane", "Joe"]]


def test_process_text() -> None:
    model = FakeInferenceModel(
        predictions=[
            [
                {
                    "label": "PERSON",
                    "score": 0.95,
                    "start": 0,
                    "end": 4,
                },
                {
                    "label": "LOW_CONFIDENCE",
                    "score": 0.1,
                    "start": 6,
                    "end": 9,
                },
                {
                    "label": "PERSON",
                    "score": 0.9,
                    "start": 9,
                    "end": 12,
                },
            ],
            [],
        ]
    )

    result = process_texts(["Jane met Joe", "No entities"],
                           model,
                           confidence_threshold=0.9,
                           anonymization_mode="redact")

    assert result == ["[PERSON] met [PERSON]", "No entities"]
    assert model.calls == [["Jane met Joe", "No entities"]]
