from __future__ import annotations

import pytest

import cinnamon_text_anonymization.inference.llm_inference as llm_inference


def _set_profile(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_IDS", "local-llm")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_NAME", "Local LLM")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_PROVIDER", "openai_compatible")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_MODEL_NAME", "test-model")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_BASE_URL", "http://llm:8000")


def test_predict_resolves_exact_entity_occurrence(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path,
) -> None:
    _set_profile(monkeypatch)
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

    model = llm_inference.LlmInferenceModel("Local LLM")
    result = model.predict(["Maria spoke to Maria."])

    assert result == [[{
        "label": "NAME_PATIENT",
        "score": 1.0,
        "text": "Maria",
        "start": 15,
        "end": 20,
    }]]
    assert response_log.read_text(encoding="utf-8") == FakeMessage.content + "\n"


def test_entity_with_global_occurrence_number_uses_first_unused_match(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _set_profile(monkeypatch)

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
    result = llm_inference.LlmInferenceModel("Local LLM").predict([text])

    assert [(entity["text"], entity["start"]) for entity in result[0]] == [
        ("Maria Müller", text.index("Maria Müller")),
        ("Berlin", text.index("Berlin")),
    ]


def test_openai_compatible_passes_thinking_setting(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_profile(monkeypatch)
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_MODEL_NAME", "Qwen/Qwen3-8B")

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

    llm_inference.LlmInferenceModel("Local LLM").predict(["Keine Entität."])

    assert fake_client.options is not None
    assert fake_client.options["extra_body"] == {
        "chat_template_kwargs": {"enable_thinking": False},
    }
    assert fake_client.options["temperature"] == 0


def test_predict_uses_german_prompt_and_merges_overlapping_chunks(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _set_profile(monkeypatch)
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

    model = llm_inference.LlmInferenceModel("Local LLM")
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
    _set_profile(monkeypatch)

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

    result = llm_inference.LlmInferenceModel("Local LLM").predict(
        ["Frau Müller wurde am 12.03.2029 aufgenommen."]
    )

    assert result == [[]]


def test_sensitive_configuration_uses_sensitive_prompt_and_labels(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _set_profile(monkeypatch)

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
    ).predict(["Maria nimmt Ramipril."])

    assert "medizinische Entitäten" in fake_client.prompt
    assert [entity["label"] for entity in result[0]] == ["MEDICATION"]


def test_llm_profile_is_required(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_profile(monkeypatch)

    with pytest.raises(ValueError, match="llmProfile is required"):
        llm_inference.LlmInferenceModel(None)
