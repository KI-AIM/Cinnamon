import pandas
import cloudpickle

from synthetic_longitudinal_data_generator.preprocess.util import split_train_test
from synthetic_longitudinal_data_generator.longitudinal_data_synthesizer import SyntheticLongitudinalDataGenerator
from synthetic_longitudinal_data_generator.deepecho import PARModel
from synthetic_longitudinal_data_generator.preprocess import util


class ParSynthesizer(SyntheticLongitudinalDataGenerator):
    """
    Model wrapping ``PAR`` model
    """

    def __init__(self, attribute_configuration=None, anonymization_configuration=None):
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config = None
        self.data_types = None
        self.entity_columns = None
        self.sequence_index = None
        self.validateDataset = None
        self.trainDataset = None
        self._sampling = None
        self._model_fitting = None
        self._data = None
        self._model_kwargs = None
        self.dataset = None
        self.synthesizer = None
        self.config = None
        self.context_columns = None

    def initialize_anonymization_configuration(self, config):
        """
        Initialize the anonymization configuration for the dataset.

        Parameters:
            config: A dictionary containing the configuration for the dataset anonymization.
        """
        synth_params = config['synthetization_configuration']['algorithm']['model_parameter']
        self._model_kwargs = {
            'epochs': synth_params['epochs'],
            'sample_size': synth_params['sample_size'],
        }
        self._model_fitting = config['synthetization_configuration']['algorithm']['model_fitting']
        self._sampling = config['synthetization_configuration']['algorithm']['sampling']

    def initialize_attribute_configuration(self, attribute_config):
        """
        Initialize the attribute configuration for the dataset.

        Parameters:
            attribute_config: A dictionary containing the configuration for the dataset attributes.
        """
        self.attribute_config = attribute_config

    def initialize_dataset(self, dataset):
        """
        Initialize and preprocess the dataset along with its metadata.

        Parameters:
            dataset: The training dataset to be processed.
        """
        self.dataset, self.entity_columns, self.context_columns, self.data_types, self.sequence_index = util.process_dataset(
            dataset, self.attribute_config['configurations'])
        self.trainDataset, self.validateDataset = split_train_test(self._model_fitting, self.dataset, self.entity_columns)

    def initialize_synthesizer(self):
        """
        Initialize the synthesizer model with the configured parameters.
        """
        self.synthesizer = PARModel(
            **self._model_kwargs,
        )

    def fit(self):
        """
        Fit the synthesizer model to the dataset using the specified configurations.
        """
        self.synthesizer.fit(data=self.trainDataset,
                             entity_columns=self.entity_columns,
                             context_columns=self.context_columns,
                             data_types=self.data_types,
                             sequence_index=self.sequence_index
                             )

    def sample(self) -> pandas.DataFrame:
        """
        Generate and return synthetic samples using the synthesizer model.

        Returns:
            synthetic data.
        """
        return self.synthesizer.sample(self._sampling['num_samples'])

    def get_model(self):
        """
        Serialize and return the model object using cloudpickle.

        Returns:
            Serialized model object as bytes.
        """
        return cloudpickle.dumps(self)

    def load_model(self, filepath):
        """
        Load a TabularModel instance from a given path.
        """
        with open(filepath, 'rb') as f:
            model = cloudpickle.load(f)
            return model

    def save_data(self, sample, filename):
        """
        Save a data sample to a CSV file.

        Parameters:
            sample: The data sample to be saved.
            filename: The name of the file where the data will be saved.
        """
        sample.to_csv(filename, index=False)
