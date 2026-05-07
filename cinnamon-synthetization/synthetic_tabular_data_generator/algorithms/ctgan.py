import cloudpickle
import pandas as pd
from pathlib import Path
from typing import Dict, Any, Optional

from synthcity.plugins import Plugins
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class CtganSynthesizer(TabularDataSynthesizer):
    """
    Wrapper for synthcity's CTGAN plugin for tabular data generation.
    """

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None
    ) -> None:
        """
        Initialize the CtganSynthesizer instance.

        Args:
            attribute_configuration (dict, optional): Configuration for dataset attributes.
            anonymization_configuration (dict, optional): Configuration for anonymizing the data.
        """
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self._model_kwargs: Optional[Dict[str, Any]] = None
        self.synthesizer = None
        self._sampling: Optional[Dict[str, Any]] = None

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """
        Core logic for initializing anonymization configuration.
        """
        synth_params = config['synthetization_configuration']['algorithm']['model_parameter']
        training_params = config['synthetization_configuration']['algorithm']['model_fitting']
        hidden_layers = int(synth_params['number_of_layers'])
        hidden_units = int(synth_params['number_of_units_in_layers'])
        batch_size = int(training_params['batch_size'])

        self._model_kwargs = {
            'n_iter': int(training_params['epochs']),
            'generator_n_layers_hidden': hidden_layers,
            'generator_n_units_hidden': hidden_units,
            'discriminator_n_layers_hidden': hidden_layers,
            'discriminator_n_units_hidden': hidden_units,
            'lr': float(2e-4),
            'weight_decay': float(1e-6),
            'batch_size': batch_size,
            'discriminator_n_iter': 1,
            'workspace': Path('workspace'),
            'compress_dataset': False,
            'sampling_patience': 500,
        }
        self._sampling = config['synthetization_configuration']['algorithm']['sampling']

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        """
        Core logic for initializing attribute configuration.
        """
        self.attribute_config = attribute_config

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        """
        Core logic for initializing the dataset.
        """
        self.dataset = df

    def _initialize_synthesizer(self) -> None:
        """
        Core logic for initializing the synthesizer.
        """
        self.synthesizer = Plugins().get("ctgan", **self._model_kwargs)

    def _fit(self) -> None:
        """
        Core logic for fitting the synthesizer.
        """
        self.synthesizer.fit(self.dataset)

    def _sample(self) -> pd.DataFrame:
        """
        Core logic for sampling data from the synthesizer.
        """
        num_samples: int = self._sampling['num_samples']
        return self.synthesizer.generate(num_samples).dataframe()

    def _get_model(self) -> bytes:
        """
        Core logic for serializing the model object.
        """
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> 'CtganSynthesizer':
        """
        Core logic for loading a serialized synthesizer instance from a file.
        """
        with open(filepath, 'rb') as f:
            model: 'CtganSynthesizer' = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """
        Core logic for saving a data sample to a CSV file.
        """
        sample.to_csv(filename, index=False)
