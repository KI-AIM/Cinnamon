import json

import pytest

from cinnamon_text_anonymization.inference.utils import (
    PROJECT_ROOT,
    load_inference_config,
    project_path,
    resolve_device,
)


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
