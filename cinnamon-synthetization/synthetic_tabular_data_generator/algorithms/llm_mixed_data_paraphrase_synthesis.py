from __future__ import annotations

import json
import math
import random
from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd

from data_processing.utils import FAILED_TEXT_GENERATION, MISSING_VALUE_STRING
from synthetic_tabular_data_generator.algorithms.llm_text_only_paraphrase_synthesis import (
    LlmTextOnlyParaphraseSynthesisSynthesizer,
)


class LlmMixedDataParaphraseSynthesisSynthesizer(LlmTextOnlyParaphraseSynthesisSynthesizer):
    """Generate one TEXT column from immutable synthetic structured ground truth."""

    _SYNTHESIZER_NAME = "llm_mixed_data_paraphrase_synthesis"

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._structured_column_configs: List[Dict[str, Any]] = []
        self._profile_data: Optional[pd.DataFrame] = None
        self._reference_texts: list[str] = []
        self._requested_profile_rows: Optional[int] = None
        self._profile_rows_used = 0

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
                f"{self._SYNTHESIZER_NAME} requires exactly one TEXT column. "
                f"Found {len(self._text_columns)} TEXT columns."
            )
        if not self._structured_column_configs:
            raise ValueError(f"{self._SYNTHESIZER_NAME} requires at least one non-TEXT column.")

    def _initialize_synthesizer(self) -> None:
        self._initialize_llm_backend(mode="mixed_data_paraphrase")

    def _fit(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self.dataset.empty:
            raise ValueError("Dataset must contain at least one row.")

        profile_source = self.reference_dataset if self.reference_dataset is not None else self.dataset
        available_rows = len(profile_source)
        self._profile_rows_used = min(self._requested_profile_rows or available_rows, available_rows)
        self._profile_data = (
            profile_source
            if self._profile_rows_used == available_rows
            else profile_source.sample(n=self._profile_rows_used)
        )
        self._reference_texts = self.deduplicate_texts(
            self._sample_non_missing_examples(
                self._profile_data[self._text_columns[0]], max_examples=len(self._profile_data),
            )
        )
        self._prompt_prefix = self._build_prompt_prefix()

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        # Synthetic rows must never be dropped because their text is empty or a placeholder.
        self.dataset = df.copy()

    def _sample(self) -> pd.DataFrame:
        if self.dataset is None or self._llm_client is None:
            raise ValueError("Synthesizer is not initialized for LLM sampling.")

        source = self.dataset.copy().reset_index(drop=True)
        rows = source.to_dict(orient="records")
        total = len(rows)
        self._sample_start_time = pd.Timestamp.utcnow().timestamp()
        self._reset_generation_counters()
        text_column = self._text_columns[0]
        generated_texts = []
        for row_index, base_row in enumerate(self._generation_rows(rows)):
            rewritten = self._rewrite_row(base_row, row_index, total)
            text = rewritten[text_column]
            if text in {MISSING_VALUE_STRING, FAILED_TEXT_GENERATION}:
                raise ValueError(f"LLM failed to include the structured ground truth for row {row_index + 1}.")
            generated_texts.append(text)
            self.report_remaining_time(self._sample_start_time, len(generated_texts), total)

        source[text_column] = generated_texts
        return source[[config["name"] for config in self._ordered_column_configs]]

    def _build_prompt_prefix(self) -> str:
        return (
            "Generate one TEXT value from synthetic structured ground truth, using the reference text as a paraphrasing guide.\n"
            f"{self.PROMPT_INJECTION_RULE}"
            "- The structured row is authoritative. Include every non-missing attribute naturally, preserving its exact meaning, values, units, negations and temporal relations. Do not infer missing values.\n"
            "- Rework the reference's wording and organization into a new coherent text; preserve standard technical terms where needed.\n"
            "- Replace or omit reference-specific facts not supported by the structured row, including identifiers. Add no unsupported or conflicting claims.\n"
            "- Generate even if the input TEXT is missing. Follow reference language; otherwise use language appropriate to the structured schema.\n"
            f"- Return only valid JSON: {{\"row\":{{\"{self._text_columns[0]}\":\"...\"}}}}.\n\n"
        )

    def _ground_truth(self, base_row: Dict[str, Any]) -> Dict[str, Any]:
        row = {config["name"]: base_row.get(config["name"]) for config in self._structured_column_configs}
        return self.serialize_row_for_prompt(row, self._structured_column_configs)

    def _required_facts(self, base_row: Dict[str, Any]) -> list[str]:
        return [
            f"{name}: {json.dumps(value, ensure_ascii=False) if isinstance(value, bool) else value}"
            for name, value in self._ground_truth(base_row).items()
            if not self._is_explicit_missing_structured_value(value)
        ]

    def _neighbor_examples(self, base_row: Dict[str, Any]) -> list[str]:
        # Paraphrase and identifier rewrite need one style/content reference per synthetic case.
        return [random.choice(self._reference_texts)] if self._reference_texts else []

    def _build_rewrite_prompt(self, base_row: Dict[str, Any]) -> str:
        return (
            f"{self._prompt_prefix or self._build_prompt_prefix()}"
            "STRUCTURED GROUND TRUTH\n"
            f"{json.dumps(self._ground_truth(base_row), ensure_ascii=False, indent=2)}\n\n"
            f"{self._guidance_prompt_block(base_row)}"
        )

    def _guidance_prompt_block(self, base_row: Dict[str, Any]) -> str:
        return (
            "REFERENCE TEXTS (supporting context only)\n"
            f"{json.dumps(self._neighbor_examples(base_row), ensure_ascii=False, indent=2)}\n"
        )

    def _row_has_no_rewritable_text(self, row: Dict[str, Any]) -> bool:
        return False

    def _merge_rewritten_row(self, base_row: Dict[str, Any], candidate_row: Dict[str, Any]) -> Dict[str, Any]:
        text_column = self._text_columns[0]
        text = candidate_row.get(text_column)
        if not isinstance(text, str) or self._is_missing_text(text) or text == FAILED_TEXT_GENERATION:
            raise ValueError("LLM response must contain a non-empty generated TEXT value.")
        # Natural-language factual consistency cannot be established by literal value matching.
        # Discard any structured keys returned by the LLM.
        return {text_column: text}

    @staticmethod
    def _is_explicit_missing_structured_value(value: Any) -> bool:
        if value is None or value is pd.NA:
            return True
        if isinstance(value, float) and math.isnan(value):
            return True
        return str(value).strip().lower() in {
            "",
            "na",
            "n/a",
            "nan",
            "null",
            "none",
            "<na>",
            MISSING_VALUE_STRING.lower(),
        }

    def _load_model(self, filepath: str) -> "LlmMixedDataParaphraseSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmMixedDataParaphraseSynthesisSynthesizer" = cloudpickle.load(file)
        return model
