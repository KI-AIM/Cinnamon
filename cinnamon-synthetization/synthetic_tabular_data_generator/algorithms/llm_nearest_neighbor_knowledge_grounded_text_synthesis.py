from __future__ import annotations

import cloudpickle
import re
from typing import Any, Dict, List, Optional

import pandas as pd

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm import LlmTextSynthesisBase


class LlmNearestNeighborKnowledgeGroundedTextSynthesisSynthesizer(LlmTextSynthesisBase):
    SUPPORTED_KNOWLEDGE_SOURCE_TYPES = {
        "none",
        "ontology",
        "taxonomy",
        "guideline",
        "local_terminology",
        "rag_index",
    }

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self.reference_dataset: Optional[pd.DataFrame] = None
        self._configured_knowledge_source_type: str = "none"
        self._max_knowledge_chunks: int = 0
        self._knowledge_context_chunks: List[str] = []
        self._knowledge_catalog: List[str] = []

    def _initialize_anonymization_configuration(self, configuration: Dict[str, Any]) -> None:
        algorithm_config, model_params, training_params = self._initialize_text_synthesis_configuration(
            configuration,
            default_similarity_strategy="structured_attributes",
            default_failure_policy=self.FAILURE_POLICY_FALLBACK_TO_BASE_ROW,
        )
        del algorithm_config

        knowledge_source_type = str(model_params.get("knowledge_source_type", "none")).strip().lower() or "none"
        if knowledge_source_type not in self.SUPPORTED_KNOWLEDGE_SOURCE_TYPES:
            raise ValueError(
                f"Unsupported knowledge_source_type '{knowledge_source_type}'. Supported values are: "
                f"{sorted(self.SUPPORTED_KNOWLEDGE_SOURCE_TYPES)}."
            )

        self._configured_knowledge_source_type = knowledge_source_type
        self._max_knowledge_chunks = max(0, int(model_params.get("max_knowledge_chunks", 5)))
        self._knowledge_context_chunks = self._split_knowledge_context(training_params.get("knowledge_context", ""))

    def _fit(self) -> None:
        super()._fit()
        self._knowledge_catalog = self._build_local_knowledge_catalog()

    def _build_knowledge_chunks(self, base_row: Dict[str, Any]) -> List[str]:
        if self._configured_knowledge_source_type == "none" or self._max_knowledge_chunks <= 0:
            return []

        prioritized_chunks = list(self._knowledge_context_chunks)
        catalog_candidates = list(self._knowledge_catalog)

        if not prioritized_chunks and self._configured_knowledge_source_type != "local_terminology":
            prioritized_chunks = []

        row_terms = self._extract_row_terms(base_row)
        ranked_catalog_chunks = self._rank_knowledge_chunks(catalog_candidates, row_terms)

        selected_chunks: List[str] = []
        for chunk in prioritized_chunks + ranked_catalog_chunks:
            if chunk in selected_chunks:
                continue
            selected_chunks.append(chunk)
            if len(selected_chunks) >= self._max_knowledge_chunks:
                break

        return selected_chunks

    def _knowledge_source_type(self) -> str:
        return self._configured_knowledge_source_type

    def _build_local_knowledge_catalog(self) -> List[str]:
        catalog: List[str] = []
        for config in self._ordered_column_configs:
            column_name = config["name"]
            column_type = str(config.get("type", "STRING")).upper()
            profile = self._column_profiles.get(column_name, {})
            profile_line = self.build_profile_line(column_name, column_type, profile).lstrip("- ").strip()
            catalog.append(f"Observed local pattern for {column_name}: {profile_line}")
        return catalog

    @staticmethod
    def _split_knowledge_context(raw_context: Any) -> List[str]:
        if raw_context is None:
            return []

        text = str(raw_context).strip()
        if not text:
            return []

        chunks = [chunk.strip() for chunk in re.split(r"\n\s*\n", text) if chunk.strip()]
        return chunks or [text]

    def _extract_row_terms(self, base_row: Dict[str, Any]) -> set[str]:
        tokens: set[str] = set()
        for key, value in base_row.items():
            tokens.update(self._tokenize_for_matching(key))
            if value is None:
                continue
            value_as_text = str(value).strip()
            if not value_as_text or value_as_text == MISSING_VALUE_STRING:
                continue
            tokens.update(self._tokenize_for_matching(value_as_text))
        return tokens

    @staticmethod
    def _tokenize_for_matching(text: str) -> set[str]:
        return {token for token in re.findall(r"[A-Za-z0-9_]+", text.lower()) if token}

    def _rank_knowledge_chunks(self, candidates: List[str], row_terms: set[str]) -> List[str]:
        scored_chunks = []
        for index, chunk in enumerate(candidates):
            chunk_terms = self._tokenize_for_matching(chunk)
            overlap_score = len(row_terms.intersection(chunk_terms))
            scored_chunks.append((overlap_score, -index, chunk))

        scored_chunks.sort(reverse=True)

        deduplicated: List[str] = []
        seen = set()
        for _score, _neg_index, chunk in scored_chunks:
            if chunk in seen:
                continue
            seen.add(chunk)
            deduplicated.append(chunk)
        return deduplicated

    def _missing_value_string(self) -> str:
        return MISSING_VALUE_STRING

    def _get_model(self) -> bytes:
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "LlmNearestNeighborKnowledgeGroundedTextSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmNearestNeighborKnowledgeGroundedTextSynthesisSynthesizer" = cloudpickle.load(file)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        sample.to_csv(filename, index=False)
