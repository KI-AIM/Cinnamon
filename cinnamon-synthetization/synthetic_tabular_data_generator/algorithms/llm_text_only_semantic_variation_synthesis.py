from __future__ import annotations

import json
from typing import Any, Dict, List, Optional

import pandas as pd

from data_processing.utils import FAILED_TEXT_GENERATION, MISSING_VALUE_STRING
from synthetic_tabular_data_generator.algorithms.llm_text_only_paraphrase_synthesis import (
    LlmTextOnlyParaphraseSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.llm.response_validation import require_first_dict_row


class LlmTextOnlySemanticVariationSynthesisSynthesizer(LlmTextOnlyParaphraseSynthesisSynthesizer):
    """
    Generate new but semantically related TEXT-only rows for fictional patients.
    """

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._required_attributes: list[dict[str, str]] = []

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        super()._initialize_anonymization_configuration(config)
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        training_params = algorithm_config.get("model_fitting", {})
        self._required_attributes = self._normalize_required_attributes(
            training_params.get("required_attributes", []),
        )

    def _initialize_synthesizer(self) -> None:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")
        self._initialize_llm_backend(mode="text_only_semantic_variation")

    def _sample(self) -> pd.DataFrame:
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        source = self.dataset.copy().reset_index(drop=True)
        num_samples = self._resolve_num_samples(len(source), allow_exceed_default=True)
        replace = num_samples > len(source)
        source = source.sample(n=num_samples, replace=replace).reset_index(drop=True)

        rows = source.to_dict(orient="records")
        total = len(rows)
        self._sample_start_time = pd.Timestamp.utcnow().timestamp()
        self._reset_generation_counters()

        generated_rows = []
        for row_index, row in enumerate(rows):
            generated_rows.append(self._generate_related_row(row, row_index, total))
            self.report_remaining_time(self._sample_start_time, len(generated_rows), total)

        ordered_columns = [cfg["name"] for cfg in self._ordered_column_configs]
        generated = pd.DataFrame(generated_rows)
        for column_name in ordered_columns:
            if column_name not in generated.columns:
                generated[column_name] = pd.NA
        return generated[ordered_columns]

    def _generate_related_row(self, base_row: Dict[str, Any], row_index: int, total_rows: int) -> Dict[str, Any]:
        if self._fitting_kwargs is None or self._llm_client is None:
            raise ValueError("Synthesizer is not initialized for LLM sampling.")
        if self._row_has_no_rewritable_text(base_row):
            return self._coerce_row(base_row)

        max_retries = self._fitting_kwargs["max_retries"]
        last_error: Optional[Exception] = None

        for attempt_index in range(max_retries):
            try:
                prompt = self._build_rewrite_prompt(base_row)
                content = self._llm_client.generate_text(prompt)
                parsed = self.parse_json_with_fallback(content)
                candidate = require_first_dict_row(parsed)
                merged = self._merge_rewritten_row(base_row, candidate)
                rewritten = self._coerce_row(merged)
                if self._is_verbatim_copy(base_row, rewritten):
                    raise ValueError("LLM returned the source text unchanged.")
                return rewritten
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                self._log_generation_attempt_failure(
                    mode="TEXT_REWRITE",
                    row_index=row_index,
                    total_rows=total_rows,
                    attempt_index=attempt_index,
                    max_retries=max_retries,
                    error=exc,
                )

        return self._handle_generation_failure(
            message=(
                f"LLM returned no valid semantic text row for sample {row_index + 1}/{total_rows} "
                f"after {max_retries} attempts."
            ),
            last_error=last_error,
            fallback_factory=lambda: self._coerce_failed_generation_row(base_row),
        )

    def _build_prompt_prefix(self) -> str:
        text_column = self._text_columns[0]
        domain_context = ""
        if self._user_prompt_domain_context:
            domain_context = f"Domain context: {self._user_prompt_domain_context}\n"
        required_attributes_block = ""
        if self._required_attributes:
            lines = []
            for item in self._required_attributes:
                name = item["name"]
                description = item["description"]
                if description:
                    lines.append(f"- {name}: {description}")
                else:
                    lines.append(f"- {name}")
            required_attributes_block = (
                "Required attributes that must be mentioned explicitly in the generated text:\n"
                + "\n".join(lines)
                + "\n"
            )

        return (
            "You generate a new TEXT value for a fictional patient based on a source table row.\n"
            f"{domain_context}"
            "Important:\n"
            "- The TEXT field contains source content from one real case.\n"
            "- Generate a new but semantically related fictional case.\n"
            "- The generated text should stay in the same medical domain, topic, and level of specificity.\n"
            "- It may add, omit, or change details when the result stays plausible and similar in content.\n"
            "- The generated patient must be fictional and should not be a copy of the source case.\n"
            "- Keep the language, tone, and style close to the source text.\n"
            "- Avoid copying sentences verbatim from the source.\n"
            "- Keep short fixed technical terms unchanged when needed for plausibility.\n"
            "- Do not mention that the text is synthetic, generated, fictional, or paraphrased.\n"
            f"- Keep a missing TEXT value as '{MISSING_VALUE_STRING}'.\n"
            "- Cover every required attribute in the generated text.\n"
            "- Treat attribute descriptions as the requirement; do not force the literal attribute label into the text unless it fits naturally.\n"
            f"{required_attributes_block}"
            "Output rules:\n"
            "- Return ONLY valid JSON.\n"
            "- Use exactly this shape: {\"row\": { ... }}\n"
            f"- Include exactly this column in row: {text_column}\n"
            f"- Generate a value only for this TEXT column: {text_column}\n"
            f"- For a missing string/text use '{MISSING_VALUE_STRING}'\n"
            "\n"
        )

    @staticmethod
    def _normalize_required_attributes(raw_value: Any) -> list[dict[str, str]]:
        return LlmTextOnlyParaphraseSynthesisSynthesizer._normalize_named_attributes(
            raw_value,
            field_name="required_attributes",
        )

    @classmethod
    def suggest_required_attributes(
        cls,
        *,
        attribute_configuration: Dict[str, Any],
        algorithm_configuration: Dict[str, Any],
        dataset: pd.DataFrame,
        max_examples: int = 10,
    ) -> list[dict[str, str]]:
        return cls._suggest_named_attributes_from_examples(
            attribute_configuration=attribute_configuration,
            algorithm_configuration=algorithm_configuration,
            dataset=dataset,
            max_examples=max_examples,
            synthesizer_name="llm_text_only_semantic_variation_synthesis",
            field_name="required_attributes",
            prompt_builder=cls._build_required_attribute_suggestion_prompt,
        )

    @staticmethod
    def _build_required_attribute_suggestion_prompt(
        text_column: str,
        examples: list[str],
        algorithm_configuration: Dict[str, Any],
    ) -> str:
        training_params = (
            algorithm_configuration.get("synthetization_configuration", {})
            .get("algorithm", {})
            .get("model_fitting", {})
        )
        domain_context = str(training_params.get("user_prompt_domain_context", "")).strip()
        domain_block = f"Domain context: {domain_context}\n" if domain_context else ""
        example_lines = [
            f"{index + 1}. {json.dumps(example, ensure_ascii=False)}"
            for index, example in enumerate(examples)
        ]
        return (
            "You propose required_attributes entries for semantic medical text generation.\n"
            f"{domain_block}"
            "Task:\n"
            "- Read the example texts from one TEXT column.\n"
            "- Suggest short, reusable attribute names and optional descriptions that a user can use to steer future text generation.\n"
            "- Focus on medically relevant concepts, entities, findings, procedures, therapies, measurements, or document anchors that recur or would be useful control knobs.\n"
            "- Prefer canonical names over literal wording from one example.\n"
            "- Keep the list compact and practical.\n"
            "- Do not invent patient-specific values.\n"
            "- If an attribute name would be unclear alone, add a short description.\n"
            "- Return at most 12 attributes.\n"
            "Output rules:\n"
            "- Return ONLY valid JSON.\n"
            "- Use exactly this shape: {\"required_attributes\": [{\"name\": \"...\", \"description\": \"...\"}]}\n"
            "- Every entry must contain a non-empty name.\n"
            f"- The source column name is: {text_column}\n"
            "\n"
            "EXAMPLE TEXTS\n"
            "----------------------------------------\n"
            f"{chr(10).join(example_lines)}\n"
        )

    @staticmethod
    def _extract_required_attributes(parsed_json: Any) -> list[dict[str, Any]]:
        return LlmTextOnlyParaphraseSynthesisSynthesizer._extract_named_attributes(
            parsed_json,
            field_name="required_attributes",
        )

    @staticmethod
    def _deduplicate_required_attributes(items: list[dict[str, str]]) -> list[dict[str, str]]:
        return LlmTextOnlyParaphraseSynthesisSynthesizer._deduplicate_named_attributes(items)
