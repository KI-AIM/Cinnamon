import sys
import types
from pathlib import Path

import pytest
import torch


PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


synthcity_module = types.ModuleType("synthcity")
plugins_module = types.ModuleType("synthcity.plugins")
plugins_module.Plugins = object
synthcity_module.plugins = plugins_module
sys.modules.setdefault("synthcity", synthcity_module)
sys.modules.setdefault("synthcity.plugins", plugins_module)


from synthetic_tabular_data_generator.algorithms import ctgan, tvae, rtvae
from synthetic_tabular_data_generator import synthcity_synthesizer


class DummyPlugins:
    def __init__(self):
        self.calls = []

    def get(self, name, **kwargs):
        self.calls.append((name, kwargs))
        return {"name": name, "kwargs": kwargs}


def _config():
    return {
        "synthetization_configuration": {
            "algorithm": {
                "model_parameter": {
                    "number_of_layers": 2,
                    "number_of_units_in_layers": 128,
                    "embedding_dim": 64,
                },
                "model_fitting": {
                    "epochs": 10,
                    "batch_size": 32,
                },
                "sampling": {
                    "num_samples": 5,
                },
            }
        }
    }


def test_ctgan_wrapper_omits_unsupported_verbose_kwarg(monkeypatch):
    plugins = DummyPlugins()
    monkeypatch.setattr(synthcity_synthesizer, "Plugins", lambda: plugins)

    synthesizer = ctgan.CtganSynthesizer()
    synthesizer.initialize_anonymization_configuration(_config())
    synthesizer.initialize_synthesizer()

    assert plugins.calls
    plugin_name, kwargs = plugins.calls[0]
    assert plugin_name == "ctgan"
    assert "verbose" not in kwargs


def test_tvae_wrapper_initializes_expected_synthcity_plugin(monkeypatch):
    plugins = DummyPlugins()
    monkeypatch.setattr(synthcity_synthesizer, "Plugins", lambda: plugins)

    synthesizer = tvae.TvaeSynthesizer()
    synthesizer.initialize_anonymization_configuration(_config())
    synthesizer.initialize_synthesizer()

    assert plugins.calls == [
        (
            "tvae",
            {
                "device": torch.device("cpu"),
                "n_units_embedding": 64,
                "encoder_n_layers_hidden": 2,
                "encoder_n_units_hidden": 128,
                "decoder_n_layers_hidden": 2,
                "decoder_n_units_hidden": 128,
                "n_iter": 10,
                "batch_size": 32,
                "lr": 1e-3,
                "weight_decay": 1e-5,
                "loss_factor": 2,
                "workspace": synthesizer._model_kwargs["workspace"],
                "compress_dataset": False,
                "sampling_patience": 500,
            },
        )
    ]


class ExportTestPlugin:
    def generate(self, count):
        import pandas as pd
        from types import SimpleNamespace
        return SimpleNamespace(dataframe=lambda: pd.DataFrame({"x": [7] * count}))


def test_classical_exports_keep_sampling_without_training_data(tmp_path):
    import pandas as pd
    from synthetic_tabular_data_generator.algorithms.arf import AdversarialRandomForestsSynthesizer
    from synthetic_tabular_data_generator.algorithms.bayesian_network import BayesianNetworkSynthesizer
    from synthetic_tabular_data_generator.algorithms.ddpm import DdpmSynthesizer
    from synthetic_tabular_data_generator.algorithms.rtvae import RtvaeSynthesizer

    classes = (ctgan.CtganSynthesizer, tvae.TvaeSynthesizer, BayesianNetworkSynthesizer,
               DdpmSynthesizer, RtvaeSynthesizer, AdversarialRandomForestsSynthesizer)
    for cls in classes:
        synth = cls()
        synth.initialize_attribute_configuration({"configurations": [{"name": "x", "type": "INTEGER"}]})
        synth.initialize_dataset(pd.DataFrame({"x": [123456789]}))
        synth.anonymization_configuration = {"api_key": "EXPORT_SECRET"}
        synth._sampling = {"num_samples": 2, "api_key": "EXPORT_SECRET"}
        synth._model_kwargs = {"random_state": 42}
        synth.synthesizer = ExportTestPlugin()
        synth._anonymization_configuration_initialized = True
        synth._synthesizer_initialized = True
        synth._fit_completed = True
        synth.set_progress_callback(lambda *_: "EXPORT_CALLBACK_SECRET")
        source = synth.dataset
        plugin = synth.synthesizer

        blob = synth.get_model()
        assert b"EXPORT_SECRET" not in blob
        assert b"EXPORT_CALLBACK_SECRET" not in blob
        path = tmp_path / "model.pkl"
        path.write_bytes(blob)
        restored = synth.load_model(str(path))
        assert restored.dataset is None
        assert restored.anonymization_configuration is None
        assert restored._progress_callback is None
        assert not restored.is_initialized_for_fit
        assert synth.dataset is source
        assert synth.synthesizer is plugin
        assert synth.is_ready_for_sampling
        if cls is AdversarialRandomForestsSynthesizer:
            assert restored.synthesizer is None
            assert not restored.is_ready_for_sampling
            assert restored._model_kwargs == {"random_state": 42}
        else:
            pd.testing.assert_frame_equal(restored.sample(), synth.sample())


@pytest.mark.parametrize("cls", [tvae.TvaeSynthesizer, rtvae.RtvaeSynthesizer])
@pytest.mark.parametrize("outcome", ["success", "fit_error", "invalid_loss", "missing_hook"])
def test_vae_loss_capture_restores_hook_and_fits_once(monkeypatch, cls, outcome):
    import pandas as pd
    from synthetic_tabular_data_generator.tabular_data_synthesizer import SynthesizerOperationError

    class VAE:
        def _loss_function(self, value):
            return value

    original = VAE._loss_function
    module = types.ModuleType("synthcity.plugins.core.models.vae")
    module.VAE = VAE
    monkeypatch.setitem(sys.modules, module.__name__, module)
    if outcome == "missing_hook":
        del VAE._loss_function

    dataset = pd.DataFrame({"x": [1, 2]})
    calls = []
    failure = RuntimeError("training failed")

    class Plugin:
        def fit(self, data):
            assert data is dataset
            calls.append(data)
            if outcome != "missing_hook":
                values = [object()] if outcome == "invalid_loss" else [torch.tensor(3.0), torch.tensor(1.5)]
                for value in values:
                    assert VAE()._loss_function(value) is value
            if outcome == "fit_error":
                raise failure

    synth = cls({}, {})
    synth.initialize_dataset(dataset)
    synth.synthesizer = Plugin()
    synth._synthesizer_initialized = True
    if outcome == "fit_error":
        with pytest.raises(SynthesizerOperationError) as exc:
            synth.fit()
        assert exc.value.__cause__ is failure
        assert not synth.is_ready_for_sampling
    else:
        assert synth.fit() == (1.5 if outcome == "success" else None)
        assert synth.is_ready_for_sampling
    assert len(calls) == 1
    if outcome == "missing_hook":
        assert not hasattr(VAE, "_loss_function")
    else:
        assert VAE._loss_function is original


def test_shared_csv_export_preserves_columns_without_index(tmp_path):
    import pandas as pd

    sample = pd.DataFrame({"text": ["hello, world", "two\nlines"], "value": [1, 2]})
    path = tmp_path / "sample.csv"
    tvae.TvaeSynthesizer().save_data(sample, str(path))
    pd.testing.assert_frame_equal(pd.read_csv(path), sample)
