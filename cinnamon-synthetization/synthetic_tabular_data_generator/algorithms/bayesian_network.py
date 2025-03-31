import cloudpickle
import pandas

from pathlib import Path
from synthcity.plugins import Plugins

from data_processing.train_test import split_train_test_cross_sectional as split_train_test
from data_processing.pre_process import pre_process_dataframe
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class BayesianNetworkSynthesizer(TabularDataSynthesizer):
    """
        Model wrapping ``BayesianNetwork`` model.
    """

    def __init__(self, attribute_configuration=None, anonymization_configuration=None):
        """
         Initialize the BayesianNetworkSynthesizer instance.

         Args:
             attribute_configuration (dict, optional): Configuration for dataset attributes.
             anonymization_configuration (dict, optional): Configuration for anonymizing the data.
        """
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config = None
        self.discrete_columns = None
        self.dataset = None
        self._model_kwargs = None
        self.trainDataset = None
        self.validateDataset = None
        self.synthesizer = None
        self._data = None
        self._model_fitting = None
        self._sampling = None
        self.attribute_configuration = None

    def initialize_anonymization_configuration(self, configuration_file):
        """
        Configure anonymization settings for the synthesizer.

        Args:
            config (dict): Configuration dictionary containing:
                - 'synthetization_configuration': Algorithm settings, including:
                    - 'model_parameter': Model hyperparameters.
                    - 'model_fitting': Fitting configurations.
                    - 'sampling': Number of samples.
        """
        synth_params = configuration_file['synthetization_configuration']['algorithm']['model_parameter']
        training_params = configuration_file['synthetization_configuration']['algorithm']['model_fitting']
        self._model_kwargs = {
            'encoder_max_clusters': int(synth_params['encoder_max_clusters']),
            'encoder_noise_scale': float(synth_params['encoder_noise_scale']),
            'struct_learning_n_iter': int(training_params['struct_learning_n_iter']),

            'struct_learning_search_method': str("tree_search"),
            'struct_learning_score': str("bic"),
            'struct_max_indegree': int(3),
            'sampling_patience': int(1000),
            'compress_dataset': bool(False),
            'random_state': int(42),
            'workspace': Path('workspace'),
            
        }
        self._sampling = configuration_file['synthetization_configuration']['algorithm']['sampling']


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
            df (pandas.DataFrame): The pandas datafrane to be processed.
        """
        self.dataset, self.discrete_columns = pre_process_dataframe(
            df,
            self.attribute_config['configurations']
        )

        self.trainDataset = self.dataset
        self.validateDataset = self.dataset

    def initialize_synthesizer(self):
        """
        Initialize the synthesizer model with the configured Args.
        """
        self.synthesizer = Plugins().get("bayesian_network",
                                         **self._model_kwargs)

    def fit(self):
        """
        Fit the synthesizer model to the dataset.
        """
        self.synthesizer.fit(self.trainDataset)

    def sample(self) -> pandas.DataFrame:
        """
        Sample the indicated number of rows from the trained model.

        Returns:
            pandas.DataFrame: Sampled data.
        """
        return self.synthesizer.generate(self._sampling['num_samples']).dataframe()

    def get_model(self):
        """
        Serialize and return the model object using cloudpickle.

        Returns:
            bytes: Serialized model object using cloudpickle.
        """
        return cloudpickle.dumps(self)

    def load_model(self, filepath):
        """
        Load a serialized CtganSynthesizer instance from a file

        Args:
            filepath (str): The filepath of a saved synthesizer model.

        Returns:
            BayesianNetworkSynthesizer: The loaded synthesizer instance.
        """
        with open(filepath, 'rb') as f:
            model = cloudpickle.load(f)
            return model

    def save_data(self, sample, filename):
        """
        Save a data sample to a CSV file.

        Args:
            sample: The data sample to be saved.
            filename: The name of the file where the data will be saved.
        """
        sample.to_csv(filename, index=False)
