import io
import json
import logging
import sys
import warnings
from pathlib import Path

import yaml

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from api_utility.logging import logger as logger_module
from api_utility.status.status_updater import (
    InterceptStdOut,
    initialize_status_file,
    update_status,
)


def _read_status(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def _get_step(data: dict, step_name: str) -> dict:
    for step in data["status"]:
        if step["step"] == step_name:
            return step
    raise AssertionError(f"Step not found: {step_name}")


def test_initialize_status_file_creates_expected_structure(tmp_path):
    status_path = tmp_path / "outputs" / "status" / "123.yaml"

    initialize_status_file(str(status_path), session_key=123, synthesizer_name="ctgan")

    assert status_path.exists()
    data = _read_status(status_path)

    assert data["session_key"] == "123"
    assert data["synthesizer_name"] == "ctgan"
    assert [step["step"] for step in data["status"]] == [
        "initialization",
        "fitting",
        "sampling",
        "callback",
    ]

    assert _get_step(data, "initialization") == {
        "step": "initialization",
        "duration": "Waiting",
        "completed": "False",
    }
    assert _get_step(data, "fitting") == {
        "step": "fitting",
        "duration": "Waiting",
        "completed": "False",
        "remaining_time": "Waiting",
    }
    assert _get_step(data, "sampling") == {
        "step": "sampling",
        "duration": "Waiting",
        "completed": "False",
        "remaining_time": "Waiting",
    }
    assert _get_step(data, "callback") == {
        "step": "callback",
        "completed": "False",
    }


def test_update_status_updates_only_target_step(tmp_path):
    status_path = tmp_path / "outputs" / "status" / "abc.yaml"
    initialize_status_file(str(status_path), session_key="abc", synthesizer_name="rtvae")

    update_status(
        str(status_path),
        step="fitting",
        duration=1.25,
        completed=True,
        remaining_time="10",
    )

    data = _read_status(status_path)
    fitting = _get_step(data, "fitting")
    initialization = _get_step(data, "initialization")

    assert fitting["duration"] == "1.25"
    assert fitting["completed"] == "True"
    assert fitting["remaining_time"] == "10"
    assert initialization["duration"] == "Waiting"
    assert initialization["completed"] == "False"


def test_update_status_does_not_add_remaining_time_to_callback_step(tmp_path):
    status_path = tmp_path / "outputs" / "status" / "callback.yaml"
    initialize_status_file(str(status_path), session_key="1", synthesizer_name="ctgan")

    update_status(str(status_path), step="callback", completed=True, remaining_time="99")

    data = _read_status(status_path)
    callback = _get_step(data, "callback")
    assert callback["completed"] == "True"
    assert "remaining_time" not in callback


def test_intercept_stdout_updates_remaining_time_when_message_matches(monkeypatch, tmp_path):
    status_path = tmp_path / "outputs" / "status" / "run.yaml"
    initialize_status_file(str(status_path), session_key="run", synthesizer_name="ddpm")

    fake_terminal = io.StringIO()
    monkeypatch.setattr(sys, "stdout", fake_terminal)
    interceptor = InterceptStdOut(str(status_path), "fitting")

    interceptor.write("Estimated remaining time: 12.5 seconds")

    data = _read_status(status_path)
    assert _get_step(data, "fitting")["remaining_time"] == "12.5"
    assert "Estimated remaining time: 12.5 seconds" in fake_terminal.getvalue()


def test_intercept_stdout_ignores_messages_without_estimate(monkeypatch, tmp_path):
    status_path = tmp_path / "outputs" / "status" / "run2.yaml"
    initialize_status_file(str(status_path), session_key="run2", synthesizer_name="arf")

    fake_terminal = io.StringIO()
    monkeypatch.setattr(sys, "stdout", fake_terminal)
    interceptor = InterceptStdOut(str(status_path), "sampling")

    interceptor.write("normal progress message")

    data = _read_status(status_path)
    assert _get_step(data, "sampling")["remaining_time"] == "Waiting"


def test_intercept_stdout_close_flushes_underlying_terminal(monkeypatch, tmp_path):
    status_path = tmp_path / "outputs" / "status" / "run3.yaml"
    initialize_status_file(str(status_path), session_key="run3", synthesizer_name="tvae")

    class DummyTerminal:
        def __init__(self):
            self.flushed = False

        def write(self, _message):
            return None

        def flush(self):
            self.flushed = True

    terminal = DummyTerminal()
    monkeypatch.setattr(sys, "stdout", terminal)
    interceptor = InterceptStdOut(str(status_path), "fitting")

    interceptor.close()

    assert terminal.flushed is True


def test_my_json_formatter_includes_mapped_and_extra_fields():
    formatter = logger_module.MyJSONFormatter(
        fmt_keys={
            "level": "levelname",
            "text": "message",
            "logger": "name",
        }
    )

    record = logging.LogRecord(
        name="api-test",
        level=logging.INFO,
        pathname=__file__,
        lineno=123,
        msg="hello %s",
        args=("world",),
        exc_info=None,
    )
    record.request_id = "req-1"

    payload = json.loads(formatter.format(record))

    assert payload["level"] == "INFO"
    assert payload["text"] == "hello world"
    assert payload["logger"] == "api-test"
    assert payload["request_id"] == "req-1"
    assert "timestamp" in payload


def test_setup_logging_installs_hooks_and_uses_config(monkeypatch):
    old_excepthook = sys.excepthook
    old_showwarning = warnings.showwarning

    captured = {
        "config": None,
        "errors": [],
        "warnings": [],
    }

    def fake_dict_config(config):
        captured["config"] = config

    try:
        monkeypatch.chdir(PROJECT_ROOT)
        monkeypatch.setattr(logger_module.logging.config, "dictConfig", fake_dict_config)
        root_logger = logging.getLogger()
        monkeypatch.setattr(
            root_logger,
            "error",
            lambda message, exc_info=False: captured["errors"].append((message, exc_info)),
        )
        monkeypatch.setattr(
            logger_module.logging,
            "warning",
            lambda message: captured["warnings"].append(message),
        )

        logger_module.setup_logging()

        assert isinstance(captured["config"], dict)
        assert captured["config"]["version"] == 1

        sys.excepthook(ValueError, ValueError("boom"), None)
        assert captured["errors"] == [("Uncaught exception", True)]

        warnings.showwarning(UserWarning("careful"), UserWarning, "demo.py", 7)
        assert captured["warnings"]
        assert "careful" in captured["warnings"][0]
        assert "UserWarning" in captured["warnings"][0]
    finally:
        sys.excepthook = old_excepthook
        warnings.showwarning = old_showwarning
