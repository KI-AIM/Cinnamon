import json
import os
from json import JSONDecodeError
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse

import pandas as pd
import requests

from data_processing.utils import TEXT_PENDING_LLM


class OllamaTextSynthesizer:
    """
    Internal LLM-based synthesizer for TEXT columns only.
    """

    DEFAULT_MODEL_NAME = "llama3.2:1b"
    DEFAULT_BASE_URL = "http://127.0.0.1:11434"
    DEFAULT_TEMPERATURE = 0.7
    DEFAULT_TOP_P = 0.9
    DEFAULT_TIMEOUT_SECONDS = 120
    DEFAULT_MAX_RETRIES = 3
    MAX_EXAMPLES_PER_COLUMN = 5

    def __init__(
        self,
        model_name: str,
        base_url: str,
        temperature: float,
        top_p: float,
        timeout_seconds: int,
        max_retries: int,
    ) -> None:
        self.model_name = model_name
        self.base_url = base_url.rstrip("/")
        self.temperature = max(0.0, min(2.0, float(temperature)))
        self.top_p = max(0.0, min(1.0, float(top_p)))
        self.timeout_seconds = max(1, int(timeout_seconds))
        self.max_retries = max(1, int(max_retries))
        self._examples_by_column: Dict[str, List[str]] = {}

    @classmethod
    def from_algorithm_or_env(cls, algorithm_config: Dict[str, Any]) -> "OllamaTextSynthesizer":
        model_params = (
            algorithm_config.get("synthetization_configuration", {})
            .get("algorithm", {})
            .get("model_parameter", {})
        )
        fitting_params = (
            algorithm_config.get("synthetization_configuration", {})
            .get("algorithm", {})
            .get("model_fitting", {})
        )

        model_name = str(
            model_params.get(
                "model_name",
                os.getenv("OLLAMA_MODEL_NAME", cls.DEFAULT_MODEL_NAME),
            )
        )
        base_url = str(
            model_params.get(
                "ollama_base_url",
                os.getenv("OLLAMA_BASE_URL", cls.DEFAULT_BASE_URL),
            )
        )
        temperature = float(
            model_params.get(
                "temperature",
                os.getenv("OLLAMA_TEMPERATURE", cls.DEFAULT_TEMPERATURE),
            )
        )
        top_p = float(
            model_params.get(
                "top_p",
                os.getenv("OLLAMA_TOP_P", cls.DEFAULT_TOP_P),
            )
        )
        timeout_seconds = int(
            fitting_params.get(
                "timeout_seconds",
                os.getenv("OLLAMA_TIMEOUT_SECONDS", cls.DEFAULT_TIMEOUT_SECONDS),
            )
        )
        max_retries = int(
            fitting_params.get(
                "max_retries",
                os.getenv("OLLAMA_MAX_RETRIES", cls.DEFAULT_MAX_RETRIES),
            )
        )

        return cls(
            model_name=model_name,
            base_url=base_url,
            temperature=temperature,
            top_p=top_p,
            timeout_seconds=timeout_seconds,
            max_retries=max_retries,
        )

    def initialize(self) -> None:
        configured_base_url = self.base_url
        last_error: Optional[Exception] = None

        for base_url in self._candidate_base_urls(configured_base_url):
            tags_url = f"{base_url}/api/tags"
            try:
                response = requests.get(tags_url, timeout=self.timeout_seconds)
                response.raise_for_status()
                self.base_url = base_url
                if base_url != configured_base_url:
                    print(
                        f"Text LLM URL fallback activated: '{configured_base_url}' was unreachable, "
                        f"using '{base_url}' instead."
                    )
                return
            except requests.exceptions.RequestException as exc:
                last_error = exc

        if last_error is not None:
            raise last_error
        raise RuntimeError("Unable to reach Ollama API for text generation.")

    def fit(self, source_df: pd.DataFrame, text_columns: List[str]) -> None:
        self._examples_by_column = {}

        for column_name in text_columns:
            if column_name not in source_df.columns:
                self._examples_by_column[column_name] = []
                continue

            values = (
                source_df[column_name]
                .dropna()
                .astype(str)
                .str.strip()
            )
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

        for _ in range(self.max_retries):
            prompt = self._build_text_prompt(text_column=text_column, context=context, examples=examples)
            payload = {
                "model": self.model_name,
                "stream": False,
                "format": "json",
                "prompt": prompt,
                "options": {
                    "temperature": self.temperature,
                    "top_p": self.top_p,
                },
            }

            try:
                response = requests.post(
                    f"{self.base_url}/api/generate",
                    json=payload,
                    timeout=self.timeout_seconds,
                )
                response.raise_for_status()
                body = response.json()
                content = body.get("response")
                if not isinstance(content, str) or not content.strip():
                    continue

                text = self._parse_text_response(content)
                if text is not None and text.strip():
                    return text.strip()
            except requests.exceptions.RequestException:
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

    def _candidate_base_urls(self, base_url: str) -> List[str]:
        candidates = [base_url.rstrip("/")]
        parsed = urlparse(base_url)
        host = parsed.hostname

        fallback_host: Optional[str] = None
        if host in {"127.0.0.1", "localhost"}:
            fallback_host = "host.docker.internal"
        elif host == "host.docker.internal":
            fallback_host = "127.0.0.1"

        if fallback_host is not None:
            fallback_url = self._replace_url_host(base_url, fallback_host)
            if fallback_url not in candidates:
                candidates.append(fallback_url)

        return candidates

    @staticmethod
    def _replace_url_host(base_url: str, new_host: str) -> str:
        parsed = urlparse(base_url)
        scheme = parsed.scheme or "http"
        port_part = f":{parsed.port}" if parsed.port is not None else ""
        path_part = parsed.path.rstrip("/")
        return f"{scheme}://{new_host}{port_part}{path_part}"
