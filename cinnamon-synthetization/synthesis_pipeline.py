"""Run synthesis stages and assemble their output independently of HTTP delivery."""

import math
import time
from copy import deepcopy

import cloudpickle

import synthesis_configuration as configuration
import synthesizer_classes as synthesizer_registry
from api_utility.status.status_updater import (
    intercept_standard_streams,
    update_component_status,
    update_status,
    update_total_synthesis_status,
)
from data_processing.post_process import post_process_dataframe
from data_processing.pre_process import pre_process_dataframe
from data_processing.utils import order_dataframe_by_config


def initialize_synthesizer(synthesizer_name, algorithm_config, attribute_config,
                           data, reference_data, progress_callback):
    synthesizer = synthesizer_registry.synthesizer_classes[synthesizer_name]['class']()
    synthesizer.set_progress_callback(progress_callback)
    synthesizer.initialize_anonymization_configuration(algorithm_config)
    synthesizer.initialize_attribute_configuration(attribute_config)
    synthesizer.initialize_dataset(data)
    if reference_data is not None:
        if not hasattr(synthesizer, "initialize_reference_dataset"):
            raise RuntimeError("Synthesizer does not support reference dataset initialization.")
        synthesizer.initialize_reference_dataset(reference_data)
    synthesizer.initialize_synthesizer()
    return synthesizer


def run_synthesizer_stage(
    stage_label,
    synthesizer_name,
    stage_attribute_config,
    stage_algorithm_config,
    input_data,
    file_path_status,
    reference_data=None,
    replace_text_with_pending=True,
    fill_text_with_pending=True,
    session_key=None,
    status_component_name=None,
    preserve_structured_values=False,
):
    stage_init_time = time.time()
    stage_algorithm = stage_algorithm_config.get("synthetization_configuration", {}).get("algorithm", {})
    tuning_cfg = stage_algorithm.get("hyperparameter_tuning", {}) if isinstance(stage_algorithm, dict) else {}
    tuning_enabled = isinstance(tuning_cfg, dict) and bool(tuning_cfg.get("enabled", False))
    raw_timeout = tuning_cfg.get("timeout_minutes") if tuning_enabled else None
    try:
        timeout_seconds = float(raw_timeout) * 60.0 if raw_timeout else None
    except (TypeError, ValueError):
        timeout_seconds = None
    tuning_fit_start_time = None

    def _resolve_tuning_fitting_remaining_time(fallback_remaining_time=None):
        if not tuning_enabled or timeout_seconds is None or tuning_fit_start_time is None:
            return fallback_remaining_time
        remaining_seconds = max(0, int(math.ceil(timeout_seconds - (time.time() - tuning_fit_start_time))))
        return str(remaining_seconds)

    def _handle_progress_update(step, remaining_time):
        if tuning_enabled and step == "fitting":
            remaining_time = _resolve_tuning_fitting_remaining_time(remaining_time)
        if remaining_time is None:
            return
        update_status(file_path_status, step=step, remaining_time=str(remaining_time))
        if status_component_name is not None:
            update_component_status(
                file_path_status,
                status_component_name,
                fitting_remaining_time=str(remaining_time) if step == "fitting" else None,
                sampling_remaining_time=str(remaining_time) if step == "sampling" else None,
                remaining_time=str(remaining_time) if step == "sampling" else None,
            )

    if preserve_structured_values:
        # The text stage consumes the final structured output without imputing or rounding it again.
        pre_processed_data = input_data.copy()
        pre_processed_reference_data = reference_data.copy() if reference_data is not None else None
        all_missing_values_column = []
    else:
        pre_processed_data, all_missing_values_column = pre_process_dataframe(
            input_data.copy(),
            stage_attribute_config['configurations'],
            replace_text_with_pending=replace_text_with_pending,
        )
        pre_processed_reference_data = None
        if reference_data is not None:
            pre_processed_reference_data, _ = pre_process_dataframe(
                reference_data.copy(),
                stage_attribute_config['configurations'],
                replace_text_with_pending=False,
            )

    working_attribute_config = {
        **stage_attribute_config,
        "configurations": [cfg for cfg in stage_attribute_config["configurations"]
                           if cfg["name"] not in all_missing_values_column],
    }
    if not working_attribute_config["configurations"]:
        raise ValueError("Dataset has no non-empty columns available for synthesis.")

    if tuning_enabled:
        tuning_metadata = getattr(synthesizer_registry, "synthesizer_tuning_metadata", {}).get(synthesizer_name, {})
        tuning_supported = bool(tuning_metadata.get("supported"))
        if not tuning_supported:
            raise RuntimeError(
                f"Hyperparameter tuning is not supported for synthesizer '{synthesizer_name}'."
            )

        from hyperparameter_tuning.optuna_tuning import (
            DEFAULT_ARTIFACT_DIR,
            optimize as run_optuna_study,
        )

        base_algorithm_config = deepcopy(stage_algorithm_config)
        stage_init_duration = time.time() - stage_init_time
        update_status(file_path_status, step='initialization', duration=stage_init_duration, completed=True, remaining_time="0")
        if status_component_name is not None:
            update_component_status(
                file_path_status,
                status_component_name,
                synthesizer_name=synthesizer_name,
                initialization_duration=stage_init_duration,
                fitting_remaining_time="Waiting",
                sampling_remaining_time="Waiting",
                completed=False,
            )

        fit_time = time.time()
        tuning_fit_start_time = fit_time
        fitting_remaining_time = _resolve_tuning_fitting_remaining_time()
        if fitting_remaining_time is not None:
            update_status(file_path_status, step='fitting', remaining_time=fitting_remaining_time)
            if status_component_name is not None:
                update_component_status(
                    file_path_status,
                    status_component_name,
                    fitting_remaining_time=fitting_remaining_time,
                )

        def _fit_one_trial(trial_algorithm_config):
            trial_synth = initialize_synthesizer(
                synthesizer_name, trial_algorithm_config, working_attribute_config,
                pre_processed_data, pre_processed_reference_data, _handle_progress_update,
            )
            return trial_synth.fit()

        sampler = tuning_cfg.get("sampler", configuration.get_study_parameter_default("sampler"))
        pruner = tuning_cfg.get("pruner", configuration.get_study_parameter_default("pruner"))
        n_trials = int(tuning_cfg.get("n_trials", 50))

        target_variable = tuning_cfg.get("target_variable") or pre_processed_data.columns[-1]
        tuning_direction = tuning_metadata.get("direction") or "minimize"

        with intercept_standard_streams(
            file_path_status,
            "fitting",
            component_name=status_component_name,
            remaining_time_transform=_resolve_tuning_fitting_remaining_time if timeout_seconds is not None else None,
        ):
            result = run_optuna_study(
                fit_metric_fn=_fit_one_trial,
                real=pre_processed_data,
                target_variable=target_variable,
                synthesizer=synthesizer_name,
                sampler=sampler,
                pruner=pruner,
                n_trials=n_trials,
                timeout=timeout_seconds,
                direction=tuning_direction,
                random_state=42,
                algorithm_config_base=base_algorithm_config,
                artifact_dir=DEFAULT_ARTIFACT_DIR,
            )

        best_cfg = result.best_algorithm_config
        if best_cfg is None:
            raise RuntimeError("Hyperparameter tuning finished without a best algorithm config.")

        best_synth = initialize_synthesizer(
            synthesizer_name, best_cfg, working_attribute_config,
            pre_processed_data, pre_processed_reference_data, _handle_progress_update,
        )
        best_synth.fit()

        fit_duration = time.time() - fit_time
        print(f"[{stage_label}] Hyperparameter tuning completed.")
        if status_component_name is not None:
            update_component_status(
                file_path_status,
                status_component_name,
                synthesizer_name=synthesizer_name,
                initialization_duration=stage_init_duration,
                fitting_duration=fit_duration,
                fitting_remaining_time="0",
                completed=False,
            )

        sample_time = time.time()
        with intercept_standard_streams(file_path_status, "sampling", component_name=status_component_name):
            samples = best_synth.sample()
        sample_duration = time.time() - sample_time
        synthesizer_class = best_synth
        print(f"[{stage_label}] Data sampled from best tuned synthesizer.")
    else:
        synthesizer_class = initialize_synthesizer(
            synthesizer_name, stage_algorithm_config, working_attribute_config,
            pre_processed_data, pre_processed_reference_data, _handle_progress_update,
        )
        print(f"[{stage_label}] Synthesizer initialized.")

        if session_key is not None and hasattr(synthesizer_class, 'synthesizer') and synthesizer_class.synthesizer is not None:
            if hasattr(synthesizer_class, '_llm_client') and synthesizer_class._llm_client is not None:
                try:
                    synthesizer_class._llm_client.set_session_key(session_key)
                except Exception as e:
                    print(f"[{stage_label}] Warning: Failed to set session key for prompt logging: {e}")

        stage_init_duration = time.time() - stage_init_time
        update_status(file_path_status, step='initialization', duration=stage_init_duration, completed=True, remaining_time="0")
        if status_component_name is not None:
            update_component_status(
                file_path_status,
                status_component_name,
                synthesizer_name=synthesizer_name,
                initialization_duration=stage_init_duration,
                fitting_remaining_time="Waiting",
                sampling_remaining_time="Waiting",
                completed=False,
            )

        fit_time = time.time()
        with intercept_standard_streams(file_path_status, "fitting", component_name=status_component_name):
            synthesizer_class.fit()
        fit_duration = time.time() - fit_time
        print(f"[{stage_label}] Synthesizer fitted.")
        if status_component_name is not None:
            update_component_status(
                file_path_status,
                status_component_name,
                synthesizer_name=synthesizer_name,
                initialization_duration=stage_init_duration,
                fitting_duration=fit_duration,
                fitting_remaining_time="0",
                completed=False,
            )

        sample_time = time.time()
        with intercept_standard_streams(file_path_status, "sampling", component_name=status_component_name):
            samples = synthesizer_class.sample()
        sample_duration = time.time() - sample_time
        print(f"[{stage_label}] Data sampled.")

    if not preserve_structured_values:
        samples = post_process_dataframe(
            samples,
            stage_attribute_config['configurations'],
            all_missing_values_column,
            fill_text_with_pending=fill_text_with_pending,
        )
    print(f"[{stage_label}] Data post-processed.")

    synthesizer_model = synthesizer_class.get_model()
    print(f"[{stage_label}] Model retrieved.")

    if status_component_name is not None:
        update_component_status(
            file_path_status,
            status_component_name,
            synthesizer_name=synthesizer_name,
            duration=stage_init_duration + fit_duration + sample_duration,
            initialization_duration=stage_init_duration,
            fitting_duration=fit_duration,
            sampling_duration=sample_duration,
            fitting_remaining_time="0",
            sampling_remaining_time="0",
            remaining_time="0",
            completed=True,
        )

    return samples, synthesizer_model, stage_init_duration, fit_duration, sample_duration


def update_pipeline_totals(file_path_status, total_init_duration, total_fit_duration, total_sample_duration, *, completed):
    update_status(
        file_path_status,
        step='initialization',
        duration=total_init_duration,
        completed=completed,
        remaining_time="0" if total_init_duration > 0 else None,
    )
    update_status(
        file_path_status,
        'fitting',
        duration=total_fit_duration,
        completed=completed,
        remaining_time="0" if total_fit_duration > 0 else None,
    )
    update_status(
        file_path_status,
        'sampling',
        duration=total_sample_duration,
        completed=completed,
        remaining_time="0" if total_sample_duration > 0 else None,
    )
    total_duration = total_init_duration + total_fit_duration + total_sample_duration
    update_total_synthesis_status(
        file_path_status,
        duration=total_duration,
        completed=completed,
        remaining_time="0" if completed and total_duration > 0 else "Waiting",
    )


def announce_component_synthesis(file_path_status, component_name, synthesizer_name):
    update_component_status(
        file_path_status,
        component_name,
        synthesizer_name=synthesizer_name,
        completed=False,
    )


def run_pipeline(synthesizer_name, file_path_status, attribute_config, algorithm_config,
                 data, original_data, session_key):
    """Return synthetic data and the exported model after updating pipeline status."""
    structured_configs, text_configs = configuration.split_attribute_configurations(attribute_config)
    data_modality = configuration.get_data_modality(synthesizer_name)
    text_reference_data = original_data if original_data is not None else data

    print(
        "Processing capabilities resolved: "
        f"data_modality={data_modality}, "
        f"structured_columns={len(structured_configs)}, text_columns={len(text_configs)}"
    )
    print(
        "Text synthesis reference dataset source: "
        f"{'original_data' if original_data is not None else 'data (fallback)'}"
    )

    total_init_duration = 0.0
    total_fit_duration = 0.0
    total_sample_duration = 0.0

    final_samples = None
    final_model = None

    if structured_configs:
        if data_modality != configuration.PROCESSING_MODALITY_STRUCTURED_ONLY:
            raise ValueError(
                "Structured columns require a structured synthesizer first. Select CTGAN, TVAE, "
                "ARF or another structured synthesizer, then configure text_synthesis_configuration."
            )
        text_config = (
            algorithm_config.get("synthetization_configuration", {}).get("text_synthesis_configuration")
            or algorithm_config.get("text_synthesis_configuration")
            or {}
        )
        text_name = text_config.get("synthetization_configuration", {}).get("algorithm", {}).get("synthesizer")
        if text_configs and (
            text_name not in synthesizer_registry.synthesizer_classes
            or configuration.get_data_modality(text_name) != configuration.PROCESSING_MODALITY_MIXED
        ):
            raise ValueError("Select a mixed-data free-text synthesizer in text_synthesis_configuration.")

        structured_algorithm_config = algorithm_config
        if text_configs and text_name == "llm_mixed_data_embedding_nearest_neighbor_synthesis":
            requested_samples = text_config["synthetization_configuration"]["algorithm"].get("sampling", {}).get("num_samples")
            if requested_samples is not None:
                if (isinstance(requested_samples, bool)
                        or not str(requested_samples).strip().isdigit()
                        or int(requested_samples) <= 0):
                    raise ValueError("Retrieval-guided num_samples must be a positive integer.")
                structured_algorithm_config = deepcopy(algorithm_config)
                structured_algorithm_config["synthetization_configuration"]["algorithm"].setdefault(
                    "sampling", {},
                )["num_samples"] = int(requested_samples)

        print("Pipeline mode: structured synthesis followed by text generation." if text_configs
              else "Pipeline mode: single-stage synthesis.")
        structured_names = [cfg["name"] for cfg in structured_configs]
        structured_attribute_config = {**attribute_config, "configurations": structured_configs}
        announce_component_synthesis(file_path_status, "structured_synthesis", synthesizer_name)
        final_samples, structured_model, init_duration, fit_duration, sample_duration = run_synthesizer_stage(
            stage_label="STRUCTURED_SYNTHESIS",
            synthesizer_name=synthesizer_name,
            stage_attribute_config=structured_attribute_config,
            stage_algorithm_config=structured_algorithm_config,
            input_data=data[structured_names],
            file_path_status=file_path_status,
            session_key=session_key,
            status_component_name="structured_synthesis",
        )
        final_samples = final_samples.reset_index(drop=True)
        final_model = structured_model
        total_init_duration += init_duration
        total_fit_duration += fit_duration
        total_sample_duration += sample_duration
        update_pipeline_totals(file_path_status, total_init_duration, total_fit_duration,
                               total_sample_duration, completed=not text_configs)

        text_models = {}
        for text_column_config in text_configs:
            text_column = text_column_config["name"]
            stage_configs = structured_configs + [text_column_config]
            stage_columns = structured_names + [text_column]
            text_input = final_samples.reindex(columns=stage_columns)
            text_algorithm_config = configuration.build_text_synthesis_algorithm_config(text_config, text_name, len(text_input))
            # Every generated structured row receives one text; tuning runs in the structured stage.
            text_algorithm = text_algorithm_config["synthetization_configuration"]["algorithm"]
            text_algorithm["sampling"]["num_samples"] = len(text_input)
            text_algorithm.pop("hyperparameter_tuning", None)
            announce_component_synthesis(file_path_status, "llm_synthesis", text_name)
            text_samples, text_model, init_duration, fit_duration, sample_duration = run_synthesizer_stage(
                stage_label="TEXT_SYNTHESIS",
                synthesizer_name=text_name,
                stage_attribute_config={**attribute_config, "configurations": stage_configs},
                stage_algorithm_config=text_algorithm_config,
                input_data=text_input,
                reference_data=text_reference_data.reindex(columns=stage_columns),
                file_path_status=file_path_status,
                replace_text_with_pending=False,
                fill_text_with_pending=False,
                preserve_structured_values=True,
                session_key=session_key,
                status_component_name="llm_synthesis",
            )
            if len(text_samples) != len(final_samples):
                raise ValueError("Text synthesis must return exactly one text per structured row.")
            if not text_samples[structured_names].reset_index(drop=True).equals(final_samples[structured_names]):
                raise ValueError("Text synthesis changed the structured ground truth.")
            final_samples[text_column] = text_samples[text_column].reset_index(drop=True)
            text_models[text_column] = text_model
            total_init_duration += init_duration
            total_fit_duration += fit_duration
            total_sample_duration += sample_duration
        if text_models:
            final_model = cloudpickle.dumps({"structured_synthesis": structured_model, "text_synthesis": text_models})
    elif text_configs and data_modality == configuration.PROCESSING_MODALITY_TEXT_ONLY:
        print("Pipeline mode: text-only synthesis.")
        announce_component_synthesis(file_path_status, "llm_synthesis", synthesizer_name)
        text_input = order_dataframe_by_config(data.copy(), attribute_config.get("configurations", []))
        text_algorithm_config = configuration.build_text_synthesis_algorithm_config(algorithm_config, synthesizer_name, len(text_input))
        final_samples, final_model, init_duration, fit_duration, sample_duration = run_synthesizer_stage(
            stage_label="TEXT_SYNTHESIS",
            synthesizer_name=synthesizer_name,
            stage_attribute_config=attribute_config,
            stage_algorithm_config=text_algorithm_config,
            input_data=text_input,
            reference_data=text_reference_data,
            file_path_status=file_path_status,
            replace_text_with_pending=False,
            fill_text_with_pending=False,
            session_key=session_key,
            status_component_name="llm_synthesis",
        )
        total_init_duration += init_duration
        total_fit_duration += fit_duration
        total_sample_duration += sample_duration
    else:
        raise ValueError("Select a text-only synthesizer for a dataset containing only TEXT columns.")

    if final_samples is None or final_model is None:
        raise RuntimeError("Pipeline did not produce synthetic data and model output.")

    final_samples = order_dataframe_by_config(final_samples, attribute_config.get("configurations", []))

    update_pipeline_totals(
        file_path_status,
        total_init_duration,
        total_fit_duration,
        total_sample_duration,
        completed=True,
    )

    return final_samples, final_model
