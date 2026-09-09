import app as app_module
import pytest
from pydantic import ValidationError


def _set_profile(monkeypatch):
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_IDS", "local-llm")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_NAME", "Local LLM")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_PROVIDER", '"ollama"')
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_MODEL_NAME", "qwen3:8b")
    monkeypatch.setenv("CINNAMON_LLM_PROFILE_LOCAL_LLM_BASE_URL", "http://ollama:11434")


def test_profiles_use_existing_environment_format(monkeypatch):
    _set_profile(monkeypatch)

    response = app_module.app.test_client().get("/profiles")

    assert response.status_code == 200
    assert response.get_json() == {
        "profiles": [{"name": "Local LLM", "provider": "ollama", "model_name": "qwen3:8b"}]
    }


def test_extract_validates_request_before_calling_llm(monkeypatch):
    _set_profile(monkeypatch)

    response = app_module.app.test_client().post(
        "/extract",
        json={"profile": "Local LLM", "text": "Some text", "fields": []},
    )

    assert response.status_code == 400
    assert response.get_json() == {"error": "'fields' must be a non-empty list."}


def test_extract_returns_instructor_result(monkeypatch):
    _set_profile(monkeypatch)
    captured = {}

    class Result:
        def model_dump(self, mode):
            assert mode == "json"
            return {
                "age": {
                    "value": 42,
                    "value_span": {"start": 15, "end": 17},
                    "evidence": "42 years old",
                    "evidence_span": {"start": 15, "end": 27},
                },
                "diagnosis": {
                    "value": "asthma",
                    "value_span": {"start": 37, "end": 43},
                    "evidence": "has asthma",
                    "evidence_span": {"start": 33, "end": 43},
                },
            }

    class InstructorClient:
        def create(self, **kwargs):
            captured.update(kwargs)
            return Result()

    class OpenAIClient:
        def __init__(self, **kwargs):
            captured["openai"] = kwargs

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(app_module, "OpenAI", OpenAIClient)

    def from_openai(*_args, **kwargs):
        captured["instructor_mode"] = kwargs["mode"]
        return InstructorClient()

    monkeypatch.setattr(app_module.instructor, "from_openai", from_openai)

    response = app_module.app.test_client().post(
        "/extract",
        json={
            "profile": "Local LLM",
            "text": "The patient is 42 years old and has asthma.",
            "fields": [
                {"name": "age", "type": "integer"},
                {"name": "diagnosis", "type": "string"},
            ],
        },
    )

    assert response.status_code == 200
    assert response.get_json() == {
        "data": {
            "values": {"age": 42, "diagnosis": "asthma"},
            "evidence": {
                "age": {
                    "value": 42,
                    "value_span": {"start": 15, "end": 17},
                    "evidence": "42 years old",
                    "evidence_span": {"start": 15, "end": 27},
                },
                "diagnosis": {
                    "value": "asthma",
                    "value_span": {"start": 36, "end": 42},
                    "evidence": "has asthma",
                    "evidence_span": {"start": 32, "end": 42},
                },
            },
        }
    }
    assert captured["openai"]["base_url"] == "http://ollama:11434/v1"
    assert captured["model"] == "qwen3:8b"
    assert captured["reasoning_effort"] == "none"
    assert captured["instructor_mode"] == app_module.instructor.Mode.MD_JSON


def test_invalid_or_null_evidence_only_clears_the_affected_field():
    fields = [
        {"name": "age", "type": "integer"},
        {"name": "diagnosis", "type": "string"},
        {"name": "gender", "type": "string"},
    ]
    model = app_module._evidence_response_model(fields)

    result = model.model_validate({
        "age": {"value": "not an integer"},
        "diagnosis": {"value": "asthma", "evidence": "asthma"},
        "gender": None,
    }).model_dump(mode="json")
    values, evidence = app_module._split_extraction(result, "Diagnosis asthma.", fields)

    assert values == {"age": None, "diagnosis": "asthma", "gender": None}
    assert evidence["age"] == app_module._empty_evidence()
    assert evidence["diagnosis"]["value"] == "asthma"
    assert evidence["gender"] == app_module._empty_evidence()


def test_configuration_is_validated_without_calling_llm(monkeypatch):
    _set_profile(monkeypatch)

    response = app_module.app.test_client().post(
        "/configuration",
        json={
            "profile": "Local LLM",
            "source_column": "clinical_note",
            "fields": [{"name": "diagnosis", "type": "string", "description": "Primary diagnosis"}],
        },
    )

    assert response.status_code == 200
    assert response.get_json()["configuration"]["source_column"] == "clinical_note"


def test_response_model_supports_dates_and_allowed_values():
    model = app_module._response_model([
        {"name": "visit_date", "type": "date", "format": "yyyy-MM-dd"},
        {"name": "severity", "type": "string", "allowed_values": ["low", "high"]},
    ])

    result = model(visit_date="2026-08-31", severity="high")
    assert result.model_dump(mode="json") == {"visit_date": "2026-08-31", "severity": "high"}
    with pytest.raises(ValidationError):
        model(visit_date="2026-08-31", severity="medium")


def test_response_model_normalizes_dates_from_german_source_format():
    model = app_module._evidence_response_model([
        {"name": "birth_date", "type": "date", "format": "yyyy-MM-dd"},
    ])

    result = model(birth_date={"value": "4.4.1997"})

    assert result.model_dump(mode="json")["birth_date"]["value"] == "1997-04-04"


def test_evidence_model_and_span_normalization():
    text = "Die Patientin wurde geboren am 12.03.1954 in Berlin."
    evidence = "geboren am 12.03.1954"
    evidence_start = text.index(evidence)
    value_start = text.index("12.03.1954")
    model = app_module._evidence_response_model([
        {"name": "birth_date", "type": "date", "format": "dd.MM.yyyy"},
    ])

    result = model(birth_date={
        "value": "1954-03-12",
        "value_span": {"start": value_start + 2, "end": value_start + 12},
        "evidence": evidence,
        "evidence_span": {"start": evidence_start + 2, "end": evidence_start + 2 + len(evidence)},
    })
    values, metadata = app_module._split_extraction(
        result.model_dump(mode="json"), text,
        [{"name": "birth_date", "type": "date", "format": "dd.MM.yyyy"}],
    )

    assert values == {"birth_date": "1954-03-12"}
    assert metadata == {
        "birth_date": {
            "value": "1954-03-12",
            "value_span": {"start": value_start, "end": value_start + 10},
            "evidence": evidence,
            "evidence_span": {"start": evidence_start, "end": evidence_start + len(evidence)},
        }
    }


def test_response_model_supports_all_selectable_extraction_types_and_type_specific_settings():
    model = app_module._response_model([
        {"name": "active", "type": "boolean"},
        {"name": "visit_date", "type": "date", "format": "yyyy-MM-dd"},
        {"name": "recorded_at", "type": "date_time", "format": "yyyy-MM-dd'T'HH:mm:ss"},
        {"name": "score", "type": "decimal", "min_value": 0.0, "max_value": 1.0},
        {"name": "age", "type": "integer", "min_value": 0, "max_value": 120},
        {"name": "severity", "type": "string", "allowed_values": ["low", "high"]},
    ])

    result = model(
        active=True,
        visit_date="2026-09-01",
        recorded_at="2026-09-01T10:30:00",
        score=0.5,
        age=42,
        severity="high",
    )
    assert result.model_dump(mode="json")["recorded_at"] == "2026-09-01T10:30:00"
    with pytest.raises(ValidationError):
        model(age=121)


def test_type_specific_settings_cannot_be_used_for_the_wrong_type():
    with pytest.raises(ValueError, match="only supported for string"):
        app_module._response_model([{"name": "age", "type": "integer", "allowed_values": [1, 2]}])
    with pytest.raises(ValueError, match="Minimum must not exceed maximum"):
        app_module._response_model([{"name": "age", "type": "integer", "min_value": 10, "max_value": 5}])
    with pytest.raises(ValueError, match="only supported for range"):
        app_module._response_model([{"name": "visit_date", "type": "date", "format": "yyyy-MM-dd", "min_value": "2020-01-01"}])
    with pytest.raises(ValueError, match="'format' is required"):
        app_module._response_model([{"name": "visit_date", "type": "date"}])
    with pytest.raises(ValueError, match="Unsupported type 'text'"):
        app_module._response_model([{"name": "summary", "type": "text"}])


def test_extract_table_returns_consistent_rows_and_skips_empty_text(monkeypatch):
    _set_profile(monkeypatch)
    received_texts = []

    class Result:
        def __init__(self, value):
            self.value = value

        def model_dump(self, mode):
            assert mode == "json"
            return {
                "diagnosis": {
                    "value": self.value,
                    "value_span": {"start": 12, "end": 12 + len(self.value)},
                    "evidence": self.value,
                    "evidence_span": {"start": 12, "end": 12 + len(self.value)},
                }
            }

    class InstructorClient:
        def create(self, **kwargs):
            prompt = kwargs["messages"][0]["content"]
            text = prompt.split("TEXT:\n", 1)[1]
            received_texts.append(text)
            return Result("asthma" if "asthma" in text else "diabetes")

    class OpenAIClient:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(app_module, "OpenAI", OpenAIClient)
    monkeypatch.setattr(app_module.instructor, "from_openai", lambda *_args, **_kwargs: InstructorClient())

    response = app_module.app.test_client().post(
        "/extract-table",
        json={
            "profile": "Local LLM",
            "fields": [{"name": "diagnosis", "type": "string"}],
            "rows": [
                {"row_index": 4, "text": "Patient has asthma."},
                {"row_index": 5, "text": ""},
                {"row_index": 6, "text": "Patient has diabetes."},
            ],
        },
    )

    assert response.status_code == 200
    assert response.get_json() == {
        "columns": ["row_index", "text_extraction_id", "diagnosis", "source_text"],
        "rows": [
            {"row_index": 4, "text_extraction_id": 1, "diagnosis": "asthma",
             "source_text": "Patient has asthma."},
            {"row_index": 5, "text_extraction_id": 2, "diagnosis": None, "source_text": ""},
            {"row_index": 6, "text_extraction_id": 3, "diagnosis": "diabetes",
             "source_text": "Patient has diabetes."},
        ],
        "evidence": {
            "rows": [
                {
                    "row_index": 4,
                    "fields": {
                        "diagnosis": {
                            "value": "asthma",
                            "value_span": {"start": 12, "end": 18},
                            "evidence": "asthma",
                            "evidence_span": {"start": 12, "end": 18},
                        }
                    },
                },
                {
                    "row_index": 5,
                    "fields": {
                        "diagnosis": {
                            "value": None,
                            "value_span": None,
                            "evidence": None,
                            "evidence_span": None,
                        }
                    },
                },
                {
                    "row_index": 6,
                    "fields": {
                        "diagnosis": {
                            "value": "diabetes",
                            "value_span": {"start": 12, "end": 20},
                            "evidence": "diabetes",
                            "evidence_span": {"start": 12, "end": 20},
                        }
                    },
                },
            ]
        },
    }
    assert received_texts == ["Patient has asthma.", "Patient has diabetes."]


def test_object_expands_five_procedures_into_five_rows(monkeypatch):
    _set_profile(monkeypatch)
    text = "Patient P1 underwent Proc1, Proc2, Proc3, Proc4 and Proc5."

    class Result:
        def model_dump(self, mode):
            assert mode == "json"
            return {
                "patient_id": {
                    "value": "P1", "value_span": None,
                    "evidence": "Patient P1", "evidence_span": None,
                },
                "procedures": [
                    {
                        "name": {
                            "value": f"Proc{index}", "value_span": None,
                            "evidence": f"Proc{index}", "evidence_span": None,
                        }
                    }
                    for index in range(1, 6)
                ],
            }

    class InstructorClient:
        def create(self, **_kwargs):
            return Result()

    class OpenAIClient:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(app_module, "OpenAI", OpenAIClient)
    monkeypatch.setattr(app_module.instructor, "from_openai", lambda *_args, **_kwargs: InstructorClient())

    response = app_module.app.test_client().post(
        "/extract-table",
        json={
            "profile": "Local LLM",
            "fields": [
                {"name": "patient_id", "type": "string"},
                {
                    "name": "procedures",
                    "type": "object",
                    "fields": [{"name": "name", "type": "string"}],
                },
            ],
            "rows": [{"row_index": 12, "text": text}],
        },
    )

    assert response.status_code == 200
    data = response.get_json()
    assert data["columns"] == ["row_index", "text_extraction_id", "patient_id", "object_type",
                               "name", "source_text"]
    assert data["rows"] == [
        {"row_index": index - 1, "source_row_index": 12, "text_extraction_id": 1,
         "patient_id": "P1" if index == 1 else None, "name": f"Proc{index}",
         "object_type": "procedures", "source_text": text if index == 1 else None}
        for index in range(1, 6)
    ]
    assert len(data["evidence"]["rows"]) == 5


def test_multiple_object_lists_are_appended_without_cartesian_product(monkeypatch):
    _set_profile(monkeypatch)

    class Result:
        def model_dump(self, mode):
            assert mode == "json"
            empty = {"value_span": None, "evidence_span": None}
            return {
                "patient_id": {"value": "P1", "evidence": "P1", **empty},
                "procedures": [
                    {"name": {"value": "Operation", "evidence": "Operation", **empty}},
                    {"name": {"value": "Biopsie", "evidence": "Biopsie", **empty}},
                ],
                "medications": [
                    {"name": {"value": "Aspirin", "evidence": "Aspirin", **empty}},
                ],
            }

    class InstructorClient:
        def create(self, **_kwargs):
            return Result()

    class OpenAIClient:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(app_module, "OpenAI", OpenAIClient)
    monkeypatch.setattr(app_module.instructor, "from_openai", lambda *_args, **_kwargs: InstructorClient())

    response = app_module.app.test_client().post(
        "/extract-table",
        json={
            "profile": "Local LLM",
            "fields": [
                {"name": "patient_id", "type": "string"},
                {"name": "procedures", "type": "object",
                 "fields": [{"name": "name", "type": "string"}]},
                {"name": "medications", "type": "object",
                 "fields": [{"name": "name", "type": "string"}]},
            ],
            "rows": [{"row_index": 7, "text": "P1 Operation Biopsie Aspirin"}],
        },
    )

    assert response.status_code == 200
    assert response.get_json()["rows"] == [
        {"row_index": 0, "source_row_index": 7, "text_extraction_id": 1, "patient_id": "P1",
         "name": "Operation", "object_type": "procedures",
         "source_text": "P1 Operation Biopsie Aspirin"},
        {"row_index": 1, "source_row_index": 7, "text_extraction_id": 1, "patient_id": None,
         "name": "Biopsie", "object_type": "procedures",
         "source_text": None},
        {"row_index": 2, "source_row_index": 7, "text_extraction_id": 1, "patient_id": None,
         "name": "Aspirin", "object_type": "medications",
         "source_text": None},
    ]


def test_failed_text_returns_an_empty_row_and_keeps_the_source_text(monkeypatch):
    _set_profile(monkeypatch)

    class InstructorClient:
        def create(self, **_kwargs):
            raise RuntimeError("output exceeded max_tokens")

    class OpenAIClient:
        def __init__(self, **_kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

    monkeypatch.setattr(app_module, "OpenAI", OpenAIClient)
    monkeypatch.setattr(app_module.instructor, "from_openai", lambda *_args, **_kwargs: InstructorClient())

    response = app_module.app.test_client().post(
        "/extract-table",
        json={
            "profile": "Local LLM",
            "fields": [{"name": "diagnosis", "type": "string"}],
            "rows": [{"row_index": 9, "text": "Source text that failed."}],
        },
    )

    assert response.status_code == 200
    assert response.get_json()["rows"] == [{
        "row_index": 9, "text_extraction_id": 1, "diagnosis": None,
        "source_text": "Source text that failed.",
    }]
    assert response.get_json()["errors"] == [{
        "source_row_index": 9, "message": "output exceeded max_tokens",
    }]
