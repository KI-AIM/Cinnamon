import io
import json
import logging
import os
import threading
from enum import IntEnum

import pandas as pd
import requests
from flask import Flask, Response, jsonify, request

from cinnamon_text_anonymization.anonymization_service import run_anonymization
from cinnamon_text_anonymization.status_store import (
    cancel_job,
    get_job_status,
    is_cancellation_requested,
    register_job,
    set_job_status,
    JobStatus
)

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)

app = Flask(__name__)
logger = logging.getLogger(__name__)

OUTPUT_PART_NAME = "text_anonymized_dataset"
ERROR_PART_NAME = "error_message"
CALLBACK_TIMEOUT_SECONDS = 30.0
ERROR_CALLBACK_TIMEOUT_SECONDS = 5.0

class StatusCode(IntEnum):
    OK = 200
    ACCEPTED = 202
    BAD_REQUEST = 400
    NOT_FOUND = 404
    CONFLICT = 409
    ERROR = 500


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


def run_anonymization_job(session_key: str, callback_url: str, data: pd.DataFrame, config: dict) -> None:
    """Runs anonymization and send the result to the callback URL.

    Args:
        session_key (str): Unique session identifier.
        callback_url (str): The client's callback URL.
        data (pd.DataFrame): The data to be anonymized.
        config (dict): The config with the details needed for anonymization.
            e.g: {"anonymization": {
                            "textAnonymizationConfiguration": {
                            "modelType":"XLM ROBERTA",
                            "columns": ["dokument_text"],
                            "confidenceThreshold":0.9,
                            "anonymizationMode":"redact"}
                            }}
    """
    if is_cancellation_requested(session_key):
        logger.info("Anonymization cancelled before processing for session %s", session_key)
        return

    try:
        text_ano_config = (config.get("anonymization") or {}).get("textAnonymizationConfiguration")
        if text_ano_config is None:
            raise ValueError("Missing anonymization.textAnonymizationConfiguration")
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
        result = run_anonymization(text_ano_config, data)
        result_bytes = result.to_csv(index=False).encode("utf-8")
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
    """Starts text anonymization in a background thread."""
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

    thread = threading.Thread(
        target=run_anonymization_job,
        args=(session_key, callback_url, data, config),
        daemon=True,
    )

    if not register_job(session_key):
        return jsonify(
            {
                "message": "A process with this session key is already running",
                "session_key": session_key,
            }
        ), StatusCode.CONFLICT

    thread.start()

    return jsonify(
        {
            "message": "Text anonymization started",
            "session_key": session_key,
            "pid": str(thread.ident),
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
    """Requests a cancellation of a running anonymization job."""
    session_key = request.form.get("session_key")
    if not session_key:
        return jsonify({"message": "No session key provided"}), StatusCode.BAD_REQUEST

    if not cancel_job(session_key):
        return jsonify(
            {
                "message": "No running process found",
                "session_key": session_key,
            }
        ), StatusCode.NOT_FOUND

    logger.info("Cancellation requested for session %s", session_key)
    return jsonify(
        {
            "message": "Cancellation requested",
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
