from abc import ABC, abstractmethod


class SyntheticLongitudinalDataGenerator(ABC):

    def __init__(self, attribute_configuration, anonymization_configuration):
        self.attribute_configuration = attribute_configuration
        self.anonymization_configuration = anonymization_configuration

    @abstractmethod
    def initialize_anonymization_configuration(self, configuration_file):
        pass

    @abstractmethod
    def initialize_attribute_configuration(self, configuration_file):
        pass

    @abstractmethod
    def initialize_dataset(self, dataset):
        pass

    @abstractmethod
    def initialize_synthesizer(self):
        pass

    @abstractmethod
    def fit(self):
        pass

    @abstractmethod
    def sample(self):
        pass

    @abstractmethod
    def get_model(self):
        pass

    @abstractmethod
    def load_model(self, filepath):
        pass

    @abstractmethod
    def save_data(self, sample, filename):
        pass
