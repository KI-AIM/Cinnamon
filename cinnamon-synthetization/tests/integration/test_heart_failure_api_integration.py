import sys
import types
import uuid
from pathlib import Path

import pandas as pd
import pytest
import yaml

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


if "synthesizer_classes" not in sys.modules:
    stub_module = types.ModuleType("synthesizer_classes")
    stub_module.synthesizer_classes = {}
    sys.modules["synthesizer_classes"] = stub_module


if "flask_cors" not in sys.modules:
    cors_stub = types.ModuleType("flask_cors")

    def _cors(app, *args, **kwargs):
        return app

    cors_stub.CORS = _cors
    sys.modules["flask_cors"] = cors_stub


import app as app_module
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


FIXTURES_ROOT = Path(__file__).resolve().parent / "test_fixtures"
DATASET_NAME = "heart_failure"
DATA_PATH = FIXTURES_ROOT / "datasets" / DATASET_NAME / "original-dataset.csv"
CONFIG_PATH = FIXTURES_ROOT / "configs" / DATASET_NAME / "all-configurations.yaml"
STATUS_DIR = PROJECT_ROOT / "outputs" / "status"
STATUS_GLOB = "it-heart-failure-*.yaml"


class InlineProcess:
    _next_pid = 20000

    def __init__(self, target, args=(), kwargs=None):
        self.target = target
        self.args = args
        self.kwargs = kwargs or {}
        InlineProcess._next_pid += 1
        self.pid = InlineProcess._next_pid
        self.error = None

    def start(self):
        try:
            self.target(*self.args, **self.kwargs)
        except Exception as exc:  # pragma: no cover
            self.error = exc

    def join(self, timeout=None):
        return None


class MockHeartFailureSynthesizer(TabularDataSynthesizer):
    def __init__(self):
        super().__init__(attribute_configuration=None, anonymization_configuration=None)
        self.dataset = None
        self.num_samples = 0

    def _initialize_anonymization_configuration(self, configuration_file):
        self.num_samples = configuration_file["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"]

    def _initialize_attribute_configuration(self, configuration_file):
        return None

    def _initialize_dataset(self, dataset):
        self.dataset = dataset.copy()

    def _initialize_synthesizer(self):
        return None

    def _fit(self):
        return None

    def _sample(self):
        replace = self.num_samples > len(self.dataset)
        return self.dataset.sample(n=self.num_samples, replace=replace, random_state=42).reset_index(drop=True)

    def _get_model(self):
        return b"mock-model"

    def _load_model(self, filepath):
        return self

    def _save_data(self, sample, filename):
        return None


class SilentInterceptStdOut:
    def __init__(self, file_name, process_stage):
        self.file_name = file_name
        self.process_stage = process_stage

    def write(self, message):
        return len(message)

    def flush(self):
        return None

    def close(self):
        return None


def _load_yaml(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def _status_step(data: dict, step_name: str) -> dict:
    for step in data["status"]:
        if step["step"] == step_name:
            return step
    raise AssertionError(f"Missing step: {step_name}")


def _cleanup_heart_failure_status_files():
    if not STATUS_DIR.exists():
        return
    for status_file in STATUS_DIR.glob(STATUS_GLOB):
        status_file.unlink(missing_ok=True)


@pytest.fixture(autouse=True)
def cleanup_status_files():
    _cleanup_heart_failure_status_files()
    yield
    _cleanup_heart_failure_status_files()


def test_heart_failure_api_generates_synthetic_dataset(monkeypatch):
    assert DATA_PATH.exists()
    assert CONFIG_PATH.exists()

    config = _load_yaml(CONFIG_PATH)
    source_df = pd.read_csv(DATA_PATH)
    expected_rows = config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"]
    expected_columns = list(source_df.columns)

    callback_calls = []

    class DummyResponse:
        status_code = 200
        text = "ok"

        @staticmethod
        def raise_for_status():
            return None

    def fake_requests_post(url, *args, **kwargs):
        callback_calls.append(
            {
                "url": url,
                "files": kwargs.get("files"),
                "data": kwargs.get("data"),
                "timeout": kwargs.get("timeout"),
            }
        )
        return DummyResponse()

    app_module.tasks.clear()
    app_module.task_locks.clear()

    monkeypatch.setattr(app_module, "Process", InlineProcess)
    monkeypatch.setitem(
        app_module.start_synthetization_process.__globals__,
        "Process",
        InlineProcess,
    )
    monkeypatch.setattr(app_module, "InterceptStdOut", SilentInterceptStdOut)
    monkeypatch.setattr(app_module.requests, "post", fake_requests_post)
    monkeypatch.setitem(
        app_module.synthesizer_classes,
        "heart_failure_mock",
        {
            "version": "0.1",
            "type": "cross-sectional",
            "class": MockHeartFailureSynthesizer,
            "display_name": "Heart Failure Mock",
            "description": "Fast mock synthesizer for integration testing",
            "URL": "/synthetic_tabular_data_generator/synthesizer_config/mock.yaml",
        },
    )

    session_key = f"it-heart-failure-{uuid.uuid4().hex}"
    callback_url = "http://callback.local/test"

    client = app_module.app.test_client()
    with CONFIG_PATH.open("rb") as attribute_config, CONFIG_PATH.open("rb") as algorithm_config, DATA_PATH.open("rb") as data_file:
        response = client.post(
            "/start_synthetization_process/heart_failure_mock",
            data={
                "session_key": session_key,
                "callback": callback_url,
                "attribute_config": (attribute_config, "attribute_config.yaml"),
                "algorithm_config": (algorithm_config, "algorithm_config.yaml"),
                "data": (data_file, "original-dataset.csv"),
            },
            content_type="multipart/form-data",
        )

    assert response.status_code == 202, response.get_data(as_text=True)
    payload = response.get_json()
    assert payload["message"] == "Synthetization Started"
    assert payload["session_key"] == session_key

    process_obj = app_module.tasks[session_key]
    assert isinstance(process_obj, InlineProcess)
    assert getattr(process_obj, "error", None) is None

    assert len(callback_calls) == 1
    callback = callback_calls[0]
    assert callback["url"] == callback_url
    assert callback["data"]["session_key"] == session_key
    assert "synthetic_data" in callback["files"]
    assert "model" in callback["files"]

    synthetic_data_file = callback["files"]["synthetic_data"][1]
    synthetic_data_file.seek(0)
    synthetic_df = pd.read_csv(synthetic_data_file)

    assert len(synthetic_df) == expected_rows
    assert list(synthetic_df.columns) == expected_columns
    assert synthetic_df.shape[1] == len(expected_columns)

    model_file = callback["files"]["model"][1]
    model_file.seek(0)
    assert model_file.read() == b"mock-model"

    status_path = Path(app_module.__file__).resolve().parent / "outputs" / "status" / f"{session_key}.yaml"
    assert status_path.exists()
    status = _load_yaml(status_path)
    assert _status_step(status, "callback")["completed"] == "True"

    status_path.unlink(missing_ok=True)
