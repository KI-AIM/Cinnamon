import os
import re
import sys
from contextlib import contextmanager

import yaml


WAITING = "Waiting"
STATUS_TRUE = "True"
STATUS_FALSE = "False"

_ESTIMATED_REMAINING_TIME_PATTERN = re.compile(r"Estimated remaining time:\s*([0-9]+(?:\.[0-9]+)?)")
_TQDM_REMAINING_TIME_PATTERN = re.compile(r"<(?P<remaining>\d{1,2}:\d{2}(?::\d{2})?)")


def initialize_status_file(file_path, session_key, synthesizer_name):
    """
    Create and initialize a YAML file to track the status of a process.
    """
    yaml_status = {
        "session_key": str(session_key),
        "synthesizer_name": str(synthesizer_name),
        "status": _build_initial_steps(),
    }

    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    _write_status_file(file_path, yaml_status)


def update_status(file_path, step, duration=None, completed=None, remaining_time=None):
    """
    Update the status of a specific step in the YAML status file.
    """
    data = _read_status_file(file_path)

    for status_step in data["status"]:
        if status_step["step"] != step:
            continue

        if duration is not None:
            status_step["duration"] = str(duration)

        if completed is not None:
            status_step["completed"] = _stringify_completed(completed)

        if "remaining_time" in status_step and remaining_time is not None:
            status_step["remaining_time"] = str(remaining_time)
        break

    _write_status_file(file_path, data)


def extract_remaining_time(message):
    estimated_match = _ESTIMATED_REMAINING_TIME_PATTERN.search(message)
    if estimated_match is not None:
        return estimated_match.group(1)

    tqdm_matches = list(_TQDM_REMAINING_TIME_PATTERN.finditer(message))
    if not tqdm_matches:
        return None

    remaining_text = tqdm_matches[-1].group("remaining")
    return str(_duration_to_seconds(remaining_text))


@contextmanager
def intercept_standard_streams(file_path, process_stage):
    original_stdout = sys.stdout
    original_stderr = sys.stderr
    try:
        sys.stdout = InterceptStream(file_path, process_stage, terminal=original_stdout)
        sys.stderr = InterceptStream(file_path, process_stage, terminal=original_stderr)
        yield
    finally:
        sys.stdout = original_stdout
        sys.stderr = original_stderr


class InterceptStream:
    """
    Mirror a stream while extracting remaining-time updates for a process stage.
    """

    def __init__(self, file_path, process_stage, terminal=None):
        self.terminal = sys.stdout if terminal is None else terminal
        self.file_path = file_path
        self.process_stage = process_stage
        self._message_buffer = ""
        self._last_remaining_time = None

    def write(self, message):
        self.terminal.write(message)
        self.terminal.flush()
        self._message_buffer = (self._message_buffer + message)[-2048:]

        remaining_time = extract_remaining_time(self._message_buffer)
        if remaining_time is not None and remaining_time != self._last_remaining_time:
            self._last_remaining_time = remaining_time
            update_status(self.file_path, step=self.process_stage, remaining_time=remaining_time)

    def flush(self):
        self.terminal.flush()

    def close(self):
        self.flush()


InterceptStdOut = InterceptStream


def _build_initial_steps():
    return [
        {
            "step": "initialization",
            "duration": WAITING,
            "completed": STATUS_FALSE,
        },
        {
            "step": "fitting",
            "duration": WAITING,
            "completed": STATUS_FALSE,
            "remaining_time": WAITING,
        },
        {
            "step": "sampling",
            "duration": WAITING,
            "completed": STATUS_FALSE,
            "remaining_time": WAITING,
        },
        {
            "step": "callback",
            "completed": STATUS_FALSE,
        },
    ]


def _stringify_completed(value):
    if isinstance(value, str):
        return value
    return STATUS_TRUE if value else STATUS_FALSE


def _read_status_file(file_path):
    with open(file_path, "r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def _write_status_file(file_path, data):
    with open(file_path, "w", encoding="utf-8") as handle:
        yaml.dump(data, handle, allow_unicode=True, default_flow_style=False)


def _duration_to_seconds(duration_text):
    parts = [int(part) for part in duration_text.split(":")]
    if len(parts) == 2:
        minutes, seconds = parts
        return minutes * 60 + seconds
    if len(parts) == 3:
        hours, minutes, seconds = parts
        return hours * 3600 + minutes * 60 + seconds
    raise ValueError(f"Unsupported duration format: {duration_text}")
