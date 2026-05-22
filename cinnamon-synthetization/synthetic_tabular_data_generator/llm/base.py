from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

import pandas as pd

from data_processing.utils import FAILED_TEXT_GENERATION
from synthetic_tabular_data_generator.llm.client import (
    LlmClient,
    LlmClientConfig,
    create_llm_client,
    load_llm_client_config,
)
from synthetic_tabular_data_generator.llm.few_shot_similarity import (
    StructuredAttributeNearestNeighborIndex,
    select_structured_attribute_neighbors,
)
from synthetic_tabular_data_generator.llm.prompt_builders import (
    build_non_text_repair_prompt_from_prefix,
    build_non_text_repair_prompt_prefix,
    build_text_enrichment_prompt_from_prefix,
    build_text_enrichment_prompt_prefix,
)
from synthetic_tabular_data_generator.llm.response_validation import require_first_dict_row
from synthetic_tabular_data_generator.llm.synthesizer_support import (
    ColumnProfileOptions,
    LlmSynthesizerSupport,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class ConfiguredLlmSynthesizerBase(TabularDataSynthesizer, LlmSynthesizerSupport):
    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._llm_config: Optional[LlmClientConfig] = None
        self._llm_client: Optional[LlmClient] = None
        self._fitting_kwargs: Optional[Dict[str, Any]] = None
        self._sampling: Optional[Dict[str, Any]] = None
        self._user_prompt_domain_context: str = ""
        self._sample_start_time: Optional[float] = None
        self._generation_failures: int = 0
        self.synthesizer = None

    def _initialize_common_llm_configuration(
        self,
        config: Dict[str, Any],
        *,
        default_profile_rows: Optional[int] = 1000,
        default_few_shot_rows: int = 0,
        include_profile_rows: bool = True,
    ) -> tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        model_params = algorithm_config.get("model_parameter", {})
        training_params = algorithm_config.get("model_fitting", {})
        self._llm_config = load_llm_client_config(config)
        few_shot_rows = model_params.get("few_shot_rows", training_params.get("few_shot_rows", default_few_shot_rows))

        self._fitting_kwargs = {
            "few_shot_rows": max(0, int(few_shot_rows)),
            "max_retries": self._llm_config.max_retries,
            "timeout_seconds": self._llm_config.timeout_seconds,
        }
        if include_profile_rows:
            profile_rows = model_params.get("profile_rows", training_params.get("profile_rows", default_profile_rows))
            if profile_rows is None:
                raise ValueError("profile_rows must be configured for this synthesizer.")
            self._fitting_kwargs["profile_rows"] = max(1, int(profile_rows))

        self._sampling = algorithm_config.get("sampling", {})
        self._user_prompt_domain_context = str(training_params.get("user_prompt_domain_context", "")).strip()
        return algorithm_config, model_params, training_params

    def _initialize_llm_backend(self, *, mode: Optional[str] = None) -> None:
        if self._llm_config is None:
            raise ValueError("Anonymization configuration is not initialized.")

        self._llm_client = create_llm_client(self._llm_config)
        self._llm_client.initialize()

        synthesizer_metadata = {
            "backend": self._llm_config.provider,
            "model_name": self._llm_config.model_name,
        }
        if mode:
            synthesizer_metadata["mode"] = mode
        self.synthesizer = synthesizer_metadata

    def _build_profile_dataframe(self, df: pd.DataFrame) -> pd.DataFrame:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")

        profile_rows = self._fitting_kwargs.get("profile_rows")
        if profile_rows is None:
            return df.copy()
        if len(df) > profile_rows:
            return df.sample(n=profile_rows).reset_index(drop=True)
        return df.copy()

    def _resolve_num_samples(self, default_num_samples: int, *, allow_exceed_default: bool) -> int:
        if self._sampling is None:
            raise ValueError("Sampling configuration is not initialized.")

        configured_num_samples = self._sampling.get("num_samples")
        if configured_num_samples is None:
            return default_num_samples

        num_samples = int(configured_num_samples)
        if num_samples <= 0:
            raise ValueError("num_samples must be greater than 0.")
        if not allow_exceed_default and num_samples > default_num_samples:
            raise ValueError("num_samples cannot exceed the number of synthetic tabular input rows.")
        return num_samples

    def _reset_generation_counters(self) -> None:
        self._generation_failures = 0

    def _handle_generation_failure(
        self,
        *,
        message: str,
        last_error: Optional[Exception],
        fallback_factory: Optional[Callable[[], Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        self._generation_failures += 1

        if fallback_factory is not None:
            return fallback_factory()

        if last_error is not None:
            raise RuntimeError(message) from last_error
        raise RuntimeError(message)

    @staticmethod
    def _summarize_exception(exc: Exception) -> str:
        message = str(exc).strip()
        if not message:
            message = exc.__class__.__name__
        return f"{exc.__class__.__name__}: {message}"

    def _log_generation_attempt_failure(
        self,
        *,
        mode: str,
        row_index: int,
        total_rows: int,
        attempt_index: int,
        max_retries: int,
        error: Exception,
        details: Optional[str] = None,
    ) -> None:
        suffix = f" ({details})" if details else ""
        print(
            f"[LLM_{mode}] sample {row_index + 1}/{total_rows}, "
            f"attempt {attempt_index + 1}/{max_retries} failed: "
            f"{self._summarize_exception(error)}{suffix}"
        )

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


class LlmTextSynthesisBase(ConfiguredLlmSynthesizerBase):
    SIMILARITY_STRATEGY_RANDOM = "Random"
    SIMILARITY_STRATEGY_ATTRIBUTES = "Attributes"

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self.reference_dataset: Optional[pd.DataFrame] = None
        self._ordered_column_configs: List[Dict[str, Any]] = []
        self._text_columns: List[str] = []
        self._column_profiles: Dict[str, Dict[str, Any]] = {}
        self._few_shot_source_df: Optional[pd.DataFrame] = None
        self._few_shot_neighbor_index: Optional[StructuredAttributeNearestNeighborIndex] = None
        self._similarity_strategy: str = self.SIMILARITY_STRATEGY_RANDOM
        self._allow_structured_corrections: bool = True
        self._repair_prompt_prefix: Optional[str] = None
        self._text_prompt_prefix: Optional[str] = None

    def _initialize_text_synthesis_configuration(
        self,
        config: Dict[str, Any],
        *,
        default_similarity_strategy: str,
    ) -> tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
        algorithm_config, model_params, training_params = self._initialize_common_llm_configuration(
            config,
            default_few_shot_rows=20,
            include_profile_rows=False,
        )
        self._similarity_strategy = self._normalize_similarity_strategy(
            model_params.get("similarity_strategy", default_similarity_strategy),
            default_similarity_strategy,
        )
        self._allow_structured_corrections = self._parse_bool_like(
            training_params.get("allow_structured_corrections", True)
        )
        return algorithm_config, model_params, training_params

    @classmethod
    def _normalize_similarity_strategy(cls, raw_value: Any, default_value: str) -> str:
        value = str(raw_value or default_value).strip()
        if not value:
            value = default_value

        if value in {cls.SIMILARITY_STRATEGY_RANDOM, cls.SIMILARITY_STRATEGY_ATTRIBUTES}:
            return value

        raise ValueError(
            f"Unsupported similarity_strategy '{value}'. Supported values are: "
            f"{[cls.SIMILARITY_STRATEGY_RANDOM, cls.SIMILARITY_STRATEGY_ATTRIBUTES]}."
        )

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        configurations = attribute_config.get("configurations", [])
        if not configurations:
            raise ValueError("Attribute configuration is empty.")

        self.attribute_config = attribute_config
        self._ordered_column_configs = sorted(configurations, key=lambda cfg: cfg.get("index", float("inf")))
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
        self._initialize_llm_backend(mode="text_synthesis")

    def _fit(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self.reference_dataset is None:
            raise ValueError("Reference dataset is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")

        profile_df = self._build_profile_dataframe(self.reference_dataset)
        self._column_profiles = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            self._column_profiles[column_name] = self.build_column_profile(
                profile_df,
                column_name,
                column_type,
                options=self._repair_column_profile_options(),
            )

        few_shot_rows = self._fitting_kwargs["few_shot_rows"]
        if few_shot_rows > 0 and not self.reference_dataset.empty:
            if self._similarity_strategy == self.SIMILARITY_STRATEGY_ATTRIBUTES:
                self._few_shot_source_df = self.reference_dataset.copy().reset_index(drop=True)
                self._few_shot_neighbor_index = StructuredAttributeNearestNeighborIndex(
                    reference_df=self._few_shot_source_df,
                    column_configs=self._ordered_column_configs,
                    missing_value_string=self._missing_value_string(),
                )
            else:
                self._few_shot_source_df = self.reference_dataset.copy().reset_index(drop=True)
                self._few_shot_neighbor_index = None
        else:
            self._few_shot_source_df = None
            self._few_shot_neighbor_index = None

        self._repair_prompt_prefix = self._build_repair_prompt_prefix()
        self._text_prompt_prefix = self._build_text_prompt_prefix()

    def _sample(self) -> pd.DataFrame:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")

        source = self.dataset.copy().reset_index(drop=True)
        num_samples = self._resolve_num_samples(len(source), allow_exceed_default=False)
        source = source.head(num_samples)

        rows = source.to_dict(orient="records")
        total = len(rows)
        self._sample_start_time = pd.Timestamp.utcnow().timestamp()
        self._reset_generation_counters()

        generated_rows: List[Dict[str, Any]] = []
        for row_index, row in enumerate(rows):
            generated_rows.append(self._generate_row(row, row_index, total))
            self.report_remaining_time(self._sample_start_time, len(generated_rows), total)

        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        generated = pd.DataFrame(generated_rows)
        for column_name in ordered_columns:
            if column_name not in generated.columns:
                generated[column_name] = pd.NA
        return generated[ordered_columns]

    def _generate_row(self, base_row: Dict[str, Any], row_index: int, total_rows: int) -> Dict[str, Any]:
        if self._fitting_kwargs is None or self._llm_client is None:
            raise ValueError("Synthesizer is not initialized for LLM sampling.")

        repaired_row = self._repair_row(base_row, row_index, total_rows)
        return self._generate_text_row(repaired_row, row_index, total_rows)

    def _repair_row(self, base_row: Dict[str, Any], row_index: int, total_rows: int) -> Dict[str, Any]:
        if self._fitting_kwargs is None or self._llm_client is None:
            raise ValueError("Synthesizer is not initialized for LLM sampling.")

        max_retries = self._fitting_kwargs["max_retries"]
        last_error: Optional[Exception] = None

        for attempt_index in range(max_retries):
            try:
                prompt = self._build_repair_prompt(base_row)
                content = self._llm_client.generate_text(prompt)
                parsed = self.parse_json_with_fallback(content)
                candidate = require_first_dict_row(parsed)
                merged = self._merge_repaired_non_text_row(base_row, candidate)
                return self._coerce_row(merged, base_row)
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                self._log_generation_attempt_failure(
                    mode="ROW_REPAIR",
                    row_index=row_index,
                    total_rows=total_rows,
                    attempt_index=attempt_index,
                    max_retries=max_retries,
                    error=exc,
                )

        if last_error is not None:
            print(
                f"[LLM_ROW_REPAIR] falling back to original structured row for sample "
                f"{row_index + 1}/{total_rows}: {self._summarize_exception(last_error)}"
            )
        return self._coerce_row(base_row, base_row)

    def _generate_text_row(self, repaired_row: Dict[str, Any], row_index: int, total_rows: int) -> Dict[str, Any]:
        if self._fitting_kwargs is None or self._llm_client is None:
            raise ValueError("Synthesizer is not initialized for LLM sampling.")

        max_retries = self._fitting_kwargs["max_retries"]
        last_error: Optional[Exception] = None

        for attempt_index in range(max_retries):
            try:
                prompt = self._build_text_prompt(repaired_row)
                content = self._llm_client.generate_text(prompt)
                parsed = self.parse_json_with_fallback(content)
                candidate = require_first_dict_row(parsed)
                merged = self._merge_generated_text_row(repaired_row, candidate)
                return self._coerce_row(merged, repaired_row)
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                self._log_generation_attempt_failure(
                    mode="TEXT_GENERATION",
                    row_index=row_index,
                    total_rows=total_rows,
                    attempt_index=attempt_index,
                    max_retries=max_retries,
                    error=exc,
                )

        return self._handle_generation_failure(
            message=(
                f"LLM returned no valid text row for sample {row_index + 1}/{total_rows} "
                f"after {max_retries} attempts."
            ),
            last_error=last_error,
            fallback_factory=lambda: self._coerce_failed_generation_row(repaired_row),
        )

    def _build_repair_prompt(self, base_row: Dict[str, Any]) -> str:
        prompt_prefix = self._repair_prompt_prefix or self._build_repair_prompt_prefix()
        primary_reference_row, reference_examples = self._build_repair_reference_context(base_row)

        return build_non_text_repair_prompt_from_prefix(
            prompt_prefix,
            base_row=self.serialize_row_values(base_row),
            primary_reference_row=primary_reference_row,
            reference_examples=reference_examples,
            knowledge_chunks=self._build_knowledge_chunks(base_row),
            knowledge_source_type=self._knowledge_source_type(),
        )

    def _build_text_prompt(self, repaired_row: Dict[str, Any]) -> str:
        prompt_prefix = self._text_prompt_prefix or self._build_text_prompt_prefix()
        primary_reference_row, reference_examples = self._build_text_reference_context(repaired_row)

        return build_text_enrichment_prompt_from_prefix(
            prompt_prefix,
            base_row=self.serialize_row_values(repaired_row),
            primary_reference_row=primary_reference_row,
            reference_examples=reference_examples,
            knowledge_chunks=self._build_knowledge_chunks(repaired_row),
            knowledge_source_type=self._knowledge_source_type(),
        )

    def _build_repair_prompt_prefix(self) -> str:
        column_order = [cfg["name"] for cfg in self._ordered_column_configs]
        profile_lines = []
        for config in self._ordered_column_configs:
            name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            line = self.build_profile_line(name, column_type, self._column_profiles.get(name, {}))
            profile_lines.append(line.replace("no observed values.", "no observed reference values."))

        return build_non_text_repair_prompt_prefix(
            column_order=column_order,
            text_columns=self._text_columns,
            profile_lines=profile_lines,
            missing_value_string=self._missing_value_string(),
            domain_context=self._user_prompt_domain_context,
        )

    def _build_text_prompt_prefix(self) -> str:
        column_order = [cfg["name"] for cfg in self._ordered_column_configs]

        return build_text_enrichment_prompt_prefix(
            column_order=column_order,
            text_columns=self._text_columns,
            missing_value_string=self._missing_value_string(),
            domain_context=self._user_prompt_domain_context,
        )

    def _merge_repaired_non_text_row(self, base_row: Dict[str, Any], candidate_row: Dict[str, Any]) -> Dict[str, Any]:
        merged: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            base_value = base_row.get(column_name)
            candidate_value = candidate_row.get(column_name, base_value)

            if column_type == "TEXT":
                merged[column_name] = base_value
                continue

            if self._allow_structured_corrections and column_name in candidate_row:
                merged[column_name] = candidate_value
            else:
                merged[column_name] = base_value

        return merged

    def _merge_generated_text_row(self, repaired_row: Dict[str, Any], candidate_row: Dict[str, Any]) -> Dict[str, Any]:
        merged: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            repaired_value = repaired_row.get(column_name)
            candidate_value = candidate_row.get(column_name, repaired_value)

            if column_type == "TEXT":
                merged[column_name] = candidate_value
                continue

            merged[column_name] = repaired_value

        return merged

    def _coerce_row(self, row: Dict[str, Any], base_row: Dict[str, Any]) -> Dict[str, Any]:
        coerced: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            value = row.get(column_name)
            base_value = base_row.get(column_name)
            coerced[column_name] = self._coerce_value(column_name, column_type, value, base_value)
        return coerced

    def _coerce_failed_generation_row(self, base_row: Dict[str, Any]) -> Dict[str, Any]:
        coerced: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            base_value = base_row.get(column_name)

            if column_type == "TEXT":
                coerced[column_name] = FAILED_TEXT_GENERATION
                continue
            if column_type == "BOOLEAN":
                coerced[column_name] = self.coerce_boolean(base_value, fallback_value=base_value)
                continue
            if column_type in self.NUMERIC_TYPES:
                numeric = self.to_float(base_value)
                if numeric is None:
                    numeric = 0.0
                coerced[column_name] = int(round(numeric)) if column_type in {"INTEGER", "DATE"} else float(numeric)
                continue

            coerced[column_name] = self.coerce_string(base_value, fallback_value=base_value)

        return coerced

    def _coerce_value(self, column_name: str, column_type: str, value: Any, base_value: Any) -> Any:
        if column_type == "BOOLEAN":
            return self.coerce_boolean(value, fallback_value=base_value)
        if column_type in self.NUMERIC_TYPES:
            return self.coerce_numeric(
                column_name,
                column_type,
                value,
                {},
                fallback_value=base_value,
            )
        if column_type == "TEXT":
            return self.coerce_text(value)
        return self.coerce_string(value, fallback_value=base_value)

    def _draw_few_shot_examples(self, base_row: Dict[str, Any]) -> List[Dict[str, Any]]:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        if self._few_shot_source_df is None or self._few_shot_source_df.empty:
            return []

        few_shot_rows = self._fitting_kwargs.get("few_shot_rows", 0)
        if few_shot_rows <= 0:
            return []

        if self._similarity_strategy == self.SIMILARITY_STRATEGY_ATTRIBUTES:
            selected_rows = select_structured_attribute_neighbors(
                base_row=self.serialize_row_values(base_row),
                reference_df=self._few_shot_source_df,
                column_configs=self._ordered_column_configs,
                k=few_shot_rows,
                missing_value_string=self._missing_value_string(),
                neighbor_index=self._few_shot_neighbor_index,
            )
            return [self.serialize_row_values(row) for row in selected_rows]

        del base_row
        n_examples = min(few_shot_rows, len(self._few_shot_source_df))
        sampled = self._few_shot_source_df.sample(n=n_examples).to_dict(orient="records")
        return [self.serialize_row_values(row) for row in sampled]

    def _build_repair_reference_context(
        self,
        base_row: Dict[str, Any],
    ) -> tuple[Optional[Dict[str, Any]], List[Dict[str, Any]]]:
        reference_examples = self._draw_few_shot_examples(base_row)
        structured_references = [self._structured_only_reference_row(row) for row in reference_examples]
        if self._similarity_strategy != self.SIMILARITY_STRATEGY_ATTRIBUTES or not structured_references:
            return None, structured_references

        primary_reference_row = structured_references[0]
        additional_reference_examples = structured_references[1:]
        return primary_reference_row, additional_reference_examples

    def _build_text_reference_context(
        self,
        repaired_row: Dict[str, Any],
    ) -> tuple[Optional[Dict[str, Any]], List[Dict[str, Any]]]:
        reference_examples = self._draw_few_shot_examples(repaired_row)
        text_only_references = [self._text_only_reference_row(row) for row in reference_examples]

        if self._similarity_strategy != self.SIMILARITY_STRATEGY_ATTRIBUTES or not text_only_references:
            return None, text_only_references

        primary_reference_row = text_only_references[0]
        additional_reference_examples = text_only_references[1:]
        return primary_reference_row, additional_reference_examples

    def _text_only_reference_row(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            column_name: row.get(column_name)
            for column_name in self._text_columns
            if column_name in row
        }

    def _structured_only_reference_row(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            config["name"]: row.get(config["name"])
            for config in self._ordered_column_configs
            if str(config.get("type", "STRING")).upper() != "TEXT" and config["name"] in row
        }

    def _repair_column_profile_options(self) -> ColumnProfileOptions:
        return ColumnProfileOptions(
            categorical_top_k=10,
            include_text_examples=False,
            text_example_limit=0,
            excluded_text_values=(self._missing_value_string(),),
        )

    def _build_knowledge_chunks(self, base_row: Dict[str, Any]) -> List[str]:
        del base_row
        return []

    def _knowledge_source_type(self) -> str:
        return "none"

    def _missing_value_string(self) -> str:
        raise NotImplementedError
