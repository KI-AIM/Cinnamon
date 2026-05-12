import cloudpickle
import json
import math
import re
import time
from typing import Any, Dict, List, Optional, Sequence, Tuple

import pandas as pd

from data_processing.utils import MISSING_VALUE_STRING, TEXT_PENDING_LLM
from synthetic_tabular_data_generator.llm import (
    ColumnProfileOptions,
    LlmClient,
    LlmClientConfig,
    LlmSynthesizerSupport,
    create_llm_client,
    load_llm_client_config,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class LlmTabularSynthesizer(TabularDataSynthesizer, LlmSynthesizerSupport):
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
        self._few_shot_source_df: Optional[pd.DataFrame] = None
        self._sample_start_time: Optional[float] = None
        self._generation_prompt_prefix: Optional[str] = None
        self._user_prompt_domain_context: str = ""

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """
        Core logic for initializing anonymization configuration.
        """
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
            "timeout_seconds": self._llm_config.timeout_seconds,
        }
        self._user_prompt_domain_context = str(training_params.get("user_prompt_domain_context", "")).strip()
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
            self._few_shot_source_df = profile_df.copy()
        else:
            self._few_shot_source_df = None

        self._generation_prompt_prefix = self._build_generation_prompt_prefix()

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

        configured_num_samples = self._sampling.get("num_samples")
        if configured_num_samples is None:
            num_samples = len(self.dataset)
        else:
            num_samples = int(configured_num_samples)
            if num_samples <= 0:
                raise ValueError("num_samples must be greater than 0.")

        max_retries = self._fitting_kwargs["max_retries"]
        self._sample_start_time = time.time()

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
        last_error: Optional[Exception] = None

        for attempt_index in range(max_retries):
            non_dict_rows = 0
            unusable_rows = 0
            coercion_errors = 0

            try:
                content = self._request_rows_from_llm(1, row_index, target_rows)
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
                        return self._coerce_row(aligned_row)
                    except Exception:  # noqa: BLE001
                        coercion_errors += 1
                        continue
            except Exception as exc:  # noqa: BLE001
                last_error = exc

        message = (
            f"LLM returned no valid row for sample {row_index + 1}/{target_rows} "
            f"after {max_retries} attempts."
        )
        if last_error is not None:
            raise RuntimeError(message) from last_error
        raise RuntimeError(message)

    def _request_rows_from_llm(self, num_rows: int, row_index: int, target_rows: int) -> str:
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        prompt = self._build_generation_prompt(num_rows, row_index, target_rows)
        return self._llm_client.generate_text(prompt)

    def _build_generation_prompt_prefix(self) -> str:
        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        column_descriptions = []

        for config in self._ordered_column_configs:
            name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            profile = self._column_profiles.get(name, {})
            column_descriptions.append(self._profile_line(name, column_type, profile))

        profile_lines = "\n".join(column_descriptions)
        shape_example = {column_name: "<value>" for column_name in ordered_columns}
        shape_text = json.dumps({"rows": [shape_example]}, ensure_ascii=True)
        domain_context_block = ""
        if self._user_prompt_domain_context:
            domain_context_block = f"Domain context: {self._user_prompt_domain_context}\n"

        return (
            "You are generating synthetic tabular rows.\n"
            f"{domain_context_block}"
            "Return ONLY valid JSON with this exact shape:\n"
            f"{shape_text}\n"
            "Use one top-level key only: rows.\n"
            "No markdown, no comments, no code fences, no extra keys.\n"
            f"Use exactly these columns: {ordered_columns}\n"
            "Never use generic column names like column_a, column_b, feature_1, field_1.\n"
            "Generation order constraint (single output step):\n"
            "- First determine all non-TEXT column values.\n"
            "- Then generate TEXT column values conditioned on those non-TEXT values.\n"
            "- Return only the final JSON rows output, no intermediate reasoning.\n"
            "Type rules:\n"
            "- INTEGER: integer number\n"
            "- DECIMAL: decimal number\n"
            "- DATE: UNIX timestamp in seconds as number\n"
            "- BOOLEAN: true or false\n"
            f"- STRING: plain text, use '{MISSING_VALUE_STRING}' for missing\n"
            f"- TEXT: realistic free text, use '{MISSING_VALUE_STRING}' for missing\n"
            "Column profiles:\n"
            f"{profile_lines}"
            "\nModel realistic relationships between columns based on the profiles."
        )

    def _build_generation_prompt(self, num_rows: int, row_index: int, target_rows: int) -> str:
        prefix = self._generation_prompt_prefix or self._build_generation_prompt_prefix()
        few_shot_block = self._build_few_shot_block()
        return (
            f"{prefix}\n"
            f"{few_shot_block}"
            "Generation task:\n"
            f"Generate exactly {num_rows} rows.\n"
            "Return exactly one realistic synthetic row in the rows array."
        )

    def _build_few_shot_block(self) -> str:
        examples = self._draw_few_shot_examples()
        if not examples:
            return ""

        few_shot_json = json.dumps(examples, ensure_ascii=True)
        return (
            "\nReference examples (learn structure only, do not copy rows):\n"
            f"{few_shot_json}\n"
        )

    def _draw_few_shot_examples(self) -> List[Dict[str, Any]]:
        if self._fitting_kwargs is None:
            return []

        few_shot_rows = self._fitting_kwargs.get("few_shot_rows", 0)
        if few_shot_rows <= 0 or self._few_shot_source_df is None or self._few_shot_source_df.empty:
            return []

        n_examples = min(few_shot_rows, len(self._few_shot_source_df))
        examples = self._few_shot_source_df.sample(n=n_examples).to_dict(orient="records")
        return [self._serialize_row_values(example) for example in examples]

    def _profile_line(self, column_name: str, column_type: str, profile: Dict[str, Any]) -> str:
        line = self.build_profile_line(column_name, column_type, profile)
        line = line.replace("no observed values.", "no observed training values.")
        line = line.replace("frequent_values=", "frequent values ")
        return line

    def _extract_rows(self, response_content: str) -> List[Dict[str, Any]]:
        parsed_json = self.parse_json_with_fallback(response_content)
        rows = self.rows_from_json(parsed_json)
        if not rows:
            rows = self._extract_rows_from_repeated_rows_blocks(response_content)
        if not rows:
            raise ValueError("No rows were found in the LLM response.")
        return rows

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
            return self._coerce_text(value)

        return self._coerce_string(value)

    def _coerce_text(self, value: Any) -> str:
        return self.coerce_text(value)

    def _coerce_boolean(self, value: Any) -> bool:
        return self.coerce_boolean(value)

    def _coerce_numeric(self, column_name: str, column_type: str, value: Any) -> Any:
        return self.coerce_numeric(column_name, column_type, value, self._column_profiles)

    def _default_numeric_value(self, column_name: str, column_type: str) -> float:
        del column_type
        return self.default_numeric_value(column_name, self._column_profiles)

    def _coerce_string(self, value: Any) -> str:
        return self.coerce_string(value)

    def _serialize_row_values(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return self.serialize_row_values(row)

    def _print_remaining_time(self, generated: int, total: int) -> None:
        self.report_remaining_time(self._sample_start_time, generated, total)
