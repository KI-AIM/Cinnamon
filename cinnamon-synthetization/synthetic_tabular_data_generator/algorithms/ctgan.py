import cloudpickle
import pandas as pd
from typing import Dict, Any, List, Optional 

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer
from synthetic_tabular_data_generator.ctgan import CTGAN


class CtganSynthesizer(TabularDataSynthesizer):
    """
    Model wrapping `CTGAN` model for synthetic data generation.
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

        # Get the batch_size
        batch_size = training_params['batch_size'] 
        if batch_size % 2 != 0:
            original_batch_size = batch_size
            batch_size += 1
            print(f"Adjusted batch_size from {original_batch_size} to {batch_size} to ensure it is even")
        
        # Find a suitable pac value that divides batch_size evenly
        common_pac_values = [10, 8, 4, 2, 1]
        pac_value = next((p for p in common_pac_values if batch_size % p == 0), 1)
        print(f"Using pac={pac_value} to ensure compatibility with batch_size={batch_size}")

        self._model_kwargs = {
            'embedding_dim': synth_params['embedding_dim'],
            'generator_dim': synth_params['generator_dim'],
            'discriminator_dim': synth_params['discriminator_dim'],
            'batch_size': batch_size,
            'epochs': training_params['epochs'],
            'generator_lr': float(2e-4),
            'generator_decay': float(1e-6),
            'discriminator_lr': float(2e-4),
            'discriminator_decay': float(1e-6),
            'discriminator_steps': 1,
            'log_frequency': True,
            'pac': pac_value, 
            'verbose': True,
            'cuda': False  
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
        Identifies and stores all categorical and boolean columns from the input dataframe.
        """
        # Select both categorical and boolean columns directly into discrete_columns
        self.discrete_columns = df.select_dtypes(include=['object', 'category', 'bool']).columns.tolist()
        
        # Store the dataframe
        self.dataset = df         

    def _initialize_synthesizer(self) -> None:
        """
        Core logic for initializing the synthesizer.
        """
        self.synthesizer = CTGAN(**self._model_kwargs)

    def _fit(self) -> None:
        """
        Core logic for fitting the synthesizer.
        """
        self.synthesizer.fit(self.dataset, self.discrete_columns)

    def _sample(self) -> pd.DataFrame:
        """
        Core logic for sampling data from the synthesizer.
        """
        num_samples: int = self._sampling['num_samples']
        return self.synthesizer.sample(num_samples)

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