from abc import ABC, abstractmethod
from typing import Dict, Any
import pandas as pd


class TabularDataSynthesizer(ABC):
    """
    Abstract base class for tabular data synthesizers with generic error handling in each function.
    """

    def __init__(self, attribute_configuration, anonymization_configuration):
        self.attribute_configuration = attribute_configuration
        self.anonymization_configuration = anonymization_configuration

    def initialize_anonymization_configuration(self, configuration_file: Dict[str, Any]) -> None:
        """
        Public method to initialize anonymization configuration with generic error handling.
        """
        try:
            self._initialize_anonymization_configuration(configuration_file)
        except Exception as e:
            raise RuntimeError(f"Error in initializing anonymization configuration: {str(e)}") from e

    def initialize_attribute_configuration(self, configuration_file: Dict[str, Any]) -> None:
        """
        Public method to initialize attribute configuration with generic error handling.
        """
        try:
            self._initialize_attribute_configuration(configuration_file)
        except Exception as e:
            raise RuntimeError(f"Error in initializing attribute configuration: {str(e)}") from e

    def initialize_dataset(self, dataset: pd.DataFrame) -> None:
        """
        Public method to initialize the dataset with generic error handling.
        """
        try:
            self._initialize_dataset(dataset)
        except Exception as e:
            raise RuntimeError(f"Error in initializing dataset: {str(e)}") from e

    def initialize_synthesizer(self) -> None:
        """
        Public method to initialize the synthesizer with generic error handling.
        """
        try:
            self._initialize_synthesizer()
        except Exception as e:
            raise RuntimeError(f"Error in initializing synthesizer: {str(e)}") from e

    def fit(self) -> None:
        """
        Public method to fit the synthesizer with generic error handling.
        """
        try:
            self._fit()
        except Exception as e:
            raise RuntimeError(f"Error in fitting the synthesizer: {str(e)}") from e

    def sample(self) -> pd.DataFrame:
        """
        Public method to sample data with generic error handling.
        """
        try:
            return self._sample()
        except Exception as e:
            raise RuntimeError(f"Error in sampling data from the synthesizer: {str(e)}") from e

    def get_model(self) -> bytes:
        """
        Public method to get the model with generic error handling.
        """
        try:
            return self._get_model()
        except Exception as e:
            raise RuntimeError(f"Error in retrieving the model: {str(e)}") from e

    def load_model(self, filepath: str) -> 'TabularDataSynthesizer':
        """
        Public method to load a model with generic error handling.
        """
        try:
            return self._load_model(filepath)
        except Exception as e:
            raise RuntimeError(f"Error in loading the model from file '{filepath}': {str(e)}") from e

    def save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """
        Public method to save data with generic error handling.
        """
        try:
            self._save_data(sample, filename)
        except Exception as e:
            raise RuntimeError(f"Error in saving data to file '{filename}': {str(e)}") from e

    # Abstract methods to be implemented by subclasses
    @abstractmethod
    def _initialize_anonymization_configuration(self, configuration_file: Dict[str, Any]) -> None:
        pass

    @abstractmethod
    def _initialize_attribute_configuration(self, configuration_file: Dict[str, Any]) -> None:
        pass

    @abstractmethod
    def _initialize_dataset(self, dataset: pd.DataFrame) -> None:
        pass

    @abstractmethod
    def _initialize_synthesizer(self) -> None:
        pass

    @abstractmethod
    def _fit(self) -> None:
        pass

    @abstractmethod
    def _sample(self) -> pd.DataFrame:
        pass

    @abstractmethod
    def _get_model(self) -> bytes:
        pass

    @abstractmethod
    def _load_model(self, filepath: str) -> 'TabularDataSynthesizer':
        pass

    @abstractmethod
    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        pass