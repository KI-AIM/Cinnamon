import io
import json
import logging
import os
import threading
from enum import IntEnum
from multiprocessing import get_context
from multiprocessing.process import BaseProcess

import pandas as pd
import requests
from flask import Flask, Response, jsonify, request

from cinnamon_text_anonymization.anonymization_service import run_anonymization
from cinnamon_text_anonymization.status_store import (
    JobStatus,
    cancel_job,
    get_job_status,
    is_cancellation_requested,
    register_job,
    set_job_progress,
    set_job_status,
)

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)

# Keep DEBUG useful for this service without allowing HTTP client loggers to
# dump prompts, request bodies, or model responses containing sensitive data.
for _logger_name in ("openai", "httpx", "httpcore", "urllib3", "filelock", "instructor"):
    logging.getLogger(_logger_name).setLevel(logging.INFO)

app = Flask(__name__)
logger = logging.getLogger(__name__)

OUTPUT_PART_NAME = "text_anonymized_dataset"
ERROR_PART_NAME = "error_message"
CALLBACK_TIMEOUT_SECONDS = 30.0
ERROR_CALLBACK_TIMEOUT_SECONDS = 5.0
PROCESS_CONTEXT = get_context("spawn")
PROCESS_TERMINATION_GRACE_SECONDS = 2.0
_running_processes: dict[str, BaseProcess] = {}
_running_processes_lock = threading.Lock()

class StatusCode(IntEnum):
    OK = 200
    ACCEPTED = 202
    BAD_REQUEST = 400
    NOT_FOUND = 404
    CONFLICT = 409
    ERROR = 500


def _reap_process(session_key: str, process: BaseProcess) -> None:
    """Waits for a child process and removes it from the local task registry."""
    process.join()
    with _running_processes_lock:
        if _running_processes.get(session_key) is process:
            _running_processes.pop(session_key, None)


def _terminate_process(process: BaseProcess) -> None:
    """Terminates a local child process and force-kills it if necessary."""
    if not process.is_alive():
        return

    process.terminate()
    process.join(PROCESS_TERMINATION_GRACE_SECONDS)
    if process.is_alive():
        process.kill()
        process.join()

    if process.is_alive():
        raise RuntimeError("Anonymization process is still running after cancellation.")


def post_success_callback(callback_url: str, data_bytes: bytes) -> None:
    """Sends the anonymized dataset to the callback URL.

    Args:
        callback_url (str): The client's callback URL.
        data_bytes (bytes): The anonymized (CSV) data as bytes.
    """
    response = requests.post(
        callback_url,
        files={
            OUTPUT_PART_NAME: (
                f"{OUTPUT_PART_NAME}.csv",
                data_bytes,
                "text/csv"
            )
        },
        timeout=CALLBACK_TIMEOUT_SECONDS,
    )
    response.raise_for_status()


def post_error_callback(callback_url: str, session_key: str, message: str,status_code: int = StatusCode.ERROR) -> None:
    """Sends error messages to the provided callback URL using multipart/form-data.

    Args:
        callback_url (str): The client's callback URL.
        session_key (str): Unique session identifier.
        message (str): The error message to send.
        status_code (int): The HTTP status code to include in the callback.
    """
    try:
        requests.post(
            callback_url,
            files={
                ERROR_PART_NAME: (
                    f"{ERROR_PART_NAME}.txt",
                    message.encode("utf-8"),
                    "text/plain"
                )
            },
            data={
                "session_key": session_key,
                "status_code": str(status_code)
            },
            timeout=ERROR_CALLBACK_TIMEOUT_SECONDS
        ).raise_for_status()
    except requests.RequestException:
        logger.exception(
            "Could not send error callback for session %s",
            session_key
        )


def text_anonymization_configs(anonymization_config: dict) -> list[dict]:
    """Returns enabled PII/sensitive text configurations.

    The nested configuration is the only supported wire format.
    """
    configurations = anonymization_config.get("textAnonymizationConfigurations", None)

    if configurations is not None:
        if not isinstance(configurations, dict):
            raise ValueError("textAnonymizationConfigurations must be an object")

        result: list[dict] = []
        for key, anonymization_type in (("pii", "PII"), ("sensitive", "SENSITIVE")):
            value = configurations.get(key)
            if value is None:
                continue
            if not isinstance(value, dict):
                raise ValueError(f"textAnonymizationConfigurations.{key} must be an object")
            if value.get("enabled", True) is False:
                continue

            result.append({**value, "anonymizationType": anonymization_type})
        return result

    raise ValueError("Missing textAnonymizationConfigurations")


def _count_text_cells(data: pd.DataFrame, configurations: list[dict]) -> int:
    """Counts non-empty text cells that the configured jobs will process."""
    total = 0
    for configuration in configurations:
        columns = configuration.get("columns", [])
        if not isinstance(columns, list):
            continue

        for column_name in columns:
            if column_name not in data.columns:
                continue

            column_position = data.columns.get_loc(column_name)
            if not isinstance(column_position, int):
                continue

            column = data.iloc[:, column_position]
            total += int((column.notna() & column.astype(str).ne("")).sum())

    return total


def _progress_model_label(configuration: dict) -> str:
    """Returns a display label for one PII/sensitive model pass."""
    anonymization_type = str(configuration.get("anonymizationType", "PII")).strip().upper()
    model_type = str(configuration.get("modelType", "MODEL")).strip()
    return f"{anonymization_type} / {model_type}"


def run_anonymization_job(session_key: str, callback_url: str, data: pd.DataFrame, config: dict) -> None:
    """Runs anonymization and send the result to the callback URL.

    Args:
        session_key (str): Unique session identifier.
        callback_url (str): The client's callback URL.
        data (pd.DataFrame): The data to be anonymized.
        config (dict): The config with the details needed for anonymization.
            e.g: {"anonymization": {
                            "textAnonymizationConfigurations": {
                            "pii": {
                            "enabled": true,
                            "modelType":"LLM",
                            "llmProfile":"BIH GPU",
                            "columns": ["dokument_text"],
                            "confidenceThreshold":0.9,
                            "anonymizationMode":"redact"},

                            "sensitive": {
                            "enabled": true,
                            "modelType":"GELECTRA",
                            "columns": ["dokument_text"],
                            "confidenceThreshold":0.9,
                            "anonymizationMode":"redact"}
                            }}
    """
    if is_cancellation_requested(session_key):
        logger.info("Anonymization cancelled before processing for session %s", session_key)
        return

    try:
        anonymization_config = config.get("anonymization") or {}
        text_ano_configs = text_anonymization_configs(anonymization_config)

        model_totals = [
            _count_text_cells(data, [text_ano_config])
            for text_ano_config in text_ano_configs
        ]
        total_text_cells = sum(model_totals)
        processed_text_cells = 0

        def persist_progress(
            model_index: int | None,
            model_processed: int | None,
            phase: str,
        ) -> None:
            model_progress: list[dict[str, object]] = []
            for index, configuration in enumerate(text_ano_configs):
                total = model_totals[index]
                if model_index is None or index > model_index:
                    processed = 0
                    model_status = "pending"
                elif index < model_index:
                    processed = total
                    model_status = "finished"
                else:
                    if phase == "callback":
                        processed = total
                        model_status = "finished"
                    else:
                        processed = model_processed or 0
                        model_status = "running"

                model_progress.append({
                    "model": _progress_model_label(configuration),
                    "processed": processed,
                    "total": total,
                    "progress": 100 if total == 0 else int(processed * 100 / total),
                    "status": model_status,
                })

            set_job_progress(
                session_key,
                processed=processed_text_cells,
                total=total_text_cells,
                phase=phase,
                model_progress=model_progress,
            )

        persist_progress(
            0 if text_ano_configs else None,
            0 if text_ano_configs else None,
            "initialization",
        )
    except Exception as error:
        logger.exception(
            "Anonymization config is empty for session %s",
            session_key
        )
        if not is_cancellation_requested(session_key):
            set_job_status(session_key, JobStatus.ERROR)
            post_error_callback(
                callback_url,
                session_key,
                str(error),
                StatusCode.BAD_REQUEST
            )
        return

    try:
        result = data
        for model_index, text_ano_config in enumerate(text_ano_configs):
            model_processed = 0

            def report_progress(
                processed: int,
                model_index: int = model_index,
            ) -> None:
                nonlocal model_processed, processed_text_cells
                model_processed += processed
                processed_text_cells += processed
                persist_progress(
                    model_index,
                    model_processed,
                    "processing",
                )

            persist_progress(
                model_index, model_processed,
                "initialization" if model_index == 0 else "processing",
            )
            result = run_anonymization(
                text_ano_config,
                result,
                progress_callback=report_progress,
            )
        result_bytes = result.to_csv(index=False).encode("utf-8")
        # Reaching this point means every configured model pass completed.
        # Make the terminal progress representation complete even if a model
        # did not report every processed cell individually.
        processed_text_cells = total_text_cells
        persist_progress(
            len(text_ano_configs) - 1 if text_ano_configs else None,
            None,
            "callback",
        )
    except Exception as error:
        logger.exception(
            "Anonymization failed for session %s",
            session_key
        )
        if not is_cancellation_requested(session_key):
            set_job_status(session_key, JobStatus.ERROR)
            post_error_callback(
                callback_url,
                session_key,
                str(error),
                StatusCode.ERROR
            )
        return

    if is_cancellation_requested(session_key):
        logger.info("Anonymization cancelled before callback for session %s", session_key)
        return

    try:
        post_success_callback(callback_url, result_bytes)
        set_job_status(session_key, JobStatus.FINISHED)
    except requests.RequestException:
        set_job_status(session_key, JobStatus.ERROR)
        logger.exception(
            "Could not send result callback for session %s",
            session_key,
        )


@app.route("/start_anonymization_process", methods=["POST"])
def start_anonymization_process():
    """Starts text anonymization in an isolated background process."""
    session_key = request.form.get("session_key")
    callback_url = request.form.get("callback")
    config_text = request.form.get("anonymizationConfig")
    data_file = request.files.get("data")

    if not session_key:
        return jsonify({"message": "No session key provided"}), StatusCode.BAD_REQUEST

    if not callback_url:
        return jsonify(
            {
                "message": "No callback URL provided",
                "session_key": session_key,
            }
        ), StatusCode.BAD_REQUEST

    if data_file is None:
        return jsonify(
            {
                "message": "No data file provided",
                "session_key": session_key,
            }
        ), StatusCode.BAD_REQUEST

    if not config_text:
        return jsonify(
            {
                "message": "No anonymizationConfig provided",
                "session_key": session_key
            }
        ), StatusCode.BAD_REQUEST

    try:
        config = json.loads(config_text)
        data = pd.read_csv(io.BytesIO(data_file.read()))
    except Exception as error:
        return jsonify(
            {
                "message": "Could not parse input",
                "error": str(error),
                "session_key": session_key,
            }
        ), StatusCode.BAD_REQUEST

    process = PROCESS_CONTEXT.Process(
        target=run_anonymization_job,
        args=(session_key, callback_url, data, config),
    )

    if not register_job(session_key):
        return jsonify(
            {
                "message": "A process with this session key is already running",
                "session_key": session_key,
            }
        ), StatusCode.CONFLICT

    process.start()
    with _running_processes_lock:
        _running_processes[session_key] = process
    threading.Thread(
        target=_reap_process,
        args=(session_key, process),
        daemon=True,
    ).start()

    return jsonify(
        {
            "message": "Text anonymization started",
            "session_key": session_key,
            "pid": str(process.pid),
        }
    ), StatusCode.ACCEPTED


@app.route("/status/<session_key>", methods=["GET"])
def get_anonymization_status(session_key: str):
    """Returns the current status for a text anonymization job/session with session key `session_key`.

    Args:
        session_key (str): Unique session identifier.
    """
    try:
        status = get_job_status(session_key)
    except (RuntimeError, ValueError) as error:
        return jsonify({"message": str(error), "session_key": session_key}), StatusCode.ERROR

    if status is None:
        return jsonify(
            {
                "message": "No status found for session key",
                "session_key": session_key,
            }
        ), StatusCode.NOT_FOUND

    return jsonify(status), StatusCode.OK


@app.route("/cancel_anonymization_process", methods=["POST"])
def cancel_anonymization_process():
    """Cancels a locally owned anonymization process."""
    session_key = request.form.get("session_key")
    if not session_key:
        return jsonify({"message": "No session key provided"}), StatusCode.BAD_REQUEST

    with _running_processes_lock:
        process = _running_processes.get(session_key)

    if process is None:
        status = get_job_status(session_key)
        if status is None:
            return jsonify(
                {
                    "message": "No running process found",
                    "session_key": session_key,
                }
            ), StatusCode.NOT_FOUND
        return jsonify(
            {
                "message": "No locally owned process found",
                "session_key": session_key,
            }
        ), StatusCode.CONFLICT

    try:
        _terminate_process(process)
    except (OSError, RuntimeError):
        logger.exception("Could not terminate anonymization process for session %s", session_key)
        return jsonify(
            {
                "message": "Could not terminate anonymization process",
                "session_key": session_key,
            }
        ), StatusCode.ERROR

    if not cancel_job(session_key):
        return jsonify(
            {
                "message": "The anonymization process already finished",
                "session_key": session_key,
            }
        ), StatusCode.CONFLICT

    logger.info("Cancelled anonymization process for session %s", session_key)
    return jsonify(
        {
            "message": "Anonymization process cancelled",
            "session_key": session_key,
        }
    ), StatusCode.OK


@app.route("/actuator/health", methods=["GET"])
def health_check() -> tuple[Response, int]:
    """Provides a health status for the application.

    Returns:
        A JSON object indicating the application's health status.
    """
    return jsonify({"status": "UP"}), StatusCode.OK


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5060)
