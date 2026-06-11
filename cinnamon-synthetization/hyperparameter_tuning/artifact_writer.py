"""
Artifact persistence and loading for Optuna hyperparameter-tuning runs.

Saving
------
After a study completes, :func:`save_artifact` writes a self-contained
timestamped directory::

    <artifact_dir>/<study_name>_<YYYYMMDD_HHMMSS>/
        run_metadata.yaml       – optuna config, input data info, tuning setup
        best_trial.yaml         – best trial params, score, timings
        param_importances.yaml  – FAnova importances (or an error note)
        study.yaml              – human-readable summary of all trials
        study.joblib            – full serialised optuna.Study (binary)

Loading
-------
:func:`load_study_from_yaml` reconstructs a fully functional
``optuna.Study`` from ``study.yaml``, including all trials and their
parameter distributions, so it can be used for further analysis or
visualisation without the binary ``study.joblib``.

:func:`load_artifact` is a convenience wrapper that accepts a run
directory and returns both the study and the run-metadata dict.
"""

from __future__ import annotations

import logging
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# YAML helpers
# ---------------------------------------------------------------------------


def _yaml_safe(obj: Any) -> Any:
    """Recursively coerce non-YAML-safe types (datetime, Path, numpy scalars)."""
    if obj is None:
        return obj
    # numpy / pandas scalars FIRST – they subclass float/int and would
    # otherwise slip through and break yaml.safe_dump.
    if not isinstance(obj, (bool, str, bytes, dict, list, tuple, set)) and hasattr(obj, "item"):
        try:
            obj = obj.item()
        except Exception:
            pass
    if isinstance(obj, (bool, int, float, str)):
        return obj
    if isinstance(obj, datetime):
        return obj.isoformat()
    if isinstance(obj, Path):
        return str(obj)
    if isinstance(obj, dict):
        return {str(k): _yaml_safe(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple, set)):
        return [_yaml_safe(v) for v in obj]
    return str(obj)


def _dump_yaml(path: Path, data: Any) -> None:
    import yaml  # type: ignore
    path.write_text(
        yaml.safe_dump(_yaml_safe(data), allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )


# ---------------------------------------------------------------------------
# Distribution serialisation / deserialisation
# ---------------------------------------------------------------------------


def _serialize_distribution(dist: Any) -> Dict[str, Any]:
    """
    Convert an Optuna ``BaseDistribution`` to a plain dict.

    Supported types: ``CategoricalDistribution``, ``FloatDistribution``,
    ``IntDistribution``.  Unknown types fall back to ``{"type": "<name>"}``.
    """
    name = dist.__class__.__name__
    if name == "CategoricalDistribution":
        return {"type": name, "choices": list(dist.choices)}
    if name == "FloatDistribution":
        return {
            "type": name,
            "low": dist.low,
            "high": dist.high,
            "log": dist.log,
            "step": dist.step,
        }
    if name == "IntDistribution":
        return {
            "type": name,
            "low": int(dist.low),
            "high": int(dist.high),
            "log": dist.log,
            "step": int(dist.step),
        }
    return {"type": name}


def _deserialize_distribution(data: Dict[str, Any]) -> Any:
    """
    Reconstruct an Optuna distribution from a plain dict produced by
    :func:`_serialize_distribution`.

    Falls back to ``CategoricalDistribution([data_value])`` for unknown types
    so that the study can still be loaded for inspection.
    """
    import optuna.distributions as od

    dtype = data.get("type", "")
    if dtype == "CategoricalDistribution":
        return od.CategoricalDistribution(choices=tuple(data["choices"]))
    if dtype == "FloatDistribution":
        return od.FloatDistribution(
            low=float(data["low"]),
            high=float(data["high"]),
            log=bool(data.get("log", False)),
            step=float(data["step"]) if data.get("step") is not None else None,
        )
    if dtype == "IntDistribution":
        return od.IntDistribution(
            low=int(data["low"]),
            high=int(data["high"]),
            log=bool(data.get("log", False)),
            step=int(data.get("step", 1)),
        )
    # Unknown – use a float placeholder so the trial can still be imported
    logger.warning("Unknown distribution type %r – using FloatDistribution placeholder.", dtype)
    return od.FloatDistribution(low=float("-inf"), high=float("inf"))


# ---------------------------------------------------------------------------
# Per-section helpers
# ---------------------------------------------------------------------------


def _serialize_best_trial(study: Any) -> Optional[Dict[str, Any]]:
    """Return the best trial as a plain dict, or ``None`` when none exist."""
    try:
        t = study.best_trial
    except ValueError:
        return None

    duration_sec: Optional[float] = None
    if t.datetime_start is not None and t.datetime_complete is not None:
        duration_sec = (t.datetime_complete - t.datetime_start).total_seconds()

    return {
        "study_name": study.study_name,
        "number": t.number,
        "params": dict(t.params),
        "value": t.value,
        "datetime_start": t.datetime_start,
        "datetime_complete": t.datetime_complete,
        "duration_seconds": duration_sec,
        "user_attrs": dict(t.user_attrs),
        "system_attrs": dict(t.system_attrs),
    }


def _serialize_trial(trial: Any) -> Dict[str, Any]:
    """
    Convert a single ``FrozenTrial`` to a plain dict suitable for YAML.

    Includes parameter distributions so the trial can be round-tripped via
    :func:`load_study_from_yaml`.
    """
    duration_sec: Optional[float] = None
    if trial.datetime_start is not None and trial.datetime_complete is not None:
        duration_sec = round(
            (trial.datetime_complete - trial.datetime_start).total_seconds(), 3
        )
    return {
        "number": trial.number,
        "state": trial.state.name,
        "value": trial.value,
        "params": dict(trial.params),
        "distributions": {
            name: _serialize_distribution(dist)
            for name, dist in trial.distributions.items()
        },
        "datetime_start": trial.datetime_start.isoformat() if trial.datetime_start else None,
        "datetime_complete": trial.datetime_complete.isoformat() if trial.datetime_complete else None,
        "duration_seconds": duration_sec,
        "user_attrs": dict(trial.user_attrs),
    }


def _compute_importances(study: Any) -> Dict[str, Any]:
    """Compute FAnova importances; return error info on failure."""
    try:
        import optuna
        importances = optuna.importance.get_param_importances(study)
        return {
            "evaluator": "FanovaImportanceEvaluator",
            "importances": dict(importances),
            "error": None,
        }
    except Exception as exc:
        logger.warning(
            "Parameter importance calculation skipped: %s. "
            "This is expected when fewer than 2 trials completed or all "
            "parameter values are constant.",
            exc,
        )
        return {
            "evaluator": "FanovaImportanceEvaluator",
            "importances": None,
            "error": str(exc),
        }


def _read_yaml_file(path: Optional[str]) -> Optional[Any]:
    """Load a YAML file and return its parsed content, or ``None`` on failure."""
    if not path:
        return None
    try:
        import yaml
        with open(path, "r", encoding="utf-8") as fh:
            return yaml.safe_load(fh)
    except Exception as exc:
        logger.warning("Could not read YAML file %s: %s", path, exc)
        return None


def _dump_study_yaml(path: Path, study: Any) -> None:
    """
    Write a human-readable YAML summary of the Optuna study.

    Each trial entry includes its ``distributions`` block so the file can be
    used to reconstruct a fully functional ``optuna.Study`` via
    :func:`load_study_from_yaml`.

    Structure
    ---------
    .. code-block:: yaml

        study_name: cinnamon_ctgan
        direction: MAXIMIZE
        n_trials_total: 20
        n_trials_complete: 18
        n_trials_pruned: 2
        n_trials_failed: 0
        best_trial:
          number: 7
          value: 0.82
          params: {model_parameter__embedding_dim: 128, ...}
          ...
        trials:
          - number: 0
            state: COMPLETE
            value: 0.71
            params: {model_parameter__embedding_dim: 64, ...}
            distributions:
              model_parameter__embedding_dim:
                type: CategoricalDistribution
                choices: [64, 128]
            duration_seconds: 4.2
            user_attrs: {}
          - ...
    """
    import yaml  # type: ignore

    trials = study.trials
    state_counts: Dict[str, int] = {}
    for t in trials:
        n = t.state.name
        state_counts[n] = state_counts.get(n, 0) + 1

    best_dict: Optional[dict] = None
    try:
        best_dict = _serialize_trial(study.best_trial)
    except ValueError:
        pass

    doc = {
        "study_name": study.study_name,
        "direction": study.direction.name,
        "n_trials_total": len(trials),
        "n_trials_complete": state_counts.get("COMPLETE", 0),
        "n_trials_pruned": state_counts.get("PRUNED", 0),
        "n_trials_failed": state_counts.get("FAIL", 0),
        "best_trial": best_dict,
        "trials": [_serialize_trial(t) for t in trials],
    }

    path.write_text(yaml.dump(doc, allow_unicode=True, sort_keys=False), encoding="utf-8")


# ---------------------------------------------------------------------------
# Loading helpers
# ---------------------------------------------------------------------------


def load_study_from_yaml(yaml_path: Path) -> Any:
    """
    Reconstruct a fully functional ``optuna.Study`` from a ``study.yaml``
    artifact file.

    The returned study is backed by an in-memory storage and contains all
    trials with their parameter distributions, states, values and timings.
    It can be used for inspection and visualisation just like a study
    returned by ``optuna.create_study()``.

    Parameters
    ----------
    yaml_path:
        Path to a ``study.yaml`` written by :func:`save_artifact`.

    Returns
    -------
    optuna.Study
        Study with all trials loaded.

    Examples
    --------
    >>> from pathlib import Path
    >>> from hyperparameter_tuning.artifact_writer import load_study_from_yaml
    >>> study = load_study_from_yaml(Path("outputs/hyperparameter_tuning/cinnamon_ctgan_20260521/study.yaml"))
    >>> print(study.best_trial.value)
    >>> print(study.best_params)
    """
    import yaml
    import optuna
    import optuna.trial as ot

    with open(yaml_path, "r", encoding="utf-8") as fh:
        doc = yaml.safe_load(fh)

    direction_raw: str = doc.get("direction", "MAXIMIZE")
    direction = (
        optuna.study.StudyDirection.MAXIMIZE
        if direction_raw.upper() == "MAXIMIZE"
        else optuna.study.StudyDirection.MINIMIZE
    )

    study = optuna.create_study(
        study_name=doc.get("study_name", "loaded_study"),
        direction="maximize" if direction == optuna.study.StudyDirection.MAXIMIZE else "minimize",
    )

    _state_map = {
        "COMPLETE":  ot.TrialState.COMPLETE,
        "PRUNED":    ot.TrialState.PRUNED,
        "FAIL":      ot.TrialState.FAIL,
        "RUNNING":   ot.TrialState.RUNNING,
        "WAITING":   ot.TrialState.WAITING,
    }

    frozen_trials: List[ot.FrozenTrial] = []
    for raw in doc.get("trials", []):
        state = _state_map.get(raw.get("state", "COMPLETE"), ot.TrialState.COMPLETE)

        distributions = {
            name: _deserialize_distribution(dist_data)
            for name, dist_data in raw.get("distributions", {}).items()
        }

        # If a trial has params but no distributions (old format), infer
        # CategoricalDistribution with the observed value as the only choice.
        for param, value in raw.get("params", {}).items():
            if param not in distributions:
                import optuna.distributions as od
                distributions[param] = od.CategoricalDistribution(choices=(value,))

        def _parse_dt(s: Optional[str]) -> Optional[datetime]:
            return datetime.fromisoformat(s) if s else None

        trial = ot.FrozenTrial(
            number=raw["number"],
            trial_id=raw["number"],
            state=state,
            value=raw.get("value"),
            values=None,
            datetime_start=_parse_dt(raw.get("datetime_start")),
            datetime_complete=_parse_dt(raw.get("datetime_complete")),
            params=raw.get("params", {}),
            distributions=distributions,
            user_attrs=raw.get("user_attrs", {}),
            system_attrs={},
            intermediate_values={},
        )
        frozen_trials.append(trial)

    study.add_trials(frozen_trials)
    logger.info("Loaded %d trials from %s", len(frozen_trials), yaml_path)
    return study


def load_artifact(run_dir: Path) -> Dict[str, Any]:
    """
    Load a complete artifact run directory.

    Returns a dict with:

    ``study``
        ``optuna.Study`` reconstructed from ``study.yaml``.
    ``study_joblib``
        ``optuna.Study`` loaded from ``study.joblib`` (exact binary copy).
        ``None`` if joblib is not available or the file is missing.
    ``run_metadata``
        Parsed content of ``run_metadata.json``.
    ``best_trial``
        Parsed content of ``best_trial.json``.
    ``param_importances``
        Parsed content of ``param_importances.json``.
    ``run_dir``
        The ``Path`` of the run directory.

    Parameters
    ----------
    run_dir:
        Path to a run directory created by :func:`save_artifact`, e.g.
        ``outputs/hyperparameter_tuning/cinnamon_ctgan_20260521_161814/``.

    Examples
    --------
    >>> from pathlib import Path
    >>> from hyperparameter_tuning.artifact_writer import load_artifact
    >>> art = load_artifact(Path("outputs/hyperparameter_tuning/cinnamon_ctgan_20260521_161814"))
    >>> print(art["study"].best_params)
    >>> print(art["run_metadata"]["tuning_setup"]["optuna_version"])
    """
    result: Dict[str, Any] = {"run_dir": run_dir}

    # study.yaml → optuna.Study
    yaml_path = run_dir / "study.yaml"
    if yaml_path.exists():
        result["study"] = load_study_from_yaml(yaml_path)
    else:
        result["study"] = None
        logger.warning("study.yaml not found in %s", run_dir)

    # study.joblib → optuna.Study (binary fallback)
    joblib_path = run_dir / "study.joblib"
    try:
        import joblib
        result["study_joblib"] = joblib.load(joblib_path) if joblib_path.exists() else None
    except Exception as exc:
        logger.warning("Could not load study.joblib: %s", exc)
        result["study_joblib"] = None

    # YAML metadata files
    import yaml as _yaml
    for key, filename in [
        ("run_metadata",      "run_metadata.yaml"),
        ("best_trial",        "best_trial.yaml"),
        ("param_importances", "param_importances.yaml"),
    ]:
        fpath = run_dir / filename
        if fpath.exists():
            try:
                result[key] = _yaml.safe_load(fpath.read_text(encoding="utf-8"))
            except Exception as exc:
                logger.warning("Could not read %s: %s", filename, exc)
                result[key] = None
        else:
            result[key] = None

    return result


# ---------------------------------------------------------------------------
# Public save entry-point
# ---------------------------------------------------------------------------


def save_artifact(
    artifact_dir: Path,
    study: Any,
    *,
    synthesizer: str,
    sampler: str,
    pruner: str,
    n_trials: int,
    direction: str,
    train_size: float,
    random_state: int,
    real: Any,
    target_variable: str,
    start_time: datetime,
    end_time: datetime,
    optuna_config_path: Optional[str] = None,
    data_path: Optional[str] = None,
    grids_dir: Optional[Path] = None,
) -> Path:
    """
    Persist the five artifact files for a completed Optuna study.

    Parameters
    ----------
    artifact_dir:
        Root directory under which to create the run subdirectory.
    study:
        The ``optuna.Study`` returned by ``study.optimize``.
    synthesizer:
        Synthesizer id (e.g. ``"ctgan"``).
    sampler:
        Short sampler name forwarded from :func:`optimize`.
    pruner:
        Short pruner name forwarded from :func:`optimize`.
    n_trials:
        Configured trial budget (not the number that actually finished).
    direction:
        ``"maximize"`` or ``"minimize"``.
    train_size:
        ML utility train/test split ratio used during scoring.
    random_state:
        Seed used for the sampler and evaluator.
    real:
        Real pandas DataFrame – only shape and column names are recorded.
    target_variable:
        Target column name for the ML utility scorer.
    start_time:
        UTC datetime captured just before ``study.optimize`` was called.
    end_time:
        UTC datetime captured just after ``study.optimize`` returned.
    optuna_config_path:
        Optional path to the ``optuna_config.yaml``.
    data_path:
        Optional CSV path recorded as metadata only.
    grids_dir:
        Optional path to ``parameter_grids/``.

    Returns
    -------
    Path
        The run subdirectory that was created.
    """
    import optuna

    ts = start_time.strftime("%Y%m%d_%H%M%S")
    run_dir = artifact_dir / f"{study.study_name}_{ts}"

    try:
        run_dir.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        logger.error("Failed to create artifact directory %s: %s", run_dir, exc)
        return run_dir

    # ------------------------------------------------------------------ #
    # 1. run_metadata.json
    # ------------------------------------------------------------------ #
    optuna_config_content = _read_yaml_file(optuna_config_path)

    grid_yaml_path: Optional[str] = None
    grid_yaml_content: Optional[Any] = None
    if grids_dir is not None:
        grid_path = grids_dir / f"{synthesizer}.yaml"
        if grid_path.exists():
            grid_yaml_path = str(grid_path)
            grid_yaml_content = _read_yaml_file(grid_yaml_path)

    input_data_info: Dict[str, Any] = {
        "path": data_path,
        "n_rows": None,
        "n_columns": None,
        "columns": None,
        "target_column": target_variable,
    }
    try:
        input_data_info["n_rows"] = int(len(real))
        input_data_info["n_columns"] = int(real.shape[1])
        input_data_info["columns"] = list(real.columns)
    except Exception as exc:
        logger.warning("Could not extract DataFrame info for artifact: %s", exc)

    run_metadata: Dict[str, Any] = {
        "optuna_config": {
            "path": optuna_config_path,
            "content": optuna_config_content,
        },
        "parameter_grid_yaml": {
            "path": grid_yaml_path,
            "content": grid_yaml_content,
        },
        "input_data": input_data_info,
        "tuning_setup": {
            "synthesizer": synthesizer,
            "study_name": study.study_name,
            "sampler": sampler,
            "pruner": pruner,
            "n_trials": n_trials,
            "direction": direction,
            "train_size": train_size,
            "random_state": random_state,
            "timestamp_start": start_time,
            "timestamp_end": end_time,
            "duration_seconds": (end_time - start_time).total_seconds(),
            "optuna_version": optuna.__version__,
        },
    }
    try:
        _dump_yaml(run_dir / "run_metadata.yaml", run_metadata)
        logger.debug("Artifact: wrote run_metadata.yaml")
    except Exception as exc:
        logger.error("Artifact: failed to write run_metadata.yaml: %s", exc)

    # ------------------------------------------------------------------ #
    # 2. best_trial.yaml
    # ------------------------------------------------------------------ #
    try:
        _dump_yaml(run_dir / "best_trial.yaml", _serialize_best_trial(study))
        logger.debug("Artifact: wrote best_trial.yaml")
    except Exception as exc:
        logger.error("Artifact: failed to write best_trial.yaml: %s", exc)

    # ------------------------------------------------------------------ #
    # 3. param_importances.yaml
    # ------------------------------------------------------------------ #
    try:
        _dump_yaml(run_dir / "param_importances.yaml", _compute_importances(study))
        logger.debug("Artifact: wrote param_importances.yaml")
    except Exception as exc:
        logger.error("Artifact: failed to write param_importances.yaml: %s", exc)

    # ------------------------------------------------------------------ #
    # 4. study.yaml  (human-readable + round-trippable)
    # ------------------------------------------------------------------ #
    try:
        _dump_study_yaml(run_dir / "study.yaml", study)
        logger.debug("Artifact: wrote study.yaml")
    except Exception as exc:
        logger.error("Artifact: failed to write study.yaml: %s", exc)

    # ------------------------------------------------------------------ #
    # 5. study.joblib  (binary, exact copy)
    # ------------------------------------------------------------------ #
    try:
        import joblib
        joblib.dump(study, run_dir / "study.joblib")
        logger.debug("Artifact: wrote study.joblib")
    except Exception as exc:
        logger.error("Artifact: failed to write study.joblib: %s", exc)

    logger.info("Hyperparameter tuning artifact saved to: %s", run_dir)
    return run_dir


__all__ = ["save_artifact", "load_study_from_yaml", "load_artifact"]
