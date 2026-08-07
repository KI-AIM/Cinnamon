from pathlib import Path
from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd
import torch
from synthcity.plugins import Plugins

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class AdversarialRandomForestsSynthesizer(TabularDataSynthesizer):
    """Wrapper for synthcity's ARF plugin."""

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

    def _initialize_attribute_configuration(self, attribute_config: Dict[str, Any]) -> None:
        """Store the attribute configuration."""
        self.attribute_config = attribute_config

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        """Store the dataset."""
        self.dataset = df

    def _initialize_synthesizer(self) -> None:
        """Create the synthcity plugin instance."""
        self.synthesizer = Plugins().get("arf", **self._model_kwargs)

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

    def _sample(self) -> pd.DataFrame:
        """Generate synthetic samples."""
        num_samples: int = self._sampling["num_samples"]
        return self.synthesizer.generate(num_samples).dataframe()

    def _get_model(self) -> bytes:
        """Serialize the synthesizer instance."""
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "AdversarialRandomForestsSynthesizer":
        """Load a serialized synthesizer instance from disk."""
        with open(filepath, "rb") as f:
            model: "AdversarialRandomForestsSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """Write sampled data to CSV."""
        sample.to_csv(filename, index=False)
