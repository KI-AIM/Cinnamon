from __future__ import annotations

from typing import Any, Dict, Optional

import pandas as pd

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class LlmKnowledgeGroundedTextSynthesisSynthesizer(TabularDataSynthesizer):
    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)

    @staticmethod
    def _not_implemented() -> None:
        raise NotImplementedError(
            "The synthesizer 'llm_knowledge_grounded_text_synthesis' is registered but not implemented yet."
        )

    def _initialize_anonymization_configuration(self, configuration: Dict[str, Any]) -> None:
        self._not_implemented()

    def _initialize_attribute_configuration(self, configuration: Dict[str, Any]) -> None:
        self._not_implemented()

    def _initialize_dataset(self, dataset: pd.DataFrame) -> None:
        self._not_implemented()

    def _initialize_synthesizer(self) -> None:
        self._not_implemented()

    def _fit(self) -> None:
        self._not_implemented()

    def _sample(self) -> pd.DataFrame:
        self._not_implemented()

    def _get_model(self) -> bytes:
        self._not_implemented()

    def _load_model(self, filepath: str) -> "LlmKnowledgeGroundedTextSynthesisSynthesizer":
        self._not_implemented()

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        self._not_implemented()
