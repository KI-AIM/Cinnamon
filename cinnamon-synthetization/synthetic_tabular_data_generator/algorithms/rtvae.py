import cloudpickle
import pandas as pd
from pathlib import Path
from synthcity.plugins import Plugins
from typing import Dict, Any, List, Optional

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class RtvaeSynthesizer(TabularDataSynthesizer):
    """
    Model wrapping `(Outlier) Robust Variational Autoencoder for Tabular Data` model for synthetic data generation.
    """

    DEFAULT_NONLIN = "leaky_relu"
    DEFAULT_DROPOUT = 0.1
    DEFAULT_LEARNING_RATE = 1e-3
    DEFAULT_WEIGHT_DECAY = 1e-5
    DEFAULT_ROBUST_BETA = 2
    DEFAULT_RANDOM_STATE = 0

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None
    ) -> None:
        """
        Initialize the rtvae instance.

        Args:
            attribute_configuration (dict, optional): Configuration for dataset attributes.
            anonymization_configuration (dict, optional): Configuration for anonymizing the data.
        """
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.discrete_columns: Optional[List[str]] = None
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

        self._model_kwargs = {
            'data_encoder_max_clusters': int(synth_params['data_encoder_max_clusters']),
            'decoder_n_layers_hidden': int(synth_params['number_of_layers']),
            'encoder_n_layers_hidden': int(synth_params['number_of_layers']),
            'decoder_n_units_hidden': int(synth_params['number_of_units_in_layers']),
            'encoder_n_units_hidden': int(synth_params['number_of_units_in_layers']),
            'decoder_nonlin': self.DEFAULT_NONLIN,
            'encoder_nonlin': self.DEFAULT_NONLIN,
            'decoder_dropout': float(self.DEFAULT_DROPOUT),
            'encoder_dropout': float(self.DEFAULT_DROPOUT),
            'n_units_embedding': int(synth_params['n_units_embedding']),

            'batch_size': int(training_params['batch_size']),
            'n_iter': int(training_params['n_iter']),
            'lr': float(self.DEFAULT_LEARNING_RATE),
            'weight_decay': float(self.DEFAULT_WEIGHT_DECAY),
            'robust_divergence_beta': int(self.DEFAULT_ROBUST_BETA),
            'random_state': int(self.DEFAULT_RANDOM_STATE)

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
        self.synthesizer = Plugins().get("rtvae", **self._model_kwargs)

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

    def _load_model(self, filepath: str) -> 'RtvaeSynthesizer':
        """
        Core logic for loading a serialized synthesizer instance from a file.
        """
        with open(filepath, 'rb') as f:
            model: 'RtvaeSynthesizer' = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """
        Core logic for saving a data sample to a CSV file.
        """
        sample.to_csv(filename, index=False)
