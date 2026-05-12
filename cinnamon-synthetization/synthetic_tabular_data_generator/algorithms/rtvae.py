from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd
from synthcity.plugins import Plugins

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class RtvaeSynthesizer(TabularDataSynthesizer):
    """Wrapper for synthcity's RTVAE plugin."""

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

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        """Store the attribute configuration."""
        self.attribute_config = attribute_config

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        """Store the dataset."""
        self.dataset = df

    def _initialize_synthesizer(self) -> None:
        """Create the synthcity plugin instance."""
        self.synthesizer = Plugins().get("rtvae", **self._model_kwargs)

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

    def _load_model(self, filepath: str) -> "RtvaeSynthesizer":
        """Load a serialized synthesizer instance from disk."""
        with open(filepath, "rb") as f:
            model: "RtvaeSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """Write sampled data to CSV."""
        sample.to_csv(filename, index=False)
