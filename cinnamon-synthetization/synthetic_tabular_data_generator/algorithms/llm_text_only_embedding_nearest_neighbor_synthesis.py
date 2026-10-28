from __future__ import annotations

import json
import heapq
from typing import Any, Dict

import pandas as pd
from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm.retrieval import RetrievalSupport
from synthetic_tabular_data_generator.algorithms.llm_text_only_paraphrase_synthesis import (
    LlmTextOnlyParaphraseSynthesisSynthesizer,
)


class LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer(
    RetrievalSupport, LlmTextOnlyParaphraseSynthesisSynthesizer
):
    """
    Generate TEXT-only rows using similarity-based retrieval as prompt context.
    """

    def __init__(self, attribute_configuration=None, anonymization_configuration=None):
        super().__init__(attribute_configuration, anonymization_configuration)
        self._initialize_retrieval()

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        super()._initialize_anonymization_configuration(config)
        self._configure_retrieval(config)

    def _initialize_synthesizer(self) -> None:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")
        self._initialize_llm_backend(mode="text_only_embedding_nearest_neighbor")

    def _fit(self) -> None:
        super()._fit()

        self._fit_embedding_index()

    def _sample(self) -> pd.DataFrame:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        source = self.dataset.copy().reset_index(drop=True)
        num_samples = self._resolve_num_samples(len(source), allow_exceed_default=True)
        source = source.sample(n=num_samples, replace=num_samples > len(source)).reset_index(drop=True)

        rows = source.to_dict(orient="records")
        total = len(rows)
        self._sample_start_time = pd.Timestamp.utcnow().timestamp()
        self._reset_generation_counters()

        generated_rows = []
        for row_index, row in enumerate(self._generation_rows(rows)):
            generated_rows.append(self._rewrite_row(row, row_index, total))
            self.report_remaining_time(self._sample_start_time, len(generated_rows), total)

        ordered_columns = [config["name"] for config in self._ordered_column_configs]
        generated = pd.DataFrame(generated_rows)
        for column_name in ordered_columns:
            if column_name not in generated.columns:
                generated[column_name] = pd.NA
        return generated[ordered_columns]

    def _build_prompt_prefix(self) -> str:
        text_column = self._text_columns[0]
        return (
            "Generate a distinct TEXT record using the source and retrieved examples as inspiration, not templates.\n"
            f"{self.PROMPT_INJECTION_RULE}"
            "- Keep the source language, document type, tone, approximate length and level of detail.\n"
            "- Recombine and vary case-specific facts to create a new plausible record; do not copy distinctive details or fact combinations from any input.\n"
            "- Ensure internal consistency of values, chronology, negations and relationships; avoid unrelated or implausible claims.\n"
            f"- If the source TEXT is missing, return '{MISSING_VALUE_STRING}'.\n"
            f"- Return only valid JSON: {{\"row\":{{\"{text_column}\":\"...\"}}}}.\n\n"
        )

    def _build_rewrite_prompt(self, base_row: Dict[str, Any]) -> str:
        text_column = self._text_columns[0]
        prompt_row = {text_column: self.serialize_value(base_row.get(text_column))}
        reference_examples = self._neighbor_examples(base_row)
        reference_block = ""
        if reference_examples:
            example_lines = [
                f"{index + 1}. {json.dumps(example, ensure_ascii=False)}"
                for index, example in enumerate(reference_examples)
            ]
            reference_block = (
                "RETRIEVED REFERENCE EXAMPLES\n"
                "----------------------------------------\n"
                f"{chr(10).join(example_lines)}\n\n"
            )

        return (
            f"{self._prompt_prefix or self._build_prompt_prefix()}"
            "SOURCE ROW\n"
            "----------------------------------------\n\n"
            f"{json.dumps({'row': prompt_row}, ensure_ascii=False, indent=2)}\n\n"
            f"{reference_block}"
        )

    def _neighbor_examples(self, base_row: Dict[str, Any]) -> list[str]:
        if self._few_shot_examples <= 0 or not self._reference_rows or not self._reference_vectors:
            return []

        text_column = self._text_columns[0]
        query_text = self.coerce_text(base_row.get(text_column))
        if query_text == MISSING_VALUE_STRING:
            return []

        query_vector = self._encode_query(query_text)
        if not query_vector:
            return []

        def scored_examples():
            for row, reference_vector in zip(self._reference_rows, self._reference_vectors):
                reference_text = self.coerce_text(row.get(text_column))
                if reference_text == MISSING_VALUE_STRING:
                    continue
                if self._exclude_self_match and reference_text == query_text:
                    continue
                score = self._score_vectors(query_vector, reference_vector)
                yield score, reference_text

        return [text for _, text in heapq.nlargest(self._few_shot_examples, scored_examples(), key=lambda item: item[0])]
