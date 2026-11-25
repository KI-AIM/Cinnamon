import json

import pytest

from cinnamon_text_anonymization.inference.utils import (
    PROJECT_ROOT,
    load_inference_config,
    project_path,
    resolve_device,
    resolve_overlapping_entities,
)


@pytest.mark.parametrize(
    ("text", "entities", "expected"),
    [
        ("No entities.", [], []),
        (
            "Maria Müller",
            [
                {"text": "Maria", "label": "NAME_PATIENT", "start": 0, "end": 5},
                {"text": "Müller", "label": "NAME_PATIENT", "start": 6, "end": 12},
                {"text": "invalid", "label": "OTHER", "start": -1, "end": 2},
                {"text": "invalid", "label": "OTHER", "start": 4, "end": 99},
            ],
            [("NAME_PATIENT", 0, 5), ("NAME_PATIENT", 6, 12)],
        ),
        (
            "Maria Müller",
            [
                {"text": "Maria Müller", "label": "NAME_PATIENT", "start": 0, "end": 12},
                {"text": "Müller", "label": "NAME_PATIENT", "start": 6, "end": 12},
            ],
            [("NAME_PATIENT", 0, 12)],
        ),
        (
            "abcdefghij",
            [
                {"text": "abcde", "label": "NAME_PATIENT", "start": 0, "end": 5, "score": 0.7},
                {"text": "cdefg", "label": "NAME_DOCTOR", "start": 2, "end": 7, "score": 0.95},
            ],
            [("NAME_DOCTOR", 2, 7)],
        ),
        (
            "Prof. Dr. Maria Müller",
            [
                {"text": "Prof. Dr.", "label": "NAME_TITLE", "start": 0, "end": 9},
                {"text": "Prof. Dr. Maria Müller", "label": "NAME_DOCTOR", "start": 0, "end": 22},
            ],
            [("NAME_TITLE", 0, 9), ("NAME_DOCTOR", 10, 22)],
        ),
    ],
)
def test_resolve_overlapping_entities(text, entities, expected) -> None:
    result = resolve_overlapping_entities(text, entities)

    actual = sorted(
        [
            (entity["label"], entity["start"], entity["end"])
            for entity in result
        ],
        key=lambda item: item[1],
    )

    assert actual == expected


@pytest.mark.parametrize(
    ("path", "expected"),
    [
        ("models", PROJECT_ROOT / "models"),
        (PROJECT_ROOT / "models", PROJECT_ROOT / "models")
    ]
)
def test_token_windows(path, expected):
    assert project_path(path) == expected

def test_load_inference_config(tmp_path):
    config = {
        "window_size": 512,
        "overlap": 128,
    }

    (tmp_path / "inference_config.json").write_text(
        json.dumps(config),
        encoding="utf-8",
    )

    assert load_inference_config(tmp_path) == (512, 128)

def test_resolve_device_cpu():
    assert resolve_device("cpu") == "cpu"
    assert resolve_device("auto") == "cpu"
