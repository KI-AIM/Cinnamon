from __future__ import annotations

import cloudpickle
from typing import Any, Dict, Optional

import pandas as pd

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm import LlmTextSynthesisBase


class LlmNearestNeighborKnowledgeGroundedTextSynthesisSynthesizer(LlmTextSynthesisBase):
    NOT_IMPLEMENTED_KNOWLEDGE_SOURCE = "NOT_IMPLEMENTED"

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self.reference_dataset: Optional[pd.DataFrame] = None
        self._configured_knowledge_source_type: str = self.NOT_IMPLEMENTED_KNOWLEDGE_SOURCE

    def _initialize_anonymization_configuration(self, configuration: Dict[str, Any]) -> None:
        algorithm_config, model_params, training_params = self._initialize_text_synthesis_configuration(
            configuration,
            default_similarity_strategy=self.SIMILARITY_STRATEGY_ATTRIBUTES,
        )
        del algorithm_config, training_params

        knowledge_source_type = str(
            model_params.get("knowledge_source_type", self.NOT_IMPLEMENTED_KNOWLEDGE_SOURCE)
        ).strip() or self.NOT_IMPLEMENTED_KNOWLEDGE_SOURCE
        if knowledge_source_type != self.NOT_IMPLEMENTED_KNOWLEDGE_SOURCE:
            raise ValueError(
                f"Unsupported knowledge_source_type '{knowledge_source_type}'. "
                f"Supported values are: ['{self.NOT_IMPLEMENTED_KNOWLEDGE_SOURCE}']."
            )

        self._configured_knowledge_source_type = knowledge_source_type

    def _build_knowledge_chunks(self, base_row: Dict[str, Any]) -> list[str]:
        del base_row
        return []

    def _knowledge_source_type(self) -> str:
        return self._configured_knowledge_source_type

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
