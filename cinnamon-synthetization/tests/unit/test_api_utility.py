import io
import sys
from pathlib import Path

import yaml

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from api_utility.status.status_updater import (
    InterceptStdOut,
    initialize_status_file,
    update_component_status,
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


def _get_component(data: dict, component_name: str) -> dict:
    component = data.get("components", {}).get(component_name)
    if component is None:
        raise AssertionError(f"Component not found: {component_name}")
    return component


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
        "remaining_time": "Waiting",
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
    assert _get_component(data, "structured_synthesis") == {
        "synthesizer_name": "Waiting",
        "duration": "Waiting",
        "initialization_duration": "Waiting",
        "fitting_duration": "Waiting",
        "fitting_remaining_time": "Waiting",
        "sampling_duration": "Waiting",
        "sampling_remaining_time": "Waiting",
        "remaining_time": "Waiting",
        "completed": "False",
    }
    assert _get_component(data, "llm_synthesis") == {
        "synthesizer_name": "Waiting",
        "duration": "Waiting",
        "initialization_duration": "Waiting",
        "fitting_duration": "Waiting",
        "fitting_remaining_time": "Waiting",
        "sampling_duration": "Waiting",
        "sampling_remaining_time": "Waiting",
        "remaining_time": "Waiting",
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
    assert initialization["remaining_time"] == "Waiting"


def test_update_status_does_not_add_remaining_time_to_callback_step(tmp_path):
    status_path = tmp_path / "outputs" / "status" / "callback.yaml"
    initialize_status_file(str(status_path), session_key="1", synthesizer_name="ctgan")

    update_status(str(status_path), step="callback", completed=True, remaining_time="99")

    data = _read_status(status_path)
    callback = _get_step(data, "callback")
    assert callback["completed"] == "True"
    assert "remaining_time" not in callback


def test_update_component_status_updates_only_target_component(tmp_path):
    status_path = tmp_path / "outputs" / "status" / "component.yaml"
    initialize_status_file(str(status_path), session_key="component", synthesizer_name="ctgan")

    update_component_status(
        str(status_path),
        "llm_synthesis",
        synthesizer_name="llm_nearest_neighbor_few_shot_text_synthesis",
        duration=3.5,
        initialization_duration=0.5,
        fitting_duration=1.0,
        sampling_duration=2.0,
        remaining_time="0",
        completed=True,
    )

    data = _read_status(status_path)
    llm_component = _get_component(data, "llm_synthesis")
    structured_component = _get_component(data, "structured_synthesis")

    assert llm_component == {
        "synthesizer_name": "llm_nearest_neighbor_few_shot_text_synthesis",
        "duration": "3.5",
        "initialization_duration": "0.5",
        "fitting_duration": "1.0",
        "fitting_remaining_time": "Waiting",
        "sampling_duration": "2.0",
        "sampling_remaining_time": "Waiting",
        "remaining_time": "0",
        "completed": "True",
    }
    assert structured_component["duration"] == "Waiting"
    assert structured_component["completed"] == "False"


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


def test_intercept_stdout_updates_component_remaining_time_when_configured(monkeypatch, tmp_path):
    status_path = tmp_path / "outputs" / "status" / "run_component.yaml"
    initialize_status_file(str(status_path), session_key="run_component", synthesizer_name="ctgan")

    fake_terminal = io.StringIO()
    monkeypatch.setattr(sys, "stdout", fake_terminal)
    interceptor = InterceptStdOut(str(status_path), "sampling", component_name="llm_synthesis")

    interceptor.write("Estimated remaining time: 7 seconds")

    data = _read_status(status_path)
    assert _get_step(data, "sampling")["remaining_time"] == "7"
    assert _get_component(data, "llm_synthesis")["remaining_time"] == "7"
    assert _get_component(data, "llm_synthesis")["sampling_remaining_time"] == "7"


def test_intercept_stdout_updates_remaining_time_from_tqdm_output(monkeypatch, tmp_path):
    status_path = tmp_path / "outputs" / "status" / "run_tqdm.yaml"
    initialize_status_file(str(status_path), session_key="run_tqdm", synthesizer_name="ctgan")

    fake_terminal = io.StringIO()
    monkeypatch.setattr(sys, "stdout", fake_terminal)
    interceptor = InterceptStdOut(str(status_path), "fitting")

    interceptor.write("\r 21%|██        | 21/100 [00:09<01:50,  1.40s/it]")

    data = _read_status(status_path)
    assert _get_step(data, "fitting")["remaining_time"] == "110"


def test_intercept_stdout_updates_remaining_time_from_split_tqdm_output(monkeypatch, tmp_path):
    status_path = tmp_path / "outputs" / "status" / "run_tqdm_split.yaml"
    initialize_status_file(str(status_path), session_key="run_tqdm_split", synthesizer_name="ctgan")

    fake_terminal = io.StringIO()
    monkeypatch.setattr(sys, "stdout", fake_terminal)
    interceptor = InterceptStdOut(str(status_path), "fitting")

    interceptor.write("\r 21%|██        | 21/100 [00:09<01")
    interceptor.write(":50,  1.40s/it]")

    data = _read_status(status_path)
    assert _get_step(data, "fitting")["remaining_time"] == "110"


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
