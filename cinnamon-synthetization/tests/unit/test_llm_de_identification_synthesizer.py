import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.algorithms.llm_text_redaction import (  # noqa: E402
    LlmTextRedactionSynthesizer,
)


def _attribute_config() -> dict:
    return {
        "configurations": [
            {"index": 0, "name": "age", "type": "INTEGER"},
            {"index": 1, "name": "gender", "type": "STRING"},
            {"index": 2, "name": "notes", "type": "TEXT"},
        ]
    }


def _algorithm_config() -> dict:
    return {
        "synthetization_configuration": {
            "algorithm": {
                "model_fitting": {
                    "system_prompt": (
                        "You are a strict clinical text redaction assistant. "
                        "Replace only configured identifier spans and return only the redacted text."
                    ),
                    "user_prompt_domain_context": "Hospital discharge documentation in German.",
                },
                "sampling": {
                    "temperature": 0.0,
                    "top_p": 1.0,
                    "max_tokens": 512,
                },
                "redaction_rules": [
                    {
                        "name": "Age",
                        "replacement_token": "[AGE]",
                    },
                    {
                        "name": "Name",
                        "replacement_token": "[NAME]",
                    },
                ],
            }
        }
    }


def _set_shared_llm_env(monkeypatch, provider: str = "ollama") -> None:
    if provider == "ollama":
        monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "ollama")
        monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "llama3.1:8b")
        monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://127.0.0.1:11434")
        monkeypatch.setenv("CINNAMON_LLM_ENDPOINT_PATH", "/api/generate")
        monkeypatch.setenv("CINNAMON_LLM_HEALTHCHECK_PATH", "/api/tags")
    else:
        monkeypatch.setenv("CINNAMON_LLM_PROVIDER", "openai_compatible")
        monkeypatch.setenv("CINNAMON_LLM_MODEL_NAME", "gpt-test")
        monkeypatch.setenv("CINNAMON_LLM_BASE_URL", "http://gpu.example.org:7086")
        monkeypatch.setenv("CINNAMON_LLM_ENDPOINT_PATH", "/v1/chat/completions")
        monkeypatch.setenv("CINNAMON_LLM_HEALTHCHECK_PATH", "/v1/models")
    monkeypatch.setenv("CINNAMON_LLM_API_KEY", "")
    monkeypatch.setenv("CINNAMON_LLM_TIMEOUT_SECONDS", "5")
    monkeypatch.setenv("CINNAMON_LLM_MAX_RETRIES", "2")
    monkeypatch.setenv("CINNAMON_LLM_VERIFY_SSL", "true")


class _DummyResponse:
    def __init__(self, payload: dict):
        self._payload = payload
        self.status_code = 200

    def raise_for_status(self):
        return None

    def json(self):
        return self._payload


def _dataset() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [80, 65],
            "gender": ["F", "M"],
            "notes": [
                "Patient Jane Doe is 78 years old and can be reached at jane@example.com.",
                "Patient John Smith, age 64, phone 555-1234.",
            ],
        }
    )


def test_llm_text_de_identification_changes_only_text_and_keeps_row_count(monkeypatch):
    _set_shared_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            prompt = kwargs["json"]["prompt"]
            assert kwargs["json"]["system"] == (
                "You are a strict clinical text redaction assistant. "
                "Replace only configured identifier spans and return only the redacted text."
            )
            assert "Target TEXT column: notes" in prompt
            assert "Domain context: Hospital discharge documentation in German." in prompt
            assert "Custom redaction rules: treat each rule name as a semantic category or concept." in prompt
            assert "- Age -> [AGE]" in prompt
            assert "- Name -> [NAME]" in prompt
            assert "Only replace or remove identifiers that match the configured redaction rules listed below." in prompt
            assert "The configured identifier categories are: Age, Name." in prompt
            assert "Replace each matching span with the exact replacement token defined by the matching redaction rule." in prompt
            assert "Do NOT infer or redact categories that are not explicitly configured." in prompt
            assert "Return ONLY valid JSON with this exact shape:" in prompt

            if "Patient Jane Doe is 78 years old" in prompt:
                return _DummyResponse(
                    {
                        "response": json.dumps(
                            {"text": "Patient [NAME] is [AGE] years old and can be reached at jane@example.com."}
                        )
                    }
                )
            return _DummyResponse({"response": json.dumps({"text": "Patient [NAME], age [AGE], phone 555-1234."})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    source = _dataset()
    synthesizer = LlmTextRedactionSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(source)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    assert len(result) == len(source)
    assert result["age"].tolist() == source["age"].tolist()
    assert result["gender"].tolist() == source["gender"].tolist()
    assert result["notes"].tolist() == [
        "Patient [NAME] is [AGE] years old and can be reached at jane@example.com.",
        "Patient [NAME], age [AGE], phone 555-1234.",
    ]


def test_llm_text_de_identification_retries_when_output_is_over_edited(monkeypatch):
    post_call_count = {"value": 0}
    _set_shared_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            post_call_count["value"] += 1
            if post_call_count["value"] == 1:
                return _DummyResponse({"response": json.dumps({"text": "Echocardiogram and Doppler"})})
            return _DummyResponse(
                {
                    "response": json.dumps(
                        {"text": "Patient [NAME] is [AGE] years old and can be reached at jane@example.com."}
                    )
                }
            )

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    source = pd.DataFrame(
        {
            "age": [80],
            "gender": ["F"],
            "notes": ["Patient Jane Doe is 78 years old and can be reached at jane@example.com."],
        }
    )
    synthesizer = LlmTextRedactionSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(source)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    assert post_call_count["value"] == 2
    assert result["notes"].tolist() == [
        "Patient [NAME] is [AGE] years old and can be reached at jane@example.com.",
    ]


def test_llm_text_de_identification_uses_custom_system_prompt_for_openai_compatible(monkeypatch):
    _set_shared_llm_env(monkeypatch, provider="openai_compatible")

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/v1/models"):
            return _DummyResponse({"data": [{"id": "gpt-test"}]})

        if method == "POST" and url.endswith("/v1/chat/completions"):
            messages = kwargs["json"]["messages"]
            assert messages[0]["role"] == "system"
            assert messages[0]["content"] == (
                "You are a strict clinical text redaction assistant. "
                "Replace only configured identifier spans and return only the redacted text."
            )
            assert messages[1]["role"] == "user"
            assert "Replace each matching span with the exact replacement token defined by the matching redaction rule." in messages[1]["content"]
            assert "Only replace or remove identifiers that match the configured redaction rules listed below." in messages[1]["content"]
            return _DummyResponse(
                {
                    "choices": [
                        {
                            "message": {
                                "content": json.dumps({"text": "Patient [NAME], age [AGE], phone 555-1234."})
                            }
                        }
                    ]
                }
            )

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    source = pd.DataFrame(
        {
            "age": [65],
            "gender": ["M"],
            "notes": ["Patient John Smith, age 64, phone 555-1234."],
        }
    )
    synthesizer = LlmTextRedactionSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(source)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    assert result["notes"].tolist() == ["Patient [NAME], age [AGE], phone 555-1234."]


def test_llm_text_de_identification_rejects_non_json_and_retries(monkeypatch):
    post_call_count = {"value": 0}
    _set_shared_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            post_call_count["value"] += 1
            if post_call_count["value"] == 1:
                return _DummyResponse({"response": "Patient [NAME], age [AGE]."})
            return _DummyResponse({"response": json.dumps({"text": "Patient [NAME], age [AGE]."})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    source = pd.DataFrame(
        {
            "age": [80],
            "gender": ["F"],
            "notes": ["Patient Jane Doe, age 78."],
        }
    )
    synthesizer = LlmTextRedactionSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(source)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    assert post_call_count["value"] == 2
    assert result["notes"].tolist() == ["Patient [NAME], age [AGE]."]


def test_llm_text_de_identification_rejects_unconfigured_replacement_tokens(monkeypatch):
    post_call_count = {"value": 0}
    _set_shared_llm_env(monkeypatch)

    def fake_request(method, url, **kwargs):
        if method == "GET" and url.endswith("/api/tags"):
            return _DummyResponse({"models": [{"name": "llama3.1:8b"}]})

        if method == "POST" and url.endswith("/api/generate"):
            post_call_count["value"] += 1
            if post_call_count["value"] == 1:
                return _DummyResponse({"response": json.dumps({"text": "Patient [NAME], age [BIRTH_DATE]."})})
            return _DummyResponse({"response": json.dumps({"text": "Patient [NAME], age [AGE]."})})

        raise AssertionError(f"Unexpected request: {method} {url}")

    monkeypatch.setattr("synthetic_tabular_data_generator.llm.client.requests.request", fake_request)

    source = pd.DataFrame(
        {
            "age": [80],
            "gender": ["F"],
            "notes": ["Patient Jane Doe, age 78."],
        }
    )
    synthesizer = LlmTextRedactionSynthesizer()
    synthesizer.initialize_anonymization_configuration(_algorithm_config())
    synthesizer.initialize_attribute_configuration(_attribute_config())
    synthesizer.initialize_dataset(source)
    synthesizer.initialize_synthesizer()
    synthesizer.fit()

    result = synthesizer.sample()

    assert post_call_count["value"] == 2
    assert result["notes"].tolist() == ["Patient [NAME], age [AGE]."]
