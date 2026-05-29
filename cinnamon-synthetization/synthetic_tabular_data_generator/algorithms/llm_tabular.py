import cloudpickle
import json
import math
import re
from typing import Any, Dict, List, Optional, Sequence, Tuple

import pandas as pd

from data_processing.utils import MISSING_VALUE_STRING, TEXT_PENDING_LLM
from synthetic_tabular_data_generator.llm import (
    ColumnProfileOptions,
    ConfiguredLlmSynthesizerBase,
)
from synthetic_tabular_data_generator.llm.prompt_builders import (
    build_tabular_non_text_generation_prompt_from_prefix,
    build_tabular_non_text_generation_prompt_prefix,
    build_tabular_text_completion_prompt_from_prefix,
    build_tabular_text_completion_prompt_prefix,
)
from synthetic_tabular_data_generator.llm.response_validation import (
    require_first_dict_row,
    require_non_empty_rows,
)


class LlmTabularSynthesizer(ConfiguredLlmSynthesizerBase):
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

        self._ordered_column_configs: List[Dict[str, Any]] = []
        self._column_profiles: Dict[str, Dict[str, Any]] = {}
        self._few_shot_source_df: Optional[pd.DataFrame] = None
        self._non_text_prompt_prefix: Optional[str] = None
        self._text_prompt_prefix: Optional[str] = None

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """
        Core logic for initializing anonymization configuration.
        """
        self._initialize_common_llm_configuration(
            config,
            default_profile_rows=1000,
            default_few_shot_rows=20,
        )

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
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")
        self._initialize_llm_backend()

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

        profile_df = self._build_profile_dataframe(self.dataset)

        self._column_profiles = {}
        for column_config in self._ordered_column_configs:
            column_name = column_config["name"]
            column_type = str(column_config.get("type", "STRING")).upper()
            self._column_profiles[column_name] = self._build_column_profile(profile_df, column_name, column_type)

        few_shot_rows = self._fitting_kwargs["few_shot_rows"]
        if few_shot_rows > 0 and not profile_df.empty:
            self._few_shot_source_df = profile_df.copy()
        else:
            self._few_shot_source_df = None

        self._non_text_prompt_prefix = self._build_non_text_generation_prompt_prefix()
        self._text_prompt_prefix = self._build_text_completion_prompt_prefix()

    def _sample(self) -> pd.DataFrame:
        """
        Generate synthetic tabular data via the configured LLM using one request per row.
        """
        if self._sampling is None:
            raise ValueError("Sampling configuration is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        if self._llm_config is None:
            raise ValueError("Model configuration is not initialized.")

        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")

        num_samples = self._resolve_num_samples(len(self.dataset), allow_exceed_default=True)
        max_retries = self._fitting_kwargs["max_retries"]
        self._sample_start_time = pd.Timestamp.utcnow().timestamp()
        self._reset_generation_counters()

        generated_rows = self._generate_rows_sequentially(num_samples, max_retries)

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
        return self.build_column_profile(
            df,
            column_name,
            column_type,
            options=ColumnProfileOptions(
                categorical_top_k=15,
                include_text_examples=False,
                excluded_text_values=(MISSING_VALUE_STRING, TEXT_PENDING_LLM),
            ),
        )

    def _generate_rows_sequentially(
        self,
        target_rows: int,
        max_retries: int,
    ) -> List[Dict[str, Any]]:
        accepted_rows: List[Dict[str, Any]] = []

        for row_index in range(target_rows):
            accepted_rows.append(self._generate_single_row(row_index, target_rows, max_retries))
            self._print_remaining_time(len(accepted_rows), target_rows)

        return accepted_rows

    def _generate_single_row(
        self,
        row_index: int,
        target_rows: int,
        max_retries: int,
    ) -> Dict[str, Any]:
        few_shot_examples = self._draw_few_shot_examples()
        structured_row = self._generate_non_text_row(row_index, target_rows, max_retries, few_shot_examples)
        if not any(str(cfg.get("type", "STRING")).upper() == "TEXT" for cfg in self._ordered_column_configs):
            return structured_row
        return self._generate_text_row(structured_row, row_index, target_rows, max_retries, few_shot_examples)

    def _generate_non_text_row(
        self,
        row_index: int,
        target_rows: int,
        max_retries: int,
        few_shot_examples: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        last_error: Optional[Exception] = None
        last_attempt_details: Optional[str] = None

        for attempt_index in range(max_retries):
            non_dict_rows = 0
            unusable_rows = 0
            coercion_errors = 0
            attempt_error: Optional[Exception] = None

            try:
                content = self._request_non_text_row_from_llm(1, row_index, target_rows, few_shot_examples)
                raw_rows = self._extract_rows(content)

                for row in raw_rows:
                    if not isinstance(row, dict):
                        non_dict_rows += 1
                        continue

                    aligned_row, used_positional_mapping = self._align_row_to_schema(row)
                    if not self._is_row_usable(row, aligned_row, used_positional_mapping):
                        unusable_rows += 1
                        continue

                    try:
                        return self._coerce_non_text_row(aligned_row)
                    except Exception:  # noqa: BLE001
                        coercion_errors += 1
                        continue
            except Exception as exc:  # noqa: BLE001
                attempt_error = exc

            if attempt_error is None:
                attempt_error = ValueError("No usable rows were accepted from the LLM response.")

            last_attempt_details = (
                f"non_dict_rows={non_dict_rows}, unusable_rows={unusable_rows}, "
                f"coercion_errors={coercion_errors}"
            )
            last_error = attempt_error
            self._log_generation_attempt_failure(
                mode="TABULAR_NON_TEXT_GENERATION",
                row_index=row_index,
                total_rows=target_rows,
                attempt_index=attempt_index,
                max_retries=max_retries,
                error=attempt_error,
                details=last_attempt_details,
            )

        return self._handle_generation_failure(
            message=(
                f"LLM returned no valid structured row for sample {row_index + 1}/{target_rows} "
                f"after {max_retries} attempts."
                f"{'' if last_attempt_details is None else f' Last attempt stats: {last_attempt_details}.'}"
            ),
            last_error=last_error,
        )

    def _generate_text_row(
        self,
        structured_row: Dict[str, Any],
        row_index: int,
        target_rows: int,
        max_retries: int,
        few_shot_examples: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        last_error: Optional[Exception] = None

        for attempt_index in range(max_retries):
            try:
                content = self._request_text_row_from_llm(structured_row, few_shot_examples)
                parsed = self.parse_json_with_fallback(content)
                candidate_row = require_first_dict_row(parsed)
                merged = self._merge_text_only_row(structured_row, candidate_row)
                return self._coerce_row(merged)
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                self._log_generation_attempt_failure(
                    mode="TABULAR_TEXT_GENERATION",
                    row_index=row_index,
                    total_rows=target_rows,
                    attempt_index=attempt_index,
                    max_retries=max_retries,
                    error=exc,
                )

        return self._handle_generation_failure(
            message=(
                f"LLM returned no valid text row for sample {row_index + 1}/{target_rows} "
                f"after {max_retries} attempts."
            ),
            last_error=last_error,
        )

    def _request_non_text_row_from_llm(
        self,
        num_rows: int,
        row_index: int,
        target_rows: int,
        few_shot_examples: List[Dict[str, Any]],
    ) -> str:
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        prompt = self._build_non_text_generation_prompt(num_rows, row_index, target_rows, few_shot_examples)
        return self._llm_client.generate_text(prompt)

    def _request_text_row_from_llm(
        self,
        structured_row: Dict[str, Any],
        few_shot_examples: List[Dict[str, Any]],
    ) -> str:
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        prompt = self._build_text_completion_prompt(structured_row, few_shot_examples)
        return self._llm_client.generate_text(prompt)

    def _build_non_text_generation_prompt_prefix(self) -> str:
        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        non_text_columns = [
            cfg["name"] for cfg in self._ordered_column_configs if str(cfg.get("type", "STRING")).upper() != "TEXT"
        ]
        text_columns = [
            cfg["name"] for cfg in self._ordered_column_configs if str(cfg.get("type", "STRING")).upper() == "TEXT"
        ]
        profile_lines = []
        for config in self._ordered_column_configs:
            name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            profile_lines.append(self._profile_line(name, column_type, self._column_profiles.get(name, {})))

        return build_tabular_non_text_generation_prompt_prefix(
            ordered_columns=ordered_columns,
            non_text_columns=non_text_columns,
            text_columns=text_columns,
            profile_lines=profile_lines,
            missing_value_string=MISSING_VALUE_STRING,
            domain_context=self._user_prompt_domain_context,
        )

    def _build_text_completion_prompt_prefix(self) -> str:
        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        text_columns = [
            cfg["name"] for cfg in self._ordered_column_configs if str(cfg.get("type", "STRING")).upper() == "TEXT"
        ]
        return build_tabular_text_completion_prompt_prefix(
            column_order=ordered_columns,
            text_columns=text_columns,
            missing_value_string=MISSING_VALUE_STRING,
            domain_context=self._user_prompt_domain_context,
        )

    def _build_non_text_generation_prompt(
        self,
        num_rows: int,
        row_index: int,
        target_rows: int,
        few_shot_examples: List[Dict[str, Any]],
    ) -> str:
        del row_index, target_rows
        prompt_prefix = self._non_text_prompt_prefix or self._build_non_text_generation_prompt_prefix()
        return build_tabular_non_text_generation_prompt_from_prefix(
            prompt_prefix,
            num_rows=num_rows,
            few_shot_examples=few_shot_examples,
        )

    def _build_text_completion_prompt(
        self,
        structured_row: Dict[str, Any],
        few_shot_examples: List[Dict[str, Any]],
    ) -> str:
        prompt_prefix = self._text_prompt_prefix or self._build_text_completion_prompt_prefix()
        return build_tabular_text_completion_prompt_from_prefix(
            prompt_prefix,
            base_row=self.serialize_row_for_prompt(structured_row, self._ordered_column_configs),
            reference_examples=self._text_only_examples(few_shot_examples),
        )

    def _draw_few_shot_examples(self) -> List[Dict[str, Any]]:
        if self._fitting_kwargs is None:
            return []

        few_shot_rows = self._fitting_kwargs.get("few_shot_rows", 0)
        if few_shot_rows <= 0 or self._few_shot_source_df is None or self._few_shot_source_df.empty:
            return []

        n_examples = min(few_shot_rows, len(self._few_shot_source_df))
        examples = self._few_shot_source_df.sample(n=n_examples).to_dict(orient="records")
        return [self.serialize_row_for_prompt(example, self._ordered_column_configs) for example in examples]

    def _text_only_examples(self, examples: Sequence[Dict[str, Any]]) -> List[Dict[str, Any]]:
        text_columns = [
            cfg["name"] for cfg in self._ordered_column_configs if str(cfg.get("type", "STRING")).upper() == "TEXT"
        ]
        return [
            {column_name: example.get(column_name) for column_name in text_columns if column_name in example}
            for example in examples
        ]

    def _profile_line(self, column_name: str, column_type: str, profile: Dict[str, Any]) -> str:
        matching_config = next(
            (config for config in self._ordered_column_configs if config["name"] == column_name),
            {"name": column_name, "type": column_type},
        )
        line = self.build_prompt_profile_line(matching_config, profile)
        line = line.replace("no observed values.", "no observed training values.")
        line = line.replace("frequent_values=", "frequent values ")
        return line

    def _extract_rows(self, response_content: str) -> List[Dict[str, Any]]:
        parsed_json = self.parse_json_with_fallback(response_content)
        rows = self.rows_from_json(parsed_json)
        if not rows:
            rows = self._extract_rows_from_repeated_rows_blocks(response_content)
        return require_non_empty_rows(rows)

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
            except json.JSONDecodeError:
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
            if position is None or position < 0:
                return {}
            indexed_keys.append((position, key))

        indexed_keys.sort(key=lambda item: item[0])
        return {position: key for position, key in indexed_keys}

    @staticmethod
    def _extract_positional_index(key: str) -> Optional[int]:
        lowered = key.lower().strip()

        numeric_match = re.match(r"^(?:column|col|feature|field|attribute)[_\-\s]?(\d+)$", lowered)
        if numeric_match:
            return int(numeric_match.group(1)) - 1

        alpha_match = re.match(r"^(?:column|col|feature|field|attribute)[_\-\s]?([a-z]+)$", lowered)
        if alpha_match:
            letters = alpha_match.group(1)
            value = 0
            for char in letters:
                value = value * 26 + (ord(char) - ord("a") + 1)
            return value - 1

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

    def _coerce_non_text_row(self, row: Dict[str, Any]) -> Dict[str, Any]:
        coerced = self._coerce_row(row)
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            if column_type == "TEXT":
                coerced[column_name] = MISSING_VALUE_STRING
        return coerced

    def _merge_text_only_row(self, structured_row: Dict[str, Any], candidate_row: Dict[str, Any]) -> Dict[str, Any]:
        merged: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            if column_type == "TEXT":
                merged[column_name] = candidate_row.get(column_name, structured_row.get(column_name))
                continue
            merged[column_name] = structured_row.get(column_name)
        return merged

    def _coerce_value(self, column_name: str, column_type: str, value: Any) -> Any:
        if column_type == "BOOLEAN":
            return self.coerce_boolean(value)

        if column_type == "DATE":
            matching_config = next(
                (config for config in self._ordered_column_configs if config["name"] == column_name),
                None,
            )
            return self.coerce_date(
                column_name,
                value,
                self._column_profiles,
                column_config=matching_config,
            )

        if column_type in self.NUMERIC_TYPES:
            return self.coerce_numeric(column_name, column_type, value, self._column_profiles)

        if column_type == "TEXT":
            return self.coerce_text(value)

        return self.coerce_string(value)

    def _print_remaining_time(self, generated: int, total: int) -> None:
        self.report_remaining_time(self._sample_start_time, generated, total)
