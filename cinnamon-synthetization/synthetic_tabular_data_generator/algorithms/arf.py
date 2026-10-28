from pathlib import Path
from typing import Any, Dict, Optional

import torch

from synthetic_tabular_data_generator.synthcity_synthesizer import SynthcitySynthesizer


class AdversarialRandomForestsSynthesizer(SynthcitySynthesizer):
    """Wrapper for synthcity's ARF plugin."""

    plugin_name = "arf"

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """Initialize synthesizer and sampling parameters."""
        synth_params = config["synthetization_configuration"]["algorithm"]["model_parameter"]
        training_params = config["synthetization_configuration"]["algorithm"]["model_fitting"]

        self._model_kwargs = {
            "num_trees": int(synth_params["num_trees"]),
            "min_node_size": int(synth_params["min_node_size"]),
            "max_iters": int(training_params["max_iters"]),
            "delta": 0,
            "early_stop": True,
            "verbose": True,
            "device": torch.device("cpu"),
            "random_state": 42,
            "sampling_patience": 1000,
            "workspace": Path("workspace"),
            "compress_dataset": False,
        }
        self._sampling = config["synthetization_configuration"]["algorithm"]["sampling"]

    def _fit(self) -> Optional[float]:
        """
        Core logic for fitting the synthesizer.

        ARF has no training loss. It trains an adversarial random forest that
        tries to distinguish real from synthetic rows; the out-of-bag (OOB)
        discriminator accuracy converges toward 0.5 when the synthetic data is
        indistinguishable from the real data. We return the distance of the
        final OOB accuracy from 0.5 — lower is better (Optuna direction
        ``minimize``). Metric extraction never breaks the normal synthesis
        path: any failure returns ``None``.
        """
        self.synthesizer.fit(self.dataset)
        try:
            acc = self.synthesizer.model.model.acc  # arfpy per-iteration OOB accuracy
            if acc:
                return abs(float(acc[-1]) - 0.5)
            print("[arf] no OOB accuracy recorded; no fit metric available.")
        except Exception as exc:  # pragma: no cover - defensive
            print(f"[arf] could not extract fit metric: {exc}")
        return None

    def _model_for_export(self) -> "AdversarialRandomForestsSynthesizer":
        model = super()._model_for_export()
        # arfpy retains x_real and uses it again in forde() on every generate().
        model.synthesizer = None
        model._synthesizer_initialized = False
        model._fit_completed = False
        return model
