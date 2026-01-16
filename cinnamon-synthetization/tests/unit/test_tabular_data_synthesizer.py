import pandas as pd
import pytest

from synthetic_tabular_data_generator.tabular_data_synthesizer import (
    TabularDataSynthesizer,
)


class DummySynthesizer(TabularDataSynthesizer):
    def __init__(self):
        super().__init__(attribute_configuration=None, anonymization_configuration=None)
        self.called = []
        self.sample_result = pd.DataFrame({"x": [1, 2]})

    def _initialize_anonymization_configuration(self, configuration_file):
        self.called.append(("anonymization", configuration_file))

    def _initialize_attribute_configuration(self, configuration_file):
        self.called.append(("attribute", configuration_file))

    def _initialize_dataset(self, dataset):
        self.called.append(("dataset", dataset))

    def _initialize_synthesizer(self):
        self.called.append(("synthesizer", None))

    def _fit(self):
        self.called.append(("fit", None))

    def _sample(self):
        self.called.append(("sample", None))
        return self.sample_result

    def _get_model(self):
        self.called.append(("get_model", None))
        return b"model"

    def _load_model(self, filepath):
        self.called.append(("load_model", filepath))
        return self

    def _save_data(self, sample, filename):
        self.called.append(("save_data", filename))


class ErrorSynthesizer(DummySynthesizer):
    def _fit(self):
        raise ValueError("boom")


def test_public_methods_delegate_to_private_methods():
    synth = DummySynthesizer()
    config = {"k": "v"}
    dataset = pd.DataFrame({"a": [1]})

    synth.initialize_anonymization_configuration(config)
    synth.initialize_attribute_configuration(config)
    synth.initialize_dataset(dataset)
    synth.initialize_synthesizer()
    synth.fit()
    sample = synth.sample()
    model = synth.get_model()
    loaded = synth.load_model("model.pkl")
    synth.save_data(dataset, "data.csv")

    assert sample.equals(synth.sample_result)
    assert model == b"model"
    assert loaded is synth
    assert synth.called == [
        ("anonymization", config),
        ("attribute", config),
        ("dataset", dataset),
        ("synthesizer", None),
        ("fit", None),
        ("sample", None),
        ("get_model", None),
        ("load_model", "model.pkl"),
        ("save_data", "data.csv"),
    ]


def test_error_handler_wraps_exceptions():
    synth = ErrorSynthesizer()

    with pytest.raises(RuntimeError) as excinfo:
        synth.fit()

    assert "Error in fitting the synthesizer" in str(excinfo.value)
    assert isinstance(excinfo.value.__cause__, ValueError)
