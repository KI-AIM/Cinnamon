import json
from json import JSONDecodeError
from typing import Any, Dict, List, Optional

import pandas as pd

from data_processing.utils import TEXT_PENDING_LLM
from synthetic_tabular_data_generator.llm import create_llm_client, load_llm_client_config


class LlmTextSynthesizer:
    """
    Internal LLM-based synthesizer for TEXT columns only.
    """

    MAX_EXAMPLES_PER_COLUMN = 5

    def __init__(self, algorithm_config: Dict[str, Any]) -> None:
        self._algorithm_config = algorithm_config
        self._llm_config = load_llm_client_config(algorithm_config)
        self._llm_client = create_llm_client(self._llm_config)
        self._examples_by_column: Dict[str, List[str]] = {}

    @classmethod
    def from_algorithm_config(cls, algorithm_config: Dict[str, Any]) -> "LlmTextSynthesizer":
        return cls(algorithm_config)

    def initialize(self) -> None:
        self._llm_client.initialize()

    def fit(self, source_df: pd.DataFrame, text_columns: List[str]) -> None:
        self._examples_by_column = {}

        for column_name in text_columns:
            if column_name not in source_df.columns:
                self._examples_by_column[column_name] = []
                continue

            values = source_df[column_name].dropna().astype(str).str.strip()
            values = values[(values != "") & (values != TEXT_PENDING_LLM)]
            self._examples_by_column[column_name] = values.head(self.MAX_EXAMPLES_PER_COLUMN).tolist()

    def generate(
        self,
        base_df: pd.DataFrame,
        text_columns: List[str],
        context_columns: List[str],
    ) -> pd.DataFrame:
        result = base_df.copy()

        for column_name in text_columns:
            if column_name not in result.columns:
                result[column_name] = pd.NA

        for row_index, row in result.iterrows():
            context = {
                column_name: self._to_json_value(row[column_name])
                for column_name in context_columns
                if column_name in result.columns
            }

            for text_column in text_columns:
                generated_text = self._generate_single_text(text_column=text_column, context=context)
                result.at[row_index, text_column] = generated_text

        return result

    def _generate_single_text(self, text_column: str, context: Dict[str, Any]) -> str:
        examples = self._examples_by_column.get(text_column, [])

        for _ in range(self._llm_config.max_retries):
            prompt = self._build_text_prompt(text_column=text_column, context=context, examples=examples)

            try:
                content = self._llm_client.generate_text(prompt)
                text = self._parse_text_response(content)
                if text is not None and text.strip():
                    return text.strip()
            except Exception:  # noqa: BLE001
                continue

        return TEXT_PENDING_LLM

    def _build_text_prompt(self, text_column: str, context: Dict[str, Any], examples: List[str]) -> str:
        examples_block = ""
        if examples:
            examples_json = json.dumps(examples, ensure_ascii=True)
            examples_block = f"\nReference style examples for '{text_column}': {examples_json}\n"

        context_json = json.dumps(context, ensure_ascii=True)
        return (
            f"Generate one synthetic text value for column '{text_column}'.\n"
            f"Context row JSON: {context_json}\n"
            "Return ONLY valid JSON in this exact format:\n"
            '{"text":"..."}\n'
            "Do not return any additional keys, markdown, or explanations."
            f"{examples_block}"
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

    def _parse_json_with_fallback(self, content: str) -> Any:
        try:
            return json.loads(content)
        except JSONDecodeError:
            decoder = json.JSONDecoder()
            for index, char in enumerate(content):
                if char not in ("{", "[", '"'):
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
