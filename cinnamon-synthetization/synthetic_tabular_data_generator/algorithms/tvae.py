from pathlib import Path
from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd
import torch

from synthcity.plugins import Plugins
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class TvaeSynthesizer(TabularDataSynthesizer):
    """Wrapper for synthcity's TVAE plugin."""

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self._model_kwargs: Optional[Dict[str, Any]] = None
        self.synthesizer = None
        self._sampling: Optional[Dict[str, Any]] = None

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

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        """Store the attribute configuration."""
        self.attribute_config = attribute_config

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        """Store the dataset."""
        self.dataset = df

    def _initialize_synthesizer(self) -> None:
        """Create the synthcity plugin instance."""
        self.synthesizer = Plugins().get("tvae", **self._model_kwargs)

    def _fit(self) -> Optional[float]:
        """Fit the synthesizer and return the final training loss.

        synthcity's TVAE plugin does not expose its training loss. We capture the
        VAE objective (reconstruction + KL) computed during fitting by briefly
        wrapping ``VAE._loss_function`` and return the last value as the final
        training loss — lower is better (Optuna direction ``minimize``). Trials
        run sequentially, so the temporary wrapper (restored in ``finally``) is
        safe. Metric extraction never breaks the normal synthesis path: any
        failure returns ``None``.
        """
        captured: List[float] = []

        # Best-effort instrumentation: wrap the VAE loss to record its values.
        # A failure to install the hook must not prevent fitting.
        vae_cls = None
        original_loss_fn = None
        try:
            from synthcity.plugins.core.models.vae import VAE
            vae_cls = VAE
            original_loss_fn = VAE._loss_function

            def _capturing_loss_fn(vae_self, *args, **kwargs):
                loss = original_loss_fn(vae_self, *args, **kwargs)
                try:
                    captured.append(float(loss.item()))
                except Exception:
                    pass
                return loss

            VAE._loss_function = _capturing_loss_fn
        except Exception as exc:  # pragma: no cover - defensive
            print(f"[tvae] could not install loss capture: {exc}")
            vae_cls = None

        # Fit exactly once. Genuine fit errors propagate to the error handler.
        try:
            self.synthesizer.fit(self.dataset)
        finally:
            if vae_cls is not None and original_loss_fn is not None:
                vae_cls._loss_function = original_loss_fn

        if captured:
            return captured[-1]
        print("[tvae] no training loss captured; no fit metric available.")
        return None

    def _sample(self) -> pd.DataFrame:
        """Generate synthetic samples."""
        num_samples: int = self._sampling["num_samples"]
        return self.synthesizer.generate(num_samples).dataframe()

    def _get_model(self) -> bytes:
        """Serialize the synthesizer instance."""
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "TvaeSynthesizer":
        """Load a serialized synthesizer instance from disk."""
        with open(filepath, "rb") as f:
            model: "TvaeSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """Write sampled data to CSV."""
        sample.to_csv(filename, index=False)
