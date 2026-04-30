import cloudpickle
import difflib
import json
import re
from json import JSONDecodeError
from typing import Any, Dict, List, Optional, Set

import pandas as pd

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm import (
    LlmClient,
    LlmClientConfig,
    create_llm_client,
    load_llm_client_config,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class LlmTextRedactionSynthesizer(TabularDataSynthesizer):
    """
    LLM-based synthesizer that redacts configured identifier categories in TEXT columns
    while keeping non-TEXT columns immutable.
    """

    DEFAULT_SYSTEM_PROMPT = (
        "You are a text redaction assistant. Replace only text spans that match configured redaction "
        "rules. Keep all other content unchanged and return only the redacted text."
    )
    DEFAULT_REDACTION_SCOPE_INSTRUCTION = (
        "Only replace or remove identifiers that match the configured redaction rules listed below. "
        "Do not redact any other identifier categories that are not explicitly configured."
    )
    RESPONSE_TEXT_KEY = "text"
    TOKEN_PATTERN = re.compile(r"\[[^\[\]\s]+\]|[A-Za-z0-9_]+|[^\w\s]", re.UNICODE)
    REPLACEMENT_TOKEN_PATTERN = re.compile(r"\[[^\[\]\s]+\]")

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self._llm_config: Optional[LlmClientConfig] = None
        self._llm_client: Optional[LlmClient] = None
        self._fitting_kwargs: Optional[Dict[str, Any]] = None
        self.synthesizer = None

        self._ordered_column_configs: List[Dict[str, Any]] = []
        self._text_columns: List[str] = []
        self._system_prompt: str = self.DEFAULT_SYSTEM_PROMPT
        self._user_prompt_domain_context: str = ""
        self._redaction_rules: List[Dict[str, str]] = []
        self._allowed_replacement_tokens: Set[str] = set()

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """
        Core logic for initializing anonymization configuration.
        """
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        fitting_params = algorithm_config.get("model_fitting", {})
        self._llm_config = load_llm_client_config(config)
        self._fitting_kwargs = {
            "max_retries": self._llm_config.max_retries,
            "timeout_seconds": self._llm_config.timeout_seconds,
        }
        self._system_prompt = self.DEFAULT_SYSTEM_PROMPT
        self._user_prompt_domain_context = self._normalized_text(fitting_params.get("user_prompt_domain_context"))
        self._redaction_rules = self._load_redaction_rules(algorithm_config.get("redaction_rules"))
        self._allowed_replacement_tokens = {rule["replacement_token"] for rule in self._redaction_rules}

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        """
        Core logic for initializing attribute configuration.
        """
        configurations = attribute_config.get("configurations", [])
        if not configurations:
            raise ValueError("Attribute configuration is empty.")

        self.attribute_config = attribute_config
        self._ordered_column_configs = sorted(configurations, key=lambda cfg: cfg.get("index", float("inf")))

        self._text_columns = [
            cfg["name"]
            for cfg in self._ordered_column_configs
            if str(cfg.get("type", "")).upper() == "TEXT"
        ]

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        """
        Core logic for initializing the dataset.
        """
        self.dataset = df.copy()

    def _initialize_synthesizer(self) -> None:
        """
        Core logic for initializing the synthesizer.
        """
        if self._llm_config is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")

        self._llm_client = create_llm_client(self._llm_config)
        self._llm_client.initialize()
        self.synthesizer = {
            "backend": self._llm_config.provider,
            "model_name": self._llm_config.model_name,
            "mode": "text_redaction",
        }

    def _fit(self) -> None:
        """
        No model training needed; validates required runtime state.
        """
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

    def _sample(self) -> pd.DataFrame:
        """
        Return same number of rows as input dataset; only TEXT columns are transformed.
        """
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")

        transformed = self.dataset.copy()
        if not self._text_columns:
            return transformed

        for row_index, row in transformed.iterrows():
            for text_column in self._text_columns:
                if text_column not in transformed.columns:
                    continue
                original_text = row[text_column]
                transformed_text = self._redact_text(
                    text_column=text_column,
                    original_text=original_text,
                )
                transformed.at[row_index, text_column] = transformed_text

        return transformed

    def _redact_text(
        self,
        text_column: str,
        original_text: Any,
    ) -> Any:
        if self._is_missing_text(original_text):
            return MISSING_VALUE_STRING

        max_retries = int(self._fitting_kwargs["max_retries"]) if self._fitting_kwargs else 1
        original_text_value = str(original_text)
        prompt = self._build_prompt(
            text_column=text_column,
            original_text=original_text_value,
        )

        for _ in range(max_retries):
            try:
                content = self._llm_client.generate_text(
                    prompt,
                    system_prompt=self._system_prompt,
                )
                candidate_text = self._parse_text_response(content)
                if candidate_text is None:
                    continue

                if not self._contains_only_allowed_new_tokens(original_text_value, candidate_text):
                    continue

                if self._was_over_edited(original_text_value, candidate_text):
                    continue

                return candidate_text
            except Exception:  # noqa: BLE001
                continue

        # If all retries fail or outputs look invalid, preserve source text.
        return original_text_value

    def _build_prompt(self, text_column: str, original_text: str) -> str:
        lines = [
            "You are sanitizing one TEXT field.",
            f"Target TEXT column: {text_column}",
        ]

        if self._user_prompt_domain_context:
            lines.append(f"Domain context: {self._user_prompt_domain_context}")
        if self._redaction_rules:
            lines.append(
                "Custom redaction rules: treat each rule name as a semantic category or concept. "
                "If the rule applies, use the exact configured replacement token."
            )
            for rule in self._redaction_rules:
                description = self._normalized_text(rule.get("description"))
                if description:
                    lines.append(
                        f"- {rule['name']} -> {rule['replacement_token']} "
                        f"(guidance: {description})"
                    )
                else:
                    lines.append(f"- {rule['name']} -> {rule['replacement_token']}")

        lines.extend(
            [
                "Task: Redact only configured identifier categories in the ORIGINAL text.",
                "Hard constraints:",
                "1) Keep all wording, structure, language, meaning, and detail exactly as in ORIGINAL except for the spans that must be redacted.",
                f"2) {self._build_scope_instruction()}",
                "3) Replace each matching span with the exact replacement token defined by the matching redaction rule.",
                "4) Do NOT summarize, do NOT output labels/keywords/metadata, and do NOT paraphrase or rewrite unrelated text.",
                "5) Do NOT normalize, correct, reorder, translate, or otherwise improve the text.",
                "6) Do NOT infer or redact categories that are not explicitly configured.",
                "7) If no configured identifier category is present, return the ORIGINAL text unchanged.",
                "8) Do not invent new facts.",
                "Return ONLY valid JSON with this exact shape:",
                '{"text":"..."}',
                "Original text:",
                original_text,
            ]
        )

        return "\n".join(lines)

    def _parse_text_response(self, content: str) -> Optional[str]:
        parsed = self._parse_json_with_fallback(content)
        return self._extract_text_candidate(parsed)

    def _parse_json_with_fallback(self, content: str) -> Optional[Any]:
        try:
            return json.loads(content)
        except JSONDecodeError:
            decoder = json.JSONDecoder()
            for index, char in enumerate(content):
                if char != "{":
                    continue
                try:
                    parsed, _ = decoder.raw_decode(content[index:])
                    return parsed
                except JSONDecodeError:
                    continue
            return None

    def _extract_text_candidate(self, parsed: Any) -> Optional[str]:
        if not isinstance(parsed, dict):
            return None

        text_value = parsed.get(self.RESPONSE_TEXT_KEY)
        if isinstance(text_value, str):
            candidate_text = text_value.strip()
            return candidate_text or None

        rows = parsed.get("rows")
        if isinstance(rows, list):
            for row in rows:
                if not isinstance(row, dict):
                    continue
                row_text = row.get(self.RESPONSE_TEXT_KEY)
                if isinstance(row_text, str) and row_text.strip():
                    return row_text.strip()

        return None

    def _contains_only_allowed_new_tokens(self, original_text: str, candidate_text: str) -> bool:
        original_tokens = set(self.REPLACEMENT_TOKEN_PATTERN.findall(original_text))
        candidate_tokens = set(self.REPLACEMENT_TOKEN_PATTERN.findall(candidate_text))
        introduced_tokens = candidate_tokens - original_tokens
        return introduced_tokens.issubset(self._allowed_replacement_tokens)

    @staticmethod
    def _was_over_edited(original_text: str, candidate_text: str) -> bool:
        original = " ".join(original_text.split())
        candidate = " ".join(candidate_text.split())

        if not candidate:
            return True

        # Block obvious low-information outputs.
        lowered = candidate.lower()
        if lowered in {"abc", "n/a", "na", "***", "*****", "[redacted]", "redacted"}:
            return True

        original_len = len(original)
        candidate_len = len(candidate)

        if original_len >= 30 and candidate_len < max(12, int(original_len * 0.55)):
            return True

        if original_len >= 120 and candidate_len < int(original_len * 0.65):
            return True

        similarity = difflib.SequenceMatcher(None, original, candidate).ratio()
        if original_len >= 60 and similarity < 0.40:
            return True

        original_tokens = original.split()
        candidate_tokens = candidate.split()
        if len(original_tokens) >= 12 and len(candidate_tokens) <= 3:
            return True

        return False

    def _build_scope_instruction(self) -> str:
        if not self._redaction_rules:
            return self.DEFAULT_REDACTION_SCOPE_INSTRUCTION

        configured_rule_names = ", ".join(rule["name"] for rule in self._redaction_rules)
        return (
            f"{self.DEFAULT_REDACTION_SCOPE_INSTRUCTION} "
            f"The configured identifier categories are: {configured_rule_names}."
        )

    @staticmethod
    def _is_missing_text(value: Any) -> bool:
        if pd.isna(value):
            return True
        text = str(value).strip()
        return text == "" or text.lower() in {"nan", "null", "none", "<na>"}

    @staticmethod
    def _normalized_text(value: Any, default: str = "") -> str:
        if value is None:
            return default
        text = str(value).strip()
        return text or default

    @classmethod
    def _load_redaction_rules(cls, value: Any) -> List[Dict[str, str]]:
        if not isinstance(value, list):
            return []

        rules: List[Dict[str, str]] = []
        for item in value:
            if not isinstance(item, dict):
                continue

            name = cls._normalized_text(item.get("name"))
            replacement_token = cls._normalized_text(item.get("replacement_token"))
            description = cls._normalized_text(item.get("description"))
            if not name or not replacement_token:
                continue

            rules.append({
                "name": name,
                "replacement_token": replacement_token,
                "description": description,
            })

        return rules

    def _get_model(self) -> bytes:
        """
        Core logic for serializing the model object.
        """
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "LlmTextRedactionSynthesizer":
        """
        Core logic for loading a serialized synthesizer instance from a file.
        """
        with open(filepath, "rb") as f:
            model: "LlmTextRedactionSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """
        Core logic for saving a data sample to a CSV file.
        """
        sample.to_csv(filename, index=False)
