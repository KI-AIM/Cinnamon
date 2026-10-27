from __future__ import annotations

from typing import Any, Dict, Optional

from synthetic_tabular_data_generator.algorithms.llm_mixed_data_paraphrase_synthesis import (
    LlmMixedDataParaphraseSynthesisSynthesizer,
)


class LlmMixedDataExpertGuidedSynthesisSynthesizer(
    LlmMixedDataParaphraseSynthesisSynthesizer
):
    """Generate text from structured ground truth and fixed expert guidance."""

    _SYNTHESIZER_NAME = "llm_mixed_data_expert_guided_synthesis"

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._expert_guidance = ""

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        _, _, training_params = self._initialize_common_llm_configuration(
            config,
            default_few_shot_rows=0,
        )
        expert_guidance = training_params.get("expert_guidance")
        if not isinstance(expert_guidance, str) or not expert_guidance.strip():
            raise ValueError("expert_guidance must contain expert instructions or anonymized examples.")
        self._expert_guidance = expert_guidance.strip()

    def _initialize_synthesizer(self) -> None:
        self._initialize_llm_backend(mode="mixed_data_expert_guided")

    def _build_prompt_prefix(self) -> str:
        text_column = self._text_columns[0]
        return (
            "Generate one TEXT value from synthetic structured ground truth.\n"
            f"Domain context: {self._user_prompt_domain_context}\n\n"
            "- The structured row is authoritative. Include every non-missing attribute naturally, preserving its exact meaning, values, units, negations and temporal relations. Do not infer missing values.\n"
            "- Follow expert guidance for language, terminology, style and structure, unless it conflicts with the structured row or output rules.\n"
            "- Use examples only for writing style; do not copy their case-specific facts. Do not invent facts to fill requested sections.\n"
            "- Generate even if the input TEXT is missing. Use the language specified by the guidance or domain context.\n"
            f"- Return only valid JSON: {{\"row\":{{\"{text_column}\":\"...\"}}}}.\n\n"
        )

    def _fit(self) -> None:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self.dataset.empty:
            raise ValueError("Dataset must contain at least one row.")
        self._prompt_prefix = self._build_prompt_prefix()

    @staticmethod
    def _guidance_usage_rules() -> str:
        return (
            "- EXPERT GUIDANCE defines only the desired language, style, document structure and reusable wording.\n"
            "- Follow its instructions and schema unless they conflict with the structured ground truth or output rules.\n"
            "- Treat example texts only as style examples. Never copy their patient facts, identifiers, dates or case-specific details.\n"
            "- Do not add factual information from an example unless it is also present in the structured ground truth.\n"
        )

    def _guidance_prompt_block(self, base_row: Dict[str, Any]) -> str:
        return (
            "EXPERT GUIDANCE (style, structure and examples only)\n"
            "----------------------------------------\n"
            f"{self._expert_guidance}\n"
            "----------------------------------------\n"
            "The structured ground truth and output rules above take precedence over this guidance.\n"
        )
