"""
Optuna-based hyperparameter tuning for tabular synthesizers.

Primary engine for searching `parameter_grids/<synth>.yaml` spaces.

Each trial fits a fresh synthesizer and optimizes the synthesizer's own fit
metric (a training loss or a goodness-of-fit score returned by ``fit()``); the
optimization direction is supplied per synthesizer by the caller. There is no
longer any evaluation of the synthetic sample against the real data.

Public surface:
    - OptunaResult dataclass
    - optimize(...) function
    - _build_sampler / _build_pruner factories
    - _build_search_space (YAML -> optuna-suggest mapping)

The mapping from `parameter_grids/<synth>.yaml` entries to suggest calls:

    * Plain list of scalars (int / float / str / bool):
        -> trial.suggest_categorical
    * List containing any non-hashable element (e.g. nested lists like
      `[[128, 128], [256, 256]]`):
        -> trial.suggest_categorical over the integer index, resolved back
           to the underlying value before being passed downstream.
    * Range-dict {min: ..., max: ..., step?: ..., log?: bool, type?: 'int'|'float'}:
        -> trial.suggest_int  when type=='int' or both bounds are ints and
           no `log`/non-integer step is set
        -> trial.suggest_float otherwise

Single-objective only. Multi-objective is intentionally left out — none of
the existing scoring paths return more than one comparable score today.
"""

from __future__ import annotations

import logging
import math
from copy import deepcopy
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, List, Mapping, Optional, Sequence, Tuple, Union

# Support both `from .artifact_writer import ...` (when imported as a package
# member) and `from artifact_writer import ...` (when this file is loaded via the
# script-mode `python hyperparameter_tuning/main.py` flow that the existing CLI
# uses).
#
# NOTE: the synthesizer-config / parameter-grid / utility-score helpers used to
# live in a sibling module `hyperparameter_tuning.hyperparameter_tuning`. That
# module was lost (never committed), so the helpers are now inlined below to keep
# this Optuna engine fully self-contained.
try:
    from .artifact_writer import save_artifact as _save_artifact  # type: ignore[no-redef]
except ImportError:  # pragma: no cover - script-mode fallback
    from artifact_writer import save_artifact as _save_artifact  # type: ignore[no-redef]

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Inlined config / grid / scoring helpers
#
# Recovered from the lost `hyperparameter_tuning.hyperparameter_tuning` module so
# the Optuna path depends only on this file. `_repo_relative` resolves against
# this file's location; since it sits in `hyperparameter_tuning/` (same as the
# original module), `parents[1]` is the `cinnamon-synthetization/` package root.
# ---------------------------------------------------------------------------


def _repo_relative(*parts: str) -> Path:
    """Build a path relative to the synthetization package root."""
    return Path(__file__).resolve().parents[1].joinpath(*parts)


DEFAULT_SYNTHESIZER_CONFIG_DIR = _repo_relative(
    "synthetic_tabular_data_generator", "synthesizer_config"
)
DEFAULT_PARAMETER_GRIDS_DIR = _repo_relative("hyperparameter_tuning", "parameter_grids")

#: Default directory under which one timestamped artifact subdirectory is
#: written per completed study (e.g. ``outputs/hyperparameter_tuning/<study>_<ts>``).
DEFAULT_ARTIFACT_DIR = _repo_relative("outputs", "hyperparameter_tuning")


def _load_yaml(path: Path) -> Dict[str, Any]:
    """Load a YAML file and validate that its root is a mapping."""
    import yaml

    with path.open("r", encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    if not isinstance(data, dict):
        raise ValueError(f"Invalid YAML root (expected mapping) in {path}")
    return data


def extract_default_hyperparameters(
    ui_config: Mapping[str, Any]
) -> Dict[str, Dict[str, Any]]:
    """
    Convert a UI config (from `synthesizer_config/*.yaml`) into default values.
    """
    configurations = ui_config.get("configurations") or {}
    if not isinstance(configurations, dict):
        raise ValueError("ui_config['configurations'] must be a mapping")

    defaults: Dict[str, Dict[str, Any]] = {}
    for group_name, group in configurations.items():
        if not isinstance(group, dict):
            continue
        params = group.get("parameters") or []
        if not isinstance(params, list):
            continue
        group_defaults: Dict[str, Any] = {}
        for p in params:
            if not isinstance(p, dict):
                continue
            name = p.get("name")
            if not name:
                continue
            group_defaults[str(name)] = p.get("default_value")
        defaults[str(group_name)] = group_defaults
    return defaults


def build_default_algorithm_config(
    synthesizer_name: str,
    config_dir: Path = DEFAULT_SYNTHESIZER_CONFIG_DIR,
) -> Dict[str, Any]:
    """
    Build an `algorithm_config` shaped like `app.py` expects:
      {"synthetization_configuration": {"algorithm": {...}}}
    """
    ui_path = config_dir / f"{synthesizer_name}.yaml"
    if not ui_path.exists():
        raise FileNotFoundError(f"Missing synthesizer config: {ui_path}")
    ui_cfg = _load_yaml(ui_path)
    defaults = extract_default_hyperparameters(ui_cfg)
    algo = {
        "synthesizer": str(ui_cfg.get("name") or synthesizer_name),
        "type": ui_cfg.get("type", "cross-sectional"),
        "version": str(ui_cfg.get("version", "0.1")),
        "model_parameter": defaults.get("model_parameter", {}),
        "model_fitting": defaults.get("model_fitting", {}),
        "sampling": defaults.get("sampling", {}),
    }
    return {"synthetization_configuration": {"algorithm": algo}}


def get_parameter_grid(
    synthesizer_type: str,
    grids_dir: Path = DEFAULT_PARAMETER_GRIDS_DIR,
    strict: bool = True,
) -> Dict[str, Any]:
    """
    Load per-synthesizer hyperparameter grid YAML from `parameter_grids/<synth>.yaml`.

    Aliases:
      - "tabddpm" -> "ddpm" (repo synthesizer id is "ddpm")
    """
    alias_map = {"tabddpm": "ddpm"}
    synth = alias_map.get(synthesizer_type, synthesizer_type)
    path = grids_dir / f"{synth}.yaml"
    if not path.exists():
        if strict:
            raise FileNotFoundError(
                f"Unknown synthesizer type: {synthesizer_type} (missing {path})"
            )
        return {}
    data = _load_yaml(path)
    if "model_parameter" not in data:
        data["model_parameter"] = {}
    if "model_fitting" not in data:
        data["model_fitting"] = {}
    if "sampling" not in data:
        data["sampling"] = {}
    return data


def _split_param_key(key: str) -> Tuple[Optional[str], Optional[str]]:
    """Split a ``<group>__<param>`` key, or return ``(None, None)`` if invalid."""
    if "__" not in key:
        return (None, None)
    group, param = key.split("__", 1)
    if group not in {"model_parameter", "model_fitting", "sampling"}:
        return (None, None)
    return (group, param)


def build_algorithm_config_from_params(params: Mapping[str, Any]) -> Dict[str, Any]:
    """
    Convert one expanded sklearn parameter dict into a concrete `algorithm_config`
    suitable for passing into `app.py` (or posting as YAML to the API).
    """
    if "algorithm_config_base" not in params:
        raise KeyError("Missing required key 'algorithm_config_base'")
    cfg = deepcopy(params["algorithm_config_base"])
    algo = cfg["synthetization_configuration"]["algorithm"]

    synth = params.get("synthesizer_name")
    if synth:
        algo["synthesizer"] = synth

    for k, v in params.items():
        if k in {"synthesizer_name", "algorithm_config_base"}:
            continue
        group, param = _split_param_key(k)
        if group is None:
            continue
        if group not in algo:
            algo[group] = {}
        algo[group][param] = v
    return cfg


# ---------------------------------------------------------------------------
# Result container
# ---------------------------------------------------------------------------


@dataclass
class OptunaResult:
    """
    Aggregate result returned by :func:`optimize`.

    Attributes:
        best_params:           Flat dict of best parameter values, keyed by
                               ``"<group>__<name>"`` matching the sklearn
                               grid convention.
        best_score:            Best fit metric observed (per the study's
                               optimization direction).
        best_algorithm_config: ``algorithm_config`` dict ready to feed into
                               ``fit_metric_fn`` / the API.
        best_synthetic:        Always ``None`` — trials optimize the fit metric
                               and no longer generate a synthetic sample. The
                               caller re-fits the best config to sample.
        study:                 Underlying ``optuna.Study`` for further
                               inspection (trials, importances, plots, ...).
        per_synthesizer:       For multi-synthesizer runs, a list of
                               per-synthesizer summaries. Empty for a single
                               synthesizer call.
    """

    best_params: Dict[str, Any]
    best_score: float
    best_algorithm_config: Optional[Dict[str, Any]]
    best_synthetic: Any
    study: Any
    per_synthesizer: List[Dict[str, Any]] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Sampler / pruner factories
# ---------------------------------------------------------------------------


def _build_sampler(
    name: str,
    search_space: Optional[Mapping[str, Sequence[Any]]] = None,
    seed: Optional[int] = None,
) -> Any:
    """
    Construct an Optuna sampler from a short name.

    Supported names:
        "tpe"     -> ``optuna.samplers.TPESampler``        (Bayesian, default)
        "cmaes"   -> ``optuna.samplers.CmaEsSampler``
        "grid"    -> ``optuna.samplers.GridSampler`` (requires ``search_space``)
        "random"  -> ``optuna.samplers.RandomSampler``
        "qmc"     -> ``optuna.samplers.QMCSampler``
        "nsgaii"  -> ``optuna.samplers.NSGAIISampler``

    The ``search_space`` argument is only used by the grid sampler and must
    map parameter names to the *exact* lists of values the search will
    enumerate.
    """
    import optuna  # local import: keep package import light

    key = (name or "tpe").strip().lower()
    if key == "tpe":
        return optuna.samplers.TPESampler(seed=seed)
    if key == "cmaes":
        return optuna.samplers.CmaEsSampler(seed=seed)
    if key == "grid":
        if not search_space:
            raise ValueError("GridSampler requires a non-empty search_space.")
        return optuna.samplers.GridSampler(dict(search_space), seed=seed)
    if key == "random":
        return optuna.samplers.RandomSampler(seed=seed)
    if key == "qmc":
        return optuna.samplers.QMCSampler(seed=seed)
    if key == "nsgaii":
        return optuna.samplers.NSGAIISampler(seed=seed)
    raise ValueError(f"Unknown sampler: {name!r}")


def _build_pruner(name: Optional[str]) -> Any:
    """
    Construct an Optuna pruner from a short name.

    Supported names:
        "median"              -> ``MedianPruner`` (default)
        "hyperband"           -> ``HyperbandPruner``
        "successive_halving"  -> ``SuccessiveHalvingPruner``
        "none" / None         -> ``NopPruner``
    """
    import optuna

    key = (name or "median").strip().lower()
    if key == "median":
        return optuna.pruners.MedianPruner()
    if key == "hyperband":
        return optuna.pruners.HyperbandPruner()
    if key == "successive_halving":
        return optuna.pruners.SuccessiveHalvingPruner()
    if key in {"none", "nop", "no"}:
        return optuna.pruners.NopPruner()
    raise ValueError(f"Unknown pruner: {name!r}")


# ---------------------------------------------------------------------------
# YAML -> search space
# ---------------------------------------------------------------------------


def _is_hashable(value: Any) -> bool:
    try:
        hash(value)
    except TypeError:
        return False
    return True


def _flatten_yaml_grid(grid_yaml: Mapping[str, Any]) -> Dict[str, Any]:
    """
    Flatten the per-group YAML grid into a single dict keyed by
    ``"<group>__<param>"`` while preserving the original value shape.
    """
    flat: Dict[str, Any] = {}
    for group in ("model_parameter", "model_fitting", "sampling"):
        group_grid = grid_yaml.get(group) or {}
        if not isinstance(group_grid, dict):
            raise ValueError(f"YAML group '{group}' must be a mapping")
        for param, values in group_grid.items():
            flat[f"{group}__{param}"] = values
    return flat


def _is_range_dict(value: Any) -> bool:
    return (
        isinstance(value, Mapping)
        and "min" in value
        and "max" in value
    )


def _suggest_for(
    trial: Any,
    key: str,
    spec: Any,
    index_lookup: Dict[str, List[Any]],
) -> Any:
    """
    Single suggestion call dispatcher used inside the optuna objective.

    Mutates ``index_lookup`` for categorical-with-unhashable specs so that
    the caller can resolve the chosen index back to the real value.
    """
    if _is_range_dict(spec):
        lo = spec["min"]
        hi = spec["max"]
        step = spec.get("step")
        use_log = bool(spec.get("log", False))
        declared_type = str(spec.get("type", "")).lower()

        is_int = (
            declared_type == "int"
            or (
                declared_type != "float"
                and isinstance(lo, int)
                and isinstance(hi, int)
                and (step is None or isinstance(step, int))
                and not use_log
            )
        )
        if is_int:
            return trial.suggest_int(key, int(lo), int(hi), step=int(step) if step else 1)
        return trial.suggest_float(
            key,
            float(lo),
            float(hi),
            step=float(step) if step is not None else None,
            log=use_log,
        )

    if isinstance(spec, list):
        if len(spec) == 0:
            raise ValueError(f"Empty list for parameter {key!r}")
        if all(_is_hashable(v) for v in spec):
            return trial.suggest_categorical(key, spec)
        # Fall back to index categorical for unhashable members (nested lists).
        index_lookup[key] = list(spec)
        idx_key = f"{key}__idx"
        idx = trial.suggest_categorical(idx_key, list(range(len(spec))))
        return deepcopy(spec[int(idx)])

    # Scalar -> single-value categorical (still recorded in the study).
    return trial.suggest_categorical(key, [spec])


def _build_search_space(grid_yaml: Mapping[str, Any]) -> Dict[str, List[Any]]:
    """
    Build an Optuna ``GridSampler`` search space from the YAML grid.

    Lists are kept as-is. Non-hashable list members are replaced by their
    integer index (matching the ``__idx`` keys produced inside the
    objective). Range-dicts are materialised by stepping through the
    declared range.
    """
    flat = _flatten_yaml_grid(grid_yaml)
    space: Dict[str, List[Any]] = {}
    for key, spec in flat.items():
        if _is_range_dict(spec):
            lo = spec["min"]
            hi = spec["max"]
            step = spec.get("step")
            declared_type = str(spec.get("type", "")).lower()
            if (
                declared_type == "int"
                or (
                    declared_type != "float"
                    and isinstance(lo, int)
                    and isinstance(hi, int)
                )
            ):
                step_i = int(step) if step else 1
                space[key] = list(range(int(lo), int(hi) + 1, step_i))
            else:
                # Float range with a step is materialisable; without step we
                # can't enumerate it for the grid sampler, so we sample the
                # endpoints + midpoint as a coarse fallback.
                if step is None:
                    mid = (float(lo) + float(hi)) / 2.0
                    space[key] = [float(lo), mid, float(hi)]
                else:
                    values: List[float] = []
                    v = float(lo)
                    while v <= float(hi) + 1e-12:
                        values.append(round(v, 12))
                        v += float(step)
                    space[key] = values
        elif isinstance(spec, list):
            if all(_is_hashable(v) for v in spec):
                space[key] = list(spec)
            else:
                space[f"{key}__idx"] = list(range(len(spec)))
        else:
            space[key] = [spec]
    return space


def _params_to_sklearn_dict(
    synthesizer: str,
    base_config: Mapping[str, Any],
    params: Mapping[str, Any],
) -> Dict[str, Any]:
    """Convert ``best_params`` -> a dict ready for ``build_algorithm_config_from_params``."""
    out: Dict[str, Any] = {
        "synthesizer_name": synthesizer,
        "algorithm_config_base": deepcopy(base_config),
    }
    for k, v in params.items():
        if k.endswith("__idx"):
            continue
        out[k] = v
    return out


# ---------------------------------------------------------------------------
# Optimize
# ---------------------------------------------------------------------------


def _run_single(
    fit_metric_fn: Callable[[Dict[str, Any]], Optional[float]],
    real: Any,
    target_variable: str,
    synthesizer: str,
    *,
    sampler: str,
    n_trials: int,
    pruner: str,
    direction: str = "maximize",
    timeout: Optional[float],
    study_name: Optional[str],
    storage: Optional[str],
    train_size: float,
    random_state: int,
    config_dir: Path,
    grids_dir: Path,
    artifact_dir: Optional[Path] = None,
    optuna_config_path: Optional[str] = None,
    data_path: Optional[str] = None,
    show_progress_bar: bool = True,
) -> OptunaResult:
    import optuna

    base_cfg = build_default_algorithm_config(synthesizer, config_dir=config_dir)
    grid_yaml = get_parameter_grid(synthesizer, grids_dir=grids_dir, strict=True)
    flat = _flatten_yaml_grid(grid_yaml)

    grid_space = _build_search_space(grid_yaml) if sampler.lower() == "grid" else None
    sampler_obj = _build_sampler(sampler, search_space=grid_space, seed=random_state)
    pruner_obj = _build_pruner(pruner)

    # Track the best config/params across trials. Optuna only stores the flat
    # param dict (with list-valued params encoded as ``__idx``), so we keep the
    # already-resolved values to reconstruct the winning algorithm_config.
    _maximize = direction.lower() != "minimize"
    best_box: Dict[str, Any] = {
        "score": float("-inf") if _maximize else float("inf"),
        "cfg": None, "params": None,
    }

    def objective(trial: "optuna.trial.Trial") -> float:
        index_lookup: Dict[str, List[Any]] = {}
        resolved: Dict[str, Any] = {}
        for key, spec in flat.items():
            resolved[key] = _suggest_for(trial, key, spec, index_lookup)

        sklearn_params = _params_to_sklearn_dict(synthesizer, base_cfg, resolved)
        algorithm_config = build_algorithm_config_from_params(sklearn_params)

        logger.info(
            "Optuna trial: synthesizer=%s trial=%s params=%s",
            synthesizer,
            trial.number,
            resolved,
        )

        try:
            metric = fit_metric_fn(algorithm_config)
        except Exception as exc:
            # Don't crash the whole study on a single bad config — record
            # the error on the trial and prune. No silent fail.
            trial.set_user_attr("error", repr(exc))
            logger.warning(
                "Trial %s failed: synthesizer=%s error=%s",
                trial.number,
                synthesizer,
                exc,
            )
            raise optuna.TrialPruned() from exc

        # A synthesizer that does not expose a fit metric (e.g. ctgan/tvae,
        # whose tuning support comes later) returns None — prune the trial with
        # a clear reason rather than scoring it.
        if metric is None or (isinstance(metric, float) and math.isnan(metric)):
            reason = (
                f"synthesizer '{synthesizer}' did not return a fit metric "
                f"(hyperparameter tuning is not supported for it)"
            )
            trial.set_user_attr("error", reason)
            logger.warning("Trial %s pruned: %s", trial.number, reason)
            raise optuna.TrialPruned()

        score = float(metric)
        logger.info(
            "Optuna trial result: synthesizer=%s trial=%s score=%.4f",
            synthesizer,
            trial.number,
            score,
        )

        is_better = score > best_box["score"] if _maximize else score < best_box["score"]
        if is_better:
            best_box["score"] = score
            best_box["cfg"] = algorithm_config
            best_box["params"] = resolved

        return score

    study = optuna.create_study(
        study_name=study_name or f"cinnamon_{synthesizer}",
        sampler=sampler_obj,
        pruner=pruner_obj,
        direction=direction,
        storage=storage,
        load_if_exists=storage is not None,
    )

    start_time = datetime.now(timezone.utc)
    study.optimize(objective, n_trials=n_trials, timeout=timeout, gc_after_trial=True,
                   show_progress_bar=show_progress_bar)
    end_time = datetime.now(timezone.utc)

    if artifact_dir is not None:
        try:
            _save_artifact(
                artifact_dir=artifact_dir,
                study=study,
                synthesizer=synthesizer,
                sampler=sampler,
                pruner=pruner,
                n_trials=n_trials,
                direction=direction,
                train_size=train_size,
                random_state=random_state,
                real=real,
                target_variable=target_variable,
                start_time=start_time,
                end_time=end_time,
                optuna_config_path=optuna_config_path,
                data_path=data_path,
                grids_dir=grids_dir,
            )
        except Exception as exc:
            logger.error("Failed to save tuning artifact: %s", exc)

    completed = [t for t in study.trials if t.state.name == "COMPLETE"]
    if completed:
        best_trial = study.best_trial  # respects direction (maximize or minimize)
        # ``best_box`` tracks the best completed trial with the same direction
        # as the study and preserves resolved (list-valued) params, so prefer
        # it. Fall back to rebuilding from ``best_trial`` if it is unset.
        if best_box["params"] is not None:
            best_params = best_box["params"]
            best_score = best_box["score"]
            best_cfg = best_box["cfg"]
        else:
            best_params = {k: v for k, v in best_trial.params.items() if not k.endswith("__idx")}
            best_score = float(best_trial.value or 0.0)
            sklearn_params = _params_to_sklearn_dict(synthesizer, base_cfg, best_params)
            best_cfg = build_algorithm_config_from_params(sklearn_params)
    else:
        logger.warning("No COMPLETE trials for synthesizer=%s.", synthesizer)
        best_params = {}
        best_score = 0.0
        best_cfg = None

    return OptunaResult(
        best_params=best_params,
        best_score=best_score,
        best_algorithm_config=best_cfg,
        best_synthetic=None,
        study=study,
    )


def optimize(
    fit_metric_fn: Callable[[Dict[str, Any]], Optional[float]],
    real: Any,
    target_variable: str,
    synthesizer: Union[str, Sequence[str]],
    *,
    sampler: str = "tpe",
    n_trials: int = 50,
    pruner: str = "median",
    direction: str = "maximize",
    timeout: Optional[float] = None,
    study_name: Optional[str] = None,
    storage: Optional[str] = None,
    train_size: float = 0.8,
    random_state: int = 42,
    config_dir: Path = DEFAULT_SYNTHESIZER_CONFIG_DIR,
    grids_dir: Path = DEFAULT_PARAMETER_GRIDS_DIR,
    artifact_dir: Optional[Path] = None,
    optuna_config_path: Optional[str] = None,
    data_path: Optional[str] = None,
    show_progress_bar: bool = False,
) -> OptunaResult:
    """
    Run an Optuna study over one or more synthesizers.

    For a single synthesizer name a single study is created and its
    :class:`OptunaResult` is returned. For a sequence of synthesizer names
    an outer loop creates one study per synthesizer; the result whose
    ``best_score`` is highest is returned, and the per-synthesizer summary
    is exposed via ``OptunaResult.per_synthesizer``. Note: across synthesizers
    the metric is not comparable (loss vs. score), so multi-synthesizer
    comparison is only meaningful when every synthesizer shares a direction.

    Args:
        fit_metric_fn: Callable that takes an ``algorithm_config`` dict, fits a
            fresh synthesizer, and returns its scalar fit metric (loss or
            score), or ``None`` if the synthesizer does not support tuning.
        real: Real dataset (pandas DataFrame). Recorded as artifact metadata
            only — no longer used to score trials.
        target_variable: Recorded as artifact metadata only.
        synthesizer: Synthesizer id (e.g. ``"ddpm"``) or a list of ids for
            a cross-synthesizer search.
        sampler: One of ``tpe / cmaes / grid / random / qmc / nsgaii``.
        n_trials: Trials per synthesizer.
        pruner: One of ``median / hyperband / successive_halving / none``.
        direction: ``maximize`` or ``minimize`` — must match the synthesizer's
            fit-metric semantics (loss -> minimize, score -> maximize).
        timeout: Stop study after this many seconds; ``None`` for no limit.
        study_name: Optuna study name. For multi-synth runs, the synthesizer
            id is appended.
        storage: Optional Optuna storage URL (e.g. ``sqlite:///optuna.db``)
            for persistent / resumable studies.
        train_size: Recorded as artifact metadata only.
        random_state: Seed forwarded to the sampler.
        config_dir: Path to ``synthesizer_config/`` (override for tests).
        grids_dir: Path to ``parameter_grids/`` (override for tests).
        artifact_dir: Directory under which a single timestamped run
            subdirectory is created after the study completes (one per study).
            Pass ``None`` (default) to skip artifact persistence.
        optuna_config_path: Filesystem path to the ``optuna_config.yaml`` used
            for this run.  Embedded verbatim in the artifact when an
            ``artifact_dir`` is set.  ``None`` records the path as absent.
        data_path: Filesystem path to the input CSV.  Recorded as metadata
            only — the file is never read or copied into the artifact.

    Returns:
        :class:`OptunaResult`. ``best_algorithm_config`` may be ``None`` if
        every trial pruned (e.g. all configs raised or returned no metric).
    """
    if isinstance(synthesizer, str):
        synth_list: List[str] = [synthesizer]
        single_mode = True
    else:
        synth_list = list(synthesizer)
        single_mode = False
        if not synth_list:
            raise ValueError("synthesizer list must not be empty")

    results: List[Tuple[str, OptunaResult]] = []
    for synth in synth_list:
        name = study_name
        if name is not None and not single_mode:
            name = f"{name}_{synth}"
        try:
            res = _run_single(
                fit_metric_fn=fit_metric_fn,
                real=real,
                target_variable=target_variable,
                synthesizer=synth,
                sampler=sampler,
                n_trials=n_trials,
                pruner=pruner,
                direction=direction,
                timeout=timeout,
                study_name=name,
                storage=storage,
                train_size=train_size,
                random_state=random_state,
                config_dir=config_dir,
                grids_dir=grids_dir,
                artifact_dir=artifact_dir,
                optuna_config_path=optuna_config_path,
                data_path=data_path,
                show_progress_bar=show_progress_bar,
            )
        except Exception as exc:
            logger.error("Synthesizer %s failed: %s", synth, exc)
            continue
        results.append((synth, res))

    if not results:
        raise RuntimeError("All synthesizers failed; no OptunaResult to return.")

    if single_mode:
        return results[0][1]

    # Pick the best across synthesizers.
    best_synth, best_res = max(
        results,
        key=lambda kv: kv[1].best_score if kv[1].best_score is not None else float("-inf"),
    )
    best_res.per_synthesizer = [
        {
            "synthesizer": s,
            "best_score": r.best_score,
            "best_params": r.best_params,
            "n_complete": sum(1 for t in r.study.trials if t.state.name == "COMPLETE"),
        }
        for s, r in results
    ]
    logger.info("Best synthesizer overall: %s score=%.4f", best_synth, best_res.best_score)
    return best_res


__all__ = [
    "OptunaResult",
    "optimize",
    "_build_sampler",
    "_build_pruner",
    "_build_search_space",
]
