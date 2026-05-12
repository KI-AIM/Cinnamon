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


def test_start_synthetization_process_returns_400_when_session_key_is_missing():
    app_module.tasks.clear()
    app_module.task_locks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/llm_tabular",
        data={},
        content_type="multipart/form-data",
    )

    assert response.status_code == 400
    payload = response.get_json()
    assert payload["message"] == "No session key provided"


def test_start_synthetization_process_returns_400_when_callback_is_missing():
    app_module.tasks.clear()
    app_module.task_locks.clear()
    client = app_module.app.test_client()

    response = client.post(
        "/start_synthetization_process/llm_tabular",
        data={"session_key": "session-without-callback"},
        content_type="multipart/form-data",
    )

    assert response.status_code == 400
    payload = response.get_json()
    assert payload["message"] == "No callback URL provided"
    assert payload["session_key"] == "session-without-callback"
    assert "session-without-callback" not in app_module.task_locks


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
    assert "supports_structured_data: true" in payload
    assert "supports_free_text_data: false" in payload


def test_get_synthesizer_config_normalizes_llm_profile_into_model_parameter(monkeypatch):
    monkeypatch.setattr(app_module, "get_llm_profile_names", lambda: ["profile-a", "profile-b"])

    client = app_module.app.test_client()
    response = client.get("/synthetic_tabular_data_generator/synthesizer_config/llm_few_shot_text_synthesis.yaml")

    assert response.status_code == 200

    payload = yaml.safe_load(response.get_data(as_text=True))
    configurations = payload["configurations"]

    assert "llm_profile" in configurations
    llm_profile_parameters = configurations["llm_profile"]["parameters"]
    assert len(llm_profile_parameters) == 1
    assert llm_profile_parameters[0]["values"] == ["profile-a", "profile-b"]
    assert llm_profile_parameters[0]["default_value"] == "profile-a"


def test_get_synthesizer_config_omits_llm_profile_when_no_profiles_exist(monkeypatch):
    monkeypatch.setattr(app_module, "get_llm_profile_names", lambda: [])

    client = app_module.app.test_client()
    response = client.get("/synthetic_tabular_data_generator/synthesizer_config/llm_few_shot_text_synthesis.yaml")

    assert response.status_code == 200

    payload = yaml.safe_load(response.get_data(as_text=True))
    configurations = payload["configurations"]

    assert "llm_profile" in configurations
    llm_profile_parameters = configurations["llm_profile"]["parameters"]
    assert len(llm_profile_parameters) == 1
    assert llm_profile_parameters[0]["values"] == []
