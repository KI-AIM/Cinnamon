from __future__ import annotations

import cloudpickle
import json
from typing import Any, Dict, List, Optional

import pandas as pd

from data_processing.utils import FAILED_TEXT_GENERATION, MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm import ConfiguredLlmSynthesizerBase
from synthetic_tabular_data_generator.llm.response_validation import require_first_dict_row


class LlmTextOnlyParaphraseSynthesisSynthesizer(ConfiguredLlmSynthesizerBase):
    """
    Rewrite TEXT-only rows with changed wording while preserving the source information.
    """

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
        self._prompt_prefix: Optional[str] = None

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        self._initialize_common_llm_configuration(
            config,
            default_few_shot_rows=0,
        )

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        configurations = attribute_config.get("configurations", [])
        if not configurations:
            raise ValueError("Attribute configuration is empty.")

        ordered_configs = sorted(configurations, key=lambda cfg: cfg.get("index", float("inf")))
        non_text_columns = [
            cfg["name"] for cfg in ordered_configs if str(cfg.get("type", "STRING")).upper() != "TEXT"
        ]
        if non_text_columns:
            raise ValueError(
                "llm_text_only_paraphrase_synthesis only supports TEXT columns. "
                f"Found non-TEXT columns: {non_text_columns}."
            )

        self.attribute_config = attribute_config
        self._ordered_column_configs = ordered_configs
        self._text_columns = [cfg["name"] for cfg in ordered_configs]

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        self.dataset = df.copy()

    def initialize_reference_dataset(self, df: pd.DataFrame) -> None:
        self.reference_dataset = df.copy()

    def _initialize_synthesizer(self) -> None:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")
        self._initialize_llm_backend(mode="text_only_paraphrase")

    def _fit(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if not self._ordered_column_configs:
            raise ValueError("Attribute configuration is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")

        self._prompt_prefix = self._build_prompt_prefix()

    def _sample(self) -> pd.DataFrame:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        source = self.dataset.copy().reset_index(drop=True)
        num_samples = self._resolve_num_samples(len(source), allow_exceed_default=False)
        source = source.head(num_samples)

        rows = source.to_dict(orient="records")
        total = len(rows)
        self._sample_start_time = pd.Timestamp.utcnow().timestamp()
        self._reset_generation_counters()

        generated_rows: List[Dict[str, Any]] = []
        for row_index, row in enumerate(rows):
            generated_rows.append(self._rewrite_row(row, row_index, total))
            self.report_remaining_time(self._sample_start_time, len(generated_rows), total)

        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        generated = pd.DataFrame(generated_rows)
        for column_name in ordered_columns:
            if column_name not in generated.columns:
                generated[column_name] = pd.NA
        return generated[ordered_columns]

    def _rewrite_row(self, base_row: Dict[str, Any], row_index: int, total_rows: int) -> Dict[str, Any]:
        if self._fitting_kwargs is None or self._llm_client is None:
            raise ValueError("Synthesizer is not initialized for LLM sampling.")
        if self._row_has_no_rewritable_text(base_row):
            return self._coerce_row(base_row)

        max_retries = self._fitting_kwargs["max_retries"]
        last_error: Optional[Exception] = None

        for attempt_index in range(max_retries):
            try:
                prompt = self._build_rewrite_prompt(base_row)
                content = self._llm_client.generate_text(prompt)
                parsed = self.parse_json_with_fallback(content)
                candidate = require_first_dict_row(parsed)
                merged = self._merge_rewritten_row(base_row, candidate)
                rewritten = self._coerce_row(merged)
                if self._is_verbatim_copy(base_row, rewritten):
                    raise ValueError("LLM returned the source text unchanged.")
                return rewritten
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                self._log_generation_attempt_failure(
                    mode="TEXT_REWRITE",
                    row_index=row_index,
                    total_rows=total_rows,
                    attempt_index=attempt_index,
                    max_retries=max_retries,
                    error=exc,
                )

        return self._handle_generation_failure(
            message=(
                f"LLM returned no valid rewritten text row for sample {row_index + 1}/{total_rows} "
                f"after {max_retries} attempts."
            ),
            last_error=last_error,
            fallback_factory=lambda: self._coerce_failed_generation_row(base_row),
        )

    def _build_prompt_prefix(self) -> str:
        column_order = [cfg["name"] for cfg in self._ordered_column_configs]
        domain_context = ""
        if self._user_prompt_domain_context:
            domain_context = f"Domain context: {self._user_prompt_domain_context}\n"

        return (
            "You rewrite TEXT values of a table row without losing information.\n"
            f"{domain_context}"
            "Important:\n"
            "- Each TEXT field already contains the source content.\n"
            "- Rewrite every non-missing TEXT field with different wording.\n"
            "- Preserve every fact, number, measurement, diagnosis, negation, uncertainty, and temporal relation.\n"
            "- Do not add information.\n"
            "- Do not remove information.\n"
            "- Keep short fixed technical terms unchanged when paraphrasing would distort meaning.\n"
            f"- Keep missing TEXT values as '{MISSING_VALUE_STRING}'.\n"
            "Output rules:\n"
            "- Return ONLY valid JSON.\n"
            "- Use exactly this shape: {\"row\": { ... }}\n"
            f"- Include all columns exactly in this list: {column_order}\n"
            f"- Rewrite only these TEXT columns: {self._text_columns}\n"
            f"- For missing strings/text use '{MISSING_VALUE_STRING}'\n"
            "\n"
        )

    def _build_rewrite_prompt(self, base_row: Dict[str, Any]) -> str:
        prompt_row = self.serialize_row_for_prompt(base_row, self._ordered_column_configs)
        return (
            f"{self._prompt_prefix or self._build_prompt_prefix()}"
            "SOURCE ROW\n"
            "----------------------------------------\n\n"
            f"{json.dumps({'row': prompt_row}, ensure_ascii=False, indent=2)}\n"
        )

    def _merge_rewritten_row(self, base_row: Dict[str, Any], candidate_row: Dict[str, Any]) -> Dict[str, Any]:
        merged: Dict[str, Any] = {}
        for column_name in self._text_columns:
            base_value = base_row.get(column_name)
            if self._is_missing_text(base_value):
                merged[column_name] = MISSING_VALUE_STRING
                continue
            merged[column_name] = candidate_row.get(column_name, base_value)
        return merged

    def _coerce_row(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            column_name: self.coerce_text(row.get(column_name))
            for column_name in self._text_columns
        }

    def _coerce_failed_generation_row(self, base_row: Dict[str, Any]) -> Dict[str, Any]:
        failed_row: Dict[str, Any] = {}
        for column_name in self._text_columns:
            base_value = base_row.get(column_name)
            failed_row[column_name] = (
                MISSING_VALUE_STRING if self._is_missing_text(base_value) else FAILED_TEXT_GENERATION
            )
        return failed_row

    def _row_has_no_rewritable_text(self, row: Dict[str, Any]) -> bool:
        return all(self._is_missing_text(row.get(column_name)) for column_name in self._text_columns)

    def _is_verbatim_copy(self, base_row: Dict[str, Any], rewritten_row: Dict[str, Any]) -> bool:
        rewritable_columns = [
            column_name for column_name in self._text_columns if not self._is_missing_text(base_row.get(column_name))
        ]
        return bool(rewritable_columns) and all(
            self.coerce_text(rewritten_row.get(column_name)) == self.coerce_text(base_row.get(column_name))
            for column_name in rewritable_columns
        )

    @staticmethod
    def _is_missing_text(value: Any) -> bool:
        if value is None:
            return True
        text = str(value).strip()
        return not text or text == MISSING_VALUE_STRING or text.lower() in {"nan", "null", "none", "<na>"}

    def _get_model(self) -> bytes:
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "LlmTextOnlyParaphraseSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmTextOnlyParaphraseSynthesisSynthesizer" = cloudpickle.load(file)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        sample.to_csv(filename, index=False)
