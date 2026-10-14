from __future__ import annotations

import logging
import os
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from threading import Lock
from typing import Any, Literal, get_args

import instructor
from instructor.v2.core.errors import IncompleteOutputException
from openai import DefaultHttpxClient, OpenAI
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)

PROFILE_IDS_ENV = "CINNAMON_LLM_PROFILE_IDS"
PROFILE_PREFIX = "CINNAMON_LLM_PROFILE_"
STATUS_DIR_ENV = "TEXT_ANONYMIZATION_STATUS_DIR"
LLM_CHUNK_SIZE_ENV = "TEXT_ANONYMIZATION_LLM_CHUNK_SIZE"
LLM_CHUNK_OVERLAP_ENV = "TEXT_ANONYMIZATION_LLM_CHUNK_OVERLAP"
DEFAULT_LLM_OUTPUT_DIRECTORY = Path(__file__).resolve().parents[2] / "outputs"
DEFAULT_LLM_CHUNK_SIZE = 2000
DEFAULT_LLM_CHUNK_OVERLAP = 200
DEFAULT_LLM_MAX_TOKENS = 2048
MIN_RETRY_CHUNK_SIZE = 250
_response_log_lock = Lock()


def create_response_log_path() -> Path:
    """Creates one timestamped response-log path for this process."""
    # In docker we'd use env TEXT_ANONYMIZATION_STATUS_DIR, and locally we use DEFAULT_LLM_OUTPUT_DIRECTORY
    output_directory = Path(os.getenv(STATUS_DIR_ENV, str(DEFAULT_LLM_OUTPUT_DIRECTORY))).expanduser()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    return output_directory / f"llm_responses_{timestamp}.log"


DEFAULT_LLM_RESPONSE_LOG_FILE = create_response_log_path()


EntityLabel = Literal[
    "DATE",
    "NAME_PATIENT",
    "NAME_DOCTOR",
    "NAME_TITLE",
    "DATE_BIRTH",
    "ID",
    "LOCATION_CITY",
    "LOCATION_ZIP",
    "LOCATION_STREET",
    "LOCATION_HOSPITAL",
    "AGE",
    "CONTACT_PHONE",
    "CONTACT_FAX",
    "LOCATION_COUNTRY",
    "LOCATION_ORGANIZATION",
    "NAME_USERNAME",
    "PROFESSION",
    "CONTACT_EMAIL",
    "NAME_EXT",
    "NAME_RELATIVE",
    "DIAGNOSIS",
    "TREATMENT",
    "MEDICATION",
]


class LlmEntity(BaseModel):
    text: str = Field(
        min_length=1,
        description="Exact substring copied verbatim from the input text.",
    )
    label: EntityLabel
    occurrence: int = Field(
        ge=1,
        description="1-based occurrence of this exact text in the input.",
    )


Entity = dict[str, Any]

LABEL_ALIASES = {"CONTACT_FAX": "CONTACT_PHONE"}
SENSITIVE_LABELS = frozenset({"DIAGNOSIS", "TREATMENT", "MEDICATION"})
PII_LABELS = frozenset(get_args(EntityLabel)) - SENSITIVE_LABELS


SENSITIVE_LLM_INSTRUCTION = """
Du extrahierst medizinische Entitäten aus deutschsprachigen klinischen Texten.

Erlaubte Labels:
- DIAGNOSIS: Erkrankungen und klinische Diagnosen.
- TREATMENT: Medizinische Behandlungen, Eingriffe und Prozeduren.
- MEDICATION: Arzneimittel und Wirkstoffe.

Extrahiere jede ausdrücklich im TEXT genannte Entität, auch wenn sie
verneint, nur vermutet, früher gegeben oder erst geplant ist.
Bewerte nicht, ob die Diagnose gesichert oder die Behandlung durchgeführt wurde.

Regeln:
- Gib ausschließlich Textstellen zurück, die wörtlich im TEXT vorkommen.
- Übernimm die Schreibweise aus dem TEXT. Ergänze und normalisiere nichts.
- Wähle für jede Entität die genaue Textspanne; nimm einleitende Wörter wie
  „Diagnose:“ oder „Therapie:“ nicht in die Spanne auf.
- Trenne mehrere eigenständige Entitäten, statt einen ganzen Satz zu markieren.
- Gib keine Namen, Daten, Orte, Laborwerte oder anderen Entitätstypen zurück.
- Gib die Entitäten in ihrer Reihenfolge im TEXT zurück.
- Antworte ausschließlich mit gültigem JSON im Format
  {"entities":[{"text":"...","label":"...","occurrence":1}]}.
- Wenn keine passende Entität vorkommt, antworte mit {"entities":[]}.
-„occurrence“ ist die 1-basierte Nummer dieses exakt gleichen Textes im
TEXTABSCHNITT: Beim ersten Vorkommen 1, beim zweiten 2 usw.

Beispiel:
TEXT:
Keine Hinweise auf eine Pneumonie. V. a. arterielle Hypertonie.
Eine Koloskopie ist geplant. Ramipril wurde abgesetzt.
Blutdruck: 145/90 mmHg.

OUTPUT:
{
  "entities": [
    {"text": "Pneumonie", "label": "DIAGNOSIS","occurrence":1},
    {"text": "arterielle Hypertonie", "label": "DIAGNOSIS","occurrence":1},
    {"text": "Koloskopie", "label": "TREATMENT","occurrence":1},
    {"text": "Ramipril", "label": "MEDICATION","occurrence":1}
  ]
}
"""

PII_LLM_INSTRUCTION = """
Du extrahierst personenbezogene Informationen (PII) aus deutschsprachigen
klinischen Texten.


Erlaubte Labels:
DATE, DATE_BIRTH, NAME_PATIENT, NAME_DOCTOR, NAME_RELATIVE, NAME_EXT,
NAME_TITLE, ID, AGE, LOCATION_CITY, LOCATION_ZIP, LOCATION_STREET,
LOCATION_HOSPITAL, LOCATION_COUNTRY, LOCATION_ORGANIZATION,
NAME_USERNAME, PROFESSION, CONTACT_PHONE, CONTACT_FAX, CONTACT_EMAIL.

-Andere Labels existieren für diese Aufgabe nicht.
-Wenn ein Text zu keinem dieser Labels gehört, wird er nicht extrahiert.
-„occurrence“ ist die 1-basierte Nummer dieses exakt gleichen Textes im
TEXTABSCHNITT: Beim ersten Vorkommen 1, beim zweiten 2 usw.

Beispiel:
TEXT:
Der 67-jährige Patient Herr Max Berger, geb. 12.03.1957,
wurde am 18.04.2034 um 08:30 Uhr im Klinikum Sonnenberg,
Hauptstraße 17, 10115 Berlin untersucht.
Diagnose: arterielle Hypertonie.
Medikation: Ramipril 5 mg.
Blutdruck: 145/90 mmHg.
Behandlung: Koloskopie.


OUTPUT:
{
  "entities": [
    {"text":"67","label":"AGE","occurrence":1},
    {"text":"Max Berger","label":"NAME_PATIENT","occurrence":1},
    {"text":"12.03.1957","label":"DATE_BIRTH","occurrence":1},
    {"text":"18.04.2034","label":"DATE","occurrence":1},
    {"text":"Klinikum Sonnenberg","label":"LOCATION_HOSPITAL","occurrence":1},
    {"text":"Hauptstraße 17","label":"LOCATION_STREET","occurrence":1},
    {"text":"10115","label":"LOCATION_ZIP","occurrence":1},
    {"text":"Berlin","label":"LOCATION_CITY","occurrence":1}
  ]
}


"""

class LlmResult(BaseModel):
    """Stores the structured entity list returned by the LLM."""

    entities: list[LlmEntity] = Field(default_factory=list)


@dataclass(frozen=True)
class LlmProfile:
    """Stores one OpenAI-compatible endpoint configuration."""

    profile_id: str
    name: str
    provider: str
    model_name: str
    base_url: str
    endpoint_path: str
    api_key: str
    timeout_seconds: float
    max_retries: int
    verify_ssl: bool
    max_tokens: int
    enable_thinking: bool


def _env_token(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9]+", "_", value.strip()).strip("_").upper()


def _profile_value(token: str, name: str, default: str = "") -> str:
    return os.getenv(f"{PROFILE_PREFIX}{token}_{name}", default).strip().strip("\"'")


def _parse_bool(value: str) -> bool:
    return value.lower() not in {"0", "false", "no", "off"}


def load_llm_profiles() -> dict[str, LlmProfile]:
    """Loads LLM profiles from the shared Cinnamon environment variables."""
    profiles: dict[str, LlmProfile] = {}
    for profile_id in os.getenv(PROFILE_IDS_ENV, "").split(","):
        profile_id = profile_id.strip()
        if not profile_id:
            continue

        token = _env_token(profile_id)
        name = _profile_value(token, "NAME", profile_id)
        provider = _profile_value(token, "PROVIDER").lower()
        model_name = _profile_value(token, "MODEL_NAME")
        base_url = _profile_value(token, "BASE_URL").rstrip("/")
        if provider not in {"ollama", "openai_compatible"} or not all(
                (model_name, base_url)
        ):
            logger.warning("Ignoring incomplete LLM profile %s", profile_id)
            continue

        profile = LlmProfile(
            profile_id=profile_id,
            name=name,
            provider=provider,
            model_name=model_name,
            base_url=base_url,
            endpoint_path=_profile_value(
                token,
                "ENDPOINT_PATH",
                "/api/generate" if provider == "ollama" else "/v1/chat/completions",
            ),
            api_key=_profile_value(token, "API_KEY"),
            timeout_seconds=float(_profile_value(token, "TIMEOUT_SECONDS", "120")),
            max_retries=max(0, int(_profile_value(token, "MAX_RETRIES", "3"))),
            verify_ssl=_parse_bool(_profile_value(token, "VERIFY_SSL", "true")),
            max_tokens=max(1, int(_profile_value(
                token,
                "MAX_TOKENS",
                str(DEFAULT_LLM_MAX_TOKENS),
            ))),
            enable_thinking=_parse_bool(
                _profile_value(
                    token,
                    "ENABLE_THINKING",
                    "false"
                )
            ),
        )
        profiles[profile_id] = profile
        profiles[name] = profile
    return profiles


def _openai_base_url(profile: LlmProfile) -> str:
    if profile.provider == "ollama":
        return f"{profile.base_url}/v1"

    endpoint_path = profile.endpoint_path.rstrip("/")
    suffix = "/chat/completions"
    if endpoint_path.endswith(suffix):
        endpoint_path = endpoint_path[: -len(suffix)]
    return f"{profile.base_url}/{endpoint_path.lstrip('/')}".rstrip("/")


def _response_text(response: Any) -> str | None:
    choices = getattr(response, "choices", None)
    if choices:
        content = getattr(getattr(choices[0], "message", None), "content", None)
        if isinstance(content, str):
            return content
        if content is not None:
            return str(content)
    return response if isinstance(response, str) else None


def _write_llm_response(response: Any) -> None:
    response_text = _response_text(response)
    if not response_text:
        return

    log_path = DEFAULT_LLM_RESPONSE_LOG_FILE.expanduser()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with _response_log_lock, log_path.open("a", encoding="utf-8") as log_file:
        log_file.write(response_text)
        if not response_text.endswith("\n"):
            log_file.write("\n")


def _find_occurrence(text: str, value: str, occurrence: int) -> int | None:
    start = 0
    for _ in range(occurrence):
        start = text.find(value, start)
        if start < 0:
            return None
        start += len(value)
    return start - len(value)


def _find_entity_occurrence(
        text: str,
        value: str,
        occurrence: int,
        used_spans: set[tuple[int, int]],
) -> int | None:
    start = _find_occurrence(text, value, occurrence)
    if start is not None and (start, start + len(value)) not in used_spans:
        return start

    start = 0
    while True:
        start = text.find(value, start)
        if start < 0:
            return None
        end = start + len(value)
        if (start, end) not in used_spans:
            return start
        start = end


def _configured_int(name: str, default: int, minimum: int) -> int:
    raw_value = os.getenv(name, str(default)).strip()
    try:
        value = int(raw_value)
    except ValueError as error:
        raise ValueError(f"{name} must be an integer, got {raw_value!r}.") from error
    if value < minimum:
        raise ValueError(f"{name} must be at least {minimum}, got {value}.")
    return value


def text_windows(
        text: str,
        chunk_size: int = DEFAULT_LLM_CHUNK_SIZE,
        overlap: int = DEFAULT_LLM_CHUNK_OVERLAP,
) -> list[tuple[int, int, str]]:
    """Splits a document into overlapping character windows."""
    if chunk_size <= 0:
        raise ValueError("chunk_size must be positive")
    if overlap < 0 or overlap >= chunk_size:
        raise ValueError("overlap must be >= 0 and smaller than chunk_size")
    if not text:
        return []

    windows = []
    start = 0
    while start < len(text):
        end = min(start + chunk_size, len(text))
        windows.append((start, end, text[start:end]))
        if end == len(text):
            break
        start = end - overlap
    return windows


def _merge_chunk_predictions(predictions: list[Entity]) -> list[Entity]:
    merged: dict[tuple[int, int], Entity] = {}
    for prediction in predictions:
        merged.setdefault((prediction["start"], prediction["end"]), prediction)
    return sorted(
        merged.values(),
        key=lambda entity: (entity["start"], entity["end"]),
    )


def prepare_entity_label(
    label: str,
    allowed_labels: frozenset[str] | None = None,
) -> str | None:
    """Maps supported aliases and rejects labels outside the evaluation schema."""
    label = label.strip().upper()
    label = LABEL_ALIASES.get(label, label)
    valid_labels = allowed_labels or frozenset(get_args(EntityLabel))
    return label if label in valid_labels else None


class LlmInferenceModel:
    """Exposes the shared entity-prediction interface for an LLM profile."""

    def __init__(
        self,
        profile_name: str | None,
        batch_size: int = 1,
        anonymization_type: str = "PII",
        prompt: str | None = None,
    ) -> None:
        profile_name = (profile_name or "").strip()
        if not profile_name:
            raise ValueError(
                "llmProfile is required when modelType is 'LLM'. "
                "It must match a configured CINNAMON_LLM_PROFILE_* profile."
            )

        profiles = load_llm_profiles()
        profile = profiles.get(profile_name)
        if profile is None:
            available = sorted(set(profiles))
            raise ValueError(
                f"Unknown LLM profile {profile_name!r}. "
                f"Available profiles: {', '.join(available) or 'none'}."
            )

        self.profile = profile
        self.batch_size = batch_size
        self.anonymization_type = anonymization_type.strip().upper()
        if self.anonymization_type not in {"PII", "SENSITIVE"}:
            raise ValueError("anonymization_type must be PII or SENSITIVE")
        self.allowed_labels = (
            SENSITIVE_LABELS if self.anonymization_type == "SENSITIVE" else PII_LABELS
        )
        self.chunk_size = _configured_int(LLM_CHUNK_SIZE_ENV, DEFAULT_LLM_CHUNK_SIZE, 1)
        self.chunk_overlap = _configured_int(
            LLM_CHUNK_OVERLAP_ENV, DEFAULT_LLM_CHUNK_OVERLAP, 0
        )
        if self.chunk_overlap >= self.chunk_size:
            raise ValueError(
                f"{LLM_CHUNK_OVERLAP_ENV} must be smaller than {LLM_CHUNK_SIZE_ENV}."
        )
        self.model_path = Path(profile.model_name)
        # Keep these attributes aligned with InferenceModel because the
        # anonymization service logs them for every model type.
        self.window_size = self.chunk_size
        self.overlap = self.chunk_overlap
        self.prompt = prompt or (
            SENSITIVE_LLM_INSTRUCTION
            if self.anonymization_type == "SENSITIVE"
            else PII_LLM_INSTRUCTION
        )

    def _predict_one(self, client: Any, text: str) -> list[Entity]:
        request_options: dict[str, Any] = {}
        generation_options: dict[str, Any] = {"temperature": 0}
        if self.profile.provider == "openai_compatible":
            request_options["extra_body"] = {
                "chat_template_kwargs": {
                    "enable_thinking": self.profile.enable_thinking,
                },
            }
        elif self.profile.provider == "ollama":
            request_options["reasoning_effort"] = (
                "medium" if self.profile.enable_thinking else "none"
            )

        try:
            result = client.create(
                model=self.profile.model_name,
                response_model=LlmResult,
                messages=[
                    {"role": "system", "content": self.prompt},
                    {"role": "user", "content": f"TEXTABSCHNITT:\n{text}"},
                ],
                max_retries=self.profile.max_retries,
                max_tokens=self.profile.max_tokens,
                **generation_options,
                **request_options,
            )
        except Exception as error:
            _write_llm_response(
                getattr(error, "last_completion", None)
                or getattr(error, "raw_response", None)
            )
            raise

        _write_llm_response(getattr(result, "_raw_response", None))
        predictions: list[Entity] = []
        used_spans: set[tuple[int, int]] = set()
        for entity in result.entities:
            label = prepare_entity_label(entity.label, self.allowed_labels)
            if label is None:
                logger.warning("Ignoring unsupported LLM label %r.", entity.label)
                continue
            start = _find_entity_occurrence(
                text, entity.text, entity.occurrence, used_spans
            )
            if start is None:
                logger.warning(
                    "Ignoring LLM entity %r because no unused exact match was found.",
                    entity.text,
                )
                continue
            end = start + len(entity.text)
            predictions.append({
                "label": label,
                "score": 1.0,
                "text": entity.text,
                "start": start,
                "end": end,
            })
            used_spans.add((start, end))
        return predictions

    def _predict_window(
            self,
            client: Any,
            text: str,
            document_start: int
    ) -> list[Entity]:
        try:
            predictions = self._predict_one(client, text)
        except IncompleteOutputException:
            if len(text) <= MIN_RETRY_CHUNK_SIZE:
                raise
            retry_size = max(MIN_RETRY_CHUNK_SIZE, len(text) // 2)
            retry_overlap = min(self.chunk_overlap, retry_size - 1)
            logger.warning(
                "LLM output was truncated; retrying %s characters in smaller windows.",
                len(text),
            )
            predictions = []
            for start, _, chunk_text in text_windows(text, retry_size, retry_overlap):
                predictions.extend(
                    self._predict_window(client, chunk_text, document_start + start)
                )
            return predictions

        return [
            {
                **entity,
                "start": entity["start"] + document_start,
                "end": entity["end"] + document_start,
            }
            for entity in predictions
        ]

    def predict(self, texts: list[str]) -> list[list[Entity]]:
        """Runs structured LLM extraction and restores document offsets."""
        if not texts:
            return []

        with OpenAI(
                base_url=_openai_base_url(self.profile),
                api_key=self.profile.api_key or "ollama",
                timeout=self.profile.timeout_seconds,
                max_retries=0,
                http_client=DefaultHttpxClient(verify=self.profile.verify_ssl),
        ) as openai_client:
            client = instructor.from_openai(openai_client, mode=instructor.Mode.MD_JSON)
            results = []
            for text in texts:
                predictions = []
                for chunk_start, _, chunk_text in text_windows(
                        text, self.chunk_size, self.chunk_overlap
                ):
                    predictions.extend(
                        self._predict_window(client, chunk_text, chunk_start)
                    )
                results.append(_merge_chunk_predictions(predictions))
            return results
