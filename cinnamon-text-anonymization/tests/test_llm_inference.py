from __future__ import annotations

import logging

import pytest

import cinnamon_text_anonymization.inference.llm_inference as llm_inference


def _profile_configuration() -> dict[str, object]:
    return {
        "id": "local-llm",
        "name": "Local LLM",
        "provider": "openai_compatible",
        "modelName": "test-model",
        "baseUrl": "http://llm:8000",
        "endpointPath": "/v1/chat/completions",
        "healthcheckPath": "/v1/models",
        "timeoutSeconds": 120,
        "maxRetries": 3,
        "verifySsl": True,
        "maxTokens": 2048,
        "enableThinking": False,
    }


def test_predict_resolves_exact_entity_occurrence(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path,
    caplog: pytest.LogCaptureFixture,
) -> None:
    caplog.set_level(logging.DEBUG, logger=llm_inference.__name__)
    response_log = tmp_path / "llm-responses.log"
    monkeypatch.setattr(llm_inference, "DEFAULT_LLM_RESPONSE_LOG_FILE", response_log)

    class FakeMessage:
        content = '{"entities":[{"text":"Maria","label":"NAME_PATIENT","occurrence":2}]}'

    class FakeChoice:
        message = FakeMessage()

    class FakeResponse:
        choices = [FakeChoice()]

    class FakeClient:
        def create(self, **_kwargs):
            result = llm_inference.LlmResult(
                entities=[llm_inference.LlmEntity(text="Maria", label="NAME_PATIENT", occurrence=2)]
            )
            object.__setattr__(result, "_raw_response", FakeResponse())
            return result

    class FakeOpenAI:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(llm_inference, "OpenAI", FakeOpenAI)
    monkeypatch.setattr(
        llm_inference.instructor,
        "from_openai",
        lambda *_args, **_kwargs: FakeClient(),
    )

    model = llm_inference.LlmInferenceModel(
        "Local LLM", profile_configuration=_profile_configuration()
    )
    result = model.predict(["Maria spoke to Maria."])

    assert result == [[{
        "label": "NAME_PATIENT",
        "score": 1.0,
        "text": "Maria",
        "start": 15,
        "end": 20,
    }]]
    assert response_log.read_text(encoding="utf-8") == FakeMessage.content + "\n"


def test_raw_llm_response_is_not_logged_at_info_level(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path,
    caplog: pytest.LogCaptureFixture,
) -> None:
    caplog.set_level(logging.INFO, logger=llm_inference.__name__)
    response_log = tmp_path / "llm-responses.log"
    monkeypatch.setattr(llm_inference, "DEFAULT_LLM_RESPONSE_LOG_FILE", response_log)

    llm_inference._write_llm_response("contains patient data")

    assert not response_log.exists()


def test_entity_with_global_occurrence_number_uses_first_unused_match(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FakeClient:
        def create(self, **_kwargs):
            return llm_inference.LlmResult(entities=[
                llm_inference.LlmEntity(
                    text="Maria Müller",
                    label="NAME_PATIENT",
                    occurrence=1,
                ),
                llm_inference.LlmEntity(
                    text="Berlin",
                    label="LOCATION_CITY",
                    occurrence=3,
                ),
            ])

    class FakeOpenAI:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(llm_inference, "OpenAI", FakeOpenAI)
    monkeypatch.setattr(
        llm_inference.instructor,
        "from_openai",
        lambda *_args, **_kwargs: FakeClient(),
    )

    text = "Frau Maria Müller wurde nach Berlin überwiesen."
    result = llm_inference.LlmInferenceModel(
        "Local LLM", profile_configuration=_profile_configuration()
    ).predict([text])

    assert [(entity["text"], entity["start"]) for entity in result[0]] == [
        ("Maria Müller", text.index("Maria Müller")),
        ("Berlin", text.index("Berlin")),
    ]


def test_openai_compatible_passes_thinking_setting(monkeypatch: pytest.MonkeyPatch) -> None:
    class FakeClient:
        def __init__(self):
            self.options = None

        def create(self, **kwargs):
            self.options = kwargs
            return llm_inference.LlmResult(entities=[])

    fake_client = FakeClient()

    class FakeOpenAI:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(llm_inference, "OpenAI", FakeOpenAI)
    monkeypatch.setattr(
        llm_inference.instructor,
        "from_openai",
        lambda *_args, **_kwargs: fake_client,
    )

    llm_inference.LlmInferenceModel(
        "Local LLM", profile_configuration=_profile_configuration()
    ).predict(["Keine Entität."])

    assert fake_client.options is not None
    assert fake_client.options["extra_body"] == {
        "chat_template_kwargs": {"enable_thinking": False},
    }
    assert fake_client.options["temperature"] == 0


def test_platform_profile_details_override_local_profile_configuration(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_REMOTE_LLM_API_KEY", "local-secret")

    model = llm_inference.LlmInferenceModel(
        "Remote LLM",
        profile_configuration={
            "id": "remote-llm",
            "name": "Remote LLM",
            "provider": "openai_compatible",
            "modelName": "remote-model",
            "baseUrl": "https://llm.example",
            "endpointPath": "/v1/chat/completions",
            "healthcheckPath": "/v1/models",
            "timeoutSeconds": 45,
            "maxRetries": 2,
            "verifySsl": False,
            "maxTokens": 4096,
            "enableThinking": True,
            "apiKey": "request-must-not-supply-secrets",
        },
    )

    assert model.profile.profile_id == "remote-llm"
    assert model.profile.model_name == "remote-model"
    assert model.profile.base_url == "https://llm.example"
    assert model.profile.timeout_seconds == 45
    assert model.profile.max_retries == 2
    assert model.profile.verify_ssl is False
    assert model.profile.max_tokens == 4096
    assert model.profile.enable_thinking is True
    assert model.profile.api_key == "local-secret"


def test_predict_uses_german_prompt_and_merges_overlapping_chunks(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv(llm_inference.LLM_CHUNK_SIZE_ENV, "24")
    monkeypatch.setenv(llm_inference.LLM_CHUNK_OVERLAP_ENV, "8")

    class FakeClient:
        def __init__(self):
            self.requests: list[str] = []
            self.system_prompts: list[str] = []

        def create(self, **kwargs):
            content = kwargs["messages"][1]["content"]
            self.requests.append(content)
            self.system_prompts.append(kwargs["messages"][0]["content"])
            chunk = content.split("TEXTABSCHNITT:\n", maxsplit=1)[1]
            entities = []
            if "Maria" in chunk:
                entities.append(llm_inference.LlmEntity(
                    text="Maria",
                    label="NAME_PATIENT",
                    occurrence=1,
                ))
            return llm_inference.LlmResult(entities=entities)

    fake_client = FakeClient()

    class FakeOpenAI:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(llm_inference, "OpenAI", FakeOpenAI)
    monkeypatch.setattr(
        llm_inference.instructor,
        "from_openai",
        lambda *_args, **_kwargs: fake_client,
    )

    model = llm_inference.LlmInferenceModel(
        "Local LLM", profile_configuration=_profile_configuration()
    )
    text = "Maria besuchte die Klinik. Danach sprach Maria mit dem Arzt."
    result = model.predict([text])

    assert result == [[{
        "label": "NAME_PATIENT",
        "score": 1.0,
        "text": "Maria",
        "start": 0,
        "end": 5,
    }, {
        "label": "NAME_PATIENT",
        "score": 1.0,
        "text": "Maria",
        "start": text.index("Maria", 1),
        "end": text.index("Maria", 1) + len("Maria"),
    }]]
    assert fake_client.requests
    assert all("Du extrahierst personenbezogene Informationen" in prompt for prompt in fake_client.system_prompts)
    assert all('"occurrence"' in prompt for prompt in fake_client.system_prompts)


def test_text_windows_cover_text_and_overlap() -> None:
    text = "Absatz eins. Absatz zwei mit einem Namen. Absatz drei."

    windows = llm_inference.text_windows(text, chunk_size=24, overlap=8)

    assert windows
    assert windows[0][0] == 0
    assert windows[-1][1] == len(text)
    assert all(text[start:end] == chunk for start, end, chunk in windows)
    assert all(
        previous[1] > current[0]
        for previous, current in zip(windows, windows[1:], strict=False)
    )


def test_predict_does_not_add_entities_without_model_predictions(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FakeClient:
        def create(self, **_kwargs):
            return llm_inference.LlmResult(entities=[])

    class FakeOpenAI:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(llm_inference, "OpenAI", FakeOpenAI)
    monkeypatch.setattr(
        llm_inference.instructor,
        "from_openai",
        lambda *_args, **_kwargs: FakeClient(),
    )

    result = llm_inference.LlmInferenceModel(
        "Local LLM", profile_configuration=_profile_configuration()
    ).predict(
        ["Frau Müller wurde am 12.03.2029 aufgenommen."]
    )

    assert result == [[]]


def test_sensitive_configuration_uses_sensitive_prompt_and_labels(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FakeClient:
        def __init__(self):
            self.prompt = ""

        def create(self, **kwargs):
            self.prompt = kwargs["messages"][0]["content"]
            return llm_inference.LlmResult(entities=[
                llm_inference.LlmEntity(
                    text="Ramipril",
                    label="MEDICATION",
                    occurrence=1,
                ),
                llm_inference.LlmEntity(
                    text="Maria",
                    label="NAME_PATIENT",
                    occurrence=1,
                ),
            ])

    fake_client = FakeClient()

    class FakeOpenAI:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(llm_inference, "OpenAI", FakeOpenAI)
    monkeypatch.setattr(
        llm_inference.instructor,
        "from_openai",
        lambda *_args, **_kwargs: fake_client,
    )

    result = llm_inference.LlmInferenceModel(
        "Local LLM",
        anonymization_type="SENSITIVE",
        profile_configuration=_profile_configuration(),
    ).predict(["Maria nimmt Ramipril."])

    assert "medizinische Entitäten" in fake_client.prompt
    assert [entity["label"] for entity in result[0]] == ["MEDICATION"]


def test_llm_profile_is_required(monkeypatch: pytest.MonkeyPatch) -> None:
    with pytest.raises(ValueError, match="llmProfile is required"):
        llm_inference.LlmInferenceModel(None)
