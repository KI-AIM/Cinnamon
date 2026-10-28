import io
import sys
import types
from pathlib import Path

import yaml
import pytest

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
from api_utility.status.status_updater import initialize_status_file


STATUS_DIR = PROJECT_ROOT / "outputs" / "status"


class CancelableProcess:
    def __init__(self, pid=43210, alive=True):
        self.pid = pid
        self._alive = alive
        self.terminate_called = False
        self.kill_called = False
        self.join_calls = []

    def is_alive(self):
        return self._alive

    def terminate(self):
        self.terminate_called = True
        self._alive = False

    def kill(self):
        self.kill_called = True
        self._alive = False

    def join(self, timeout=None):
        self.join_calls.append(timeout)


class FinishedProcess:
    def __init__(self, pid=54321, exitcode=0):
        self.pid = pid
        self.exitcode = exitcode

    def is_alive(self):
        return False


class StartedProcess:
    def __init__(self, target=None, args=(), kwargs=None, pid=65432):
        self.target = target
        self.args = args
        self.kwargs = kwargs or {}
        self.pid = pid
        self.started = False

    def start(self):
        self.started = True


def _status_file_path(session_key: str) -> Path:
    return STATUS_DIR / f"{session_key}.yaml"


def _delete_status_file(session_key: str) -> None:
    _status_file_path(session_key).unlink(missing_ok=True)


def _text_attribute_config() -> dict:
    return {
        "configurations": [
            {"name": "age", "type": "INTEGER", "index": 0},
            {"name": "note", "type": "TEXT", "index": 1},
        ]
    }


def _structured_attribute_config() -> dict:
    return {
        "configurations": [
            {"name": "age", "type": "INTEGER", "index": 0},
        ]
    }


def _algorithm_config() -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "sampling": {"num_samples": 1},
            }
        }
    }


def test_get_study_parameter_default_reads_values_from_study_yaml():
    app_module.load_study_config.cache_clear()

    assert app_module.get_study_parameter_default("sampler") == "tpe"
    assert app_module.get_study_parameter_default("pruner") == "none"


def test_run_synthesizer_stage_uses_global_timeout_countdown_for_hyperparameter_tuning(monkeypatch, tmp_path):
    class FakeSynth:
        def __init__(self):
            self._progress_callback = None

        def set_progress_callback(self, callback):
            self._progress_callback = callback

        def initialize_anonymization_configuration(self, _config):
            return None

        def initialize_attribute_configuration(self, _config):
            return None

        def initialize_dataset(self, _dataset):
            return None

        def initialize_synthesizer(self):
            return None

        def fit(self):
            if self._progress_callback is not None:
                self._progress_callback("fitting", "7")
            print("Estimated remaining time: 7 seconds")
            return 1.0

        def sample(self):
            return app_module.pd.DataFrame([{"age": 42}])

        def get_model(self):
            return b"fake-model"

    fake_optuna_module = types.ModuleType("hyperparameter_tuning.optuna_tuning")
    fake_optuna_module.DEFAULT_ARTIFACT_DIR = str(tmp_path / "artifacts")

    status_path = tmp_path / "outputs" / "status" / "hpt-status.yaml"
    initialize_status_file(str(status_path), session_key="hpt-status", synthesizer_name="fake_synth")

    def fake_optimize(**kwargs):
        kwargs["fit_metric_fn"](kwargs["algorithm_config_base"])
        with status_path.open("r", encoding="utf-8") as handle:
            status = yaml.safe_load(handle)
        steps = {step["step"]: step for step in status["status"]}
        assert steps["fitting"]["remaining_time"] == "300"
        assert status["components"]["structured_synthesis"]["fitting_remaining_time"] == "300"
        return types.SimpleNamespace(best_algorithm_config=kwargs["algorithm_config_base"])

    fake_optuna_module.optimize = fake_optimize

    monkeypatch.setitem(sys.modules, "hyperparameter_tuning.optuna_tuning", fake_optuna_module)
    monkeypatch.setattr(app_module, "synthesizer_classes", {"fake_synth": {"class": FakeSynth}})
    monkeypatch.setattr(
        app_module,
        "synthesizer_tuning_metadata",
        {"fake_synth": {"supported": True, "direction": "minimize"}},
    )
    monkeypatch.setattr(app_module, "post_process_dataframe", lambda samples, *_args, **_kwargs: samples)
    monkeypatch.setattr(app_module.time, "time", lambda: 1_000.0)

    samples, model, stage_init_duration, fit_duration, sample_duration = app_module.run_synthesizer_stage(
        stage_label="STRUCTURED_SYNTHESIS",
        synthesizer_name="fake_synth",
        stage_attribute_config=_structured_attribute_config(),
        stage_algorithm_config={
            "synthetization_configuration": {
                "algorithm": {
                    "sampling": {"num_samples": 1},
                    "hyperparameter_tuning": {
                        "enabled": True,
                        "timeout_minutes": 5,
                        "n_trials": 1,
                    },
                }
            }
        },
        input_data=app_module.pd.DataFrame([{"age": 1}]),
        file_path_status=str(status_path),
        status_component_name="structured_synthesis",
    )

    assert samples.to_dict(orient="records") == [{"age": 42}]
    assert model == b"fake-model"
    assert stage_init_duration == 0.0
    assert fit_duration == 0.0
    assert sample_duration == 0.0


def test_run_synthesizer_stage_uses_study_yaml_defaults_for_sampler_and_pruner(monkeypatch, tmp_path):
    class FakeSynth:
        def set_progress_callback(self, _callback):
            return None

        def initialize_anonymization_configuration(self, _config):
            return None

        def initialize_attribute_configuration(self, _config):
            return None

        def initialize_dataset(self, _dataset):
            return None

        def initialize_synthesizer(self):
            return None

        def fit(self):
            return 1.0

        def sample(self):
            return app_module.pd.DataFrame([{"age": 42}])

        def get_model(self):
            return b"fake-model"

    fake_optuna_module = types.ModuleType("hyperparameter_tuning.optuna_tuning")
    fake_optuna_module.DEFAULT_ARTIFACT_DIR = str(tmp_path / "artifacts")

    status_path = tmp_path / "outputs" / "status" / "default-study-values.yaml"
    initialize_status_file(str(status_path), session_key="default-study-values", synthesizer_name="fake_synth")

    app_module.load_study_config.cache_clear()

    def fake_optimize(**kwargs):
        assert kwargs["sampler"] == app_module.get_study_parameter_default("sampler")
        assert kwargs["pruner"] == app_module.get_study_parameter_default("pruner")
        return types.SimpleNamespace(best_algorithm_config=kwargs["algorithm_config_base"])

    fake_optuna_module.optimize = fake_optimize

    monkeypatch.setitem(sys.modules, "hyperparameter_tuning.optuna_tuning", fake_optuna_module)
    monkeypatch.setattr(app_module, "synthesizer_classes", {"fake_synth": {"class": FakeSynth}})
    monkeypatch.setattr(
        app_module,
        "synthesizer_tuning_metadata",
        {"fake_synth": {"supported": True, "direction": "minimize"}},
    )
    monkeypatch.setattr(app_module, "post_process_dataframe", lambda samples, *_args, **_kwargs: samples)
    monkeypatch.setattr(app_module.time, "time", lambda: 1_000.0)

    samples, model, *_durations = app_module.run_synthesizer_stage(
        stage_label="STRUCTURED_SYNTHESIS",
        synthesizer_name="fake_synth",
        stage_attribute_config=_structured_attribute_config(),
        stage_algorithm_config={
            "synthetization_configuration": {
                "algorithm": {
                    "sampling": {"num_samples": 1},
                    "hyperparameter_tuning": {
                        "enabled": True,
                        "timeout_minutes": 5,
                        "n_trials": 1,
                    },
                }
            }
        },
        input_data=app_module.pd.DataFrame([{"age": 1}]),
        file_path_status=str(status_path),
        status_component_name="structured_synthesis",
    )

    assert samples.to_dict(orient="records") == [{"age": 42}]
    assert model == b"fake-model"


def test_start_synthetization_process_returns_400_when_session_key_is_missing():
    app_module.tasks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/ctgan",
        data={},
        content_type="multipart/form-data",
    )

    assert response.status_code == 400
    payload = response.get_json()
    assert payload["message"] == "No session key provided"


def test_start_synthetization_process_returns_400_when_callback_is_missing():
    app_module.tasks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/ctgan",
        data={"session_key": "session-without-callback"},
        content_type="multipart/form-data",
    )

    assert response.status_code == 400
    payload = response.get_json()
    assert payload["message"] == "No callback URL provided"
    assert payload["session_key"] == "session-without-callback"
    assert "session-without-callback" not in app_module.tasks


def test_start_synthetization_process_allows_missing_original_data_for_text_synthesis(monkeypatch):
    app_module.tasks.clear()
    monkeypatch.setattr(app_module, "get_data_modality", lambda _name: "text_only")
    monkeypatch.setattr(app_module.PROCESS_CONTEXT, "Process", StartedProcess)
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/llm_text_only_paraphrase_synthesis",
        data={
            "session_key": "session-without-original-data",
            "callback": "http://callback.local/test",
            "attribute_config": (
                io.BytesIO(b"configurations:\n  - name: note\n    type: TEXT\n"),
                "attribute_config.yaml",
            ),
            "algorithm_config": (
                io.BytesIO(b"synthetization_configuration:\n  algorithm: {}\n"),
                "algorithm_config.yaml",
            ),
            "data": (io.BytesIO(b"age\n1\n"), "data.csv"),
        },
        content_type="multipart/form-data",
    )

    assert response.status_code == 202
    payload = response.get_json()
    assert payload["message"] == "Synthetization Started"
    assert payload["session_key"] == "session-without-original-data"
    assert payload["pid"] == 65432
    assert isinstance(app_module.tasks["session-without-original-data"], StartedProcess)


def test_start_synthetization_process_allows_missing_original_data_for_structured_synthesis(monkeypatch):
    app_module.tasks.clear()
    monkeypatch.setattr(app_module, "get_data_modality", lambda _name: "structured_only")
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/bayesian_network",
        data={
            "session_key": "session-without-original-data-structured",
            "callback": "http://callback.local/test",
            "attribute_config": (
                io.BytesIO(b"configurations:\n  - name: age\n    type: INTEGER\n"),
                "attribute_config.yaml",
            ),
            "algorithm_config": (
                io.BytesIO(b"synthetization_configuration:\n  algorithm: {}\n"),
                "algorithm_config.yaml",
            ),
            "data": (io.BytesIO(b"age\n1\n"), "data.csv"),
        },
        content_type="multipart/form-data",
    )

    assert response.status_code != 400
    payload = response.get_json()
    assert payload["session_key"] == "session-without-original-data-structured"
    assert payload["message"] != "No original_data file provided"


def test_start_synthetization_process_returns_400_for_invalid_attribute_config_yaml():
    app_module.tasks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/ctgan",
        data={
            "session_key": "invalid-attribute-config",
            "callback": "http://callback.local/test",
            "attribute_config": (io.BytesIO(b"configurations: ["), "attribute_config.yaml"),
            "algorithm_config": (
                io.BytesIO(b"synthetization_configuration:\n  algorithm: {}\n"),
                "algorithm_config.yaml",
            ),
            "data": (io.BytesIO(b"col_a\n1\n"), "data.csv"),
            "original_data": (io.BytesIO(b"col_a\n1\n"), "original_data.csv"),
        },
        content_type="multipart/form-data",
    )

    assert response.status_code == 400
    payload = response.get_json()
    assert payload["message"] == "Uploaded file 'attribute_config' contains invalid YAML."
    assert payload["session_key"] == "invalid-attribute-config"


def test_start_synthetization_process_returns_400_for_invalid_algorithm_config_shape():
    app_module.tasks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/ctgan",
        data={
            "session_key": "invalid-algorithm-config",
            "callback": "http://callback.local/test",
            "attribute_config": (
                io.BytesIO(b"configurations:\n  - name: age\n    type: INTEGER\n"),
                "attribute_config.yaml",
            ),
            "algorithm_config": (io.BytesIO(b"foo: bar\n"), "algorithm_config.yaml"),
            "data": (io.BytesIO(b"age\n1\n"), "data.csv"),
            "original_data": (io.BytesIO(b"age\n1\n"), "original_data.csv"),
        },
        content_type="multipart/form-data",
    )

    assert response.status_code == 400
    payload = response.get_json()
    assert payload["message"] == "Uploaded file 'algorithm_config' is missing required key 'synthetization_configuration'."
    assert payload["session_key"] == "invalid-algorithm-config"


def test_start_synthetization_process_returns_400_for_invalid_data_csv():
    app_module.tasks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/ctgan",
        data={
            "session_key": "invalid-data-csv",
            "callback": "http://callback.local/test",
            "attribute_config": (
                io.BytesIO(b"configurations:\n  - name: age\n    type: INTEGER\n"),
                "attribute_config.yaml",
            ),
            "algorithm_config": (
                io.BytesIO(b"synthetization_configuration:\n  algorithm: {}\n"),
                "algorithm_config.yaml",
            ),
            "data": (io.BytesIO(b"age\n\"unterminated\n"), "data.csv"),
            "original_data": (io.BytesIO(b"age\n1\n"), "original_data.csv"),
        },
        content_type="multipart/form-data",
    )

    assert response.status_code == 400
    payload = response.get_json()
    assert payload["message"] == "Uploaded file 'data' contains invalid CSV data."
    assert payload["session_key"] == "invalid-data-csv"


def test_cancel_synthetization_process_returns_400_when_session_key_is_missing():
    app_module.tasks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/cancel_synthetization_process",
        data={"pid": "1234"},
        content_type="multipart/form-data",
    )

    assert response.status_code == 400
    payload = response.get_json()
    assert payload["message"] == "No session key provided"


def test_cancel_synthetization_process_returns_404_for_unknown_session():
    app_module.tasks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/cancel_synthetization_process",
        data={"session_key": "missing-session", "pid": "1234"},
        content_type="multipart/form-data",
    )

    assert response.status_code == 404
    payload = response.get_json()
    assert payload["message"] == "No running task found for session key"
    assert payload["session_key"] == "missing-session"


def test_cancel_synthetization_process_returns_409_for_pid_mismatch():
    app_module.tasks.clear()
    session_key = "pid-mismatch-session"
    app_module.tasks[session_key] = CancelableProcess(pid=2222)
    client = app_module.app.test_client()

    response = client.post(
        "/cancel_synthetization_process",
        data={"session_key": session_key, "pid": "1111"},
        content_type="multipart/form-data",
    )

    assert response.status_code == 409
    payload = response.get_json()
    assert payload["message"] == "PID does not match the registered task"
    assert payload["session_key"] == session_key
    assert payload["pid"] == 2222


def test_cancel_synthetization_process_cleans_up_task_and_marks_status_cancelled():
    app_module.tasks.clear()
    session_key = "cancel-success-session"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "ctgan")

    process = CancelableProcess(pid=9876, alive=True)
    app_module.tasks[session_key] = process
    client = app_module.app.test_client()

    response = client.post(
        "/cancel_synthetization_process",
        data={"session_key": session_key, "pid": "9876"},
        content_type="multipart/form-data",
    )

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["message"] == "Task canceled"
    assert payload["session_key"] == session_key
    assert payload["pid"] == 9876
    assert process.terminate_called is True
    assert session_key not in app_module.tasks

    with status_path.open("r", encoding="utf-8") as handle:
        status = yaml.safe_load(handle)

    steps = {step["step"]: step for step in status["status"]}
    assert steps["callback"]["completed"] in {False, "False"}
    assert steps["fitting"]["remaining_time"] == "Cancelled"
    assert steps["sampling"]["remaining_time"] == "Cancelled"
    assert status["components"]["structured_synthesis"]["remaining_time"] == "Cancelled"
    assert status["components"]["llm_synthesis"]["remaining_time"] == "Cancelled"
    assert status["components"]["total_synthesis"]["remaining_time"] == "Cancelled"

    _delete_status_file(session_key)


def test_get_status_prunes_finished_tasks():
    app_module.tasks.clear()
    session_key = "finished-task-session"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "ctgan")
    app_module.tasks[session_key] = FinishedProcess(pid=2468)
    client = app_module.app.test_client()

    response = client.get(f"/get_status/{session_key}")

    assert response.status_code == 200
    assert session_key not in app_module.tasks

    _delete_status_file(session_key)


def test_synthesize_data_uses_original_data_as_text_reference_dataset(monkeypatch):
    captured_calls = []

    class DummyResponse:
        status_code = 200
        text = "ok"

        @staticmethod
        def raise_for_status():
            return None

    def fake_run_synthesizer_stage(**kwargs):
        captured_calls.append(kwargs)
        return kwargs["input_data"].copy(), b"model", 0.1, 0.2, 0.3

    monkeypatch.setattr(
        app_module,
        "synthesizer_classes",
        {
            "llm_text_synth": {"class": object},
        },
    )
    monkeypatch.setattr(app_module, "get_data_modality", lambda _name: "text_only")
    monkeypatch.setattr(
        app_module,
        "load_text_synthesis_defaults",
        lambda _name: {"llm_profile": {}, "model_parameter": {}, "model_fitting": {}, "sampling": {}},
    )
    monkeypatch.setattr(app_module, "run_synthesizer_stage", fake_run_synthesizer_stage)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: DummyResponse())

    data = app_module.pd.DataFrame([{"note": "[TEXT_PENDING_LLM]"}])
    original_data = app_module.pd.DataFrame([{"note": "original note"}])
    session_key = "text-reference-original-data"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "llm_text_synth")

    result = app_module.synthesize_data(
        "llm_text_synth",
        str(status_path),
        {"configurations": [{"name": "note", "type": "TEXT", "index": 0}]},
        _algorithm_config(),
        data,
        original_data,
        "http://callback.local/test",
        session_key,
    )

    assert result["status_code"] == 200
    assert len(captured_calls) == 1
    assert captured_calls[0]["input_data"].equals(data)
    assert captured_calls[0]["reference_data"].equals(original_data)

    _delete_status_file(session_key)


def test_synthesize_data_rejects_legacy_direct_mixed_synthesis(monkeypatch, tmp_path):
    monkeypatch.setattr(app_module, "synthesizer_classes", {"llm_mixed": {"class": object}})
    monkeypatch.setattr(app_module, "get_data_modality", lambda _: "mixed")
    errors = []
    monkeypatch.setattr(app_module, "send_callback_error", lambda *args: errors.append(args))
    result = app_module.synthesize_data(
        "llm_mixed", str(tmp_path / "status.yaml"), _text_attribute_config(), _algorithm_config(),
        app_module.pd.DataFrame([{"age": 80, "note": "Source"}]), None, "http://callback", "test",
    )
    assert result["status_code"] == 500
    assert "structured synthesizer first" in errors[0][2]


def test_synthesize_data_marks_llm_component_before_text_stage_starts(monkeypatch):
    class DummyResponse:
        status_code = 200
        text = "ok"

        @staticmethod
        def raise_for_status():
            return None

    def fake_run_synthesizer_stage(**kwargs):
        with status_path.open("r", encoding="utf-8") as handle:
            status = yaml.safe_load(handle)
        assert status["components"]["llm_synthesis"]["synthesizer_name"] == "llm_text_synth"
        return kwargs["input_data"].copy(), b"model", 0.1, 0.2, 0.3

    monkeypatch.setattr(
        app_module,
        "synthesizer_classes",
        {
            "llm_text_synth": {"class": object},
        },
    )
    monkeypatch.setattr(app_module, "get_data_modality", lambda _name: "text_only")
    monkeypatch.setattr(
        app_module,
        "load_text_synthesis_defaults",
        lambda _name: {"llm_profile": {}, "model_parameter": {}, "model_fitting": {}, "sampling": {}},
    )
    monkeypatch.setattr(app_module, "run_synthesizer_stage", fake_run_synthesizer_stage)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: DummyResponse())

    data = app_module.pd.DataFrame([{"note": "input note"}])
    session_key = "text-component-visible-early"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "llm_text_synth")

    result = app_module.synthesize_data(
        "llm_text_synth",
        str(status_path),
        {"configurations": [{"name": "note", "type": "TEXT", "index": 0}]},
        _algorithm_config(),
        data,
        None,
        "http://callback.local/test",
        session_key,
    )

    assert result["status_code"] == 200

    _delete_status_file(session_key)


def test_synthesize_data_falls_back_to_input_data_when_original_data_is_missing(monkeypatch):
    captured_calls = []

    class DummyResponse:
        status_code = 200
        text = "ok"

        @staticmethod
        def raise_for_status():
            return None

    def fake_run_synthesizer_stage(**kwargs):
        captured_calls.append(kwargs)
        return kwargs["input_data"].copy(), b"model", 0.1, 0.2, 0.3

    monkeypatch.setattr(
        app_module,
        "synthesizer_classes",
        {
            "llm_text_synth": {"class": object},
        },
    )
    monkeypatch.setattr(app_module, "get_data_modality", lambda _name: "text_only")
    monkeypatch.setattr(
        app_module,
        "load_text_synthesis_defaults",
        lambda _name: {"llm_profile": {}, "model_parameter": {}, "model_fitting": {}, "sampling": {}},
    )
    monkeypatch.setattr(app_module, "run_synthesizer_stage", fake_run_synthesizer_stage)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: DummyResponse())

    data = app_module.pd.DataFrame([{"note": "input note"}])
    session_key = "text-reference-fallback-data"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "llm_text_synth")

    result = app_module.synthesize_data(
        "llm_text_synth",
        str(status_path),
        {"configurations": [{"name": "note", "type": "TEXT", "index": 0}]},
        _algorithm_config(),
        data,
        None,
        "http://callback.local/test",
        session_key,
    )

    assert result["status_code"] == 200
    assert len(captured_calls) == 1
    assert captured_calls[0]["input_data"].equals(data)
    assert captured_calls[0]["reference_data"].equals(data)

    _delete_status_file(session_key)


def test_synthesize_data_requires_text_method_for_mixed_data(monkeypatch):
    captured_calls = []

    class DummyResponse:
        status_code = 200
        text = "ok"

        @staticmethod
        def raise_for_status():
            return None

    def fake_run_synthesizer_stage(**kwargs):
        captured_calls.append(kwargs)
        return kwargs["input_data"].copy(), b"model", 0.1, 0.2, 0.3

    monkeypatch.setattr(
        app_module,
        "synthesizer_classes",
        {
            "ctgan": {"class": object},
        },
    )
    monkeypatch.setattr(app_module, "get_data_modality", lambda _name: "structured_only")
    monkeypatch.setattr(app_module, "run_synthesizer_stage", fake_run_synthesizer_stage)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: DummyResponse())

    data = app_module.pd.DataFrame([{"age": 50, "note": "[TEXT_PENDING_LLM]"}])
    original_data = app_module.pd.DataFrame([{"age": 70, "note": "original note"}])
    session_key = "two-stage-reference-original-data"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "ctgan")

    result = app_module.synthesize_data(
        "ctgan",
        str(status_path),
        _text_attribute_config(),
        _algorithm_config(),
        data,
        original_data,
        "http://callback.local/test",
        session_key,
    )

    assert result["status_code"] == 500
    assert captured_calls == []

    _delete_status_file(session_key)


def test_get_algorithms_includes_processing_capabilities(monkeypatch):
    monkeypatch.setattr(
        app_module,
        "synthesizer_classes",
        {
            "ctgan": {
                "display_name": "CTGAN",
                "version": "1.0",
                "type": "generative_model",
                "class": object,
                "description": "Test synthesizer",
                "URL": "/synthetic_tabular_data_generator/synthesizer_config/ctgan.yaml",
            }
        },
    )

    client = app_module.app.test_client()
    response = client.get("/get_algorithms")

    assert response.status_code == 200
    payload = response.get_data(as_text=True)
    assert "processing_capabilities:" in payload
    assert "data_modality: structured_only" in payload
    assert "generation_scope" not in payload


def test_get_synthesizer_config_normalizes_llm_profile_into_model_parameter(monkeypatch):
    monkeypatch.setattr(app_module, "get_llm_profile_names", lambda: ["profile-a", "profile-b"])

    client = app_module.app.test_client()
    response = client.get("/synthetic_tabular_data_generator/synthesizer_config/llm_mixed_data_paraphrase_synthesis.yaml")

    assert response.status_code == 200

    payload = yaml.safe_load(response.get_data(as_text=True))
    configurations = payload["configurations"]

    assert "llm_profile" in configurations
    llm_profile_parameters = configurations["llm_profile"]["parameters"]
    assert len(llm_profile_parameters) == 1
    assert llm_profile_parameters[0]["values"] == ["profile-a", "profile-b"]
    assert llm_profile_parameters[0]["default_value"] == ""
    assert llm_profile_parameters[0]["mandatory"] is True


def test_get_synthesizer_config_omits_llm_profile_when_no_profiles_exist(monkeypatch):
    monkeypatch.setattr(app_module, "get_llm_profile_names", lambda: [])

    client = app_module.app.test_client()
    response = client.get("/synthetic_tabular_data_generator/synthesizer_config/llm_mixed_data_paraphrase_synthesis.yaml")

    assert response.status_code == 200

    payload = yaml.safe_load(response.get_data(as_text=True))
    configurations = payload["configurations"]

    assert "llm_profile" in configurations
    llm_profile_parameters = configurations["llm_profile"]["parameters"]
    assert len(llm_profile_parameters) == 1
    assert llm_profile_parameters[0]["values"] == []


def test_format_synthesis_exception_message_classifies_llm_configuration_errors():
    message = app_module._format_synthesis_exception_message(
        ValueError("Unknown llm_profile 'missing-profile'. Available profiles: none.")
    )

    assert message.startswith("LLM configuration error:")
    assert "Unknown llm_profile" in message


def test_format_synthesis_exception_message_classifies_llm_connection_errors():
    message = app_module._format_synthesis_exception_message(
        RuntimeError("Unable to reach the configured LLM API.")
    )

    assert message == "LLM connection error: Unable to reach the configured LLM API."


def test_format_synthesis_exception_message_falls_back_to_unexpected_error():
    message = app_module._format_synthesis_exception_message(RuntimeError("Pipeline did not produce output"))

    assert message == "Unexpected error occurred: Pipeline did not produce output"


def test_post_callback_request_disables_proxy_lookup(monkeypatch):
    captured = {}

    class DummyResponse:
        status_code = 200

    class DummySession:
        def __init__(self):
            self.trust_env = True

        def __enter__(self):
            captured["session"] = self
            return self

        def __exit__(self, exc_type, exc, tb):
            return None

        def post(self, url, **kwargs):
            captured["url"] = url
            captured["kwargs"] = kwargs
            return DummyResponse()

    monkeypatch.setattr(app_module.requests, "Session", DummySession)

    response = app_module.post_callback_request(
        "http://localhost:8080/callback",
        files={"model": ("model.pkl", b"model-bytes")},
        data={"session_key": "session-1"},
        timeout=12.5,
    )

    assert response.status_code == 200
    assert captured["session"].trust_env is False
    assert captured["url"] == "http://localhost:8080/callback"
    assert captured["kwargs"]["data"] == {"session_key": "session-1"}
    assert captured["kwargs"]["timeout"] == 12.5


def test_build_text_synthesis_algorithm_config_preserves_num_samples_for_direct_text_only_synthesizer(monkeypatch):
    monkeypatch.setattr(
        app_module,
        "load_text_synthesis_defaults",
        lambda _name: {"llm_profile": {}, "model_parameter": {}, "model_fitting": {}, "sampling": {}},
    )

    config = app_module.build_text_synthesis_algorithm_config(
        {
            "synthetization_configuration": {
                "algorithm": {
                    "sampling": {
                        "num_samples": 2,
                        "temperature": 0.8,
                    },
                }
            }
        },
        "llm_text_only_embedding_nearest_neighbor_synthesis",
        57,
    )

    algorithm = config["synthetization_configuration"]["algorithm"]
    assert algorithm["sampling"]["num_samples"] == 2
    assert algorithm["sampling"]["temperature"] == 0.8


@pytest.mark.parametrize("structured_name", ["ctgan", "tvae", "arf", "ddpm", "rtvae", "bayesian_network", "llm_tabular"])
@pytest.mark.parametrize("with_original", [False, True])
@pytest.mark.parametrize("text_samples", [None, 6])
def test_structured_then_text_pipeline_preserves_config_rows_and_models(monkeypatch, tmp_path, structured_name, with_original, text_samples):
    import cloudpickle
    import pandas as pd

    calls, callbacks = [], []
    text_name = "llm_mixed_data_embedding_nearest_neighbor_synthesis"
    monkeypatch.setattr(app_module, "synthesizer_classes", {structured_name: {}, text_name: {}})
    monkeypatch.setattr(app_module, "get_data_modality", lambda name: "mixed" if name == text_name else "structured_only")
    monkeypatch.setattr(app_module, "send_callback_error", lambda *args: pytest.fail(str(args)))
    count = text_samples or 3
    synthetic = pd.DataFrame({"age": pd.Series(([65, 42, None] * 2)[:count], dtype="Int64")})

    def run_stage(**kwargs):
        calls.append(kwargs)
        if kwargs["stage_label"] == "STRUCTURED_SYNTHESIS":
            assert list(kwargs["input_data"].columns) == ["age"]
            assert kwargs["stage_algorithm_config"]["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] == count
            return synthetic.copy(), b"structured-model", 1, 2, 3
        pd.testing.assert_frame_equal(kwargs["input_data"][["age"]], synthetic)
        assert kwargs["preserve_structured_values"] is True
        assert kwargs["input_data"].iloc[:, -1].isna().all()
        result = kwargs["input_data"].copy()
        result[result.columns[-1]] = [f"Text {i}" for i in range(count)]
        return result, b"text-model", 1, 2, 3

    monkeypatch.setattr(app_module, "run_synthesizer_stage", run_stage)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: (
        callbacks.append(kwargs) or types.SimpleNamespace(status_code=200, raise_for_status=lambda: None)))
    attrs = {"configurations": [{"name": "note", "type": "TEXT", "index": 0},
                                {"name": "age", "type": "INTEGER", "index": 1},
                                {"name": "report", "type": "TEXT", "index": 2}]}
    data = pd.DataFrame([{"age": 80, "note": "Input", "report": "Report"}])
    original = pd.DataFrame([{"age": 90, "note": "Original", "report": "Original report"}]) if with_original else None
    config = {"synthetization_configuration": {
        "algorithm": {"synthesizer": structured_name, "model_parameter": {"number_of_layers": 3},
                      "model_fitting": {"epochs": 17}, "sampling": {"num_samples": 3},
                      "hyperparameter_tuning": {"enabled": structured_name != "llm_tabular", "n_trials": 2,
                                                "sampler": "grid", "pruner": "none", "timeout_minutes": 5}},
        "text_synthesis_configuration": {"synthetization_configuration": {"algorithm": {
            "synthesizer": text_name, "model_parameter": {"few_shot_examples": 5},
            "sampling": {"num_samples": text_samples, "temperature": 0.4},
        }}},
    }}
    status = tmp_path / "status.yaml"
    initialize_status_file(str(status), "pipeline", structured_name)
    result = app_module.synthesize_data(structured_name, str(status), attrs, config, data, original,
                                        "http://callback", "pipeline")
    assert result["status_code"] == 200
    assert len(calls) == 3
    original_algorithm = config["synthetization_configuration"]["algorithm"]
    executed_algorithm = calls[0]["stage_algorithm_config"]["synthetization_configuration"]["algorithm"]
    assert executed_algorithm == {**original_algorithm, "sampling": {"num_samples": count}}
    assert original_algorithm["sampling"]["num_samples"] == 3
    for call in calls[1:]:
        algorithm = call["stage_algorithm_config"]["synthetization_configuration"]["algorithm"]
        assert algorithm["sampling"]["num_samples"] == count
        assert algorithm["sampling"]["temperature"] == 0.4
        assert algorithm["model_parameter"]["few_shot_examples"] == 5
        assert "hyperparameter_tuning" not in algorithm
        pd.testing.assert_frame_equal(call["reference_data"],
            (original if original is not None else data)[list(call["input_data"].columns)])
    files = callbacks[0]["files"]
    output = pd.read_csv(files["synthetic_data"][1])
    assert len(output) == count
    assert list(output.columns) == ["note", "age", "report"]
    assert output["age"].iloc[:2].tolist() == [65, 42]
    assert pd.isna(output["age"].iloc[2])
    model = cloudpickle.load(files["model"][1])
    assert model == {"structured_synthesis": b"structured-model",
                     "text_synthesis": {"note": b"text-model", "report": b"text-model"}}
    assert config["synthetization_configuration"]["text_synthesis_configuration"]["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"] == text_samples


def test_text_stage_preserves_final_structured_values_without_preprocessing(monkeypatch, tmp_path):
    import pandas as pd
    from synthetic_tabular_data_generator.algorithms.llm_mixed_data_paraphrase_synthesis import (
        LlmMixedDataParaphraseSynthesisSynthesizer,
    )

    text = "age: 0; active: false; weight: 65.125; visit_date: 02.01.2024."

    class LocalTextSynthesizer(LlmMixedDataParaphraseSynthesisSynthesizer):
        def _initialize_anonymization_configuration(self, config):
            self._fitting_kwargs = {"max_retries": 1}

        def _initialize_synthesizer(self):
            self._llm_client = types.SimpleNamespace(generate_text=lambda _: '{"row": {"note": "' + text + '"}}')

    def forbid_preprocessing(*args, **kwargs):
        pytest.fail("The text stage must not impute, round, or drop ground-truth data")

    monkeypatch.setattr(app_module, "synthesizer_classes", {"local_text": {"class": LocalTextSynthesizer}})
    monkeypatch.setattr(app_module, "pre_process_dataframe", forbid_preprocessing)
    monkeypatch.setattr(app_module, "post_process_dataframe", forbid_preprocessing)
    source = pd.DataFrame({"age": pd.Series([0], dtype="Int64"), "active": [False], "weight": [65.125],
                           "visit_date": ["02.01.2024"], "unknown": pd.Series([None], dtype="Int64"), "note": [None]})
    attrs = {"configurations": [{"name": name, "type": kind} for name, kind in
             [("age", "INTEGER"), ("active", "BOOLEAN"), ("weight", "DECIMAL"),
              ("visit_date", "DATE"), ("unknown", "INTEGER"), ("note", "TEXT")]]}
    attrs["configurations"][3]["configurations"] = [{"dateFormatter": "dd.MM.yyyy"}]
    status = tmp_path / "status.yaml"
    initialize_status_file(str(status), "text-stage", "local_text")
    samples, *_ = app_module.run_synthesizer_stage(
        stage_label="TEXT_SYNTHESIS", synthesizer_name="local_text", stage_attribute_config=attrs,
        stage_algorithm_config={}, input_data=source, reference_data=source,
        file_path_status=str(status), preserve_structured_values=True,
    )
    pd.testing.assert_frame_equal(samples.drop(columns="note"), source.drop(columns="note"))
    assert samples["note"].tolist() == [text]


@pytest.fixture
def job_environment(monkeypatch, tmp_path):
    app_module.tasks.clear()
    monkeypatch.setattr(app_module, "MAX_SYNTHESIS_JOBS", 1)
    monkeypatch.setattr(app_module, "RESULTS_DIR", tmp_path / "results")
    monkeypatch.setattr(app_module, "_status_file_path", lambda key: str(tmp_path / f"{key}.yaml"))
    yield tmp_path
    for process in list(app_module.tasks.values()):
        if hasattr(process, "terminate") and app_module._task_is_alive(process):
            app_module._terminate_task_process(process)
    app_module.tasks.clear()


def test_start_rejects_duplicate_and_capacity_before_reading_uploads(monkeypatch, job_environment):
    key = "active"
    status_path = app_module._status_file_path(key)
    initialize_status_file(status_path, key, "ctgan")
    before = Path(status_path).read_bytes()
    app_module.tasks[key] = CancelableProcess()
    monkeypatch.setattr(app_module, "initialize_input_data", lambda _: pytest.fail("must reject before reading input"))
    client = app_module.app.test_client()
    assert client.post("/start_synthetization_process/ctgan", data={"session_key": key}).status_code == 409
    assert client.post("/start_synthetization_process/ctgan", data={"session_key": "other"}).status_code == 429
    assert Path(status_path).read_bytes() == before
    assert not Path(app_module._status_file_path("other")).exists()


def test_simultaneous_starts_register_only_one_process(monkeypatch, job_environment):
    from concurrent.futures import ThreadPoolExecutor
    import threading
    barrier = threading.Barrier(2)
    starts = []

    def initialize(_):
        key = app_module.request.form["session_key"]
        path = app_module._status_file_path(key)
        initialize_status_file(path, key, "ctgan")
        starts.append(key)
        return key, "http://callback", path, {}, {}, app_module.pd.DataFrame(), None

    monkeypatch.setattr(app_module, "initialize_input_data", initialize)
    monkeypatch.setattr(app_module.PROCESS_CONTEXT, "Process", StartedProcess)

    def start():
        barrier.wait(timeout=5)
        with app_module.app.test_client() as client:
            return client.post("/start_synthetization_process/ctgan", data={"session_key": "same"}).status_code

    with ThreadPoolExecutor(max_workers=2) as executor:
        codes = list(executor.map(lambda _: start(), range(2)))
    assert sorted(codes) == [202, 409]
    assert starts == ["same"]


def test_process_start_failure_marks_job_failed_and_frees_capacity(monkeypatch, job_environment):
    key = "start-failed"
    path = app_module._status_file_path(key)
    initialize_status_file(path, key, "ctgan")
    monkeypatch.setattr(app_module, "initialize_input_data", lambda _: (
        key, "http://callback", path, {}, {}, app_module.pd.DataFrame(), None,
    ))

    class FailingProcess(StartedProcess):
        def start(self):
            raise OSError("cannot start process")

    monkeypatch.setattr(app_module.PROCESS_CONTEXT, "Process", FailingProcess)
    response = app_module.app.test_client().post("/start_synthetization_process/ctgan", data={"session_key": key})
    assert response.status_code == 500
    assert not app_module.tasks
    assert yaml.safe_load(Path(path).read_text())["job_state"] == "failed"


def test_real_spawned_process_can_be_cancelled(job_environment):
    import time
    key = "real-cancel"
    path = app_module._status_file_path(key)
    initialize_status_file(path, key, "ctgan")
    process = app_module.PROCESS_CONTEXT.Process(target=time.sleep, args=(30,))
    process.start()
    app_module.tasks[key] = process
    pid = process.pid
    response = app_module.app.test_client().post(
        "/cancel_synthetization_process", data={"session_key": key, "pid": str(pid)},
    )
    assert response.status_code == 200
    assert key not in app_module.tasks
    assert yaml.safe_load(Path(path).read_text())["job_state"] == "cancelled"


def test_crashed_process_is_reaped_and_marked_failed(job_environment):
    import os
    key = "real-crash"
    path = app_module._status_file_path(key)
    initialize_status_file(path, key, "ctgan")
    process = app_module.PROCESS_CONTEXT.Process(target=os._exit, args=(7,))
    process.start()
    app_module.tasks[key] = process
    process.join(timeout=10)
    assert not process.is_alive()
    response = app_module.app.test_client().get(f"/get_status/{key}")
    assert response.status_code == 200
    assert response.json["job_state"] == "failed"
    assert "exitcode=7" in response.json["error"]
    assert key not in app_module.tasks


def test_synthesis_failure_records_terminal_state_before_error_callback(monkeypatch, job_environment):
    key = "failed"
    path = app_module._status_file_path(key)
    initialize_status_file(path, key, "ctgan")
    monkeypatch.setattr(app_module, "synthesizer_classes", {"ctgan": {"class": object}})
    monkeypatch.setattr(app_module, "get_data_modality", lambda _: "structured_only")
    monkeypatch.setattr(app_module, "run_synthesizer_stage", lambda **_: (_ for _ in ()).throw(ValueError("training failed")))
    observed = []
    monkeypatch.setattr(app_module, "send_callback_error", lambda *args: observed.append(yaml.safe_load(Path(path).read_text())))
    result = app_module.synthesize_data("ctgan", path, _structured_attribute_config(), _algorithm_config(),
                                        app_module.pd.DataFrame({"age": [42]}), None, "http://callback", key)
    assert result["status_code"] == 500
    assert observed[0]["job_state"] == "failed"
    assert "training failed" in observed[0]["error"]
    assert observed[0]["components"]["total_synthesis"]["remaining_time"] == "Failed"


def test_failed_delivery_retains_results_and_retry_does_not_resynthesize(monkeypatch, job_environment):
    import requests
    key = "delivery"
    path = app_module._status_file_path(key)
    initialize_status_file(path, key, "ctgan")
    app_module.update_pipeline_totals(path, 1, 2, 3, completed=True)
    samples = app_module.pd.DataFrame({"age": [42]})
    app_module._save_callback_result(key, "http://original-callback", samples, b"model-bytes")
    attempts = []

    def post(url, **kwargs):
        attempts.append((url, {name: entry[1].read() for name, entry in kwargs["files"].items()}))
        if len(attempts) == 1:
            raise requests.Timeout("response lost")
        return types.SimpleNamespace(raise_for_status=lambda: None)

    monkeypatch.setattr(app_module, "post_callback_request", post)
    monkeypatch.setattr(app_module, "send_callback_error", lambda *args: pytest.fail("delivery failure is not synthesis failure"))
    first = app_module._deliver_saved_result(key, path)
    assert first["status_code"] == 502
    assert len(attempts) == 1
    state = yaml.safe_load(Path(path).read_text())
    assert state["job_state"] == "delivery_failed"
    assert state["components"]["total_synthesis"]["completed"] == "True"
    assert app_module._result_directory(key).exists()
    assert app_module.app.test_client().post("/start_synthetization_process/ctgan", data={"session_key": key}).status_code == 409

    monkeypatch.setattr(app_module.PROCESS_CONTEXT, "Process", StartedProcess)
    client = app_module.app.test_client()
    assert client.post("/retry_synthetization_callback", data={"session_key": key}).status_code == 202
    assert client.post("/retry_synthetization_callback", data={"session_key": key}).status_code == 409
    task = app_module.tasks[key]
    assert task.target is app_module._deliver_saved_result
    assert task.target(*task.args)["status_code"] == 200
    assert attempts[0] == attempts[1]
    assert attempts[1][1]["synthetic_data"] == samples.to_csv(index=False).encode()
    assert attempts[1][1]["model"] == b"model-bytes"
    assert not app_module._result_directory(key).exists()
    state = yaml.safe_load(Path(path).read_text())
    assert state["job_state"] == "succeeded"
    assert next(step for step in state["status"] if step["step"] == "callback")["completed"] == "True"


def test_crash_during_delivery_preserves_completed_synthesis(job_environment):
    key = "delivery-crash"
    path = app_module._status_file_path(key)
    initialize_status_file(path, key, "ctgan")
    app_module.update_pipeline_totals(path, 1, 2, 3, completed=True)
    app_module._save_callback_result(key, "http://callback", app_module.pd.DataFrame({"age": [42]}), b"model")
    app_module._finish_job(path, "delivering")
    app_module.tasks[key] = FinishedProcess(exitcode=-9)
    app_module._prune_finished_tasks()
    state = yaml.safe_load(Path(path).read_text())
    assert state["job_state"] == "delivery_failed"
    assert state["components"]["total_synthesis"]["completed"] == "True"
    assert app_module._result_directory(key).exists()


def test_cancel_does_not_overwrite_confirmed_delivery(job_environment):
    key = "already-delivered"
    path = app_module._status_file_path(key)
    initialize_status_file(path, key, "ctgan")
    app_module._finish_job(path, "succeeded")
    app_module.tasks[key] = CancelableProcess(pid=123)
    response = app_module.app.test_client().post(
        "/cancel_synthetization_process", data={"session_key": key, "pid": "123"},
    )
    assert response.status_code == 409
    assert yaml.safe_load(Path(path).read_text())["job_state"] == "succeeded"


def test_delivered_result_is_retained_when_final_status_cannot_be_saved(monkeypatch, job_environment):
    key = "status-failure"
    path = app_module._status_file_path(key)
    initialize_status_file(path, key, "ctgan")
    app_module._save_callback_result(key, "http://callback", app_module.pd.DataFrame({"age": [42]}), b"model")
    original = app_module.set_job_status

    def set_status(file_path, state, error=None):
        if state == "succeeded":
            raise OSError("disk unavailable")
        original(file_path, state, error)

    monkeypatch.setattr(app_module, "set_job_status", set_status)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: types.SimpleNamespace(raise_for_status=lambda: None))
    result = app_module._deliver_saved_result(key, path)
    assert result["status_code"] == 500
    assert "check the receiver" in result["message"]
    assert app_module._result_directory(key).exists()


def test_structured_llm_restores_empty_columns(monkeypatch, tmp_path):
    import json
    from synthetic_tabular_data_generator.algorithms.llm_tabular import LlmTabularSynthesizer

    class LocalLlm(LlmTabularSynthesizer):
        def _initialize_anonymization_configuration(self, config):
            self._fitting_kwargs = {"max_retries": 1}
            self._sampling = {"num_samples": 2}

        def _initialize_synthesizer(self):
            self._llm_client = types.SimpleNamespace(generate_text=lambda _: json.dumps({"rows": [{"age": 42}]}))

    monkeypatch.setitem(app_module.synthesizer_classes, "local", {"class": LocalLlm})
    attrs = {"configurations": [{"name": "empty", "type": "INTEGER", "index": 0},
                                 {"name": "age", "type": "INTEGER", "index": 1}]}
    source = app_module.pd.DataFrame({"empty": [None, None], "age": [40, 50]})
    path = str(tmp_path / "status.yaml")
    initialize_status_file(path, "empty-column", "local")
    result, *_ = app_module.run_synthesizer_stage(
        stage_label="STRUCTURED", synthesizer_name="local", stage_attribute_config=attrs,
        stage_algorithm_config={}, input_data=source, file_path_status=path,
    )
    assert list(result.columns) == ["empty", "age"]
    assert len(result) == 2
    assert result["empty"].isna().all()
    assert result["age"].tolist() == [42, 42]
    assert len(attrs["configurations"]) == 2
    with pytest.raises(ValueError, match="no non-empty columns"):
        app_module.run_synthesizer_stage(
            stage_label="STRUCTURED", synthesizer_name="local", stage_attribute_config=attrs,
            stage_algorithm_config={}, input_data=source.assign(age=None), file_path_status=path,
        )
