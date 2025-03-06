import cloudpickle
import pandas

from data_processing.train_test import split_train_test_cross_sectional as split_train_test
from data_processing.pre_process import pre_process_dataframe
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer
from synthetic_tabular_data_generator.ctgan import TVAE


class TvaeSynthesizer(TabularDataSynthesizer):
    """
    Model wrapping ``TVAE`` model
    """

    def __init__(self, attribute_configuration=None, anonymization_configuration=None):
        """
         Initialize the TvaeSynthesizer instance.

         Args:
             attribute_configuration (dict, optional): Configuration for dataset attributes.
             anonymization_configuration (dict, optional): Configuration for anonymizing the data.
         """
        super().__init__(attribute_configuration, anonymization_configuration)
        self.discrete_columns = None
        self.attribute_config = None
        self.validateDataset = None
        self.trainDataset = None
        self._sampling = None
        self._model_fitting = None
        self._data = None
        self._model_kwargs = None
        self.dataset = None
        self.synthesizer = None
        self.config = None

    def initialize_anonymization_configuration(self, config):
        """
        Configure anonymization settings for the synthesizer.

        Args:
            config (dict): Configuration dictionary containing:
                - 'synthetization_configuration': Algorithm settings, including:
                    - 'model_parameter': Model hyperparameters.
                    - 'model_fitting': Fitting configurations.
                    - 'sampling': Number of samples.
        """
        synth_params = config['synthetization_configuration']['algorithm']['model_parameter']
        self._model_kwargs = {
            'embedding_dim': synth_params['embedding_dim'],
            'compress_dims': synth_params['compress_dims'],
            'decompress_dims': synth_params['decompress_dims'],
            'l2scale': float(synth_params['l2scale']),
            'loss_factor': synth_params['loss_factor'],
            'epochs': synth_params['epochs'],
        }
        self._model_fitting = config['synthetization_configuration']['algorithm']['model_fitting']
        self._sampling = config['synthetization_configuration']['algorithm']['sampling']

    def initialize_attribute_configuration(self, attribute_config):
        """
        Initializes the configuration for handling attributes based on the provided configuration.

        Args:
            attribute_config: configuration for various attributes
        """
        self.attribute_config = attribute_config

    def initialize_dataset(self, df:pandas.DataFrame):
        """
        Preprocess the dataset and split it into training and validation sets.

        Args:
            df (pd.DataFrame): The pandas datafrane to be processed.
        """
        self.dataset, self.discrete_columns = pre_process_dataframe(
            df,
            self.attribute_config['configurations']
        )
        self.trainDataset, self.validateDataset = split_train_test(
            self._model_fitting,
            self.dataset
        )

    def initialize_synthesizer(self):
        """
        Initialize the synthesizer model with the configured Args.
        """
        self.synthesizer = TVAE(
            **self._model_kwargs
        )

    def fit(self):
        """
        Fit the synthesizer model to the dataset using the specified configurations.
        """
        self.synthesizer.fit(self.trainDataset, self.discrete_columns)

    def sample(self) -> pandas.DataFrame:
        """
        Sample the indicated number of rows from the trained model.

        Returns:
            pandas.DataFrame: Sampled data.
        """
        return self.synthesizer.sample(self._sampling['num_samples'])

    def get_model(self):
        """
        Serialize and return the model object using cloudpickle.

        Returns:
            bytes: Serialized model object using cloudpickle.
        """
        return cloudpickle.dumps(self)

    def load_model(self, filepath):
        """
        Load a serialized TvaeSynthesizer instance from a file

        Args:
            filepath (str): The filepath of a saved synthesizer model.

        Returns:
            TvaeSynthesizer: The loaded synthesizer instance.
        """
        with open(filepath, 'rb') as f:
            model = cloudpickle.load(f)
            return model

    def save_data(self, sample: pandas.DataFrame, filename: str):
        """
        Save a data sample to a CSV file.

        Args:
            sample: The data sample to be saved.
            filename: The name of the file where the data will be saved.
        """
        sample.to_csv(filename, index=False)
