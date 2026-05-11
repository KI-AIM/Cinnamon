import cloudpickle
import json
import math
import time
from json import JSONDecodeError
from typing import Any, Dict, List, Optional

import pandas as pd

from data_processing.utils import BOOLEAN_MAP, MISSING_BOOLEAN, MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm import (
    LlmClient,
    LlmClientConfig,
    create_llm_client,
    load_llm_client_config,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class LlmFewShotTextSynthesisSynthesizer(TabularDataSynthesizer):
    """
    LLM-based synthesizer that enriches synthetic tabular rows by generating TEXT values.
    Structured values are preserved by default but may be adjusted by the LLM when needed.
    """

    NUMERIC_TYPES = {"INTEGER", "DECIMAL", "DATE"}

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self.reference_dataset: Optional[pd.DataFrame] = None
        self._llm_config: Optional[LlmClientConfig] = None
        self._llm_client: Optional[LlmClient] = None
        self._sampling: Optional[Dict[str, Any]] = None
        self._fitting_kwargs: Optional[Dict[str, Any]] = None
        self.synthesizer = None

        self._ordered_column_configs: List[Dict[str, Any]] = []
        self._text_columns: List[str] = []
        self._column_profiles: Dict[str, Dict[str, Any]] = {}
        self._few_shot_source_df: Optional[pd.DataFrame] = None
        self._few_shot_examples: List[Dict[str, Any]] = []
        self._similarity_strategy: str = "random"
        self._user_prompt_domain_context: str = ""
        self._sample_start_time: Optional[float] = None
        self._allow_structured_corrections: bool = True

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        model_params = algorithm_config.get("model_parameter", {})
        training_params = algorithm_config.get("model_fitting", {})
        self._llm_config = load_llm_client_config(config)
        profile_rows = model_params.get("profile_rows", training_params.get("profile_rows", 1000))
        few_shot_rows = model_params.get("few_shot_rows", training_params.get("few_shot_rows", 20))
        self._fitting_kwargs = {
            "profile_rows": max(1, int(profile_rows)),
            "few_shot_rows": max(0, int(few_shot_rows)),
            "max_retries": self._llm_config.max_retries,
        }
        self._similarity_strategy = str(model_params.get("similarity_strategy", "random")).strip().lower() or "random"
        self._sampling = algorithm_config.get("sampling", {})
        self._allow_structured_corrections = self._parse_bool_like(
            training_params.get("allow_structured_corrections", True)
        )
        self._user_prompt_domain_context = str(training_params.get("user_prompt_domain_context", "")).strip()

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        configurations = attribute_config.get("configurations", [])
        if not configurations:
            raise ValueError("Attribute configuration is empty.")

        self.attribute_config = attribute_config
        self._ordered_column_configs = sorted(configurations, key=lambda cfg: cfg.get("index", math.inf))
        self._text_columns = [
            cfg["name"] for cfg in self._ordered_column_configs if str(cfg.get("type", "STRING")).upper() == "TEXT"
        ]

        if not self._text_columns:
            raise ValueError("No TEXT columns found in attribute configuration.")

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        self.dataset = df.copy()

    def initialize_reference_dataset(self, df: pd.DataFrame) -> None:
        self.reference_dataset = df.copy()

    def _initialize_synthesizer(self) -> None:
        if self._llm_config is None:
            raise ValueError("Anonymization configuration is not initialized.")
        self._llm_client = create_llm_client(self._llm_config)
        self._llm_client.initialize()
        self.synthesizer = {
            "backend": self._llm_config.provider,
            "model_name": self._llm_config.model_name,
            "mode": "text_synthesis",
        }

    def _fit(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self.reference_dataset is None:
            raise ValueError("Reference dataset is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")

        profile_rows = self._fitting_kwargs["profile_rows"]
        profile_df = self.reference_dataset
        if len(profile_df) > profile_rows:
            profile_df = profile_df.sample(n=profile_rows).reset_index(drop=True)
        else:
            profile_df = profile_df.copy()

        self._column_profiles = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            self._column_profiles[column_name] = self._build_column_profile(profile_df, column_name, column_type)

        few_shot_rows = self._fitting_kwargs["few_shot_rows"]
        if few_shot_rows > 0 and not profile_df.empty:
            self._few_shot_source_df = profile_df.copy()
            n_examples = min(few_shot_rows, len(profile_df))
            sampled = profile_df.sample(n=n_examples).to_dict(orient="records")
            self._few_shot_examples = [self._serialize_row_values(row) for row in sampled]
        else:
            self._few_shot_source_df = None
            self._few_shot_examples = []

    def _sample(self) -> pd.DataFrame:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")

        source = self.dataset.copy().reset_index(drop=True)
        num_samples = self._sampling.get("num_samples")
        if isinstance(num_samples, int):
            if num_samples <= 0:
                raise ValueError("num_samples must be greater than 0.")
            if num_samples > len(source):
                raise ValueError("num_samples cannot exceed the number of synthetic tabular input rows.")
            source = source.head(num_samples)

        rows = source.to_dict(orient="records")
        total = len(rows)
        self._sample_start_time = time.time()

        generated_rows: List[Dict[str, Any]] = []
        for row_index, row in enumerate(rows):
            generated_rows.append(self._generate_row(row, row_index, total))
            self._print_remaining_time(len(generated_rows), total)

        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        generated = pd.DataFrame(generated_rows)
        for column_name in ordered_columns:
            if column_name not in generated.columns:
                generated[column_name] = pd.NA
        return generated[ordered_columns]

    def _generate_row(self, base_row: Dict[str, Any], row_index: int, total_rows: int) -> Dict[str, Any]:
        max_retries = self._fitting_kwargs["max_retries"]
        last_error: Optional[Exception] = None

        for attempt in range(max_retries):
            try:
                prompt = self._build_prompt(base_row)
                content = self._llm_client.generate_text(prompt)
                parsed = self._parse_json_with_fallback(content)
                candidate = self._extract_row_from_response(parsed)
                if candidate is None:
                    raise ValueError("No row object found in LLM response.")

                merged = self._merge_candidate_row(base_row, candidate)
                coerced = self._coerce_row(merged, base_row)
                print(
                    "[LLM TEXT SYNTH] "
                    f"row={row_index + 1}/{total_rows} "
                    f"attempt={attempt + 1}/{max_retries} "
                    "accepted=1",
                    flush=True,
                )
                return coerced
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                print(
                    "[LLM TEXT SYNTH] "
                    f"row={row_index + 1}/{total_rows} "
                    f"attempt={attempt + 1}/{max_retries} "
                    f"error={type(exc).__name__}: {exc}",
                    flush=True,
                )

        fallback = self._coerce_base_row(base_row)
        if last_error is not None:
            print(f"[LLM TEXT SYNTH] fallback used due to: {last_error}", flush=True)
        return fallback

    def _merge_candidate_row(self, base_row: Dict[str, Any], candidate_row: Dict[str, Any]) -> Dict[str, Any]:
        merged: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            base_value = base_row.get(column_name)
            candidate_value = candidate_row.get(column_name, base_value)

            if column_type == "TEXT":
                merged[column_name] = candidate_value
                continue

            if self._allow_structured_corrections and column_name in candidate_row:
                merged[column_name] = candidate_value
            else:
                merged[column_name] = base_value

        return merged

    def _build_prompt(self, base_row: Dict[str, Any]) -> str:
        column_order = [cfg["name"] for cfg in self._ordered_column_configs]
        profile_lines = []
        for config in self._ordered_column_configs:
            name = config["name"]
            col_type = str(config.get("type", "STRING")).upper()
            profile_lines.append(self._profile_line(name, col_type, self._column_profiles.get(name, {})))

        domain_context = ""
        if self._user_prompt_domain_context:
            domain_context = f"Domain context: {self._user_prompt_domain_context}\n"

        text_columns = ", ".join(self._text_columns)
        return (
            "You enrich one synthetic tabular row.\n"
            f"{domain_context}"
            "Primary objective:\n"
            f"- Generate realistic values for TEXT columns: {text_columns}\n"
            "- Keep non-TEXT values unchanged unless they are clearly implausible in combination.\n"
            "- If you correct non-TEXT fields, use minimal changes and stay close to the original synthetic row.\n"
            "Output rules:\n"
            "- Return ONLY valid JSON.\n"
            "- Use exactly this shape: {\"row\": { ... }}\n"
            f"- Include all columns exactly in this list: {column_order}\n"
            f"- For missing strings/text use '{MISSING_VALUE_STRING}'\n"
            "- BOOLEAN values must be true/false.\n"
            "- DATE values must be UNIX timestamps in seconds.\n"
            "Column profiles derived from original data:\n"
            f"{chr(10).join(profile_lines)}\n"
            f"{self._build_few_shot_block(base_row)}"
            "Current synthetic row:\n"
            f"{json.dumps({'row': self._serialize_row_values(base_row)}, ensure_ascii=True)}"
        )

    def _build_few_shot_block(self, base_row: Dict[str, Any]) -> str:
        examples = self._draw_few_shot_examples(base_row)
        if not examples:
            return ""

        return (
            "Reference rows from original data (learn semantics and writing style, never copy):\n"
            f"{json.dumps({'rows': examples}, ensure_ascii=True)}\n"
        )

    def _draw_few_shot_examples(self, base_row: Dict[str, Any]) -> List[Dict[str, Any]]:
        del base_row
        if self._similarity_strategy != "random":
            print(
                "[LLM TEXT SYNTH] "
                f"similarity_strategy='{self._similarity_strategy}' is not implemented yet; "
                "falling back to random few-shot selection.",
                flush=True,
            )
        return list(self._few_shot_examples)

    def _build_column_profile(self, df: pd.DataFrame, column_name: str, column_type: str) -> Dict[str, Any]:
        if column_name not in df.columns:
            return {"type": column_type, "available": False}

        series = df[column_name]
        missing_ratio = float(series.isna().mean()) if len(series) else 1.0
        profile: Dict[str, Any] = {"type": column_type, "available": True, "missing_ratio": round(missing_ratio, 4)}

        if column_type in self.NUMERIC_TYPES:
            numeric = pd.to_numeric(series, errors="coerce").dropna()
            if numeric.empty:
                profile["available"] = False
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
            values = values[(values != "") & (values != MISSING_VALUE_STRING)]
            profile["kind"] = "text"
            profile["example_snippets"] = values.head(3).tolist()
            if values.empty:
                profile["available"] = False
            return profile

        values = series.dropna().astype(str)
        if values.empty:
            profile["available"] = False
            return profile
        profile["kind"] = "categorical"
        distribution = values.value_counts(normalize=True).head(10)
        profile["top_values"] = [
            {"value": str(value), "ratio": round(float(ratio), 4)} for value, ratio in distribution.items()
        ]
        return profile

    def _profile_line(self, column_name: str, column_type: str, profile: Dict[str, Any]) -> str:
        if not profile or not profile.get("available", False):
            return f"- {column_name} ({column_type}): no observed reference values."

        missing_ratio = profile.get("missing_ratio", 0.0)
        if profile.get("kind") == "numeric":
            return (
                f"- {column_name} ({column_type}): "
                f"min={profile.get('min')}, max={profile.get('max')}, mean={profile.get('mean')}, "
                f"std={profile.get('std')}, missing_ratio={missing_ratio}"
            )
        if profile.get("kind") == "text":
            snippets = profile.get("example_snippets", [])
            snippet_preview = " | ".join(snippets[:2]) if snippets else "n/a"
            return f"- {column_name} ({column_type}): missing_ratio={missing_ratio}, text_examples={snippet_preview}"

        top_values = profile.get("top_values", [])
        values_repr = ", ".join(
            f"{entry.get('value')} ({entry.get('ratio')})"
            for entry in top_values
            if isinstance(entry, dict)
        )
        return f"- {column_name} ({column_type}): frequent_values=[{values_repr}], missing_ratio={missing_ratio}"

    @staticmethod
    def _parse_bool_like(value: Any) -> bool:
        if isinstance(value, bool):
            return value
        if isinstance(value, (int, float)):
            return value != 0
        if isinstance(value, str):
            normalized = value.strip().lower()
            if normalized in {"1", "true", "yes", "y", "on"}:
                return True
            if normalized in {"0", "false", "no", "n", "off"}:
                return False
        return bool(value)

    def _parse_json_with_fallback(self, text: str) -> Any:
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

    def _extract_row_from_response(self, parsed_json: Any) -> Optional[Dict[str, Any]]:
        if isinstance(parsed_json, dict):
            row = parsed_json.get("row")
            if isinstance(row, dict):
                return row
            rows = parsed_json.get("rows")
            if isinstance(rows, list) and rows and isinstance(rows[0], dict):
                return rows[0]
        if isinstance(parsed_json, list) and parsed_json and isinstance(parsed_json[0], dict):
            return parsed_json[0]
        return None

    def _coerce_row(self, row: Dict[str, Any], base_row: Dict[str, Any]) -> Dict[str, Any]:
        coerced: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            value = row.get(column_name)
            base_value = base_row.get(column_name)
            coerced[column_name] = self._coerce_value(column_name, column_type, value, base_value)
        return coerced

    def _coerce_base_row(self, base_row: Dict[str, Any]) -> Dict[str, Any]:
        coerced: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            base_value = base_row.get(column_name)

            if column_type == "TEXT":
                coerced[column_name] = self._coerce_text(base_value)
                continue
            if column_type == "BOOLEAN":
                coerced[column_name] = self._coerce_boolean(base_value, base_value)
                continue
            if column_type in self.NUMERIC_TYPES:
                numeric = self._to_float(base_value)
                if numeric is None:
                    numeric = self._default_numeric_value(column_name)
                coerced[column_name] = int(round(numeric)) if column_type in {"INTEGER", "DATE"} else float(numeric)
                continue

            coerced[column_name] = self._coerce_string(base_value, base_value)

        return coerced

    def _coerce_value(self, column_name: str, column_type: str, value: Any, base_value: Any) -> Any:
        if column_type == "BOOLEAN":
            return self._coerce_boolean(value, base_value)
        if column_type in self.NUMERIC_TYPES:
            return self._coerce_numeric(column_name, column_type, value, base_value)
        if column_type == "TEXT":
            return self._coerce_text(value)
        return self._coerce_string(value, base_value)

    def _coerce_text(self, value: Any) -> str:
        if value is None:
            return MISSING_VALUE_STRING
        text = str(value).strip()
        if not text or text.lower() in {"nan", "null", "none", "<na>"}:
            return MISSING_VALUE_STRING
        return text

    def _coerce_string(self, value: Any, base_value: Any) -> str:
        if value is None:
            return self._coerce_text(base_value)
        text = str(value).strip()
        if not text or text.lower() in {"nan", "null", "none", "<na>"}:
            return self._coerce_text(base_value)
        return text

    def _coerce_boolean(self, value: Any, base_value: Any) -> bool:
        for candidate in (value, base_value):
            if isinstance(candidate, bool):
                return candidate
            try:
                if candidate in BOOLEAN_MAP:
                    return bool(BOOLEAN_MAP[candidate])
            except TypeError:
                continue
            if candidate is not None:
                text = str(candidate).strip()
                if text in BOOLEAN_MAP:
                    return bool(BOOLEAN_MAP[text])
                lower = text.lower()
                if lower in BOOLEAN_MAP:
                    return bool(BOOLEAN_MAP[lower])
        return MISSING_BOOLEAN

    def _coerce_numeric(self, column_name: str, column_type: str, value: Any, base_value: Any) -> Any:
        numeric = self._to_float(value)
        if numeric is None:
            numeric = self._to_float(base_value)
        if numeric is None:
            numeric = self._default_numeric_value(column_name)

        profile = self._column_profiles.get(column_name, {})
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

    def _default_numeric_value(self, column_name: str) -> float:
        profile = self._column_profiles.get(column_name, {})
        mean = profile.get("mean")
        if isinstance(mean, (int, float)):
            return float(mean)
        return 0.0

    @staticmethod
    def _to_float(value: Any) -> Optional[float]:
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
            if not normalized or normalized.lower() in {"nan", "null", "none", "<na>"}:
                return None
            return float(normalized)
        except (TypeError, ValueError):
            return None

    @staticmethod
    def _serialize_value(value: Any) -> Any:
        if isinstance(value, bool):
            return value
        if isinstance(value, (int, float, str)) or value is None:
            if isinstance(value, float) and math.isnan(value):
                return None
            return value
        if pd.isna(value):
            return None
        return str(value)

    def _serialize_row_values(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {key: self._serialize_value(value) for key, value in row.items()}

    def _print_remaining_time(self, generated: int, total: int) -> None:
        if self._sample_start_time is None:
            return
        elapsed = max(time.time() - self._sample_start_time, 1e-6)
        remaining = max(total - generated, 0)
        if remaining == 0:
            self._report_remaining_time("sampling", 0)
            print("Estimated remaining time: 0s", flush=True)
            return
        rows_per_second = generated / elapsed
        if rows_per_second <= 0:
            self._report_remaining_time("sampling", None)
            print("Estimated remaining time: unknown", flush=True)
            return
        remaining_seconds = int(math.ceil(remaining / rows_per_second))
        self._report_remaining_time("sampling", remaining_seconds)
        print(f"Estimated remaining time: {remaining_seconds}s", flush=True)

    def _get_model(self) -> bytes:
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "LlmFewShotTextSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmFewShotTextSynthesisSynthesizer" = cloudpickle.load(file)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        sample.to_csv(filename, index=False)
