import sys
from pathlib import Path

import optuna
import pytest

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from hyperparameter_tuning.optuna_tuning import optimize


def run_study(fit_metric_fn, **kwargs):
    return optimize(
        fit_metric_fn=fit_metric_fn,
        real=None,
        target_variable="x",
        synthesizer="ctgan",
        sampler="random",
        direction="minimize",
        **kwargs,
    )


@pytest.mark.parametrize("pruner", ["median", "hyperband", "successive_halving", "unknown"])
def test_unsupported_pruning_is_rejected_before_training(pruner):
    def fit(_):
        pytest.fail("Unsupported pruning must be rejected before training")

    with pytest.raises(ValueError, match="Pruning is not supported"):
        run_study(fit, pruner=pruner, n_trials=1)


def test_failed_trials_remain_errors_and_later_trials_complete():
    outcomes = iter([RuntimeError("training failed"), None, float("nan"),
                     float("inf"), float("-inf"), "invalid", 2.0, 1.0])

    def fit(_):
        value = next(outcomes)
        if isinstance(value, Exception):
            raise value
        return value

    result = run_study(fit, n_trials=8)
    trials = result.study.trials
    assert isinstance(result.study.pruner, optuna.pruners.NopPruner)
    assert [trial.state for trial in trials] == (
        [optuna.trial.TrialState.FAIL] * 6 + [optuna.trial.TrialState.COMPLETE] * 2
    )
    assert "training failed" in trials[0].user_attrs["error"]
    assert "no fit metric" in trials[1].user_attrs["error"]
    for trial in trials[:6]:
        assert trial.user_attrs["error"]
        assert trial.value is None
    assert result.best_score == 1.0
    assert result.best_algorithm_config is not None


def test_all_failed_trials_produce_no_best_configuration():
    result = run_study(lambda _: None, n_trials=2)
    assert result.best_algorithm_config is None
    assert all(trial.state == optuna.trial.TrialState.FAIL for trial in result.study.trials)
