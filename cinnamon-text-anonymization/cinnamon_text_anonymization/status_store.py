from __future__ import annotations

import fcntl
import os
import re
import tempfile
from collections.abc import Iterator
from contextlib import contextmanager
from enum import Enum
from pathlib import Path

import yaml

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
    with lock_path.open("a+", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


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


def write_status_file(session_key: str, status: JobStatus) -> None:
    """Logs the `status` for an initiated job/session with session key `session_key`.
    
    Args:
        session_key (str): Unique session identifier.
        status (JobStatus): The status for of the running job.
    """
    path = status_file_path(session_key)
    STATUS_DIRECTORY.mkdir(parents=True, exist_ok=True)
    payload = {"session_key": session_key, "status": status.value}

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
        if read_status_file(path) is not None:
            write_status_file(session_key, status)


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
        write_status_file(session_key, JobStatus.CANCELED)
    return True
