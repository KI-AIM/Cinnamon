import io
import sys
import types
from pathlib import Path

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
    assert app_module.get_study_parameter_default("pruner") == "median"


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
    monkeypatch.setattr(app_module, "get_text_synthesizer_name", lambda: "llm_nearest_neighbor_few_shot_text_synthesis")
    monkeypatch.setattr(app_module, "get_processing_capabilities", lambda _name: (False, True))
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
    monkeypatch.setattr(app_module, "get_text_synthesizer_name", lambda: "llm_text_synth")
    monkeypatch.setattr(app_module, "get_processing_capabilities", lambda _name: (True, False))
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
    monkeypatch.setattr(app_module, "get_text_synthesizer_name", lambda: "llm_text_synth")
    monkeypatch.setattr(app_module, "get_processing_capabilities", lambda _name: (False, True))
    monkeypatch.setattr(
        app_module,
        "load_text_synthesis_defaults",
        lambda _name: {"llm_profile": {}, "model_parameter": {}, "model_fitting": {}, "sampling": {}},
    )
    monkeypatch.setattr(app_module, "run_synthesizer_stage", fake_run_synthesizer_stage)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: DummyResponse())

    data = app_module.pd.DataFrame([{"age": 50, "note": "[TEXT_PENDING_LLM]"}])
    original_data = app_module.pd.DataFrame([{"age": 70, "note": "original note"}])
    session_key = "text-reference-original-data"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "llm_text_synth")

    result = app_module.synthesize_data(
        "llm_text_synth",
        str(status_path),
        _text_attribute_config(),
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
    monkeypatch.setattr(app_module, "get_text_synthesizer_name", lambda: "llm_text_synth")
    monkeypatch.setattr(app_module, "get_processing_capabilities", lambda _name: (False, True))
    monkeypatch.setattr(
        app_module,
        "load_text_synthesis_defaults",
        lambda _name: {"llm_profile": {}, "model_parameter": {}, "model_fitting": {}, "sampling": {}},
    )
    monkeypatch.setattr(app_module, "run_synthesizer_stage", fake_run_synthesizer_stage)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: DummyResponse())

    data = app_module.pd.DataFrame([{"age": 50, "note": "input note"}])
    session_key = "text-component-visible-early"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "llm_text_synth")

    result = app_module.synthesize_data(
        "llm_text_synth",
        str(status_path),
        _text_attribute_config(),
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
    monkeypatch.setattr(app_module, "get_text_synthesizer_name", lambda: "llm_text_synth")
    monkeypatch.setattr(app_module, "get_processing_capabilities", lambda _name: (False, True))
    monkeypatch.setattr(
        app_module,
        "load_text_synthesis_defaults",
        lambda _name: {"llm_profile": {}, "model_parameter": {}, "model_fitting": {}, "sampling": {}},
    )
    monkeypatch.setattr(app_module, "run_synthesizer_stage", fake_run_synthesizer_stage)
    monkeypatch.setattr(app_module, "post_callback_request", lambda *args, **kwargs: DummyResponse())

    data = app_module.pd.DataFrame([{"age": 50, "note": "input note"}])
    session_key = "text-reference-fallback-data"
    status_path = _status_file_path(session_key)
    _delete_status_file(session_key)
    initialize_status_file(str(status_path), session_key, "llm_text_synth")

    result = app_module.synthesize_data(
        "llm_text_synth",
        str(status_path),
        _text_attribute_config(),
        _algorithm_config(),
        data,
        None,
        "http://callback.local/test",
        session_key,
    )

    assert result["status_code"] == 200
    assert len(captured_calls) == 1
    expected_input_data = app_module.create_text_synthesis_input(data, _text_attribute_config())
    assert captured_calls[0]["input_data"].equals(expected_input_data)
    assert captured_calls[0]["reference_data"].equals(data)

    _delete_status_file(session_key)


def test_synthesize_data_uses_original_data_for_two_stage_text_reference(monkeypatch):
    captured_calls = []

    class DummyResponse:
        status_code = 200
        text = "ok"

        @staticmethod
        def raise_for_status():
            return None

    def fake_run_synthesizer_stage(**kwargs):
        captured_calls.append(kwargs)
        input_data = kwargs["input_data"].copy()
        if kwargs["stage_label"] == "STRUCTURED_SYNTHESIS":
            return input_data, b"structured-model", 0.1, 0.2, 0.3
        return input_data, b"text-model", 0.1, 0.2, 0.3

    capability_map = {
        "ctgan": (True, False),
        "llm_text_synth": (False, True),
    }

    monkeypatch.setattr(
        app_module,
        "synthesizer_classes",
        {
            "ctgan": {"class": object},
            "llm_text_synth": {"class": object},
        },
    )
    monkeypatch.setattr(app_module, "get_text_synthesizer_name", lambda: "llm_text_synth")
    monkeypatch.setattr(app_module, "get_processing_capabilities", lambda name: capability_map[name])
    monkeypatch.setattr(
        app_module,
        "load_text_synthesis_defaults",
        lambda _name: {"llm_profile": {}, "model_parameter": {}, "model_fitting": {}, "sampling": {}},
    )
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

    assert result["status_code"] == 200
    assert len(captured_calls) == 2
    assert captured_calls[0]["stage_label"] == "STRUCTURED_SYNTHESIS"
    assert captured_calls[0]["reference_data"] is None
    assert captured_calls[1]["stage_label"] == "TEXT_SYNTHESIS"
    assert captured_calls[1]["reference_data"].equals(original_data)
    assert list(captured_calls[1]["input_data"].columns) == ["age", "note"]

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
    assert "generation_scope: structured_only" in payload


def test_get_synthesizer_config_normalizes_llm_profile_into_model_parameter(monkeypatch):
    monkeypatch.setattr(app_module, "get_llm_profile_names", lambda: ["profile-a", "profile-b"])

    client = app_module.app.test_client()
    response = client.get("/synthetic_tabular_data_generator/synthesizer_config/llm_nearest_neighbor_few_shot_text_synthesis.yaml")

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
    response = client.get("/synthetic_tabular_data_generator/synthesizer_config/llm_nearest_neighbor_few_shot_text_synthesis.yaml")

    assert response.status_code == 200

    payload = yaml.safe_load(response.get_data(as_text=True))
    configurations = payload["configurations"]

    assert "llm_profile" in configurations
    llm_profile_parameters = configurations["llm_profile"]["parameters"]
    assert len(llm_profile_parameters) == 1
    assert llm_profile_parameters[0]["values"] == []


def test_build_text_synthesis_algorithm_config_prefers_nested_text_configuration():
    config = app_module.build_text_synthesis_algorithm_config(
        {
            "synthetization_configuration": {
                "algorithm": {
                    "synthesizer": "ctgan",
                },
                "text_synthesis_configuration": {
                    "synthetization_configuration": {
                        "algorithm": {
                            "llm_profile": {
                                "llm_profile": "Profile Nested",
                            },
                            "sampling": {
                                "temperature": 0.4,
                                "top_p": 0.8,
                            },
                        }
                    }
                },
            },
            "text_synthesis_configuration": {
                "synthetization_configuration": {
                    "algorithm": {
                        "llm_profile": {
                            "llm_profile": "Profile Legacy",
                        },
                    }
                }
            },
        },
        "ctgan",
        "llm_nearest_neighbor_few_shot_text_synthesis",
        5,
    )

    algorithm = config["synthetization_configuration"]["algorithm"]
    assert algorithm["llm_profile"]["llm_profile"] == "Profile Nested"
    assert algorithm["sampling"]["temperature"] == 0.4
    assert algorithm["sampling"]["top_p"] == 0.8
    assert algorithm["sampling"]["num_samples"] == 5


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
