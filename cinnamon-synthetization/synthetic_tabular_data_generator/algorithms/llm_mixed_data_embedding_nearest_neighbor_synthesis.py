from __future__ import annotations

import math
import heapq
from typing import Any, Callable, Dict, Optional

import cloudpickle
import pandas as pd

from data_processing.utils import (
    MISSING_VALUE_STRING,
    get_date_input_formats,
    parse_to_unix,
)
from synthetic_tabular_data_generator.algorithms.llm_mixed_data_paraphrase_synthesis import (
    LlmMixedDataParaphraseSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.llm.retrieval import RetrievalSupport


SimilarityFunction = Callable[[Any, Any], float]


class LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer(
    RetrievalSupport, LlmMixedDataParaphraseSynthesisSynthesizer,
):
    """Retrieve references for synthetic structured rows and generate matching text."""

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._initialize_retrieval()
        self._text_similarity_weight = 0.7
        self._structured_similarity_weight = 0.3
        self._structured_similarity_functions: Dict[str, SimilarityFunction] = {}

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        super()._initialize_anonymization_configuration(config)
        self._configure_retrieval(config)
        model_params = config["synthetization_configuration"]["algorithm"].get("model_parameter", {})
        self._text_similarity_weight = self._parse_similarity_weight(
            model_params.get("text_similarity_weight", 0.7),
            "text_similarity_weight",
        )
        self._structured_similarity_weight = self._parse_similarity_weight(
            model_params.get("structured_similarity_weight", 0.3),
            "structured_similarity_weight",
        )
        if self._text_similarity_weight + self._structured_similarity_weight <= 0:
            raise ValueError("At least one similarity weight must be greater than 0.")

    @staticmethod
    def _parse_similarity_weight(raw_value: Any, name: str) -> float:
        value = float(raw_value)
        if not math.isfinite(value) or value < 0:
            raise ValueError(f"{name} must be a finite number greater than or equal to 0.")
        return value

    def _initialize_synthesizer(self) -> None:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")
        self._initialize_llm_backend(mode="mixed_data_embedding_nearest_neighbor")

    def _fit(self) -> None:
        super()._fit()
        if self._text_similarity_weight > 0:
            self._fit_embedding_index()
        else:
            self._fit_reference_rows_without_embeddings()
        self._build_structured_similarity_functions()
        self._normalized_reference_rows = [self._normalize_structured_row(row) for row in self._reference_rows]

    def _fit_reference_rows_without_embeddings(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        reference_df = self.reference_dataset if self.reference_dataset is not None else self.dataset
        text_column = self._text_columns[0]
        reference_rows = [
            row
            for row in reference_df.to_dict(orient="records")
            if self.coerce_text(row.get(text_column)) != MISSING_VALUE_STRING
        ]
        self._reference_rows = self.deduplicate_reference_rows(reference_rows, text_column)
        self._reference_vectors = [None] * len(self._reference_rows)

    def _build_prompt_prefix(self) -> str:
        text_column = self._text_columns[0]
        return (
            "Generate one TEXT value from synthetic structured ground truth.\n"
            f"{self.PROMPT_INJECTION_RULE}"
            "- The structured row is authoritative. Include every non-missing attribute naturally, preserving its exact meaning, values, units, negations and temporal relations. Do not infer missing values.\n"
            "- Use retrieved texts for language, terminology, structure and general context; combine and rephrase their patterns rather than copying them.\n"
            "- Do not transfer identifiers or case-specific facts absent from the structured row, or introduce unsupported or conflicting claims.\n"
            "- Generate even if the input TEXT is missing. Follow reference language; otherwise use language appropriate to the structured schema.\n"
            f"- Return only valid JSON: {{\"row\":{{\"{text_column}\":\"...\"}}}}.\n\n"
        )

    def _retrieval_query(self, row):
        return "; ".join(self._required_facts(row))

    def _neighbor_examples(self, base_row: Dict[str, Any]) -> list[str]:
        if self._few_shot_examples <= 0 or not self._reference_rows or not self._reference_vectors:
            return []

        text_column = self._text_columns[0]
        query_text = self._retrieval_query(base_row)

        query_vector = self._encode_query(query_text) if self._text_similarity_weight > 0 else None
        query_values = self._normalize_structured_row(base_row)
        source_text = self.coerce_text(base_row.get(text_column))
        total_weight = self._text_similarity_weight + self._structured_similarity_weight
        def scored_examples():
            for row, reference_vector, reference_values in zip(self._reference_rows, self._reference_vectors, self._normalized_reference_rows, strict=True):
                reference_text = self.coerce_text(row.get(text_column))
                if reference_text == MISSING_VALUE_STRING:
                    continue
                if self._exclude_self_match and reference_text == source_text:
                    continue

                text_similarity = (
                    self._score_vectors(query_vector, reference_vector)
                    if query_vector is not None
                    else 0.0
                )
                structured_similarity = self._score_structured_rows(query_values, reference_values)
                combined_similarity = (
                    self._text_similarity_weight * text_similarity
                    + self._structured_similarity_weight * structured_similarity
                ) / total_weight
                yield combined_similarity, reference_text

        return [text for _, text in heapq.nlargest(self._few_shot_examples, scored_examples(), key=lambda item: item[0])]

    def _build_structured_similarity_functions(self) -> None:
        functions: Dict[str, SimilarityFunction] = {}
        for config in self._structured_column_configs:
            name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            if column_type in {"INTEGER", "DECIMAL", "DATE", "DATE_TIME"}:
                reference = self._profile_data
                values = [self._normalize_structured_similarity_value(config, value) for value in reference[name]]
                values = [value for value in values if value is not None]
                maximum = max(values) if values else None
                minimum = min(values) if values else None
                value_range = (maximum - minimum) if maximum is not None and minimum is not None else 1.0
                max_distance = max(abs(value_range), 1.0)
                base_function = self._linear_similarity(max_distance)
            else:
                base_function = self._equality_similarity
            functions[name] = self._missing_aware_similarity(base_function)
        self._structured_similarity_functions = functions

    def _normalize_structured_row(self, row):
        return {config["name"]: self._normalize_structured_similarity_value(config, row.get(config["name"]))
                for config in self._structured_column_configs}

    def _score_structured_rows(self, query_row: Dict[str, Any], reference_row: Dict[str, Any]) -> float:
        functions = self._structured_similarity_functions
        return (sum(function(query_row[name], reference_row[name]) for name, function in functions.items())
                / len(functions)) if functions else 0.0

    def _normalize_structured_similarity_value(self, config: Dict[str, Any], value: Any) -> Any:
        column_type = str(config.get("type", "STRING")).upper()
        if self._is_missing_similarity_value(value):
            return None
        if column_type in {"DATE", "DATE_TIME"}:
            numeric = self.to_float(value)
            if numeric is not None:
                return numeric
            parsed = parse_to_unix(value, get_date_input_formats(config))
            return None if pd.isna(parsed) else float(parsed)
        if column_type in {"INTEGER", "DECIMAL"}:
            return self.to_float(value)
        if column_type == "BOOLEAN":
            return self.coerce_boolean(value, fallback_value=value)
        return str(value).strip()

    @staticmethod
    def _missing_aware_similarity(function: SimilarityFunction) -> SimilarityFunction:
        def similarity(x: Any, y: Any) -> float:
            if x is None or y is None:
                return 1.0 if x is None and y is None else 0.0
            return float(function(x, y))

        return similarity

    @staticmethod
    def _linear_similarity(max_distance: float) -> SimilarityFunction:
        def similarity(x: Any, y: Any) -> float:
            return max(0.0, 1.0 - abs(float(x) - float(y)) / max_distance)

        return similarity

    @staticmethod
    def _equality_similarity(x: Any, y: Any) -> float:
        return 1.0 if x == y else 0.0

    @staticmethod
    def _is_missing_similarity_value(value: Any) -> bool:
        if value is None:
            return True
        if isinstance(value, float) and math.isnan(value):
            return True
        return str(value).strip().lower() in {"", "nan", "null", "none", "<na>", MISSING_VALUE_STRING.lower()}

    def _load_model(self, filepath: str) -> "LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer" = cloudpickle.load(file)
        return model
