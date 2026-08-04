import sys
import types
from pathlib import Path

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


from synthetic_tabular_data_generator.algorithms import ctgan, tvae


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
    monkeypatch.setattr(ctgan, "Plugins", lambda: plugins)

    synthesizer = ctgan.CtganSynthesizer()
    synthesizer.initialize_anonymization_configuration(_config())
    synthesizer.initialize_synthesizer()

    assert plugins.calls
    plugin_name, kwargs = plugins.calls[0]
    assert plugin_name == "ctgan"
    assert "verbose" not in kwargs


def test_tvae_wrapper_initializes_expected_synthcity_plugin(monkeypatch):
    plugins = DummyPlugins()
    monkeypatch.setattr(tvae, "Plugins", lambda: plugins)

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
