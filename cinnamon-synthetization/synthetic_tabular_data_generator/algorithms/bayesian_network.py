from pathlib import Path
from typing import Any, Dict, List, Optional

import cloudpickle
import pandas as pd
from synthcity.plugins import Plugins

from synthetic_tabular_data_generator.tabular_data_synthesizer import TabularDataSynthesizer


class BayesianNetworkSynthesizer(TabularDataSynthesizer):
    """Wrapper for synthcity's Bayesian Network plugin."""

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
            "encoder_max_clusters": int(synth_params["encoder_max_clusters"]),
            "encoder_noise_scale": float(synth_params["encoder_noise_scale"]),
            "struct_learning_n_iter": int(training_params["struct_learning_n_iter"]),
            "sampling_patience": int(1000),
            "compress_dataset": bool(False),
            "random_state": int(42),
            "workspace": Path("workspace"),
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
        self.synthesizer = Plugins().get("bayesian_network", **self._model_kwargs)

    def _fit(self) -> Optional[float]:
        """
        Core logic for fitting the synthesizer.

        A Bayesian network has no training loss. We score the learned DAG
        against the training data with the same structure-scoring metric the
        plugin used to search (k2 / bic / bdeu / bds) — higher is better
        (Optuna direction ``maximize``). Metric extraction never breaks the
        normal synthesis path: any failure returns ``None``.
        """
        self.synthesizer.fit(self.dataset)
        try:
            network = self.synthesizer.model.model  # pgmpy BayesianNetwork
            scorer_cls = self.synthesizer._get_structure_scorer()
            return float(scorer_cls(data=self.dataset).score(network))
        except Exception as exc:  # pragma: no cover - defensive
            print(f"[bayesian_network] could not extract fit metric: {exc}")
        return None

    def _sample(self) -> pd.DataFrame:
        """Generate synthetic samples."""
        num_samples: int = self._sampling["num_samples"]
        return self.synthesizer.generate(num_samples).dataframe()

    def _get_model(self) -> bytes:
        """Serialize the synthesizer instance."""
        return cloudpickle.dumps(self)

    def _load_model(self, filepath: str) -> "BayesianNetworkSynthesizer":
        """Load a serialized synthesizer instance from disk."""
        with open(filepath, "rb") as f:
            model: "BayesianNetworkSynthesizer" = cloudpickle.load(f)
        return model

    def _save_data(self, sample: pd.DataFrame, filename: str) -> None:
        """Write sampled data to CSV."""
        sample.to_csv(filename, index=False)
