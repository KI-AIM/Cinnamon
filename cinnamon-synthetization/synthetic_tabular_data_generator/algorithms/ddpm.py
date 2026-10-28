from pathlib import Path
from typing import Any, Dict, Optional

import torch

from synthetic_tabular_data_generator.synthcity_synthesizer import SynthcitySynthesizer


class DdpmSynthesizer(SynthcitySynthesizer):
    """Wrapper for synthcity's TabDDPM plugin."""

    plugin_name = "ddpm"

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """Initialize synthesizer and sampling parameters."""
        synth_params = config["synthetization_configuration"]["algorithm"]["model_parameter"]
        training_params = config["synthetization_configuration"]["algorithm"]["model_fitting"]

        self._model_kwargs = {
            "device": torch.device("cpu"),
            # training loop
            "n_iter": int(training_params.get("max_iters", 1000)),
            "lr": float(training_params.get("lr", 0.002)),
            "batch_size": int(training_params.get("batch_size", 1024)),
            "num_timesteps": int(training_params.get("num_timesteps", 1000)),
            "gaussian_loss_type": synth_params.get("gaussian_loss_type", "mse"),
            "scheduler": synth_params.get("scheduler", "cosine"),
            # model definition
            "is_classification": bool(synth_params.get("is_classification", False)),  # true = regression
            "model_type": synth_params.get("model_type", "mlp"),
            "dim_embed": int(synth_params.get("dim_embed", 128)),
            "model_params": {
                "n_layers_hidden": int(synth_params.get("n_layers_hidden", 3)),
                "n_units_hidden": int(synth_params.get("n_units_hidden", 256)),
                "dropout": float(synth_params.get("dropout", 0.0)),
            },
            "validation_size": float(training_params.get("validation_size", 0.0)),
            "validation_metric": training_params.get("validation_metric", None),
        }
        self._sampling = config["synthetization_configuration"]["algorithm"]["sampling"]

    def _fit(self) -> Optional[float]:
        """
        Core logic for fitting the synthesizer.

        Returns the final-epoch training loss (lower is better — the Optuna
        direction for ddpm is ``minimize``). Metric extraction never breaks the
        normal synthesis path: any failure returns ``None``, which the
        hyperparameter-tuning objective treats as a failed trial.
        """
        self.synthesizer.fit(self.dataset)
        try:
            loss_history = getattr(self.synthesizer, "loss_history", None)
            if loss_history is not None and len(loss_history) > 0:
                return float(loss_history["loss"].iloc[-1])
            print("[ddpm] loss_history empty; no fit metric available.")
        except Exception as exc:  # pragma: no cover - defensive
            print(f"[ddpm] could not extract fit metric: {exc}")
        return None
