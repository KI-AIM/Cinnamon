from __future__ import annotations

import io
import json
from unittest.mock import Mock, patch

import pandas as pd
import pytest

import app as app_module


@pytest.fixture()
def client():
    app_module.app.config.update(TESTING=True)
    with app_module.app.test_client() as test_client:
        yield test_client


def request_data(*,
                 session_key: str = "session-1",
                 callback: str = "http://platform/api/process/session-1/callback",
                 config: str | None = None,
                 csv: bytes = b"text\nJane\n") -> dict:
    return {
        "session_key": session_key,
        "callback": callback,
        "anonymizationConfig": config
        if config is not None
        else json.dumps(
            {
                "anonymization": {
                    "textAnonymizationConfiguration": {
                        "modelType": "XLM ROBERTA",
                        "columns": ["text"],
                        "confidenceThreshold": 0.9,
                        "anonymizationMode": "redact",
                    }
                }
            }
        ),
        "data": (io.BytesIO(csv), "data.csv"),
    }


def test_health_endpoint(client) -> None:
    response = client.get("/actuator/health")

    assert response.status_code == app_module.StatusCode.OK
    assert response.get_json() == {"status": "UP"}


@pytest.mark.parametrize(
    ("missing_field", "message"),
    [
        ("session_key", "No session key provided"),
        ("callback", "No callback URL provided"),
        ("data", "No data file provided"),
        ("anonymizationConfig", "No anonymizationConfig provided"),
    ],
)
def test_start_endpoint_rejects_missing_required_parts(client, missing_field, message) -> None:
    data = request_data()
    data.pop(missing_field)

    response = client.post(
        "/start_anonymization_process",
        data=data,
        content_type="multipart/form-data",
    )

    assert response.status_code == app_module.StatusCode.BAD_REQUEST
    assert response.get_json()["message"] == message


def test_start_endpoint_rejects_invalid_json(client) -> None:
    response = client.post(
        "/start_anonymization_process",
        data=request_data(config="not-json"),
        content_type="multipart/form-data",
    )

    assert response.status_code == app_module.StatusCode.BAD_REQUEST
    body = response.get_json()
    assert body["message"] == "Could not parse input"
    assert body["session_key"] == "session-1"


def test_start_endpoint_parses_csv_and_starts_background_job(client) -> None:
    fake_thread = Mock()
    fake_thread.ident = 1234

    with patch.object(app_module.threading, "Thread", return_value=fake_thread) as thread_factory:
        response = client.post(
            "/start_anonymization_process",
            data=request_data(),
            content_type="multipart/form-data",
        )

    assert response.status_code == app_module.StatusCode.ACCEPTED
    assert response.get_json() == {
        "message": "Text anonymization started",
        "session_key": "session-1",
        "pid": "1234",
    }
    thread_factory.assert_called_once()
    thread_kwargs = thread_factory.call_args.kwargs
    assert thread_kwargs["target"] is app_module.run_anonymization_job
    assert thread_kwargs["daemon"] is True
    assert thread_kwargs["args"][0:2] == (
        "session-1",
        "http://platform/api/process/session-1/callback",
    )
    pd.testing.assert_frame_equal(
        thread_kwargs["args"][2],
        pd.DataFrame({"text": ["Jane"]}),
    )
    ano_config =  thread_kwargs["args"][3]["anonymization"]["textAnonymizationConfiguration"]
    assert ano_config["modelType"] == "XLM ROBERTA"
    fake_thread.start.assert_called_once_with()


def test_run_anonymization_job_posts_success_callback() -> None:
    data = pd.DataFrame({"text": ["[PERSON]"]})
    config = {"anonymization": {"textAnonymizationConfiguration": {"columns": ["text"]}}}

    with (
        patch.object(app_module, "run_anonymization", return_value=data),
        patch.object(app_module, "post_success_callback") as success_callback,
        patch.object(app_module, "post_error_callback") as error_callback,
    ):
        app_module.run_anonymization_job("session-1", "http://callback", data, config)

    success_callback.assert_called_once_with(
        "http://callback",
        b"text\n[PERSON]\n",
    )
    error_callback.assert_not_called()


def test_run_anonymization_job_posts_error_callback_when_processing_fails() -> None:
    data = pd.DataFrame({"text": ["Jane"]})
    config = {"anonymization": {"textAnonymizationConfiguration": {"columns": ["text"]}}}

    with (
        patch.object(app_module, "run_anonymization", side_effect=ValueError("bad model")),
        patch.object(app_module, "post_success_callback") as success_callback,
        patch.object(app_module, "post_error_callback") as error_callback,
    ):
        app_module.run_anonymization_job("session-1", "http://callback", data, config)

    success_callback.assert_not_called()
    error_callback.assert_called_once_with(
        "http://callback",
        "session-1",
        "bad model",
        app_module.StatusCode.ERROR,
    )


def test_post_success_callback_sends_csv_as_expected_multipart_part() -> None:
    response = Mock()

    with patch.object(app_module.requests, "post", return_value=response) as post:
        app_module.post_success_callback("http://callback", b"text\nJane\n")

    post.assert_called_once()
    call_kwargs = post.call_args.kwargs
    assert call_kwargs["timeout"] == app_module.CALLBACK_TIMEOUT_SECONDS
    assert call_kwargs["files"][app_module.OUTPUT_PART_NAME] == (
        "text_anonymized_dataset.csv",
        b"text\nJane\n",
        "text/csv",
    )
    response.raise_for_status.assert_called_once_with()
