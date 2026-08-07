from __future__ import annotations

from typing import Any, Callable, Dict, Optional

import pandas as pd

from synthetic_tabular_data_generator.llm.client import (
    LlmClient,
    LlmClientConfig,
    create_llm_client,
    load_llm_client_config,
)
from synthetic_tabular_data_generator.llm.synthesizer_support import LlmSynthesizerSupport
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class ConfiguredLlmSynthesizerBase(TabularDataSynthesizer, LlmSynthesizerSupport):
    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._llm_config: Optional[LlmClientConfig] = None
        self._llm_client: Optional[LlmClient] = None
        self._fitting_kwargs: Optional[Dict[str, Any]] = None
        self._sampling: Optional[Dict[str, Any]] = None
        self._user_prompt_domain_context: str = ""
        self._sample_start_time: Optional[float] = None
        self._generation_failures: int = 0
        self.synthesizer = None

    def _initialize_common_llm_configuration(
        self,
        config: Dict[str, Any],
        *,
        default_few_shot_rows: int = 0,
    ) -> tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        model_params = algorithm_config.get("model_parameter", {})
        training_params = algorithm_config.get("model_fitting", {})
        self._llm_config = load_llm_client_config(config)
        few_shot_rows = model_params.get(
            "few_shot_rows",
            training_params.get("few_shot_rows", default_few_shot_rows),
        )

        self._fitting_kwargs = {
            "few_shot_rows": max(0, int(few_shot_rows)),
            "max_retries": self._llm_config.max_retries,
            "timeout_seconds": self._llm_config.timeout_seconds,
        }

        self._sampling = algorithm_config.get("sampling", {})
        self._user_prompt_domain_context = str(training_params.get("user_prompt_domain_context", "")).strip()
        return algorithm_config, model_params, training_params

    def _initialize_llm_backend(self, *, mode: Optional[str] = None) -> None:
        if self._llm_config is None:
            raise ValueError("Anonymization configuration is not initialized.")

        self._llm_client = create_llm_client(self._llm_config)
        self._llm_client.initialize()

        synthesizer_metadata = {
            "backend": self._llm_config.provider,
            "model_name": self._llm_config.model_name,
        }
        if mode:
            synthesizer_metadata["mode"] = mode
        self.synthesizer = synthesizer_metadata

    def _build_profile_dataframe(self, df: pd.DataFrame) -> pd.DataFrame:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration is not initialized.")
        return df.copy()

    def _resolve_num_samples(self, default_num_samples: int, *, allow_exceed_default: bool) -> int:
        if self._sampling is None:
            raise ValueError("Sampling configuration is not initialized.")

        configured_num_samples = self._sampling.get("num_samples")
        if configured_num_samples is None:
            return default_num_samples

        num_samples = int(configured_num_samples)
        if num_samples <= 0:
            raise ValueError("num_samples must be greater than 0.")
        if not allow_exceed_default and num_samples > default_num_samples:
            raise ValueError("num_samples cannot exceed the number of synthetic tabular input rows.")
        return num_samples

    def _reset_generation_counters(self) -> None:
        self._generation_failures = 0

    def _handle_generation_failure(
        self,
        *,
        message: str,
        last_error: Optional[Exception],
        fallback_factory: Optional[Callable[[], Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        self._generation_failures += 1

        if fallback_factory is not None:
            return fallback_factory()

        if last_error is not None:
            raise RuntimeError(message) from last_error
        raise RuntimeError(message)

    @staticmethod
    def _summarize_exception(exc: Exception) -> str:
        message = str(exc).strip()
        if not message:
            message = exc.__class__.__name__
        return f"{exc.__class__.__name__}: {message}"

    def _log_generation_attempt_failure(
        self,
        *,
        mode: str,
        row_index: int,
        total_rows: int,
        attempt_index: int,
        max_retries: int,
        error: Exception,
        details: Optional[str] = None,
    ) -> None:
        suffix = f" ({details})" if details else ""
        print(
            f"[LLM_{mode}] sample {row_index + 1}/{total_rows}, "
            f"attempt {attempt_index + 1}/{max_retries} failed: "
            f"{self._summarize_exception(error)}{suffix}"
        )

    @staticmethod
    def _parse_bool_like(value: Any) -> bool:
        if isinstance(value, bool):
            return value
        if isinstance(value, (int, float)):
            return value != 0
        if isinstance(value, str):
            normalized = value.strip().lower()
            if normalized in {"1", "true", "yes", "y", "on"}:
                return True
            if normalized in {"0", "false", "no", "n", "off"}:
                return False
        return bool(value)
