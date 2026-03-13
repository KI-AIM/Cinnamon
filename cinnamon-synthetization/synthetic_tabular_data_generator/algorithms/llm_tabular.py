import cloudpickle
import json
import math
import re
import time
from json import JSONDecodeError
from typing import Any, Dict, List, Optional, Sequence, Tuple

import pandas as pd

from data_processing.utils import BOOLEAN_MAP, MISSING_BOOLEAN, MISSING_VALUE_STRING, TEXT_PENDING_LLM
from synthetic_tabular_data_generator.llm import (
    LlmClient,
    LlmClientConfig,
    create_llm_client,
    load_llm_client_config,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class LlmTabularSynthesizer(TabularDataSynthesizer):
    """
    LLM-based tabular synthesizer backed by a configurable LLM provider.
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
        self._llm_config: Optional[LlmClientConfig] = None
        self._llm_client: Optional[LlmClient] = None
        self._fitting_kwargs: Optional[Dict[str, Any]] = None
        self._sampling: Optional[Dict[str, Any]] = None
        self.synthesizer = None

        self._ordered_column_configs: List[Dict[str, Any]] = []
        self._column_profiles: Dict[str, Dict[str, Any]] = {}
        self._few_shot_examples: List[Dict[str, Any]] = []
        self._sample_start_time: Optional[float] = None

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """
        Core logic for initializing anonymization configuration.
        """
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        training_params = algorithm_config["model_fitting"]
        self._llm_config = load_llm_client_config(config)
        self._fitting_kwargs = {
            "profile_rows": max(1, int(training_params.get("profile_rows", 1000))),
            "few_shot_rows": max(0, int(training_params.get("few_shot_rows", 20))),
            "max_retries": self._llm_config.max_retries,
            "timeout_seconds": self._llm_config.timeout_seconds,
        }
        self._sampling = algorithm_config["sampling"]

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        """
        Core logic for initializing attribute configuration.
        """
        configurations = attribute_config.get("configurations", [])
        if not configurations:
            raise ValueError("Attribute configuration is empty.")

        self.attribute_config = attribute_config
        self._ordered_column_configs = sorted(configurations, key=lambda cfg: cfg.get("index", math.inf))

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        """
        Core logic for initializing the dataset.
        """
        self.dataset = df.copy()

    def _initialize_synthesizer(self) -> None:
        """
        Core logic for initializing the synthesizer.
        """
        if self._llm_config is None or self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")

        self._llm_client = create_llm_client(self._llm_config)
        self._llm_client.initialize()
        self.synthesizer = {
            "backend": self._llm_config.provider,
            "model_name": self._llm_config.model_name,
        }

    def _fit(self) -> None:
        """
        Build schema and value profiles that are used in the LLM prompts.
        """
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if not self._ordered_column_configs:
            raise ValueError("Attribute configuration is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")

        profile_rows = self._fitting_kwargs["profile_rows"]
        if len(self.dataset) > profile_rows:
            profile_df = self.dataset.sample(n=profile_rows).reset_index(drop=True)
        else:
            profile_df = self.dataset.copy()

        self._column_profiles = {}
        for column_config in self._ordered_column_configs:
            column_name = column_config["name"]
            column_type = str(column_config.get("type", "STRING")).upper()
            self._column_profiles[column_name] = self._build_column_profile(profile_df, column_name, column_type)

        few_shot_rows = self._fitting_kwargs["few_shot_rows"]
        if few_shot_rows > 0 and not profile_df.empty:
            n_examples = min(few_shot_rows, len(profile_df))
            examples = profile_df.sample(n=n_examples).to_dict(orient="records")
            self._few_shot_examples = [self._serialize_row_values(example) for example in examples]
        else:
            self._few_shot_examples = []

    def _sample(self) -> pd.DataFrame:
        """
        Generate synthetic tabular data via the configured LLM in exactly one batch.
        """
        if self._sampling is None:
            raise ValueError("Sampling configuration is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        if self._llm_config is None:
            raise ValueError("Model configuration is not initialized.")

        num_samples = int(self._sampling["num_samples"])
        if num_samples <= 0:
            raise ValueError("num_samples must be greater than 0.")

        max_retries = self._fitting_kwargs["max_retries"]
        self._sample_start_time = time.time()

        generated_rows = self._generate_batch_rows(num_samples, max_retries)
        self._print_remaining_time(len(generated_rows), num_samples)

        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        generated = pd.DataFrame(generated_rows)
        for column_name in ordered_columns:
            if column_name not in generated.columns:
                generated[column_name] = pd.NA

        return generated[ordered_columns]

    def _get_model(self) -> bytes:
        """
        Core logic for serializing the model object.
        """
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "LlmTabularSynthesizer":
        """
        Core logic for loading a serialized synthesizer instance from a file.
        """
        with open(filepath, "rb") as f:
            model: "LlmTabularSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """
        Core logic for saving a data sample to a CSV file.
        """
        sample.to_csv(filename, index=False)

    def _build_column_profile(self, df: pd.DataFrame, column_name: str, column_type: str) -> Dict[str, Any]:
        if column_name not in df.columns:
            return {"type": column_type, "available": False, "reason": "column_missing"}

        column_series = df[column_name]
        missing_ratio = float(column_series.isna().mean()) if len(column_series) else 1.0
        profile: Dict[str, Any] = {
            "type": column_type,
            "available": True,
            "missing_ratio": round(missing_ratio, 4),
        }

        if column_type in self.NUMERIC_TYPES:
            numeric = pd.to_numeric(column_series, errors="coerce").dropna()
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

        values = column_series.dropna().astype(str)
        if values.empty:
            profile["available"] = False
            profile["reason"] = "no_categorical_values"
            return profile

        value_distribution = values.value_counts(normalize=True).head(15)
        profile["kind"] = "categorical"
        profile["top_values"] = [
            {"value": str(value), "ratio": round(float(ratio), 4)} for value, ratio in value_distribution.items()
        ]
        return profile

    def _generate_batch_rows(
        self,
        target_rows: int,
        max_retries: int,
    ) -> List[Dict[str, Any]]:
        accepted_rows: List[Dict[str, Any]] = []
        last_error: Optional[Exception] = None

        # Weak/local models often return only 1-2 valid rows per call even when asked for more.
        # Use retries per missing row instead of a flat retry cap per batch.
        max_attempts = max_retries * max(1, target_rows)

        for attempt_index in range(max_attempts):
            remaining = target_rows - len(accepted_rows)
            if remaining <= 0:
                break

            accepted_before_attempt = len(accepted_rows)
            non_dict_rows = 0
            unusable_rows = 0
            coercion_errors = 0

            try:
                content = self._request_rows_from_llm(remaining)
                raw_rows = self._extract_rows(content)
                self._print_llm_raw_output(attempt_index + 1, max_attempts, remaining, content)

                for row in raw_rows:
                    if not isinstance(row, dict):
                        non_dict_rows += 1
                        continue

                    aligned_row, used_positional_mapping = self._align_row_to_schema(row)
                    if not self._is_row_usable(row, aligned_row, used_positional_mapping):
                        unusable_rows += 1
                        continue

                    try:
                        coerced_row = self._coerce_row(aligned_row)
                        accepted_rows.append(coerced_row)
                        if len(accepted_rows) >= target_rows:
                            break
                    except Exception:  # noqa: BLE001
                        # Skip malformed rows and continue processing remaining candidates.
                        coercion_errors += 1
                        continue

                accepted_in_attempt = len(accepted_rows) - accepted_before_attempt
                print(
                    "[LLM DEBUG] "
                    f"attempt={attempt_index + 1}/{max_attempts} "
                    f"requested={remaining} "
                    f"raw_rows={len(raw_rows)} "
                    f"accepted={accepted_in_attempt} "
                    f"non_dict={non_dict_rows} "
                    f"unusable={unusable_rows} "
                    f"coercion_errors={coercion_errors}"
                )
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                print(
                    "[LLM DEBUG] "
                    f"attempt={attempt_index + 1}/{max_attempts} "
                    f"requested={remaining} "
                    f"error={type(exc).__name__}: {exc}"
                )

            if len(accepted_rows) >= target_rows:
                break

        if len(accepted_rows) < target_rows:
            message = (
                f"LLM returned too few valid rows ({len(accepted_rows)}/{target_rows}) "
                f"after {max_attempts} attempts."
            )
            if last_error is not None:
                raise RuntimeError(message) from last_error
            raise RuntimeError(message)

        return accepted_rows[:target_rows]

    def _request_rows_from_llm(self, num_rows: int) -> str:
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        prompt = self._build_generation_prompt(num_rows)
        return self._llm_client.generate_text(prompt)

    @staticmethod
    def _print_llm_raw_output(attempt: int, max_retries: int, requested_rows: int, content: str) -> None:
        max_chars = 8000
        text = content if len(content) <= max_chars else f"{content[:max_chars]}...<truncated>"
        print(
            "[LLM DEBUG] "
            f"attempt={attempt}/{max_retries} "
            f"requested={requested_rows} "
            f"raw_response={text}"
        )

    def _build_generation_prompt(self, num_rows: int) -> str:
        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        column_descriptions = []

        for config in self._ordered_column_configs:
            name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            profile = self._column_profiles.get(name, {})
            column_descriptions.append(self._profile_line(name, column_type, profile))

        profile_lines = "\n".join(column_descriptions)
        few_shot_block = ""
        if self._few_shot_examples:
            few_shot_json = json.dumps(self._few_shot_examples, ensure_ascii=True)
            few_shot_block = (
                "\nReference examples (learn structure only, do not copy rows):\n"
                f"{few_shot_json}"
            )

        shape_example = {column_name: "<value>" for column_name in ordered_columns}
        shape_text = json.dumps({"rows": [shape_example]}, ensure_ascii=True)

        return (
            "You are generating synthetic tabular rows.\n"
            f"Generate exactly {num_rows} rows.\n"
            "Return ONLY valid JSON with this exact shape:\n"
            f"{shape_text}\n"
            "Use one top-level key only: rows.\n"
            "No markdown, no comments, no code fences, no extra keys.\n"
            f"Use exactly these columns: {ordered_columns}\n"
            "Never use generic column names like column_a, column_b, feature_1, field_1.\n"
            "Type rules:\n"
            "- INTEGER: integer number\n"
            "- DECIMAL: decimal number\n"
            "- DATE: UNIX timestamp in seconds as number\n"
            "- BOOLEAN: true or false\n"
            f"- STRING: plain text, use '{MISSING_VALUE_STRING}' for missing\n"
            f"- TEXT: plain text, use '{TEXT_PENDING_LLM}' if needed\n"
            "Column profiles:\n"
            f"{profile_lines}"
            f"{few_shot_block}\n"
            "Model realistic relationships between columns based on the profiles."
        )

    def _profile_line(self, column_name: str, column_type: str, profile: Dict[str, Any]) -> str:
        if not profile or not profile.get("available", False):
            return f"- {column_name} ({column_type}): no observed training values."

        missing_ratio = profile.get("missing_ratio", 0.0)
        if profile.get("kind") == "numeric":
            return (
                f"- {column_name} ({column_type}): min={profile.get('min')}, max={profile.get('max')}, "
                f"mean={profile.get('mean')}, std={profile.get('std')}, missing_ratio={missing_ratio}"
            )

        top_values = profile.get("top_values", [])
        values_repr = ", ".join(
            f"{entry.get('value')} ({entry.get('ratio')})"
            for entry in top_values[:10]
            if isinstance(entry, dict)
        )
        return (
            f"- {column_name} ({column_type}): frequent values [{values_repr}], "
            f"missing_ratio={missing_ratio}"
        )

    def _extract_rows(self, response_content: str) -> List[Dict[str, Any]]:
        parsed_json = self._parse_json_with_fallback(response_content)
        rows = self._rows_from_json(parsed_json)
        if not rows:
            rows = self._extract_rows_from_repeated_rows_blocks(response_content)
        if not rows:
            raise ValueError("No rows were found in the LLM response.")
        return rows

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

    def _rows_from_json(self, parsed_json: Any) -> List[Dict[str, Any]]:
        if isinstance(parsed_json, list):
            return [row for row in parsed_json if isinstance(row, dict)]

        if isinstance(parsed_json, dict):
            if isinstance(parsed_json.get("rows"), list):
                return [row for row in parsed_json["rows"] if isinstance(row, dict)]

            for value in parsed_json.values():
                if isinstance(value, list):
                    return [row for row in value if isinstance(row, dict)]

        return []

    def _extract_rows_from_repeated_rows_blocks(self, content: str) -> List[Dict[str, Any]]:
        extracted_rows: List[Dict[str, Any]] = []

        for match in re.finditer(r'"rows"\s*:\s*\[', content):
            array_start = match.end() - 1
            array_end = self._find_matching_bracket(content, array_start)
            if array_end is None:
                continue

            candidate = content[array_start : array_end + 1]
            try:
                parsed = json.loads(candidate)
            except JSONDecodeError:
                continue

            if isinstance(parsed, list):
                extracted_rows.extend([row for row in parsed if isinstance(row, dict)])

        return extracted_rows

    @staticmethod
    def _find_matching_bracket(text: str, start_index: int) -> Optional[int]:
        depth = 0
        for index in range(start_index, len(text)):
            char = text[index]
            if char == "[":
                depth += 1
            elif char == "]":
                depth -= 1
                if depth == 0:
                    return index
        return None

    def _align_row_to_schema(self, row: Dict[str, Any]) -> Tuple[Dict[str, Any], bool]:
        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        if not ordered_columns:
            return row, False

        expected_matches = sum(1 for col in ordered_columns if col in row)
        if expected_matches > 0:
            return row, False

        row_keys = list(row.keys())
        positional_map = self._build_positional_key_map(row_keys)
        if positional_map:
            aligned = {}
            for idx, column_name in enumerate(ordered_columns):
                source_key = positional_map.get(idx)
                aligned[column_name] = row.get(source_key) if source_key is not None else None
            return aligned, True

        if len(row_keys) == len(ordered_columns):
            aligned_by_order = {}
            for column_name, value in zip(ordered_columns, row.values()):
                aligned_by_order[column_name] = value
            return aligned_by_order, True

        return row, False

    def _build_positional_key_map(self, row_keys: Sequence[Any]) -> Dict[int, str]:
        indexed_keys: List[Tuple[int, str]] = []
        for key in row_keys:
            if not isinstance(key, str):
                return {}
            position = self._extract_positional_index(key)
            if position is None:
                return {}
            indexed_keys.append((position, key))

        indexed_keys.sort(key=lambda item: item[0])
        return {idx: key for idx, (_, key) in enumerate(indexed_keys)}

    @staticmethod
    def _extract_positional_index(key: str) -> Optional[int]:
        lowered = key.lower().strip()

        numeric_match = re.match(r"^(?:column|col|feature|field|attribute)[_\-\s]?(\d+)$", lowered)
        if numeric_match:
            return int(numeric_match.group(1))

        alpha_match = re.match(r"^(?:column|col|feature|field|attribute)[_\-\s]?([a-z]+)$", lowered)
        if alpha_match:
            letters = alpha_match.group(1)
            value = 0
            for char in letters:
                value = value * 26 + (ord(char) - ord("a") + 1)
            return value

        return None

    def _is_row_usable(
        self,
        original_row: Dict[str, Any],
        aligned_row: Dict[str, Any],
        used_positional_mapping: bool,
    ) -> bool:
        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        if not ordered_columns:
            return False

        values = [aligned_row.get(col) for col in ordered_columns]
        if not any(value is not None for value in values):
            return False

        # Reject rows that mirror schema labels instead of real values, e.g. {"Age":"Age", ...}
        echoed_columns = 0
        for col, value in zip(ordered_columns, values):
            if isinstance(value, str) and value.strip().lower() == col.strip().lower():
                echoed_columns += 1
        if echoed_columns >= max(1, math.ceil(len(ordered_columns) * 0.5)):
            return False

        # Reject rows containing nested structures in expected fields.
        if any(isinstance(value, (dict, list, tuple, set)) for value in values if value is not None):
            return False

        if used_positional_mapping:
            return any(value is not None for value in aligned_row.values())

        expected_matches = sum(1 for col in ordered_columns if col in original_row)
        return expected_matches >= max(1, math.ceil(len(ordered_columns) * 0.5))

    def _coerce_row(self, row: Dict[str, Any]) -> Dict[str, Any]:
        coerced: Dict[str, Any] = {}

        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            value = row.get(column_name)
            coerced[column_name] = self._coerce_value(column_name, column_type, value)

        return coerced

    def _coerce_value(self, column_name: str, column_type: str, value: Any) -> Any:
        if column_type == "BOOLEAN":
            return self._coerce_boolean(value)

        if column_type in self.NUMERIC_TYPES:
            return self._coerce_numeric(column_name, column_type, value)

        if column_type == "TEXT":
            return TEXT_PENDING_LLM

        return self._coerce_string(value)

    def _coerce_boolean(self, value: Any) -> bool:
        if isinstance(value, bool):
            return value

        if isinstance(value, (dict, list, tuple, set)):
            return MISSING_BOOLEAN

        if value is None or (isinstance(value, float) and math.isnan(value)):
            return MISSING_BOOLEAN

        try:
            if value in BOOLEAN_MAP:
                return bool(BOOLEAN_MAP[value])
        except TypeError:
            return MISSING_BOOLEAN

        as_string = str(value).strip()
        if as_string in BOOLEAN_MAP:
            return bool(BOOLEAN_MAP[as_string])

        lower = as_string.lower()
        if lower in BOOLEAN_MAP:
            return bool(BOOLEAN_MAP[lower])

        return MISSING_BOOLEAN

    def _coerce_numeric(self, column_name: str, column_type: str, value: Any) -> Any:
        profile = self._column_profiles.get(column_name, {})

        numeric_value = self._to_float(value)
        if numeric_value is None:
            numeric_value = self._default_numeric_value(column_name, column_type)

        if profile.get("kind") == "numeric" and profile.get("available"):
            min_value = profile.get("min")
            max_value = profile.get("max")
            if isinstance(min_value, (int, float)):
                numeric_value = max(numeric_value, float(min_value))
            if isinstance(max_value, (int, float)):
                numeric_value = min(numeric_value, float(max_value))

        if column_type in {"INTEGER", "DATE"}:
            return int(round(numeric_value))

        return float(numeric_value)

    def _default_numeric_value(self, column_name: str, column_type: str) -> float:
        profile = self._column_profiles.get(column_name, {})
        if profile.get("kind") == "numeric" and profile.get("available"):
            mean = profile.get("mean")
            if isinstance(mean, (int, float)):
                return float(mean)

        return 0.0 if column_type == "DECIMAL" else 0.0

    def _coerce_string(self, value: Any) -> str:
        if value is None:
            return MISSING_VALUE_STRING

        as_string = str(value).strip()
        if not as_string or as_string.lower() in {"nan", "null", "none", "<na>"}:
            return MISSING_VALUE_STRING

        return as_string

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
            print("Estimated remaining time: 0s")
            return

        rows_per_second = generated / elapsed
        if rows_per_second <= 0:
            print("Estimated remaining time: unknown")
            return

        remaining_seconds = int(math.ceil(remaining / rows_per_second))
        print(f"Estimated remaining time: {remaining_seconds}s")
