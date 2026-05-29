import json
import math
import time
from dataclasses import dataclass
from json import JSONDecodeError
from typing import Any, Dict, List, Optional, Sequence

import pandas as pd

from data_processing.utils import BOOLEAN_MAP, MISSING_BOOLEAN, MISSING_VALUE_STRING
from data_processing.utils import get_date_format, parse_to_date_format, parse_to_unix


@dataclass(frozen=True)
class ColumnProfileOptions:
    categorical_top_k: int = 10
    include_text_examples: bool = False
    text_example_limit: int = 0
    excluded_text_values: Sequence[str] = ()


class LlmSynthesizerSupport:
    NUMERIC_TYPES = {"INTEGER", "DECIMAL", "DATE"}
    _NULL_LIKE_STRINGS = {"nan", "null", "none", "<na>"}

    def build_column_profile(
        self,
        df: pd.DataFrame,
        column_name: str,
        column_type: str,
        *,
        options: Optional[ColumnProfileOptions] = None,
    ) -> Dict[str, Any]:
        resolved_options = options or ColumnProfileOptions()
        if column_name not in df.columns:
            return {"type": column_type, "available": False, "reason": "column_missing"}

        series = df[column_name]
        missing_ratio = float(series.isna().mean()) if len(series) else 1.0
        profile: Dict[str, Any] = {
            "type": column_type,
            "available": True,
            "missing_ratio": round(missing_ratio, 4),
        }

        if column_type in self.NUMERIC_TYPES:
            numeric = pd.to_numeric(series, errors="coerce").dropna()
            if numeric.empty:
                profile["available"] = False
                profile["reason"] = "no_numeric_values"
                return profile

            profile.update(
                {
                    "kind": "numeric",
                    "min": float(numeric.min()),
                    "max": float(numeric.max()),
                    "mean": float(numeric.mean()),
                    "std": float(numeric.std(ddof=0)) if len(numeric) > 1 else 0.0,
                }
            )
            return profile

        if column_type == "TEXT":
            values = series.dropna().astype(str).str.strip()
            excluded_values = {"", *resolved_options.excluded_text_values}
            values = values[~values.isin(excluded_values)]
            profile["kind"] = "text"
            if resolved_options.include_text_examples:
                profile["example_snippets"] = values.head(resolved_options.text_example_limit).tolist()
            if values.empty:
                profile["available"] = False
                profile["reason"] = "no_text_values"
            return profile

        values = series.dropna().astype(str)
        if values.empty:
            profile["available"] = False
            profile["reason"] = "no_categorical_values"
            return profile

        distribution = values.value_counts(normalize=True).head(resolved_options.categorical_top_k)
        profile["kind"] = "categorical"
        profile["top_values"] = [
            {"value": str(value), "ratio": round(float(ratio), 4)} for value, ratio in distribution.items()
        ]
        return profile

    def build_profile_line(self, column_name: str, column_type: str, profile: Dict[str, Any]) -> str:
        if not profile or not profile.get("available", False):
            return f"- {column_name} ({column_type}): no observed values."

        missing_ratio = profile.get("missing_ratio", 0.0)
        if profile.get("kind") == "numeric":
            return (
                f"- {column_name} ({column_type}): min={profile.get('min')}, max={profile.get('max')}, "
                f"mean={profile.get('mean')}, std={profile.get('std')}, missing_ratio={missing_ratio}"
            )
        if profile.get("kind") == "text":
            snippets = profile.get("example_snippets", [])
            if snippets:
                snippet_preview = " | ".join(snippets[:2])
                return (
                    f"- {column_name} ({column_type}): missing_ratio={missing_ratio}, "
                    f"text_examples={snippet_preview}"
                )
            return f"- {column_name} ({column_type}): missing_ratio={missing_ratio}"

        top_values = profile.get("top_values", [])
        values_repr = ", ".join(
            f"{entry.get('value')} ({entry.get('ratio')})"
            for entry in top_values
            if isinstance(entry, dict)
        )
        return f"- {column_name} ({column_type}): frequent_values=[{values_repr}], missing_ratio={missing_ratio}"

    def build_prompt_profile_line(self, column_config: Dict[str, Any], profile: Dict[str, Any]) -> str:
        column_name = str(column_config.get("name", ""))
        column_type = str(column_config.get("type", "STRING")).upper()
        if column_type != "DATE":
            return self.build_profile_line(column_name, column_type, profile)
        if not profile or not profile.get("available", False):
            return f"- {column_name} ({column_type}): no observed values."
        if profile.get("kind") != "numeric":
            return self.build_profile_line(column_name, column_type, profile)

        date_format = get_date_format(column_config)
        min_value = self._format_profile_date_stat(profile.get("min"), date_format)
        max_value = self._format_profile_date_stat(profile.get("max"), date_format)
        mean_value = self._format_profile_date_stat(profile.get("mean"), date_format)
        std_days = self._format_profile_date_std_days(profile.get("std"))
        missing_ratio = profile.get("missing_ratio", 0.0)
        return (
            f"- {column_name} ({column_type}): min={min_value}, max={max_value}, "
            f"mean={mean_value}, std_days={std_days}, missing_ratio={missing_ratio}"
        )

    @staticmethod
    def parse_json_with_fallback(text: str) -> Any:
        try:
            return json.loads(text)
        except JSONDecodeError:
            decoder = json.JSONDecoder()
            for index, char in enumerate(text):
                if char not in ("{", "["):
                    continue
                try:
                    parsed, _ = decoder.raw_decode(text[index:])
                    return parsed
                except JSONDecodeError:
                    continue
            raise ValueError("The LLM did not return valid JSON content.")

    @staticmethod
    def rows_from_json(parsed_json: Any, row_key: str = "rows") -> List[Dict[str, Any]]:
        if isinstance(parsed_json, list):
            return [row for row in parsed_json if isinstance(row, dict)]

        if isinstance(parsed_json, dict):
            rows = parsed_json.get(row_key)
            if isinstance(rows, list):
                return [row for row in rows if isinstance(row, dict)]

            for value in parsed_json.values():
                if isinstance(value, list):
                    return [row for row in value if isinstance(row, dict)]

        return []

    def coerce_text(self, value: Any, fallback_value: Any = None) -> str:
        candidate = fallback_value if value is None else value
        if candidate is None:
            return MISSING_VALUE_STRING

        text = str(candidate).strip()
        if not text or text.lower() in self._NULL_LIKE_STRINGS:
            return MISSING_VALUE_STRING
        return text

    def coerce_string(self, value: Any, fallback_value: Any = None) -> str:
        candidate = fallback_value if value is None else value
        if candidate is None:
            return MISSING_VALUE_STRING

        text = str(candidate).strip()
        if not text or text.lower() in self._NULL_LIKE_STRINGS:
            return MISSING_VALUE_STRING
        return text

    def coerce_boolean(self, value: Any, fallback_value: Any = None) -> Any:
        for candidate in (value, fallback_value):
            if isinstance(candidate, bool):
                return candidate
            if isinstance(candidate, (dict, list, tuple, set)):
                continue
            if candidate is None or (isinstance(candidate, float) and math.isnan(candidate)):
                continue

            try:
                if candidate in BOOLEAN_MAP:
                    return bool(BOOLEAN_MAP[candidate])
            except TypeError:
                continue

            text = str(candidate).strip()
            if text in BOOLEAN_MAP:
                return bool(BOOLEAN_MAP[text])
            lower = text.lower()
            if lower in BOOLEAN_MAP:
                return bool(BOOLEAN_MAP[lower])

        return MISSING_BOOLEAN

    def coerce_numeric(
        self,
        column_name: str,
        column_type: str,
        value: Any,
        column_profiles: Dict[str, Dict[str, Any]],
        *,
        fallback_value: Any = None,
    ) -> Any:
        numeric = self.to_float(value)
        if numeric is None:
            numeric = self.to_float(fallback_value)
        if numeric is None:
            numeric = self.default_numeric_value(column_name, column_profiles)

        profile = column_profiles.get(column_name, {})
        if profile.get("kind") == "numeric" and profile.get("available"):
            min_value = profile.get("min")
            max_value = profile.get("max")
            if isinstance(min_value, (int, float)):
                numeric = max(numeric, float(min_value))
            if isinstance(max_value, (int, float)):
                numeric = min(numeric, float(max_value))

        if column_type in {"INTEGER", "DATE"}:
            return int(round(numeric))
        return float(numeric)

    @staticmethod
    def default_numeric_value(column_name: str, column_profiles: Dict[str, Dict[str, Any]]) -> float:
        profile = column_profiles.get(column_name, {})
        mean = profile.get("mean")
        if isinstance(mean, (int, float)):
            return float(mean)
        return 0.0

    @staticmethod
    def to_float(value: Any) -> Optional[float]:
        if isinstance(value, bool):
            return float(int(value))

        if isinstance(value, (int, float)):
            if isinstance(value, float) and math.isnan(value):
                return None
            return float(value)

        if value is None:
            return None

        try:
            normalized = str(value).strip().replace(",", ".")
            if not normalized or normalized.lower() in LlmSynthesizerSupport._NULL_LIKE_STRINGS:
                return None
            return float(normalized)
        except (TypeError, ValueError):
            return None

    @staticmethod
    def serialize_value(value: Any) -> Any:
        if isinstance(value, bool):
            return value
        if isinstance(value, (int, float, str)) or value is None:
            if isinstance(value, float) and math.isnan(value):
                return None
            return value
        if pd.isna(value):
            return None
        return str(value)

    def serialize_row_values(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {key: self.serialize_value(value) for key, value in row.items()}

    def serialize_row_for_prompt(
        self,
        row: Dict[str, Any],
        column_configs: Sequence[Dict[str, Any]],
    ) -> Dict[str, Any]:
        serialized = self.serialize_row_values(row)
        date_formats = self._date_format_by_column(column_configs)
        if not date_formats:
            return serialized

        prompt_row = dict(serialized)
        for column_name, date_format in date_formats.items():
            if column_name not in prompt_row:
                continue
            prompt_row[column_name] = self._format_date_for_prompt(prompt_row[column_name], date_format)
        return prompt_row

    def coerce_date(
        self,
        column_name: str,
        value: Any,
        column_profiles: Dict[str, Dict[str, Any]],
        *,
        fallback_value: Any = None,
        column_config: Optional[Dict[str, Any]] = None,
    ) -> Any:
        numeric_value = self.coerce_numeric(
            column_name,
            "DATE",
            value,
            column_profiles,
            fallback_value=fallback_value,
        )
        if self.to_float(value) is not None or column_config is None:
            return numeric_value

        parsed_value = self._parse_date_to_unix(value, column_config)
        parsed_fallback = self._parse_date_to_unix(fallback_value, column_config)
        if parsed_value is None and parsed_fallback is None:
            return numeric_value

        return self.coerce_numeric(
            column_name,
            "DATE",
            parsed_value,
            column_profiles,
            fallback_value=parsed_fallback,
        )

    @staticmethod
    def _date_format_by_column(column_configs: Sequence[Dict[str, Any]]) -> Dict[str, str]:
        formats: Dict[str, str] = {}
        for column_config in column_configs:
            column_name = column_config.get("name")
            column_type = str(column_config.get("type", "STRING")).upper()
            if not isinstance(column_name, str) or column_type != "DATE":
                continue
            formats[column_name] = get_date_format(column_config)
        return formats

    def _format_date_for_prompt(self, value: Any, date_format: str) -> Any:
        numeric_value = self.to_float(value)
        if numeric_value is None:
            return self.serialize_value(value)
        return parse_to_date_format(numeric_value, date_format)

    @staticmethod
    def _parse_date_to_unix(value: Any, column_config: Dict[str, Any]) -> Optional[int]:
        if value is None:
            return None
        if isinstance(value, float) and math.isnan(value):
            return None

        normalized = str(value).strip()
        if not normalized or normalized.lower() in LlmSynthesizerSupport._NULL_LIKE_STRINGS:
            return None

        parsed = parse_to_unix(normalized, get_date_format(column_config))
        if pd.isna(parsed):
            return None
        return int(parsed)

    def _format_profile_date_stat(self, value: Any, date_format: str) -> Any:
        numeric_value = self.to_float(value)
        if numeric_value is None:
            return value
        return parse_to_date_format(numeric_value, date_format)

    @staticmethod
    def _format_profile_date_std_days(value: Any) -> Any:
        numeric_value = LlmSynthesizerSupport.to_float(value)
        if numeric_value is None:
            return value
        return round(numeric_value / 86400.0, 2)

    def report_remaining_time(self, sample_start_time: Optional[float], generated: int, total: int) -> None:
        if sample_start_time is None:
            return

        elapsed = max(time.time() - sample_start_time, 1e-6)
        remaining = max(total - generated, 0)
        if remaining == 0:
            self._report_remaining_time("sampling", 0)
            return

        rows_per_second = generated / elapsed
        if rows_per_second <= 0:
            self._report_remaining_time("sampling", None)
            return

        remaining_seconds = int(math.ceil(remaining / rows_per_second))
        self._report_remaining_time("sampling", remaining_seconds)
