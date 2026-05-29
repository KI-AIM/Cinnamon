from pathlib import Path
from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd
from synthcity.plugins import Plugins

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class DdpmSynthesizer(TabularDataSynthesizer):
    """Wrapper for synthcity's TabDDPM plugin."""

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self.attribute_config: Optional[Dict[str, Any]] = None
        self.discrete_columns: Optional[List[str]] = None
        self.dataset: Optional[pd.DataFrame] = None
        self._model_kwargs: Optional[Dict[str, Any]] = None
        self.synthesizer = None
        self._sampling: Optional[Dict[str, Any]] = None

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        """Initialize synthesizer and sampling parameters."""
        synth_params = config["synthetization_configuration"]["algorithm"]["model_parameter"]
        training_params = config["synthetization_configuration"]["algorithm"]["model_fitting"]

        self._model_kwargs = {
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

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        """Store the attribute configuration."""
        self.attribute_config = attribute_config

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        """Store the dataset."""
        self.dataset = df

    def _initialize_synthesizer(self) -> None:
        """Create the synthcity plugin instance."""
        self.synthesizer = Plugins().get("ddpm", **self._model_kwargs)

    def _fit(self) -> None:
        """Fit the synthesizer to the dataset."""
        self.synthesizer.fit(self.dataset)

    def _sample(self) -> pd.DataFrame:
        """Generate synthetic samples."""
        num_samples: int = self._sampling["num_samples"]
        return self.synthesizer.generate(num_samples).dataframe()

    def _get_model(self) -> bytes:
        """Serialize the synthesizer instance."""
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "DdpmSynthesizer":
        """Load a serialized synthesizer instance from disk."""
        with open(filepath, "rb") as f:
            model: "DdpmSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """Write sampled data to CSV."""
        sample.to_csv(filename, index=False)

