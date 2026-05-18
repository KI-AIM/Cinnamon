import cloudpickle
from typing import Any, Dict, Optional

import pandas as pd

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm import LlmTextSynthesisBase


class LlmNearestNeighborFewShotTextSynthesisSynthesizer(LlmTextSynthesisBase):
    """
    LLM-based synthesizer that enriches synthetic tabular rows by generating TEXT values.
    Structured values are preserved by default but may be adjusted by the LLM when needed.
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

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        self._initialize_text_synthesis_configuration(
            config,
            default_similarity_strategy="random",
            default_failure_policy=self.FAILURE_POLICY_FALLBACK_TO_BASE_ROW,
        )

    def _missing_value_string(self) -> str:
        return MISSING_VALUE_STRING

    def _get_model(self) -> bytes:
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "LlmNearestNeighborFewShotTextSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmNearestNeighborFewShotTextSynthesisSynthesizer" = cloudpickle.load(file)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        sample.to_csv(filename, index=False)
