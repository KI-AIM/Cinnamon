from __future__ import annotations

from typing import Any, Dict, Optional

import cloudpickle

from synthetic_tabular_data_generator.algorithms.llm_mixed_data_paraphrase_synthesis import (
    LlmMixedDataParaphraseSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.algorithms.llm_text_only_indirect_identifier_rewrite_synthesis import (
    LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer,
)


class LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer(
    LlmMixedDataParaphraseSynthesisSynthesizer,
    LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer,
):
    """De-identify one TEXT column, then align the structured columns with it."""

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer._initialize_anonymization_configuration(
            self,
            config,
        )
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        self._initialize_profile_rows_configuration(algorithm_config.get("model_parameter", {}))

    def _initialize_synthesizer(self) -> None:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")
        self._initialize_llm_backend(mode="mixed_data_indirect_identifier_rewrite_consistency")

    def _load_model(self, filepath: str) -> "LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer" = cloudpickle.load(file)
        return model
