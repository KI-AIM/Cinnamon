from __future__ import annotations

import json
import math
from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd

from data_processing.utils import FAILED_TEXT_GENERATION, MISSING_VALUE_STRING
from synthetic_tabular_data_generator.algorithms.llm_text_only_paraphrase_synthesis import (
    LlmTextOnlyParaphraseSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.llm import ColumnProfileOptions
from synthetic_tabular_data_generator.llm.response_validation import require_first_dict_row


class LlmMixedDataParaphraseSynthesisSynthesizer(LlmTextOnlyParaphraseSynthesisSynthesizer):
    """Paraphrase one TEXT column, then align the structured columns with it."""

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._structured_column_configs: List[Dict[str, Any]] = []
        self._column_profiles: Dict[str, Dict[str, Any]] = {}
        self._requested_profile_rows: Optional[int] = None
        self._profile_rows_used = 0
        self._consistency_prompt_prefix: Optional[str] = None

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        _, model_params, _ = self._initialize_common_llm_configuration(config, default_few_shot_rows=0)
        self._initialize_profile_rows_configuration(model_params)

    def _initialize_profile_rows_configuration(self, model_params: Dict[str, Any]) -> None:
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

        self.attribute_config = attribute_config
        self._ordered_column_configs = sorted(configurations, key=lambda cfg: cfg.get("index", math.inf))
        self._text_columns = [
            cfg["name"]
            for cfg in self._ordered_column_configs
            if str(cfg.get("type", "STRING")).upper() == "TEXT"
        ]
        self._structured_column_configs = [
            cfg
            for cfg in self._ordered_column_configs
            if str(cfg.get("type", "STRING")).upper() != "TEXT"
        ]
        if len(self._text_columns) != 1:
            raise ValueError(
                "llm_mixed_data_paraphrase_synthesis requires exactly one TEXT column. "
                f"Found {len(self._text_columns)} TEXT columns."
            )
        if not self._structured_column_configs:
            raise ValueError("llm_mixed_data_paraphrase_synthesis requires at least one non-TEXT column.")

    def _initialize_synthesizer(self) -> None:
        self._initialize_llm_backend(mode="mixed_data_paraphrase_consistency")

    def _fit(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self.dataset.empty:
            raise ValueError("Dataset must contain at least one row.")

        self._validate_text_only_source_content()
        profile_source = self.reference_dataset if self.reference_dataset is not None else self.dataset
        available_rows = len(profile_source)
        self._profile_rows_used = min(self._requested_profile_rows or available_rows, available_rows)
        profile_df = (
            profile_source
            if self._profile_rows_used == available_rows
            else profile_source.sample(n=self._profile_rows_used)
        )
        self._column_profiles = {
            config["name"]: self.build_column_profile(
                profile_df,
                config["name"],
                str(config.get("type", "STRING")).upper(),
                options=ColumnProfileOptions(categorical_top_k=15, include_text_examples=False),
            )
            for config in self._structured_column_configs
        }
        self._prompt_prefix = self._build_prompt_prefix()
        self._consistency_prompt_prefix = self._build_consistency_prompt_prefix(available_rows)

    def _sample(self) -> pd.DataFrame:
        if self.dataset is None or self._llm_client is None:
            raise ValueError("Synthesizer is not initialized for LLM sampling.")

        source = self.dataset.copy().reset_index(drop=True)
        source = source.head(self._resolve_num_samples(len(source), allow_exceed_default=False))
        rows = source.to_dict(orient="records")
        total = len(rows)
        self._sample_start_time = pd.Timestamp.utcnow().timestamp()
        self._reset_generation_counters()

        generated_rows = []
        for row_index, base_row in enumerate(rows):
            rewritten_text = self._rewrite_row(base_row, row_index, total)
            rewritten_row = {**base_row, **rewritten_text}
            if rewritten_text[self._text_columns[0]] in {MISSING_VALUE_STRING, FAILED_TEXT_GENERATION}:
                generated_rows.append(self._coerce_mixed_row(rewritten_row, base_row))
            else:
                generated_rows.append(self._align_structured_row(rewritten_row, base_row, row_index, total))
            self.report_remaining_time(self._sample_start_time, len(generated_rows), total)

        columns = [config["name"] for config in self._ordered_column_configs]
        return pd.DataFrame(generated_rows, columns=columns)

    def _build_rewrite_prompt(self, base_row: Dict[str, Any]) -> str:
        text_column = self._text_columns[0]
        prompt_row = {text_column: self.serialize_value(base_row.get(text_column))}
        return (
            f"{self._prompt_prefix or self._build_prompt_prefix()}"
            "SOURCE ROW\n"
            "----------------------------------------\n\n"
            f"{json.dumps({'row': prompt_row}, ensure_ascii=False, indent=2)}\n"
        )

    def _align_structured_row(
        self,
        rewritten_row: Dict[str, Any],
        base_row: Dict[str, Any],
        row_index: int,
        total_rows: int,
    ) -> Dict[str, Any]:
        if self._fitting_kwargs is None or self._llm_client is None:
            raise ValueError("Synthesizer is not initialized for LLM sampling.")

        last_error: Optional[Exception] = None
        for attempt_index in range(self._fitting_kwargs["max_retries"]):
            try:
                content = self._llm_client.generate_text(self._build_consistency_prompt(rewritten_row))
                candidate = require_first_dict_row(self.parse_json_with_fallback(content))
                merged = dict(rewritten_row)
                for config in self._structured_column_configs:
                    name = config["name"]
                    if str(config.get("type", "STRING")).upper() != "ID" and name in candidate:
                        merged[name] = candidate[name]
                return self._coerce_mixed_row(merged, base_row)
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                self._log_generation_attempt_failure(
                    mode="MIXED_ROW_CONSISTENCY",
                    row_index=row_index,
                    total_rows=total_rows,
                    attempt_index=attempt_index,
                    max_retries=self._fitting_kwargs["max_retries"],
                    error=exc,
                )

        if last_error is not None:
            print(
                f"[LLM_MIXED_ROW_CONSISTENCY] falling back to the rewritten text and original structured "
                f"values for sample {row_index + 1}/{total_rows}: {self._summarize_exception(last_error)}"
            )
        return self._coerce_mixed_row(rewritten_row, base_row)

    def _build_consistency_prompt_prefix(self, available_rows: int) -> str:
        columns = [config["name"] for config in self._ordered_column_configs]
        text_column = self._text_columns[0]
        structured_columns = [config["name"] for config in self._structured_column_configs]
        profile_lines = [
            self.build_prompt_profile_line(config, self._column_profiles[config["name"]])
            for config in self._structured_column_configs
        ]
        domain_context = (
            f"Domain context: {self._user_prompt_domain_context}\n"
            if self._user_prompt_domain_context
            else ""
        )
        return (
            "You align the structured values of a mixed table row with its rewritten TEXT.\n"
            f"{domain_context}"
            "Information:\n"
            f"- Statistical profiles were calculated from {self._profile_rows_used} of {available_rows} reference rows.\n"
            f"- Treat {text_column} as fixed ground truth and do not change it.\n"
            "- Change a structured value only when the TEXT clearly supports the correction.\n"
            "- Keep structured values that are already consistent with the TEXT.\n"
            "- Do not invent facts that are absent from the TEXT.\n"
            "- Keep ID columns unchanged and do not reconstruct identifiers.\n"
            "- Respect the statistical profiles, configured types, plausible ranges, and chronological relationships.\n"
            "- Ensure the final row is internally consistent.\n"
            "Output rules:\n"
            "- Return ONLY valid JSON using exactly this shape: {\"row\": { ... }}\n"
            f"- Include exactly these columns in this order: {columns}\n"
            f"- Return the TEXT column unchanged and adjust only these structured columns when necessary: {structured_columns}\n"
            f"- Use '{MISSING_VALUE_STRING}' for missing string or TEXT values.\n"
            "Statistical column profiles:\n"
            f"{chr(10).join(profile_lines)}\n\n"
        )

    def _build_consistency_prompt(self, rewritten_row: Dict[str, Any]) -> str:
        prompt_row = self.serialize_row_for_prompt(rewritten_row, self._ordered_column_configs)
        return (
            f"{self._consistency_prompt_prefix}"
            "ROW WITH REWRITTEN TEXT\n"
            "----------------------------------------\n\n"
            f"{json.dumps({'row': prompt_row}, ensure_ascii=False, indent=2)}\n"
        )

    def _coerce_mixed_row(self, row: Dict[str, Any], base_row: Dict[str, Any]) -> Dict[str, Any]:
        result: Dict[str, Any] = {}
        for config in self._ordered_column_configs:
            name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            value = row.get(name)
            fallback = base_row.get(name)
            if column_type == "TEXT":
                result[name] = self.coerce_text(value, fallback_value=fallback)
            elif column_type == "BOOLEAN":
                result[name] = self.coerce_boolean(value, fallback_value=fallback)
            elif column_type == "DATE":
                result[name] = self.coerce_date(
                    name,
                    value,
                    self._column_profiles,
                    fallback_value=fallback,
                    column_config=config,
                )
            elif column_type in self.NUMERIC_TYPES:
                result[name] = self.coerce_numeric(
                    name,
                    column_type,
                    value,
                    self._column_profiles,
                    fallback_value=fallback,
                )
            else:
                result[name] = self.coerce_string(value, fallback_value=fallback)
        return result

    def _load_model(self, filepath: str) -> "LlmMixedDataParaphraseSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmMixedDataParaphraseSynthesisSynthesizer" = cloudpickle.load(file)
        return model
