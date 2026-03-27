import cloudpickle
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


class LlmAttributeToTextSynthesizer(TabularDataSynthesizer):
    """
    LLM-based synthesizer that generates one TEXT attribute from immutable tabular attributes.
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
        self._target_text_column: Optional[str] = None
        self._user_prompt: str = (
            "Write a concise clinical note based only on the provided immutable attributes."
        )

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

        configured_target = str(model_params.get("target_text_column", "")).strip()
        self._target_text_column = configured_target or None
        configured_prompt = str(model_params.get("user_prompt", "")).strip()
        if configured_prompt:
            self._user_prompt = configured_prompt

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

        if self._target_text_column is None:
            if len(self._text_columns) != 1:
                raise ValueError(
                    "LLM Attribute-to-Text requires exactly one TEXT target column "
                    "or model_parameter.target_text_column."
                )
            self._target_text_column = self._text_columns[0]

        if self._target_text_column not in self._text_columns:
            raise ValueError(
                f"Target text column '{self._target_text_column}' must exist in attribute configuration "
                "and be typed as TEXT."
            )

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
            "mode": "attribute_to_text",
        }

    def _fit(self) -> None:
        """
        No training needed; validates required runtime state.
        """
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        if self._target_text_column is None:
            raise ValueError("Target text column is not configured.")

    def _sample(self) -> pd.DataFrame:
        """
        Keep input row count; generate only the target TEXT column.
        """
        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")
        if self._llm_client is None:
            raise ValueError("LLM client is not initialized.")
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        if self._target_text_column is None:
            raise ValueError("Target text column is not configured.")

        result = self.dataset.copy()
        if self._target_text_column not in result.columns:
            raise ValueError(
                f"Target text column '{self._target_text_column}' does not exist in the input dataset."
            )

        immutable_columns = [col for col in result.columns if col != self._target_text_column]
        for row_index, row in result.iterrows():
            immutable_attributes = {
                column_name: self._to_json_value(row[column_name])
                for column_name in immutable_columns
            }
            generated_text = self._generate_text(immutable_attributes)
            result.at[row_index, self._target_text_column] = generated_text

        return result

    def _generate_text(self, immutable_attributes: Dict[str, Any]) -> str:
        max_retries = int(self._fitting_kwargs["max_retries"]) if self._fitting_kwargs else 1
        prompt = self._build_prompt(immutable_attributes)

        for _ in range(max_retries):
            try:
                content = self._llm_client.generate_text(prompt)
                parsed = self._parse_text_response(content)
                if parsed is not None and parsed.strip():
                    return parsed.strip()
            except Exception:  # noqa: BLE001
                continue

        return MISSING_VALUE_STRING

    def _build_prompt(self, immutable_attributes: Dict[str, Any]) -> str:
        attributes_json = json.dumps(immutable_attributes, ensure_ascii=True)
        return (
            "You are generating one TEXT value for a tabular row.\n"
            f"Target TEXT column: {self._target_text_column}\n"
            f"User instruction: {self._user_prompt}\n"
            f"Immutable attributes JSON (must not be changed): {attributes_json}\n"
            "Rules:\n"
            "1) Use only information from immutable attributes and user instruction.\n"
            "2) Do not modify, invent, or contradict immutable attribute values.\n"
            "3) Return exactly one text value.\n"
            "Return ONLY valid JSON in this exact format:\n"
            '{"text":"..."}'
        )

    def _parse_text_response(self, content: str) -> Optional[str]:
        parsed = self._parse_json_with_fallback(content)
        if isinstance(parsed, dict):
            value = parsed.get("text")
            if isinstance(value, str):
                return value
        if isinstance(parsed, str):
            return parsed
        return None

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

    def _load_model(self, filepath: str) -> "LlmAttributeToTextSynthesizer":
        """
        Core logic for loading a serialized synthesizer instance from a file.
        """
        with open(filepath, "rb") as f:
            model: "LlmAttributeToTextSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """
        Core logic for saving a data sample to a CSV file.
        """
        sample.to_csv(filename, index=False)
