from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd
from synthcity.plugins import Plugins

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class SynthcitySynthesizer(TabularDataSynthesizer):
    """Shared lifecycle and persistence for the classical Synthcity plugins."""

    plugin_name: str

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self._model_kwargs: Optional[Dict[str, Any]] = None
        self._sampling: Optional[Dict[str, Any]] = None

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        """Store the attribute configuration."""
        self.attribute_config = attribute_config

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        """Store the dataset."""
        self.dataset = df

    def _initialize_synthesizer(self) -> None:
        """Create the synthcity plugin instance."""
        self.synthesizer = Plugins().get(self.plugin_name, **self._model_kwargs)

    def _sample(self) -> pd.DataFrame:
        """Generate synthetic samples."""
        num_samples: int = self._sampling["num_samples"]
        return self.synthesizer.generate(num_samples).dataframe()

    def _get_model(self) -> bytes:
        """Serialize the synthesizer instance."""
        return cloudpickle.dumps(self._model_for_export())

    def _load_model(self, filepath: str) -> "TabularDataSynthesizer":
        """Load a serialized synthesizer instance from disk."""
        with open(filepath, "rb") as f:
            model: "TabularDataSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """Write sampled data to CSV."""
        sample.to_csv(filename, index=False)

    def _fit_vae(self) -> Optional[float]:
        """Capture the final VAE loss; the temporary hook requires sequential fits."""
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
            print(f"[{self.plugin_name}] could not install loss capture: {exc}")
            vae_cls = None

        # Fit exactly once. Genuine fit errors propagate to the error handler.
        try:
            self.synthesizer.fit(self.dataset)
        finally:
            if vae_cls is not None and original_loss_fn is not None:
                vae_cls._loss_function = original_loss_fn

        if captured:
            return captured[-1]
        print(f"[{self.plugin_name}] no training loss captured; no fit metric available.")
        return None

