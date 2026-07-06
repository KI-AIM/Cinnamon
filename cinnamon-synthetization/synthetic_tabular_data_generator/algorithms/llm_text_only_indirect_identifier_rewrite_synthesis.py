from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, Optional

import yaml

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.algorithms.llm_text_only_paraphrase_synthesis import (
    LlmTextOnlyParaphraseSynthesisSynthesizer,
)

_CATEGORY_CONFIG_DIR = Path(__file__).resolve().parents[1] / "PHI_IPI_Configs"


@lru_cache(maxsize=2)
def _load_yaml_config(filename: str) -> dict[str, Any]:
    with (_CATEGORY_CONFIG_DIR / filename).open("r", encoding="utf-8") as handle:
        content = yaml.safe_load(handle) or {}
    if not isinstance(content, dict):
        raise ValueError(f"Invalid YAML structure in {filename}.")
    return content


class LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer(
    LlmTextOnlyParaphraseSynthesisSynthesizer
):
    """
    Rewrite a single TEXT column while generalizing direct and indirect identifiers.
    """

    _SUPPORTED_INDIRECT_LEVELS = {"low", "medium", "high"}
    _DIRECT_IDENTIFIER_RULES = [
        "PHI/direct identifiers must always be removed or replaced, regardless of the selected indirect identifier level.",
        "Never retain the original real-world value of a PHI span in the rewritten text.",
        "When a PHI category below applies, use the category-specific action exactly as specified.",
        "Preserve the surrounding sentence structure and clinical meaning as much as possible after PHI handling.",
        "Do not infer, add, or fabricate new identifying information during PHI handling.",
    ]
    _IPI_GLOBAL_RULES = [
        "For indirect identifiers, prefer abstraction, generalization, or replacement over removal.",
        "Remove information only when it is clearly identifying and cannot be safely abstracted or replaced while preserving clinical coherence.",
        "When numbers, dates, ages, times, weights, heights, dosages, durations, frequencies, or unit-bearing values occur, preserve the original value type, surface format, and unit where possible; generalize or replace the value rather than deleting it or converting it to free text.",
    ]
    _STYLE_RULES = [
        "Use different wording and sentence structure where possible.",
        "Do not merely correct spelling, punctuation, or abbreviations.",
        "Keep fixed medical terms unchanged when paraphrasing would distort meaning.",
        "Avoid vivid or scene-like narrative detail when a clinically equivalent generalized formulation is possible.",
        "Keep the text useful for clinical understanding, research review, and downstream information extraction.",
    ]
    _LEVEL_SPECIFIC_STYLE_RULES = {
        "low": [
            "For LOW level, prioritize clinical fidelity and information preservation over broad generalization.",
        ],
        "high": [
            "Avoid vivid, unusual, or uniquely identifying narrative detail when a clinically equivalent generalized formulation is possible.",
            "Keep the text useful for clinical understanding, research review, and downstream information extraction, but prioritize privacy over fine-grained detail.",
        ],
    }

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._indirect_identifier_level = "medium"

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        training_params = algorithm_config.get("model_fitting", {})

        level = str(training_params.get("indirect_identifier_level", "medium")).strip().lower() or "medium"
        if level not in self._SUPPORTED_INDIRECT_LEVELS:
            raise ValueError(
                "indirect_identifier_level must be one of: low, medium, high."
            )

        super()._initialize_anonymization_configuration(config)
        self._indirect_identifier_level = level

    def _initialize_synthesizer(self) -> None:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")
        self._initialize_llm_backend(mode="text_only_indirect_identifier_rewrite")

    @staticmethod
    def _build_category_rule_block(*, title: str, entries: list[dict[str, Any]]) -> str:
        lines = [f"{title}:"]
        for entry in entries:
            category_id = str(entry.get("id", "")).strip()
            description = str(entry.get("description", "")).strip()
            action = str(entry.get("action", "")).strip()
            if not category_id or not action:
                continue
            lines.append(f"- {category_id}: {description} Action: {action}")
        return "\n".join(lines) + "\n\n"

    def _build_phi_category_block(self) -> str:
        config = _load_yaml_config("phi_categories_with_action.yaml")
        categories = config.get("direct_identifier_categories", [])
        if not isinstance(categories, list):
            raise ValueError("phi_categories_with_action.yaml must define direct_identifier_categories as a list.")
        return self._build_category_rule_block(
            title="PHI categories and required actions (always apply these rules)",
            entries=[entry for entry in categories if isinstance(entry, dict)],
        )

    def _build_ipi_category_block(self) -> str:
        config = _load_yaml_config("ipi_categories_with_action.yaml")
        categories = config.get("ipi_categories", [])
        if not isinstance(categories, list):
            raise ValueError("ipi_categories_with_action.yaml must define ipi_categories as a list.")

        resolved_entries = []
        for entry in categories:
            if not isinstance(entry, dict):
                continue
            actions = entry.get("actions", {})
            if not isinstance(actions, dict):
                continue
            action = str(actions.get(self._indirect_identifier_level, "")).strip()
            if not action:
                continue
            resolved_entries.append(
                {
                    "id": entry.get("id", ""),
                    "description": entry.get("description", ""),
                    "action": action,
                }
            )

        return self._build_category_rule_block(
            title=f"Indirect identifier categories and required actions ({self._indirect_identifier_level.upper()})",
            entries=resolved_entries,
        )

    def _build_prompt_prefix(self) -> str:
        text_column = self._text_columns[0]
        direct_rules_block = self._build_rule_block(
            title="Direct identifier handling rules (always apply these rules)",
            rules=self._DIRECT_IDENTIFIER_RULES,
        )
        ipi_global_rules_block = self._build_rule_block(
            title="Indirect identifier handling rules (apply in all levels)",
            rules=self._IPI_GLOBAL_RULES,
        )
        phi_category_block = self._build_phi_category_block()
        ipi_category_block = self._build_ipi_category_block()
        style_block = self._build_rule_block(
            title="Style rules",
            rules=self._STYLE_RULES + self._LEVEL_SPECIFIC_STYLE_RULES.get(self._indirect_identifier_level, []),
        )
        preservation_line = "Preserve the clinical meaning, but reduce direct and indirect identifiability."
        if self._indirect_identifier_level == "high":
            preservation_line = (
                "Preserve the broad clinical message, but strongly reduce direct and indirect identifiability."
            )

        return (
            "You are an expert clinical de-identification rewriter.\n"
            "Rewrite the TEXT value of one clinical table row into a safer version with lower re-identification risk.\n"
            "Core objectives:\n"
            "- Rewrite the non-missing TEXT field in fluent clinical language.\n"
            f"- {preservation_line}\n"
            "- Always remove or replace direct identifiers.\n"
            "- Always review the text for indirect identifiers and quasi-identifiers.\n"
            "- Apply the selected indirect anonymization level consistently across the whole text.\n"
            "- Never invent new patient-specific facts.\n"
            "- Never mention anonymization, placeholders, rewriting, or de-identification.\n"
            f"- Keep a missing TEXT value as '{MISSING_VALUE_STRING}'.\n"
            f"- selected anonymization level: {self._indirect_identifier_level.upper()}\n"
            "\n"
            f"{direct_rules_block}"
            f"{phi_category_block}"
            f"{ipi_global_rules_block}"
            f"{ipi_category_block}"
            f"{style_block}"
            "\n"
            "Output rules:\n"
            "- Return ONLY valid JSON.\n"
            "- Use exactly this shape: {\"row\": { ... }}\n"
            f"- Include exactly this column in row: {text_column}\n"
            f"- Rewrite only this TEXT column: {text_column}\n"
            f"- For a missing string/text use '{MISSING_VALUE_STRING}'\n"
            "\n"
        )

    @staticmethod
    def _build_rule_block(*, title: str, rules: list[str]) -> str:
        lines = [f"{title}:"]
        lines.extend(f"- {rule}" for rule in rules)
        return "\n".join(lines) + "\n\n"
