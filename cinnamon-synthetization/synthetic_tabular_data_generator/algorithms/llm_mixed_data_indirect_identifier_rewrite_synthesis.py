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
    """Rewrite reference identifiers while preserving synthetic structured ground truth."""

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
        self._initialize_llm_backend(mode="mixed_data_indirect_identifier_rewrite")

    def _build_prompt_prefix(self) -> str:
        return LlmMixedDataParaphraseSynthesisSynthesizer._build_prompt_prefix(self) + (
            "Identifier handling applies ONLY to additional reference details absent from the ground truth.\n"
            "Replace reference identifiers covered by structured columns with the exact synthetic values.\n"
            "The following category actions must never remove, replace or generalize REQUIRED FACTS.\n"
            f"Selected anonymization level: {self._indirect_identifier_level.upper()}\n"
            f"{self._build_phi_category_block()}"
            f"{self._build_ipi_category_block()}"
        )

    def _load_model(self, filepath: str) -> "LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer":
        with open(filepath, "rb") as file:
            model: "LlmMixedDataIndirectIdentifierRewriteSynthesisSynthesizer" = cloudpickle.load(file)
        return model
