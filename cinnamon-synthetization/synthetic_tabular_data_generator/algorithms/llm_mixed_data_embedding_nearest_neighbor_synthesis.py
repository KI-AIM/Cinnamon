from __future__ import annotations

import math
from typing import Any, Callable, Dict, Optional

import cloudpickle
import pandas as pd

from data_processing.utils import (
    MISSING_VALUE_STRING,
    get_date_format,
    parse_to_unix,
)
from synthetic_tabular_data_generator.algorithms.llm_mixed_data_paraphrase_synthesis import (
    LlmMixedDataParaphraseSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.algorithms.llm_text_only_embedding_nearest_neighbor_synthesis import (
    LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer,
)


SimilarityFunction = Callable[[Any, Any], float]


class LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer(
    LlmMixedDataParaphraseSynthesisSynthesizer,
    LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer,
):
    """Retrieve references for synthetic structured rows and generate matching text."""

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._text_similarity_weight = 0.7
        self._structured_similarity_weight = 0.3
        self._structured_similarity_functions: Dict[str, SimilarityFunction] = {}

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer._initialize_anonymization_configuration(
            self,
            config,
        )
        model_params = config["synthetization_configuration"]["algorithm"].get("model_parameter", {})
        self._initialize_profile_rows_configuration(model_params)
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
        LlmMixedDataParaphraseSynthesisSynthesizer._fit(self)
        if self._text_similarity_weight > 0:
            self._fit_embedding_index()
        else:
            self._fit_reference_rows_without_embeddings()
        self._build_structured_similarity_functions()

    def _fit_reference_rows_without_embeddings(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        reference_df = self.reference_dataset if self.reference_dataset is not None else self.dataset
        text_column = self._text_columns[0]
        self._reference_rows = [
            row
            for row in reference_df.to_dict(orient="records")
            if self.coerce_text(row.get(text_column)) != MISSING_VALUE_STRING
        ]
        self._reference_vectors = [None] * len(self._reference_rows)

    def _build_prompt_prefix(self) -> str:
        return super()._build_prompt_prefix() + (
            "- Use nearest-neighbor reference texts to construct a coherent new case around the fixed ground truth.\n"
            "- Vary wording and narrative structure while keeping every required fact unchanged.\n\n"
        )

    def _neighbor_examples(self, base_row: Dict[str, Any]) -> list[str]:
        if self._few_shot_examples <= 0 or not self._reference_rows or not self._reference_vectors:
            return []

        text_column = self._text_columns[0]
        query_text = "; ".join(self._required_facts(base_row))

        query_vector = self._encode_query(query_text) if self._text_similarity_weight > 0 else None
        scored_examples: list[tuple[float, str]] = []
        for row, reference_vector in zip(self._reference_rows, self._reference_vectors):
            reference_text = self.coerce_text(row.get(text_column))
            if reference_text == MISSING_VALUE_STRING:
                continue
            if self._exclude_self_match and reference_text == self.coerce_text(base_row.get(text_column)):
                continue

            text_similarity = (
                self._score_vectors(query_vector, reference_vector)
                if query_vector is not None
                else 0.0
            )
            structured_similarity = self._score_structured_rows(base_row, row)
            total_weight = self._text_similarity_weight + self._structured_similarity_weight
            combined_similarity = (
                self._text_similarity_weight * text_similarity
                + self._structured_similarity_weight * structured_similarity
            ) / total_weight
            scored_examples.append((combined_similarity, reference_text))

        scored_examples.sort(key=lambda item: item[0], reverse=True)
        return [text for _, text in scored_examples[: self._few_shot_examples]]

    def _build_structured_similarity_functions(self) -> None:
        functions: Dict[str, SimilarityFunction] = {}
        for config in self._structured_column_configs:
            name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            if column_type in {"INTEGER", "DECIMAL", "DATE"}:
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

    def _score_structured_rows(self, query_row: Dict[str, Any], reference_row: Dict[str, Any]) -> float:
        scores = []
        for config in self._structured_column_configs:
            name = config["name"]
            function = self._structured_similarity_functions.get(name)
            if function is None:
                continue
            query_value = self._normalize_structured_similarity_value(config, query_row.get(name))
            reference_value = self._normalize_structured_similarity_value(config, reference_row.get(name))
            scores.append(float(function(query_value, reference_value)))
        return sum(scores) / len(scores) if scores else 0.0

    def _normalize_structured_similarity_value(self, config: Dict[str, Any], value: Any) -> Any:
        column_type = str(config.get("type", "STRING")).upper()
        if self._is_missing_similarity_value(value):
            return None
        if column_type == "DATE":
            numeric = self.to_float(value)
            if numeric is not None:
                return numeric
            parsed = parse_to_unix(value, get_date_format(config))
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
