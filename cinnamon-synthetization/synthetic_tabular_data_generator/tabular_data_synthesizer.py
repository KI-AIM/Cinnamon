from __future__ import annotations

from abc import ABC, abstractmethod
from functools import wraps
from typing import Any, Callable, Dict, Optional, TypeVar

import pandas as pd


ConfigurationDict = Dict[str, Any]
T = TypeVar("T")


class SynthesizerOperationError(RuntimeError):
    def __init__(self, operation: str, message: str) -> None:
        self.operation = operation
        super().__init__(f"Error during {operation}: {message}")


class TabularDataSynthesizer(ABC):
    def __init__(
        self,
        attribute_configuration: Optional[ConfigurationDict],
        anonymization_configuration: Optional[ConfigurationDict],
    ) -> None:
        self.attribute_configuration: Optional[ConfigurationDict] = attribute_configuration
        self.anonymization_configuration: Optional[ConfigurationDict] = anonymization_configuration
        self.dataset: Optional[pd.DataFrame] = None
        self.synthesizer: Any = None
        self._progress_callback: Optional[Callable[[str, Any], None]] = None
        self._anonymization_configuration_initialized = anonymization_configuration is not None
        self._attribute_configuration_initialized = attribute_configuration is not None
        self._dataset_initialized = False
        self._synthesizer_initialized = False
        self._fit_completed = False

    def set_progress_callback(self, callback: Optional[Callable[[str, Any], None]]) -> None:
        self._progress_callback = callback

    def _report_remaining_time(self, stage: str, remaining_time: Any) -> None:
        if callable(self._progress_callback):
            self._progress_callback(stage, remaining_time)

    @property
    def is_initialized_for_fit(self) -> bool:
        return (
            self._anonymization_configuration_initialized
            and self._attribute_configuration_initialized
            and self._dataset_initialized
            and self._synthesizer_initialized
        )

    @property
    def is_ready_for_sampling(self) -> bool:
        return self.is_initialized_for_fit and self._fit_completed

    @staticmethod
    def error_handler(operation: str) -> Callable[[Callable[..., T]], Callable[..., T]]:
        def decorator(func: Callable[..., T]) -> Callable[..., T]:
            @wraps(func)
            def wrapper(self: "TabularDataSynthesizer", *args: Any, **kwargs: Any) -> T:
                try:
                    return func(self, *args, **kwargs)
                except SynthesizerOperationError:
                    raise
                except Exception as exc:
                    raise SynthesizerOperationError(operation, str(exc)) from exc

            return wrapper

        return decorator

    @error_handler("anonymization configuration initialization")
    def initialize_anonymization_configuration(self, configuration: ConfigurationDict) -> None:
        self._initialize_anonymization_configuration(configuration)
        self.anonymization_configuration = configuration
        self._anonymization_configuration_initialized = True
        self._synthesizer_initialized = False
        self._fit_completed = False

    @error_handler("attribute configuration initialization")
    def initialize_attribute_configuration(self, configuration: ConfigurationDict) -> None:
        self._initialize_attribute_configuration(configuration)
        self.attribute_configuration = configuration
        self._attribute_configuration_initialized = True
        self._fit_completed = False

    @error_handler("dataset initialization")
    def initialize_dataset(self, dataset: pd.DataFrame) -> None:
        self._initialize_dataset(dataset)
        if self.dataset is None:
            self.dataset = dataset
        self._dataset_initialized = True
        self._fit_completed = False

    @error_handler("synthesizer initialization")
    def initialize_synthesizer(self) -> None:
        self._require_anonymization_configuration()
        self._initialize_synthesizer()
        self._synthesizer_initialized = True
        self._fit_completed = False

    @error_handler("fitting")
    def fit(self) -> None:
        self._require_initialized_for_fit()
        self._fit()
        self._fit_completed = True

    @error_handler("sampling")
    def sample(self) -> pd.DataFrame:
        self._require_ready_for_sampling()
        return self._sample()

    @error_handler("model retrieval")
    def get_model(self) -> bytes:
        return self._get_model()

    @error_handler("model loading")
    def load_model(self, filepath: str) -> "TabularDataSynthesizer":
        return self._load_model(filepath)

    @error_handler("data saving")
    def save_data(self, sample: pd.DataFrame, filename: str) -> None:
        self._save_data(sample, filename)

    def _require_anonymization_configuration(self) -> None:
        if not self._anonymization_configuration_initialized:
            raise ValueError("Anonymization configuration must be initialized before initializing the synthesizer.")

    def _require_initialized_for_fit(self) -> None:
        if self.is_initialized_for_fit:
            return
        raise ValueError(
            "Synthesizer must be initialized with anonymization configuration, "
            "attribute configuration, dataset, and synthesizer instance before fitting."
        )

    def _require_ready_for_sampling(self) -> None:
        if self.is_ready_for_sampling:
            return
        if not self.is_initialized_for_fit:
            raise ValueError(
                "Synthesizer must be fully initialized before sampling."
            )
        raise ValueError("Synthesizer must be fitted before sampling.")

    @abstractmethod
    def _initialize_anonymization_configuration(self, configuration: ConfigurationDict) -> None:
        raise NotImplementedError

    @abstractmethod
    def _initialize_attribute_configuration(self, configuration: ConfigurationDict) -> None:
        raise NotImplementedError

    @abstractmethod
    def _initialize_dataset(self, dataset: pd.DataFrame) -> None:
        raise NotImplementedError

    @abstractmethod
    def _initialize_synthesizer(self) -> None:
        raise NotImplementedError

    @abstractmethod
    def _fit(self) -> None:
        raise NotImplementedError

    @abstractmethod
    def _sample(self) -> pd.DataFrame:
        raise NotImplementedError

    @abstractmethod
    def _get_model(self) -> bytes:
        raise NotImplementedError

    @abstractmethod
    def _load_model(self, filepath: str) -> "TabularDataSynthesizer":
        raise NotImplementedError

    @abstractmethod
    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        raise NotImplementedError
