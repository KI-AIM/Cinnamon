import cloudpickle
import pandas as pd
from pathlib import Path
from synthcity.plugins import Plugins
from typing import Dict, Any, Optional, List

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer



class DdpmSynthesizer(TabularDataSynthesizer):
    """
    Wrapper for synthcity's TabDDPMPlugin for tabular data generation.
    """

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
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

        is_classification: bool = False
            Whether the task is classification or regression.
        n_iter: int = 1000
            Number of epochs for training.
        lr: float = 0.002
            Learning rate.
        batch_size: int = 1024
            Size of mini-batches.
        num_timesteps: int = 1000
            Number of timesteps to use in the diffusion process.
        gaussian_loss_type: str = "mse"
            Type of loss to use for the Gaussian diffusion process. Either "mse" or "kl".
        scheduler: str = "cosine"
            The scheduler of forward process variance 'beta' to use. Either "cosine" or "linear".
        model_type: str = "mlp"
            Type of diffusion model to use ("mlp", "resnet", or "tabnet").
        model_params: dict = dict(n_layers_hidden=3, n_units_hidden=256, dropout=0.0)
            Parameters of the diffusion model. Should be different for different model types.
        dim_embed: int = 128
            Dimensionality of the embedding space.

        """
        
        synth_params = config['synthetization_configuration']['algorithm']['model_parameter']
        training_params = config['synthetization_configuration']['algorithm']['model_fitting']

        self._model_kwargs = {

            # training loop
            'n_iter': int(training_params.get('max_iters', 1000)),
            'lr': float(training_params.get('lr', 0.002)),
            'batch_size': int(training_params.get('batch_size', 1024)),
            'num_timesteps': int(training_params.get('num_timesteps', 1000)),
            #'gaussian_loss_type': training_params.get('gaussian_loss_type', 'mse'),
            #'scheduler': training_params.get('scheduler', 'cosine'),

            # model definition
            'is_classification': bool(synth_params.get('is_classification', False)), # true = regression
            #'model_type': synth_params.get('model_type', 'mlp'),
            'dim_embed': int(synth_params.get('dim_embed', 128)),

            # continuous feature handling
            #'continuous_encoder': synth_params.get('continuous_encoder'),

            # validation
            'validation_size': float(training_params.get('validation_size', 0.0)),
            'validation_metric': training_params.get('validation_metric', None),

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
        self.synthesizer = Plugins().get("ddpm", **self._model_kwargs)

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

    def _load_model(self, filepath: str) -> 'DdpmSynthesizer':
        """
        Core logic for loading a serialized synthesizer instance from a file.
        """
        with open(filepath, 'rb') as f:
            model: 'DdpmSynthesizer' = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """
        Core logic for saving a data sample to a CSV file.
        """
        sample.to_csv(filename, index=False)

