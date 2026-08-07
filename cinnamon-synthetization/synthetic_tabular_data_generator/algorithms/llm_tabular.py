from __future__ import annotations

import cloudpickle
import json
import math
import re
from typing import Any, Dict, List, Optional, Sequence, Tuple

import pandas as pd

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm import ColumnProfileOptions, ConfiguredLlmSynthesizerBase
from synthetic_tabular_data_generator.llm.prompt_builders import build_tabular_non_text_generation_prompt_from_prefix
from synthetic_tabular_data_generator.llm.response_validation import require_non_empty_rows


class LlmTabularSynthesizer(ConfiguredLlmSynthesizerBase):
    """Generate structured-only synthetic rows from statistical column profiles."""

    NUMERIC_TYPES = {"INTEGER", "DECIMAL", "DATE"}

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self._ordered_column_configs: List[Dict[str, Any]] = []
        self._column_profiles: Dict[str, Dict[str, Any]] = {}
        self._requested_profile_rows: Optional[int] = None
        self._profile_rows_used = 0
        self._generation_prompt_prefix: Optional[str] = None

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        _, model_params, _ = self._initialize_common_llm_configuration(config)
        raw_profile_rows = model_params.get("profile_rows")
        if raw_profile_rows is None or (
            isinstance(raw_profile_rows, str) and raw_profile_rows.strip().startswith("$")
        ):
            self._requested_profile_rows = None
            return

        profile_rows = int(raw_profile_rows)
        if profile_rows <= 0:
            raise ValueError("profile_rows must be greater than 0.")
        self._requested_profile_rows = profile_rows

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        configurations = attribute_config.get("configurations", [])
        if not configurations:
            raise ValueError("Attribute configuration is empty.")

        ordered_configs = sorted(configurations, key=lambda config: config.get("index", math.inf))
        text_columns = [
            config["name"]
            for config in ordered_configs
            if str(config.get("type", "STRING")).upper() == "TEXT"
        ]
        if text_columns:
            raise ValueError(
                "llm_tabular only supports structured data. "
                f"Found TEXT columns: {text_columns}."
            )

        self.attribute_config = attribute_config
        self._ordered_column_configs = ordered_configs

    def _initialize_dataset(self, dataset: pd.DataFrame) -> None:
        missing_columns = [
            config["name"] for config in self._ordered_column_configs if config["name"] not in dataset.columns
        ]
        if missing_columns:
            raise ValueError(f"Dataset is missing configured columns: {missing_columns}.")
        self.dataset = dataset.copy()

    def _initialize_synthesizer(self) -> None:
        self._initialize_llm_backend(mode="structured_tabular")

    def _fit(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self.dataset.empty:
            raise ValueError("Dataset must contain at least one row for profile calculation.")
        if not self._ordered_column_configs:
            raise ValueError("Attribute configuration is not initialized.")

        available_rows = len(self.dataset)
        self._profile_rows_used = min(self._requested_profile_rows or available_rows, available_rows)
        profile_df = (
            self.dataset.copy()
            if self._profile_rows_used == available_rows
            else self.dataset.sample(n=self._profile_rows_used)
        )

        self._column_profiles = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            self._column_profiles[column_name] = self.build_column_profile(
                profile_df,
                column_name,
                column_type,
                options=ColumnProfileOptions(categorical_top_k=15, include_text_examples=False),
            )

        self._generation_prompt_prefix = self._build_generation_prompt_prefix()

    def _sample(self) -> pd.DataFrame:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        num_samples = self._resolve_num_samples(len(self.dataset), allow_exceed_default=True)
        max_retries = self._fitting_kwargs["max_retries"]
        self._sample_start_time = pd.Timestamp.utcnow().timestamp()
        self._reset_generation_counters()

        rows = []
        for row_index in range(num_samples):
            rows.append(self._generate_row(row_index, num_samples, max_retries))
            self.report_remaining_time(self._sample_start_time, len(rows), num_samples)

        ordered_columns = [config["name"] for config in self._ordered_column_configs]
        return pd.DataFrame(rows, columns=ordered_columns)

    def _generate_row(self, row_index: int, total_rows: int, max_retries: int) -> Dict[str, Any]:
        last_error: Optional[Exception] = None
        last_details = ""

        for attempt_index in range(max_retries):
            non_dict_rows = 0
            unusable_rows = 0
            coercion_errors = 0
            try:
                for row in self._request_rows_from_llm():
                    if not isinstance(row, dict):
                        non_dict_rows += 1
                        continue
                    aligned_row, used_positional_mapping = self._align_row_to_schema(row)
                    if not self._is_row_usable(row, aligned_row, used_positional_mapping):
                        unusable_rows += 1
                        continue
                    try:
                        return self._coerce_row(aligned_row)
                    except Exception:  # noqa: BLE001
                        coercion_errors += 1
            except Exception as exc:  # noqa: BLE001
                last_error = exc

            if last_error is None:
                last_error = ValueError("No usable rows were accepted from the LLM response.")
            last_details = (
                f"non_dict_rows={non_dict_rows}, unusable_rows={unusable_rows}, "
                f"coercion_errors={coercion_errors}"
            )
            self._log_generation_attempt_failure(
                mode="TABULAR_STRUCTURED_GENERATION",
                row_index=row_index,
                total_rows=total_rows,
                attempt_index=attempt_index,
                max_retries=max_retries,
                error=last_error,
                details=last_details,
            )

        return self._handle_generation_failure(
            message=(
                f"LLM returned no valid structured row for sample {row_index + 1}/{total_rows} "
                f"after {max_retries} attempts. Last attempt stats: {last_details}."
            ),
            last_error=last_error,
        )

    def _request_rows_from_llm(self) -> List[Dict[str, Any]]:
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")
        prefix = self._generation_prompt_prefix or self._build_generation_prompt_prefix()
        prompt = build_tabular_non_text_generation_prompt_from_prefix(prefix, num_rows=1)
        content = self._llm_client.generate_text(prompt)
        parsed_json = self.parse_json_with_fallback(content)
        rows = self.rows_from_json(parsed_json)
        if not rows:
            rows = self._extract_rows_from_repeated_rows_blocks(content)
        return require_non_empty_rows(rows)

    def _build_generation_prompt_prefix(self) -> str:
        ordered_columns = [config["name"] for config in self._ordered_column_configs]
        shape = json.dumps(
            {"rows": [{column_name: "<value>" for column_name in ordered_columns}]},
            ensure_ascii=False,
        )
        profile_lines = [
            self.build_prompt_profile_line(config, self._column_profiles.get(config["name"], {}))
            for config in self._ordered_column_configs
        ]
        domain_context = (
            f"Domain context: {self._user_prompt_domain_context}\n"
            if self._user_prompt_domain_context
            else ""
        )
        dataset_rows = len(self.dataset) if self.dataset is not None else self._profile_rows_used

        return (
            "You generate new synthetic rows containing only structured tabular data.\n"
            f"{domain_context}"
            "Information:\n"
            f"- The statistical profiles were calculated from {self._profile_rows_used} of {dataset_rows} input rows.\n"
            "- Use the column schema and statistical profiles to generate plausible new rows.\n"
            "- Preserve plausible value ranges, categorical frequencies, missingness, and relationships between columns.\n"
            "- Do not reconstruct or copy an original input row.\n"
            "- Each generated row must be internally consistent.\n"
            "- Avoid impossible numerical, categorical, boolean, or chronological combinations.\n"
            "Output rules:\n"
            "- Return ONLY valid JSON.\n"
            f"- Use exactly this shape: {shape}\n"
            "- Use one top-level key only: rows.\n"
            f"- Include exactly these columns in this order: {ordered_columns}\n"
            "- Do not add comments, markdown, code fences, explanations, or extra keys.\n"
            "Type rules:\n"
            "- INTEGER: integer number\n"
            "- DECIMAL: decimal number\n"
            "- DATE: a date string using the configured column format\n"
            "- BOOLEAN: true or false\n"
            f"- STRING: plain text; use '{MISSING_VALUE_STRING}' for missing values\n"
            "Statistical column profiles:\n"
            f"{chr(10).join(profile_lines)}\n"
        )

    def _extract_rows_from_repeated_rows_blocks(self, content: str) -> List[Dict[str, Any]]:
        rows: List[Dict[str, Any]] = []
        for match in re.finditer(r'"rows"\s*:\s*\[', content):
            start = match.end() - 1
            end = self._find_matching_bracket(content, start)
            if end is None:
                continue
            try:
                candidate = json.loads(content[start : end + 1])
            except json.JSONDecodeError:
                continue
            if isinstance(candidate, list):
                rows.extend(row for row in candidate if isinstance(row, dict))
        return rows

    @staticmethod
    def _find_matching_bracket(text: str, start_index: int) -> Optional[int]:
        depth = 0
        for index in range(start_index, len(text)):
            if text[index] == "[":
                depth += 1
            elif text[index] == "]":
                depth -= 1
                if depth == 0:
                    return index
        return None

    def _align_row_to_schema(self, row: Dict[str, Any]) -> Tuple[Dict[str, Any], bool]:
        ordered_columns = [config["name"] for config in self._ordered_column_configs]
        if any(column_name in row for column_name in ordered_columns):
            return row, False

        positional_map = self._build_positional_key_map(list(row.keys()))
        if positional_map:
            return {
                column_name: row.get(positional_map.get(index))
                for index, column_name in enumerate(ordered_columns)
            }, True
        if len(row) == len(ordered_columns):
            return dict(zip(ordered_columns, row.values())), True
        return row, False

    def _build_positional_key_map(self, row_keys: Sequence[Any]) -> Dict[int, str]:
        indexed_keys: List[Tuple[int, str]] = []
        for key in row_keys:
            if not isinstance(key, str):
                return {}
            position = self._extract_positional_index(key)
            if position is None or position < 0:
                return {}
            indexed_keys.append((position, key))
        return dict(sorted(indexed_keys))

    @staticmethod
    def _extract_positional_index(key: str) -> Optional[int]:
        normalized = key.lower().strip()
        numeric_match = re.match(r"^(?:column|col|feature|field|attribute)[_\-\s]?(\d+)$", normalized)
        if numeric_match:
            return int(numeric_match.group(1)) - 1
        alpha_match = re.match(r"^(?:column|col|feature|field|attribute)[_\-\s]?([a-z]+)$", normalized)
        if not alpha_match:
            return None
        value = 0
        for character in alpha_match.group(1):
            value = value * 26 + ord(character) - ord("a") + 1
        return value - 1

    def _is_row_usable(
        self,
        original_row: Dict[str, Any],
        aligned_row: Dict[str, Any],
        used_positional_mapping: bool,
    ) -> bool:
        ordered_columns = [config["name"] for config in self._ordered_column_configs]
        values = [aligned_row.get(column_name) for column_name in ordered_columns]
        if not any(value is not None for value in values):
            return False
        if any(isinstance(value, (dict, list, tuple, set)) for value in values if value is not None):
            return False
        echoed_columns = sum(
            isinstance(value, str) and value.strip().lower() == column_name.lower()
            for column_name, value in zip(ordered_columns, values)
        )
        if echoed_columns >= max(1, math.ceil(len(ordered_columns) * 0.5)):
            return False
        if used_positional_mapping:
            return True
        matches = sum(column_name in original_row for column_name in ordered_columns)
        return matches >= max(1, math.ceil(len(ordered_columns) * 0.5))

    def _coerce_row(self, row: Dict[str, Any]) -> Dict[str, Any]:
        result: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            value = row.get(column_name)
            if column_type == "BOOLEAN":
                result[column_name] = self.coerce_boolean(value)
            elif column_type == "DATE":
                result[column_name] = self.coerce_date(
                    column_name,
                    value,
                    self._column_profiles,
                    column_config=config,
                )
            elif column_type in self.NUMERIC_TYPES:
                result[column_name] = self.coerce_numeric(
                    column_name,
                    column_type,
                    value,
                    self._column_profiles,
                )
            else:
                result[column_name] = self.coerce_string(value)
        return result

    def _get_model(self) -> bytes:
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "LlmTabularSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmTabularSynthesizer" = cloudpickle.load(file)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        sample.to_csv(filename, index=False)
