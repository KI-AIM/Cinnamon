import cloudpickle
import difflib
import json
from json import JSONDecodeError
from typing import Any, Dict, List, Optional

import pandas as pd

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm import (
    LlmClient,
    LlmClientConfig,
    create_llm_client,
    load_llm_client_config,
)
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class LlmTextDeIdentificationSynthesizer(TabularDataSynthesizer):
    """
    LLM-based synthesizer that de-identifies TEXT columns while keeping non-TEXT columns immutable.
    """

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
        self._non_text_columns: List[str] = []
        self._pii_replacement_token: str = "[REDACTED]"

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """
        Core logic for initializing anonymization configuration.
        """
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        model_params = algorithm_config.get("model_parameter", {})
        self._llm_config = load_llm_client_config(config)
        self._fitting_kwargs = {
            "max_retries": self._llm_config.max_retries,
            "timeout_seconds": self._llm_config.timeout_seconds,
        }
        self._pii_replacement_token = str(model_params.get("pii_replacement_token", "[REDACTED]"))

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
        self._non_text_columns = [
            cfg["name"]
            for cfg in self._ordered_column_configs
            if str(cfg.get("type", "")).upper() != "TEXT"
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
            "mode": "text_de_identification",
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
            immutable_attributes = {
                column_name: self._to_json_value(row[column_name])
                for column_name in self._non_text_columns
                if column_name in transformed.columns
            }
            for text_column in self._text_columns:
                if text_column not in transformed.columns:
                    continue
                original_text = row[text_column]
                transformed_text = self._de_identify_text(
                    text_column=text_column,
                    original_text=original_text,
                    immutable_attributes=immutable_attributes,
                )
                transformed.at[row_index, text_column] = transformed_text

        return transformed

    def _de_identify_text(
        self,
        text_column: str,
        original_text: Any,
        immutable_attributes: Dict[str, Any],
    ) -> Any:
        if self._is_missing_text(original_text):
            return MISSING_VALUE_STRING

        max_retries = int(self._fitting_kwargs["max_retries"]) if self._fitting_kwargs else 1
        prompt = self._build_prompt(
            text_column=text_column,
            original_text=str(original_text),
            immutable_attributes=immutable_attributes,
        )

        for _ in range(max_retries):
            try:
                content = self._llm_client.generate_text(prompt)
                parsed = self._parse_text_response(content)
                if parsed is None:
                    continue

                candidate_text = parsed.strip()
                if not candidate_text:
                    continue

                if self._was_over_edited(str(original_text), candidate_text):
                    continue

                return candidate_text
            except Exception:  # noqa: BLE001
                continue

        # If all retries fail or outputs look over-edited, preserve source text.
        return str(original_text)

    def _build_prompt(self, text_column: str, original_text: str, immutable_attributes: Dict[str, Any]) -> str:
        immutable_json = json.dumps(immutable_attributes, ensure_ascii=True)
        return (
            "You are sanitizing one TEXT field.\n"
            f"Immutable tabular attributes (DO NOT MODIFY these values): {immutable_json}\n"
            f"Target TEXT column: {text_column}\n"
            f"PII replacement token: {self._pii_replacement_token}\n"
            "Task: De-identify the ORIGINAL text with minimal edits only.\n"
            "Hard constraints:\n"
            "1) Keep wording, structure, length, medical meaning, and detail level as close as possible to ORIGINAL.\n"
            "2) Replace or remove ONLY direct PII spans (names, phone numbers, emails, exact addresses, IDs).\n"
            "3) If there is an attribute contradiction, edit ONLY the contradictory span; do not rewrite unrelated text.\n"
            "4) Do NOT summarize, do NOT output labels/keywords/metadata, and do NOT paraphrase globally.\n"
            "5) Do NOT replace the narrative with attribute values.\n"
            "6) If no PII and no contradiction are present, return the ORIGINAL text unchanged.\n"
            "7) Do not invent new facts.\n"
            "Return ONLY valid JSON with this exact shape:\n"
            '{"text":"..."}\n'
            f"Original text:\n{original_text}"
        )

    def _parse_text_response(self, content: str) -> Optional[str]:
        parsed = self._parse_json_with_fallback(content)
        if isinstance(parsed, dict):
            text_value = parsed.get("text")
            if isinstance(text_value, str):
                return text_value
        if isinstance(parsed, str):
            return parsed
        return None

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

    @staticmethod
    def _parse_json_with_fallback(content: str) -> Any:
        try:
            return json.loads(content)
        except JSONDecodeError:
            decoder = json.JSONDecoder()
            for index, char in enumerate(content):
                if char not in ('{', '[', '"'):
                    continue
                try:
                    parsed, _ = decoder.raw_decode(content[index:])
                    return parsed
                except JSONDecodeError:
                    continue
            return content

    @staticmethod
    def _is_missing_text(value: Any) -> bool:
        if pd.isna(value):
            return True
        text = str(value).strip()
        return text == "" or text.lower() in {"nan", "null", "none", "<na>"}

    @staticmethod
    def _to_json_value(value: Any) -> Any:
        if pd.isna(value):
            return None
        if isinstance(value, (str, int, float, bool)) or value is None:
            return value
        return str(value)

    def _get_model(self) -> bytes:
        """
        Core logic for serializing the model object.
        """
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "LlmTextDeIdentificationSynthesizer":
        """
        Core logic for loading a serialized synthesizer instance from a file.
        """
        with open(filepath, "rb") as f:
            model: "LlmTextDeIdentificationSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """
        Core logic for saving a data sample to a CSV file.
        """
        sample.to_csv(filename, index=False)
