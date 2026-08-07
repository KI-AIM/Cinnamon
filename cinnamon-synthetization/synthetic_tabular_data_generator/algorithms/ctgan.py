from pathlib import Path
from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd
import torch

from synthcity.plugins import Plugins
from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class CtganSynthesizer(TabularDataSynthesizer):
    """Wrapper for synthcity's CTGAN plugin."""

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
        hidden_layers = int(synth_params["number_of_layers"])
        hidden_units = int(synth_params["number_of_units_in_layers"])
        batch_size = int(training_params["batch_size"])

        self._model_kwargs = {
            "device": torch.device("cpu"),
            "n_iter": int(training_params["epochs"]),
            "generator_n_layers_hidden": hidden_layers,
            "generator_n_units_hidden": hidden_units,
            "discriminator_n_layers_hidden": hidden_layers,
            "discriminator_n_units_hidden": hidden_units,
            "lr": float(2e-4),
            "weight_decay": float(1e-6),
            "batch_size": batch_size,
            "discriminator_n_iter": 1,
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
        self.synthesizer = Plugins().get("ctgan", **self._model_kwargs)

    def _fit(self) -> Optional[float]:
        """Fit the synthesizer and return the final-epoch generator loss.

        synthcity's CTGAN plugin does not expose a ``loss_history``. Its GAN core
        computes per-epoch losses in ``GAN._train_epoch`` (returning
        ``(g_loss, d_loss)``). We briefly wrap that method to record the
        generator loss each epoch and return the last value — lower is better
        (Optuna direction ``minimize``). GAN losses are adversarial/non-stationary,
        so this is a weak quality signal, but it mirrors the "final-epoch training
        loss" objective used by the other synthesizers. Trials run sequentially,
        so the temporary wrapper (restored in ``finally``) is safe. Metric
        extraction never breaks the normal synthesis path: any failure returns
        ``None``.
        """
        captured: List[float] = []

        # Best-effort instrumentation: wrap the GAN epoch loop to record the
        # generator loss. A failure to install the hook must not prevent fitting.
        gan_cls = None
        original_train_epoch = None
        try:
            from synthcity.plugins.core.models.gan import GAN
            gan_cls = GAN
            original_train_epoch = GAN._train_epoch

            def _capturing_train_epoch(gan_self, *args, **kwargs):
                g_loss, d_loss = original_train_epoch(gan_self, *args, **kwargs)
                try:
                    captured.append(float(g_loss))
                except Exception:
                    pass
                return g_loss, d_loss

            GAN._train_epoch = _capturing_train_epoch
        except Exception as exc:  # pragma: no cover - defensive
            print(f"[ctgan] could not install loss capture: {exc}")
            gan_cls = None

        # Fit exactly once. Genuine fit errors propagate to the error handler.
        try:
            self.synthesizer.fit(self.dataset)
        finally:
            if gan_cls is not None and original_train_epoch is not None:
                gan_cls._train_epoch = original_train_epoch

        if captured:
            return captured[-1]
        print("[ctgan] no training loss captured; no fit metric available.")
        return None

    def _sample(self) -> pd.DataFrame:
        """Generate synthetic samples."""
        num_samples: int = self._sampling["num_samples"]
        return self.synthesizer.generate(num_samples).dataframe()

    def _get_model(self) -> bytes:
        """Serialize the synthesizer instance."""
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "CtganSynthesizer":
        """Load a serialized synthesizer instance from disk."""
        with open(filepath, "rb") as f:
            model: "CtganSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """Write sampled data to CSV."""
        sample.to_csv(filename, index=False)
