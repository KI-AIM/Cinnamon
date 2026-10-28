from pathlib import Path
from typing import Any, Dict, Optional


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
