from abc import ABC, abstractmethod
from typing import Dict, Any, Callable, TypeVar
import pandas as pd
import traceback
import functools

# Define generic return type for method decorators
T = TypeVar('T')

class TabularDataSynthesizer(ABC):
    """
    Abstract base class for tabular data synthesizers with enhanced error handling.
    
    This base class provides standardized error handling with stack trace capture
    for all core synthesizer operations, reducing code duplication in subclasses.
    """

    def __init__(self, attribute_configuration, anonymization_configuration):
        self.attribute_configuration = attribute_configuration
        self.anonymization_configuration = anonymization_configuration
    
    @staticmethod
    def error_handler(method_name: str) -> Callable:
        """
        Decorator for capturing detailed error information including stack traces.
        
        Args:
            method_name: Name of the method being wrapped for error reporting
            
        Returns:
            Decorated function with enhanced error handling
        """
        def decorator(func: Callable[..., T]) -> Callable[..., T]:
            @functools.wraps(func)
            def wrapper(self, *args, **kwargs) -> T:
                try:
                    return func(self, *args, **kwargs)
                except Exception as e:
                    # Capture full stack trace
                    stack_trace = traceback.format_exc()
                    
                    # Print the detailed error with stack trace
                    print(f"Error in {method_name}: {str(e)}")
                    print(f"Stack trace: {stack_trace}")
                    
                    # Raise a new exception with both the original error and method info
                    raise RuntimeError(f"Error in {method_name}: {str(e)}") from e
            return wrapper
        return decorator

    @error_handler("initializing anonymization configuration")
    def initialize_anonymization_configuration(self, configuration_file: Dict[str, Any]) -> None:
        """Public method to initialize anonymization configuration with enhanced error handling."""
        self._initialize_anonymization_configuration(configuration_file)

    @error_handler("initializing attribute configuration")
    def initialize_attribute_configuration(self, configuration_file: Dict[str, Any]) -> None:
        """Public method to initialize attribute configuration with enhanced error handling."""
        self._initialize_attribute_configuration(configuration_file)

    @error_handler("initializing dataset")
    def initialize_dataset(self, dataset: pd.DataFrame) -> None:
        """Public method to initialize the dataset with enhanced error handling."""
        self._initialize_dataset(dataset)

    @error_handler("initializing synthesizer")
    def initialize_synthesizer(self) -> None:
        """Public method to initialize the synthesizer with enhanced error handling."""
        self._initialize_synthesizer()

    @error_handler("fitting the synthesizer")
    def fit(self) -> None:
        """Public method to fit the synthesizer with enhanced error handling."""
        self._fit()

    @error_handler("sampling data from the synthesizer")
    def sample(self) -> pd.DataFrame:
        """Public method to sample data with enhanced error handling."""
        return self._sample()

    @error_handler("retrieving the model")
    def get_model(self) -> bytes:
        """Public method to get the model with enhanced error handling."""
        return self._get_model()

    @error_handler("loading the model from file")
    def load_model(self, filepath: str) -> 'TabularDataSynthesizer':
        """Public method to load a model with enhanced error handling."""
        return self._load_model(filepath)

    @error_handler("saving data to file")
    def save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """Public method to save data with enhanced error handling."""
        self._save_data(sample, filename)

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