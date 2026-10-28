from pathlib import Path
from typing import Any, Dict, Optional

import pandas as pd

from synthetic_tabular_data_generator.synthcity_synthesizer import SynthcitySynthesizer


class BayesianNetworkSynthesizer(SynthcitySynthesizer):
    """Wrapper for synthcity's Bayesian Network plugin."""

    plugin_name = "bayesian_network"

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

    def _initialize_dataset(self, df: pd.DataFrame) -> None:
        self._output_columns = list(df.columns)
        constant_columns = [column for column in df if df[column].nunique(dropna=False) == 1]
        self._constant_values = {column: df[column].iloc[0] for column in constant_columns}
        self.dataset = df.drop(columns=constant_columns)

    def _fit(self) -> Optional[float]:
        """
        Core logic for fitting the synthesizer.

        A Bayesian network has no training loss. We score the learned DAG
        against the training data with the same structure-scoring metric the
        plugin used to search (k2 / bic / bdeu / bds) — higher is better
        (Optuna direction ``maximize``). Metric extraction never breaks the
        normal synthesis path: any failure returns ``None``.
        """
        if self.dataset.empty:
            return None

        self.synthesizer.fit(self.dataset)
        try:
            network = self.synthesizer.model.model  # pgmpy BayesianNetwork
            scorer_cls = self.synthesizer._get_structure_scorer()
            return float(scorer_cls(data=self.dataset).score(network))
        except Exception as exc:  # pragma: no cover - defensive
            print(f"[bayesian_network] could not extract fit metric: {exc}")
        return None

    def _sample(self) -> pd.DataFrame:
        num_samples = int(self._sampling["num_samples"])
        constant_values = getattr(self, "_constant_values", {})
        output_columns = getattr(self, "_output_columns", None)
        if output_columns is not None and len(constant_values) == len(output_columns):
            samples = pd.DataFrame(index=range(num_samples))
        else:
            samples = self.synthesizer.generate(num_samples).dataframe()

        for column, value in constant_values.items():
            samples[column] = value
        return samples[output_columns] if output_columns is not None else samples

    def _model_for_export(self) -> "BayesianNetworkSynthesizer":
        model = super()._model_for_export()
        model._constant_values = getattr(self, "_constant_values", {}).copy()
        output_columns = getattr(self, "_output_columns", None)
        model._output_columns = output_columns.copy() if output_columns is not None else None
        if self.dataset is not None and self.dataset.empty:
            model.synthesizer = None
        return model
