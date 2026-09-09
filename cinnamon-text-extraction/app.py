import logging
import os
import re
from copy import deepcopy
from dataclasses import dataclass
from datetime import date, datetime
from typing import Annotated, Any, Literal

import instructor
from flask import Flask, jsonify, request
from openai import DefaultHttpxClient, OpenAI
from pydantic import BaseModel, BeforeValidator, Field, ValidationError, create_model, model_validator


logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)

app = Flask(__name__)
logger = logging.getLogger(__name__)

PROFILE_IDS_ENV = "CINNAMON_LLM_PROFILE_IDS"
PROFILE_PREFIX = "CINNAMON_LLM_PROFILE_"
FIELD_NAME_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
FIELD_TYPES = {
    "boolean": bool,
    "date": date,
    "date_time": datetime,
    "decimal": float,
    "integer": int,
    "string": str,
}
RANGE_FIELD_TYPES = {"decimal", "integer"}
DATE_FIELD_TYPES = {"date", "date_time"}
EXTRACTION_INSTRUCTION = (
    "Extract only the requested facts from the text. "
    "Use null when a fact is not explicitly present and do not guess or add information. "
    "Normalize dates to YYYY-MM-DD and date-times to ISO 8601, regardless of their source format. "
    "For every extracted value, return an exact contiguous evidence quote from the original text. "
    "Return zero-based, end-exclusive character spans for both the source value and the complete evidence quote."
)


class TextSpan(BaseModel):
    start: int = Field(ge=0)
    end: int = Field(ge=0)

    @model_validator(mode="after")
    def validate_order(self):
        if self.end <= self.start:
            raise ValueError("Span end must be greater than span start.")
        return self


class EvidenceValue(BaseModel):
    @model_validator(mode="wrap")
    @classmethod
    def discard_invalid_value(cls, value, handler):
        try:
            return handler(value)
        except ValidationError:
            return handler({})


@dataclass(frozen=True)
class LlmProfile:
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


def _env_token(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9]+", "_", value.strip()).strip("_").upper()


def _profile_value(token: str, name: str, default: str = "") -> str:
    return os.getenv(f"{PROFILE_PREFIX}{token}_{name}", default).strip().strip('"\'')


def _parse_bool(value: str) -> bool:
    return value.lower() not in {"0", "false", "no", "off"}


def load_llm_profiles() -> dict[str, LlmProfile]:
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
        if provider not in {"ollama", "openai_compatible"} or not model_name or not base_url:
            logger.warning("Ignoring incomplete LLM profile %s", profile_id)
            continue

        profiles[name] = LlmProfile(
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
            max_tokens=max(1, int(_profile_value(token, "MAX_TOKENS", "4096"))),
        )
    return profiles


def _openai_base_url(profile: LlmProfile) -> str:
    if profile.provider == "ollama":
        return f"{profile.base_url}/v1"

    suffix = "/chat/completions"
    endpoint_path = profile.endpoint_path.rstrip("/")
    if endpoint_path.endswith(suffix):
        endpoint_path = endpoint_path[: -len(suffix)]
    return f"{profile.base_url}/{endpoint_path.lstrip('/')}".rstrip("/")


def _response_model(fields: Any, model_name: str = "TextExtractionResult", nested: bool = False):
    if not isinstance(fields, list) or not fields:
        raise ValueError("'fields' must be a non-empty list.")

    model_fields = {}
    for field_config in fields:
        if not isinstance(field_config, dict):
            raise ValueError("Each field must be an object.")

        name = str(field_config.get("name", "")).strip()
        field_type_name = str(field_config.get("type", "string")).strip().lower()
        description = str(field_config.get("description", "")).strip()
        allowed_values = field_config.get("allowed_values", [])
        min_value = field_config.get("min_value")
        max_value = field_config.get("max_value")
        date_format = str(field_config.get("format", "")).strip()
        if not FIELD_NAME_PATTERN.fullmatch(name):
            raise ValueError(f"Invalid field name '{name}'.")
        if name in model_fields:
            raise ValueError(f"Duplicate field name '{name}'.")
        if field_type_name == "object":
            if nested:
                raise ValueError("Nested object fields are not supported.")
            object_fields = field_config.get("fields")
            item_model = _response_model(
                object_fields,
                f"{name.title().replace('_', '')}Item",
                nested=True,
            )
            model_fields[name] = (
                list[item_model],
                Field(
                    default_factory=list,
                    description=description or f"All extracted {name} entries; empty if absent.",
                ),
            )
            continue
        if field_type_name not in FIELD_TYPES:
            supported = ", ".join([*FIELD_TYPES, "object"])
            raise ValueError(f"Unsupported type '{field_type_name}'. Supported types: {supported}.")
        if not isinstance(allowed_values, list):
            raise ValueError(f"Allowed values for '{name}' must be a list.")
        if allowed_values and field_type_name != "string":
            raise ValueError("'allowed_values' is only supported for string fields.")
        if (_has_value(min_value) or _has_value(max_value)) and field_type_name not in RANGE_FIELD_TYPES:
            raise ValueError("'min_value' and 'max_value' are only supported for range field types.")
        if field_type_name in DATE_FIELD_TYPES and not date_format:
            raise ValueError(f"'format' is required for {field_type_name} fields.")
        if date_format and field_type_name not in DATE_FIELD_TYPES:
            raise ValueError("'format' is only supported for date and date_time fields.")

        value_type = FIELD_TYPES[field_type_name]
        if field_type_name == "date":
            value_type = Annotated[date, BeforeValidator(_parse_date)]
        elif field_type_name == "date_time":
            value_type = Annotated[datetime, BeforeValidator(_parse_date_time)]
        if allowed_values:
            try:
                converted_values = tuple(_convert_value(value, field_type_name) for value in allowed_values)
            except (TypeError, ValueError) as error:
                raise ValueError(f"Invalid allowed value for field '{name}'.") from error
            value_type = Literal.__getitem__(converted_values)
        field_options = {"description": description or f"Extracted value for {name}; null if absent."}
        converted_min = _optional_value(min_value, field_type_name)
        converted_max = _optional_value(max_value, field_type_name)
        if converted_min is not None:
            field_options["ge"] = converted_min
        if converted_max is not None:
            field_options["le"] = converted_max
        if converted_min is not None and converted_max is not None and converted_min > converted_max:
            raise ValueError(f"Minimum must not exceed maximum for field '{name}'.")
        model_fields[name] = (
            value_type | None,
            Field(default=None, **field_options),
        )

    return create_model(model_name, **model_fields)


def _evidence_response_model(
    fields: Any, model_name: str = "TextExtractionEvidenceResult", nested: bool = False
):
    value_model = _response_model(fields, nested=nested)
    evidence_fields = {}
    for field_config in fields:
        name = str(field_config.get("name", "")).strip()
        if str(field_config.get("type", "string")).strip().lower() == "object":
            item_model = _evidence_response_model(
                field_config.get("fields"),
                f"{name.title().replace('_', '')}EvidenceItem",
                nested=True,
            )
            evidence_fields[name] = (
                list[item_model | None] | None,
                Field(default=None, description=f"All extracted {name} entries with source evidence."),
            )
            continue
        value_field = value_model.model_fields[name]
        evidence_model = create_model(
            f"{name.title().replace('_', '')}Evidence",
            __base__=EvidenceValue,
            value=(value_field.annotation, deepcopy(value_field)),
            value_span=(TextSpan | None, Field(default=None, description="Span of the source value.")),
            evidence=(str | None, Field(default=None, description="Exact quote supporting the value.")),
            evidence_span=(TextSpan | None, Field(default=None, description="Span of the evidence quote.")),
        )
        evidence_fields[name] = (
            evidence_model | None,
            Field(default=None, description=f"Extracted value and source evidence for {name}; null if absent."),
        )
    return create_model(model_name, **evidence_fields)


def _convert_value(value: Any, field_type_name: str) -> Any:
    if field_type_name == "boolean":
        normalized = str(value).strip().lower()
        if normalized not in {"true", "false"}:
            raise ValueError("Boolean values must be true or false.")
        return normalized == "true"
    if field_type_name == "date":
        return date.fromisoformat(str(value).strip())
    if field_type_name == "date_time":
        return datetime.fromisoformat(str(value).strip().replace("Z", "+00:00"))
    return FIELD_TYPES[field_type_name](value)


def _parse_date(value: Any) -> Any:
    if value is None or isinstance(value, date):
        return value
    text = str(value).strip()
    for pattern in ("%Y-%m-%d", "%d.%m.%Y", "%d.%m.%y", "%d/%m/%Y", "%d-%m-%Y"):
        try:
            return datetime.strptime(text, pattern).date()
        except ValueError:
            pass
    return value


def _parse_date_time(value: Any) -> Any:
    if value is None or isinstance(value, datetime):
        return value
    text = str(value).strip()
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        pass
    for pattern in ("%d.%m.%Y %H:%M:%S", "%d.%m.%Y %H:%M", "%d/%m/%Y %H:%M:%S"):
        try:
            return datetime.strptime(text, pattern)
        except ValueError:
            pass
    return value


def _optional_value(value: Any, field_type_name: str) -> Any:
    if not _has_value(value):
        return None
    try:
        return _convert_value(value, field_type_name)
    except (TypeError, ValueError) as error:
        raise ValueError(f"Invalid range value for type '{field_type_name}'.") from error


def _has_value(value: Any) -> bool:
    return value is not None and value != ""


def extract_text(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise ValueError("Request body must be a JSON object.")

    profile_name = str(payload.get("profile", "")).strip()
    text = str(payload.get("text", "")).strip()
    if not profile_name:
        raise ValueError("'profile' is required.")
    if not text:
        raise ValueError("'text' is required.")

    profiles = load_llm_profiles()
    if profile_name not in profiles:
        available = ", ".join(profiles) or "none"
        raise ValueError(f"Unknown profile '{profile_name}'. Available profiles: {available}.")

    profile = profiles[profile_name]
    fields = payload.get("fields")
    response_model = _evidence_response_model(fields)

    with OpenAI(
        base_url=_openai_base_url(profile),
        api_key=profile.api_key or "ollama",
        timeout=float(os.getenv("TEXT_EXTRACTION_LLM_TIMEOUT_SECONDS", str(profile.timeout_seconds))),
        max_retries=0,
        http_client=DefaultHttpxClient(verify=profile.verify_ssl),
    ) as openai_client:
        client = instructor.from_openai(openai_client, mode=instructor.Mode.MD_JSON)
        result = _extract_with_client(client, profile, response_model, text)
    values, evidence = _split_extraction(result.model_dump(mode="json"), text, fields)
    return {"values": values, "evidence": evidence}


def extract_table(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise ValueError("Request body must be a JSON object.")

    profile_name = str(payload.get("profile", "")).strip()
    profiles = load_llm_profiles()
    if profile_name not in profiles:
        available = ", ".join(profiles) or "none"
        raise ValueError(f"Unknown profile '{profile_name}'. Available profiles: {available}.")

    rows = payload.get("rows")
    if not isinstance(rows, list):
        raise ValueError("'rows' must be a list.")

    fields = payload.get("fields")
    response_model = _evidence_response_model(fields)
    flat_fields, object_fields = _flattened_fields(fields)
    field_names = [str(field["name"]).strip() for field in flat_fields]
    reserved_names = {"source_text", "text_extraction_id"}
    if reserved_names.intersection(field_names):
        raise ValueError("'source_text' and 'text_extraction_id' are reserved fields.")
    if object_fields:
        primitive_count = sum(
            str(field.get("type", "string")).strip().lower() != "object" for field in fields
        )
        field_names.insert(primitive_count, "object_type")
    profile = profiles[profile_name]
    row_indices = [row.get("row_index", position) if isinstance(row, dict) else None
                   for position, row in enumerate(rows)]
    if any(not isinstance(row_index, int) for row_index in row_indices):
        raise ValueError("'row_index' must be an integer.")
    if len(set(row_indices)) != len(row_indices):
        raise ValueError("'row_index' values must be unique.")

    result_by_index = {}
    evidence_by_index = {}
    source_text_by_index = {}
    errors = []
    rows_for_llm = []
    for position, row in enumerate(rows):
        if not isinstance(row, dict):
            raise ValueError("Each row must be an object.")
        row_index = row_indices[position]
        text = str(row.get("text") or "").strip()
        source_text_by_index[row_index] = row.get("text")
        if text:
            rows_for_llm.append({"row_index": row_index, "text": text})
        else:
            values, evidence = _empty_extraction(fields)
            result_by_index[row_index] = values
            evidence_by_index[row_index] = evidence

    with OpenAI(
        base_url=_openai_base_url(profile),
        api_key=profile.api_key or "ollama",
        timeout=float(os.getenv("TEXT_EXTRACTION_LLM_TIMEOUT_SECONDS", str(profile.timeout_seconds))),
        max_retries=0,
        http_client=DefaultHttpxClient(verify=profile.verify_ssl),
    ) as openai_client:
        client = instructor.from_openai(openai_client, mode=instructor.Mode.MD_JSON)
        for row in rows_for_llm:
            try:
                result = _extract_with_client(client, profile, response_model, row["text"])
                values, evidence = _split_extraction(result.model_dump(mode="json"), row["text"], fields)
            except Exception as error:
                logger.exception("Text extraction failed for source row %s", row["row_index"])
                values, evidence = _empty_extraction(fields)
                errors.append({"source_row_index": row["row_index"], "message": str(error)})
            result_by_index[row["row_index"]] = values
            evidence_by_index[row["row_index"]] = evidence

    if not object_fields:
        result_rows = [{"row_index": row_index, "text_extraction_id": position + 1,
                        **result_by_index[row_index],
                        "source_text": source_text_by_index[row_index]}
                       for position, row_index in enumerate(row_indices)]
        evidence_rows = [
            {"row_index": row_index, "fields": evidence_by_index[row_index]}
            for row_index in row_indices
        ]
    else:
        result_rows = []
        evidence_rows = []
        for source_position, source_row_index in enumerate(row_indices):
            flattened = _flatten_extraction(
                result_by_index[source_row_index],
                evidence_by_index[source_row_index],
                fields,
                object_fields,
            )
            for flattened_position, (values, evidence) in enumerate(flattened):
                row_index = len(result_rows)
                result_rows.append({
                    "row_index": row_index,
                    "source_row_index": source_row_index,
                    "text_extraction_id": source_position + 1,
                    **values,
                    "source_text": (source_text_by_index[source_row_index]
                                    if flattened_position == 0 else None),
                })
                evidence_rows.append({
                    "row_index": row_index,
                    "source_row_index": source_row_index,
                    "fields": evidence,
                })
    response = {
        "columns": ["row_index", "text_extraction_id", *field_names, "source_text"],
        "rows": result_rows,
        "evidence": {"rows": evidence_rows},
    }
    if errors:
        response["errors"] = errors
    return response


def _empty_evidence(value: Any = None) -> dict[str, Any]:
    return {
        "value": value,
        "value_span": None,
        "evidence": None,
        "evidence_span": None,
    }


def _empty_extraction(
    fields: list[dict[str, Any]],
) -> tuple[dict[str, Any], dict[str, Any]]:
    values = {}
    evidence = {}
    for field in fields:
        name = str(field["name"]).strip()
        if str(field.get("type", "string")).strip().lower() == "object":
            values[name] = []
            evidence[name] = []
        else:
            values[name] = None
            evidence[name] = _empty_evidence()
    return values, evidence


def _split_extraction(
    extracted: dict[str, Any], text: str, fields: list[dict[str, Any]]
) -> tuple[dict[str, Any], dict[str, Any]]:
    values = {}
    evidence = {}
    for field in fields:
        name = str(field["name"]).strip()
        if str(field.get("type", "string")).strip().lower() == "object":
            object_values = []
            object_evidence = []
            items = extracted.get(name)
            if isinstance(items, list):
                for item in items:
                    if isinstance(item, dict):
                        item_values, item_evidence = _split_extraction(item, text, field["fields"])
                        object_values.append(item_values)
                        object_evidence.append(item_evidence)
            values[name] = object_values
            evidence[name] = object_evidence
            continue
        item = extracted.get(name)
        if not isinstance(item, dict):
            item = _empty_evidence()
        value = item.get("value")
        values[name] = value
        evidence[name] = _normalize_evidence(value, item, text, field)
    return values, evidence


def _flattened_fields(
    fields: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    flat_fields = []
    object_fields = [field for field in fields
                     if str(field.get("type", "string")).strip().lower() == "object"]
    names = {}
    primitive_fields = [field for field in fields
                        if str(field.get("type", "string")).strip().lower() != "object"]
    for candidates, nested in ((primitive_fields, False),
                               ([nested for field in object_fields for nested in field["fields"]], True)):
        for candidate in candidates:
            name = str(candidate["name"]).strip()
            if name in names:
                previous, previous_nested = names[name]
                compatible = (nested and previous_nested
                              and candidate.get("type") == previous.get("type")
                              and candidate.get("format") == previous.get("format"))
                if compatible:
                    continue
                raise ValueError(f"Duplicate or incompatible table column name '{name}'.")
            names[name] = (candidate, nested)
            flat_fields.append(candidate)
    if object_fields and "object_type" in names:
        raise ValueError("'object_type' is reserved when object fields are configured.")
    return flat_fields, object_fields


def _flatten_extraction(
    values: dict[str, Any],
    evidence: dict[str, Any],
    fields: list[dict[str, Any]],
    object_fields: list[dict[str, Any]],
) -> list[tuple[dict[str, Any], dict[str, Any]]]:
    base_values = {}
    base_evidence = {}
    for field in fields:
        name = str(field["name"]).strip()
        if str(field.get("type", "string")).strip().lower() != "object":
            base_values[name] = values.get(name)
            base_evidence[name] = evidence.get(name, _empty_evidence())

    rows = []
    nested_names = {str(field["name"]).strip()
                    for object_field in object_fields for field in object_field["fields"]}
    for object_field in object_fields:
        object_name = str(object_field["name"]).strip()
        object_values = values.get(object_name)
        object_evidence = evidence.get(object_name)
        if not isinstance(object_values, list):
            continue
        for index, item_values in enumerate(object_values):
            item_values = item_values if isinstance(item_values, dict) else {}
            item_evidence = (object_evidence[index]
                             if isinstance(object_evidence, list) and index < len(object_evidence)
                             and isinstance(object_evidence[index], dict) else {})
            row_values = {**base_values, **dict.fromkeys(nested_names), "object_type": object_name}
            row_evidence = {**base_evidence,
                            **{name: _empty_evidence() for name in nested_names},
                            "object_type": _empty_evidence(object_name)}
            for field in object_field["fields"]:
                name = str(field["name"]).strip()
                row_values[name] = item_values.get(name)
                row_evidence[name] = item_evidence.get(name, _empty_evidence())
            rows.append((row_values, row_evidence))
    if not rows:
        rows.append(({**base_values, **dict.fromkeys(nested_names), "object_type": None},
                     {**base_evidence,
                      **{name: _empty_evidence() for name in nested_names},
                      "object_type": _empty_evidence()}))
    for row_values, row_evidence in rows[1:]:
        for name in base_values:
            row_values[name] = None
            row_evidence[name] = _empty_evidence()
    return rows


def _normalize_evidence(
    value: Any, item: dict[str, Any], text: str, field: dict[str, Any] | None = None
) -> dict[str, Any]:
    if value is None:
        return _empty_evidence()

    quote = item.get("evidence")
    if not isinstance(quote, str) or not quote:
        return _empty_evidence(value)

    reported_evidence_span = item.get("evidence_span")
    evidence_start = _closest_occurrence(text, quote, _span_start(reported_evidence_span))
    if evidence_start is None:
        return _empty_evidence(value)
    evidence_span = {"start": evidence_start, "end": evidence_start + len(quote)}

    value_texts = [str(value).lower() if isinstance(value, bool) else str(value)]
    formatted_value = _formatted_source_value(value, field)
    if formatted_value is not None:
        value_texts.insert(0, formatted_value)
    relative_start = next((quote.find(candidate) for candidate in value_texts if quote.find(candidate) >= 0), -1)
    if relative_start >= 0:
        matched_value = next(candidate for candidate in value_texts if quote.find(candidate) == relative_start)
        value_span = {
            "start": evidence_start + relative_start,
            "end": evidence_start + relative_start + len(matched_value),
        }
    else:
        value_span = _shifted_value_span(
            item.get("value_span"), reported_evidence_span, evidence_span
        )

    return {
        "value": value,
        "value_span": value_span,
        "evidence": quote,
        "evidence_span": evidence_span,
    }


def _formatted_source_value(value: Any, field: dict[str, Any] | None) -> str | None:
    if not field or field.get("type") not in DATE_FIELD_TYPES or not field.get("format"):
        return None
    java_format = str(field["format"])
    if "S" in java_format:
        return None
    python_format = java_format.replace("'", "")
    for java_token, python_token in (
        ("yyyy", "%Y"), ("yy", "%y"), ("MM", "%m"), ("dd", "%d"),
        ("HH", "%H"), ("hh", "%I"), ("mm", "%M"), ("ss", "%S"),
    ):
        python_format = python_format.replace(java_token, python_token)
    try:
        parsed = (date.fromisoformat(str(value)) if field["type"] == "date"
                  else datetime.fromisoformat(str(value).replace("Z", "+00:00")))
        return parsed.strftime(python_format)
    except ValueError:
        return None


def _span_start(span: Any) -> int | None:
    return span.get("start") if isinstance(span, dict) and isinstance(span.get("start"), int) else None


def _closest_occurrence(text: str, value: str, preferred_start: int | None) -> int | None:
    occurrences = [match.start() for match in re.finditer(re.escape(value), text)]
    if not occurrences:
        return None
    if preferred_start is None:
        return occurrences[0]
    return min(occurrences, key=lambda start: abs(start - preferred_start))


def _shifted_value_span(
    value_span: Any, reported_evidence_span: Any, actual_evidence_span: dict[str, int]
) -> dict[str, int] | None:
    if not isinstance(value_span, dict):
        return None
    start = value_span.get("start")
    end = value_span.get("end")
    if not isinstance(start, int) or not isinstance(end, int) or end <= start:
        return None
    reported_start = _span_start(reported_evidence_span)
    shift = actual_evidence_span["start"] - reported_start if reported_start is not None else 0
    shifted = {"start": start + shift, "end": end + shift}
    if (shifted["start"] < actual_evidence_span["start"]
            or shifted["end"] > actual_evidence_span["end"]):
        return None
    return shifted


def _extract_with_client(client: Any, profile: LlmProfile, response_model: Any, text: str) -> Any:
    options = {
        "model": profile.model_name,
        "response_model": response_model,
        "messages": [{"role": "user", "content": f"{EXTRACTION_INSTRUCTION}\n\nTEXT:\n{text}"}],
        "max_retries": profile.max_retries,
        "temperature": 0,
        "max_tokens": profile.max_tokens,
    }
    if profile.provider == "ollama":
        options["reasoning_effort"] = "none"
    return client.create(**options)


def validate_configuration(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise ValueError("Request body must be a JSON object.")

    profile_name = str(payload.get("profile", "")).strip()
    source_column = str(payload.get("source_column", "")).strip()
    if profile_name not in load_llm_profiles():
        raise ValueError(f"Unknown profile '{profile_name}'.")
    if not source_column:
        raise ValueError("'source_column' is required.")

    fields = payload.get("fields")
    _response_model(fields)
    _flattened_fields(fields)
    return {
        "profile": profile_name,
        "source_column": source_column,
        "fields": [
            _normalized_field(field)
            for field in fields
        ],
    }


def _normalized_field(field: dict[str, Any]) -> dict[str, Any]:
    field_type = str(field.get("type", "string")).strip().lower()
    normalized = {
                "name": str(field["name"]).strip(),
                "type": field_type,
                "description": str(field.get("description", "")).strip(),
    }
    if field_type == "object":
        normalized["fields"] = [_normalized_field(nested) for nested in field["fields"]]
        return normalized
    if field_type == "string":
        normalized["allowed_values"] = field.get("allowed_values", [])
    if field_type in RANGE_FIELD_TYPES:
        if _has_value(field.get("min_value")):
            normalized["min_value"] = field["min_value"]
        if _has_value(field.get("max_value")):
            normalized["max_value"] = field["max_value"]
    if field_type in DATE_FIELD_TYPES:
        normalized["format"] = str(field["format"]).strip()
    return normalized


@app.get("/actuator/health")
def health_check():
    return jsonify({"status": "UP"})


@app.get("/profiles")
def get_profiles():
    profiles = load_llm_profiles().values()
    return jsonify(
        {
            "profiles": [
                {"name": profile.name, "provider": profile.provider, "model_name": profile.model_name}
                for profile in profiles
            ]
        }
    )


@app.post("/extract")
def extract():
    try:
        return jsonify({"data": extract_text(request.get_json(silent=True))})
    except ValueError as error:
        return jsonify({"error": str(error)}), 400
    except Exception as error:
        logger.exception("Text extraction failed")
        return jsonify({"error": str(error)}), 502


@app.post("/configuration")
def configure():
    try:
        return jsonify({"configuration": validate_configuration(request.get_json(silent=True))})
    except ValueError as error:
        return jsonify({"error": str(error)}), 400


@app.post("/extract-table")
def extract_table_route():
    try:
        return jsonify(extract_table(request.get_json(silent=True)))
    except ValueError as error:
        return jsonify({"error": str(error)}), 400
    except Exception as error:
        logger.exception("Tabular text extraction failed")
        return jsonify({"error": str(error)}), 502


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5070)
