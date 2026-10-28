from pathlib import Path
from typing import Any, Dict, Optional

import torch

from synthetic_tabular_data_generator.synthcity_synthesizer import SynthcitySynthesizer


class TvaeSynthesizer(SynthcitySynthesizer):
    """Wrapper for synthcity's TVAE plugin."""

    plugin_name = "tvae"

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """Initialize synthesizer and sampling parameters."""
        synth_params = config["synthetization_configuration"]["algorithm"]["model_parameter"]
        training_params = config["synthetization_configuration"]["algorithm"]["model_fitting"]

        embedding_dim = int(synth_params["embedding_dim"])
        hidden_layers = int(synth_params["number_of_layers"])
        hidden_units = int(synth_params["number_of_units_in_layers"])

        self._model_kwargs = {
            "device": torch.device("cpu"),
            "n_units_embedding": embedding_dim,
            "encoder_n_layers_hidden": hidden_layers,
            "encoder_n_units_hidden": hidden_units,
            "decoder_n_layers_hidden": hidden_layers,
            "decoder_n_units_hidden": hidden_units,
            "n_iter": int(training_params["epochs"]),
            "batch_size": int(training_params["batch_size"]),
            "lr": float(1e-3),
            "weight_decay": float(1e-5),
            "loss_factor": int(2),
            "workspace": Path("workspace"),
            "compress_dataset": False,
            "sampling_patience": 500,
        }
        self._sampling = config["synthetization_configuration"]["algorithm"]["sampling"]

    def _fit(self) -> Optional[float]:
        return self._fit_vae()
