from typing import Any, Dict, Optional

import torch

from synthetic_tabular_data_generator.synthcity_synthesizer import SynthcitySynthesizer


class RtvaeSynthesizer(SynthcitySynthesizer):
    """Wrapper for synthcity's RTVAE plugin."""

    plugin_name = "rtvae"

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """Initialize synthesizer and sampling parameters."""
        synth_params = config["synthetization_configuration"]["algorithm"]["model_parameter"]
        training_params = config["synthetization_configuration"]["algorithm"]["model_fitting"]

        self._model_kwargs = {
            "device": torch.device("cpu"),
            "data_encoder_max_clusters": int(synth_params["data_encoder_max_clusters"]),
            "decoder_n_layers_hidden": int(synth_params["number_of_layers"]),
            "encoder_n_layers_hidden": int(synth_params["number_of_layers"]),
            "decoder_n_units_hidden": int(synth_params["number_of_units_in_layers"]),
            "encoder_n_units_hidden": int(synth_params["number_of_units_in_layers"]),
            "decoder_nonlin": "leaky_relu",
            "encoder_nonlin": "leaky_relu",
            "decoder_dropout": float(0.1),
            "encoder_dropout": float(0.1),
            "n_units_embedding": int(synth_params["n_units_embedding"]),
            "batch_size": int(training_params["batch_size"]),
            "n_iter": int(training_params["n_iter"]),
            "lr": float(1e-3),
            "weight_decay": float(1e-5),
            "robust_divergence_beta": int(2),
            "random_state": int(0),
        }
        self._sampling = config["synthetization_configuration"]["algorithm"]["sampling"]

    def _fit(self) -> Optional[float]:
        return self._fit_vae()
