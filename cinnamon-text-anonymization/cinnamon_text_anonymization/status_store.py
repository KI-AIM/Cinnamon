from __future__ import annotations

import json
import os
import re
import tempfile
from collections.abc import Iterator
from contextlib import contextmanager
from enum import Enum
from pathlib import Path

import yaml
from filelock import FileLock


class JobStatus(str, Enum):
    RUNNING = "RUNNING"
    FINISHED = "FINISHED"
    CANCELED = "CANCELED"
    ERROR = "ERROR"

STATUS_DIRECTORY = Path(
    os.getenv(
        "TEXT_ANONYMIZATION_STATUS_DIR",
        str(Path(__file__).resolve().parents[1] / "outputs" / "status"),
    )
)

SAFE_SESSION_KEY = re.compile(r"^[A-Za-z0-9._-]+$")
LOCK_FILE_NAME = ".status.lock"


def status_file_path(session_key: str) -> Path:
    """Returns the status-file path for a session key.

    Args:
        session_key (str): Unique session identifier.

    Returns:
        A path to the status file.
    """
    if not SAFE_SESSION_KEY.fullmatch(session_key):
        raise ValueError("session_key contains unsupported filename characters")
    return STATUS_DIRECTORY / f"{session_key}.yaml"


@contextmanager
def _status_directory_lock() -> Iterator[None]:
    """Acquires an exclusive lock for operations on the status directory.

    The lock prevents multiple processes from modifying files in
    `STATUS_DIRECTORY` at the same time. The directory and lock file are
    created if necessary, and the lock is released when the context manager
    exits.

    Yields:
        None while the exclusive directory lock is held.
    """
    STATUS_DIRECTORY.mkdir(parents=True, exist_ok=True)
    lock_path = STATUS_DIRECTORY / LOCK_FILE_NAME
    with FileLock(str(lock_path)):
        yield


def read_status_file(path: Path) -> dict[str, str] | None:
    """Reads the jobs status file from `path`/

    Args:
        path: The path where the status file resides.
    """
    try:
        with path.open("r", encoding="utf-8") as status_file:
            status = yaml.safe_load(status_file)
    except FileNotFoundError:
        return None

    if not isinstance(status, dict):
        raise RuntimeError(f"Invalid status file: {path}")

    return {str(key): str(value) for key, value in status.items()}


def write_status_file(
    session_key: str,
    status: JobStatus,
    *,
    metadata: dict[str, str] | None = None,
) -> None:
    """Logs the `status` for an initiated job/session with session key `session_key`.

    Args:
        session_key (str): Unique session identifier.
        status (JobStatus): The status for of the running job.
    """
    path = status_file_path(session_key)
    STATUS_DIRECTORY.mkdir(parents=True, exist_ok=True)
    payload = {"session_key": session_key, "status": status.value}
    if metadata:
        payload.update(metadata)

    temporary_path: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=STATUS_DIRECTORY,
            prefix=f".{session_key}.",
            suffix=".tmp",
            delete=False,
        ) as temporary_file:
            yaml.safe_dump(payload, temporary_file, sort_keys=False)
            temporary_path = temporary_file.name

        os.replace(temporary_path, path)
        temporary_path = None
    finally:
        if temporary_path is not None:
            try:
                os.unlink(temporary_path)
            except FileNotFoundError:
                pass


def register_job(session_key: str) -> bool:
    """Registers a new initiated job/session with session key `session_key`.

    Args:
        session_key (str): Unique session identifier.

    Returns:
        A boolean, whether the job was registered.
    """
    with _status_directory_lock():
        current_status = read_status_file(status_file_path(session_key))
        if current_status is not None and current_status.get("status") == JobStatus.RUNNING:
            return False
        write_status_file(session_key, JobStatus.RUNNING)
    return True


def set_job_status(session_key: str, status: JobStatus) -> None:
    """Sets the `status` for an initiated job/session with session key `session_key`.

    Args:
        session_key (str): Unique session identifier.
        status (JobStatus): The status for of the running job.
    """
    with _status_directory_lock():
        path = status_file_path(session_key)
        current_status = read_status_file(path)
        if current_status is None:
            return

        # Cancellation is terminal. A worker that is just finishing must not
        # overwrite a cancellation request with FINISHED or ERROR.
        if current_status.get("status") == JobStatus.CANCELED.value:
            return

        metadata = {
            key: value
            for key, value in current_status.items()
            if key not in {"session_key", "status", "pid"}
        }
        write_status_file(session_key, status, metadata=metadata)


def set_job_progress(
    session_key: str,
    processed: int,
    total: int,
    *,
    phase: str | None = None,
    model_progress: list[dict[str, object]] | None = None,
) -> None:
    """Persists progress for a running job.

    Progress is measured in processed non-empty text cells. ``processed`` and
    ``total`` represent the complete multi-model job. ``model_progress``
    preserves the progress of every configured pass, including the active
    pass and passes that have already completed.
    Values are stored as strings because status files use a simple string
    representation, while the frontend converts them to numbers.
    """
    if processed < 0 or total < 0:
        raise ValueError("processed and total must be non-negative")
    processed = min(processed, total) if total else 0
    progress = 100 if total == 0 else int(processed * 100 / total)

    with _status_directory_lock():
        path = status_file_path(session_key)
        current_status = read_status_file(path)
        if current_status is None or current_status.get("status") != JobStatus.RUNNING.value:
            return

        metadata = {
            key: value
            for key, value in current_status.items()
            if key not in {
                "session_key",
                "status",
                "pid",
                "progress",
                "processed",
                "total",
                "phase",
                "model_progress",
            }
        }
        metadata.update({
            "progress": str(progress),
            "processed": str(processed),
            "total": str(total),
        })
        if phase is not None:
            metadata["phase"] = phase
        if model_progress is not None:
            metadata["model_progress"] = json.dumps(model_progress)

        write_status_file(session_key, JobStatus.RUNNING, metadata=metadata)


def get_job_status(session_key: str) -> dict[str, str] | None:
    """Reads the status for a session, if one has been registered.

    Args:
        session_key (str): Unique session identifier.
    """
    path = status_file_path(session_key)
    return read_status_file(path)


def is_cancellation_requested(session_key: str) -> bool:
    """Whether a cancellation for the current running job is requested.

    Args:
        session_key (str): Unique session identifier.

    Returns:
        A boolean, whether a cancellation request was initiated.
    """
    status = get_job_status(session_key)
    return status is not None and status.get("status") == JobStatus.CANCELED


def cancel_job(session_key: str) -> bool:
    """Marks a running job as canceled in the shared status store.

    Args:
        session_key (str): Unique session identifier.

    Returns:
        A boolean, whether the job is canceled.
    """
    with _status_directory_lock():
        path = status_file_path(session_key)
        current_status = read_status_file(path)
        if current_status is None or current_status.get("status") != JobStatus.RUNNING:
            return False
        metadata = {
            key: value
            for key, value in current_status.items()
            if key not in {"session_key", "status", "pid"}
        }
        write_status_file(session_key, JobStatus.CANCELED, metadata=metadata)
    return True
