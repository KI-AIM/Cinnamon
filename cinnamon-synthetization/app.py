import io
import math
import os
import sys
import time
from copy import deepcopy
from functools import lru_cache
from multiprocessing import get_context

import cloudpickle
import pandas as pd
import requests
import yaml
from flask import Flask, request, jsonify, send_from_directory, Response
from flask_cors import CORS

from api_utility.status.status_updater import initialize_status_file
from api_utility.status.status_updater import intercept_standard_streams
from api_utility.status.status_updater import update_component_status
from api_utility.status.status_updater import update_status
from api_utility.status.status_updater import update_total_synthesis_status
import synthesizer_classes as synthesizer_registry
from data_processing.post_process import post_process_dataframe
from data_processing.pre_process import pre_process_dataframe
from data_processing.utils import (
    order_dataframe_by_config,
)
from synthetic_tabular_data_generator.llm import get_llm_profile_names
from synthetic_tabular_data_generator.embedding_profiles import get_embedding_profile_names


app = Flask(__name__)
tasks = {}
CORS(app)

synthesizer_classes = synthesizer_registry.synthesizer_classes
synthesizer_tuning_metadata = getattr(synthesizer_registry, "synthesizer_tuning_metadata", {})

PROCESS_CONTEXT = get_context("spawn")

CALLBACK_TIMEOUT_SECONDS = 30.0
ERROR_CALLBACK_TIMEOUT_SECONDS = 5.0
SYNTHESIZER_CONFIG_DIR = os.path.join(
    os.path.dirname(__file__),
    "synthetic_tabular_data_generator",
    "synthesizer_config",
)
HYPERPARAMETER_TUNING_DIR = os.path.join(os.path.dirname(__file__), "hyperparameter_tuning")

PROCESSING_MODALITY_STRUCTURED_ONLY = "structured_only"
PROCESSING_MODALITY_TEXT_ONLY = "text_only"
PROCESSING_MODALITY_MIXED = "mixed"
PROCESSING_MODALITIES = {
    PROCESSING_MODALITY_STRUCTURED_ONLY,
    PROCESSING_MODALITY_TEXT_ONLY,
    PROCESSING_MODALITY_MIXED,
}


def configure_realtime_logging():
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if not callable(reconfigure):
            continue
        try:
            reconfigure(line_buffering=True, write_through=True)
        except TypeError:
            reconfigure(line_buffering=True)
        except Exception:
            continue


def _status_file_path(session_key):
    return os.path.join(os.path.dirname(__file__), 'outputs', 'status', f"{session_key}.yaml")


def _cleanup_task_state(task_id):
    tasks.pop(task_id, None)


def _prune_finished_tasks():
    for task_id, task_process in list(tasks.items()):
        try:
            if _task_is_alive(task_process):
                continue
        except Exception as exc:
            print(f"Warning: Failed to inspect task {task_id}: {exc}")
        _cleanup_task_state(task_id)


def _mark_task_cancelled(task_id):
    file_path = _status_file_path(task_id)
    if not os.path.exists(file_path):
        return

    try:
        update_status(file_path, 'callback', completed=False)
        for step in ('initialization', 'fitting', 'sampling'):
            update_status(file_path, step, remaining_time='Cancelled')
        update_total_synthesis_status(file_path, completed=False, remaining_time='Cancelled')
        for component_name in ('structured_synthesis', 'llm_synthesis'):
            update_component_status(
                file_path,
                component_name,
                remaining_time='Cancelled',
                fitting_remaining_time='Cancelled',
                sampling_remaining_time='Cancelled',
                completed=False,
            )
    except Exception as exc:
        print(f"Warning: Failed to update cancellation status for session {task_id}: {exc}")


def _task_is_alive(task_process):
    is_alive = getattr(task_process, 'is_alive', None)
    if callable(is_alive):
        return bool(is_alive())
    exitcode = getattr(task_process, 'exitcode', None)
    return exitcode is None


def _terminate_task_process(task_process):
    terminate = getattr(task_process, 'terminate', None)
    if not callable(terminate):
        raise RuntimeError('Task process cannot be terminated cleanly.')
    terminate()

    join = getattr(task_process, 'join', None)
    if callable(join):
        join(timeout=2.0)

    if _task_is_alive(task_process):
        kill = getattr(task_process, 'kill', None)
        if callable(kill):
            kill()
            if callable(join):
                join(timeout=1.0)

    if _task_is_alive(task_process):
        raise RuntimeError('Task process is still running after cancellation attempt.')


def _load_yaml_file(upload, field_name, *, required_keys=None):
    try:
        content = upload.read()
        decoded_content = content.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(f"Uploaded file '{field_name}' must be UTF-8 encoded.") from exc

    try:
        parsed = yaml.safe_load(decoded_content)
    except yaml.YAMLError as exc:
        raise ValueError(f"Uploaded file '{field_name}' contains invalid YAML.") from exc

    if not isinstance(parsed, dict):
        raise ValueError(f"Uploaded file '{field_name}' must contain a YAML object at the top level.")

    for required_key in required_keys or ():
        if required_key not in parsed:
            raise ValueError(f"Uploaded file '{field_name}' is missing required key '{required_key}'.")

    return parsed


def _load_csv_file(upload, field_name):
    try:
        content = upload.read()
        decoded_content = content.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(f"Uploaded file '{field_name}' must be UTF-8 encoded.") from exc

    try:
        dataframe = pd.read_csv(io.StringIO(decoded_content))
    except Exception as exc:
        raise ValueError(f"Uploaded file '{field_name}' contains invalid CSV data.") from exc

    if dataframe.empty and len(dataframe.columns) == 0:
        raise ValueError(f"Uploaded file '{field_name}' must contain at least one CSV column.")
    if dataframe.columns.has_duplicates:
        raise ValueError(f"Uploaded file '{field_name}' must not contain duplicate CSV column names.")

    return dataframe


def _validate_attribute_config(attribute_config):
    configurations = attribute_config.get("configurations")
    if not isinstance(configurations, list) or not configurations:
        raise ValueError("Uploaded file 'attribute_config' must define a non-empty 'configurations' list.")


def _validate_algorithm_config(algorithm_config):
    synthetization_configuration = algorithm_config.get("synthetization_configuration")
    if not isinstance(synthetization_configuration, dict):
        raise ValueError("Uploaded file 'algorithm_config' must define 'synthetization_configuration'.")

    algorithm_section = synthetization_configuration.get("algorithm")
    if not isinstance(algorithm_section, dict):
        raise ValueError(
            "Uploaded file 'algorithm_config' must define 'synthetization_configuration.algorithm'."
        )


def initialize_input_data(synthesizer_name):
    """
    Extract and validate input data from the incoming request.

    Args:
        synthesizer_name (str): Name of the synthesizer being used.

    Returns:
        tuple: Session key, callback URL, status file path, attribute configuration, algorithm configuration, and data.
    """
    print('Initializing input data')
    if 'session_key' not in request.form:
        print('No session key provided')
        return 'No session key provided', 400
    if 'callback' not in request.form:
        print('No callback URL provided')
        return 'No callback URL provided', 400
    if 'attribute_config' not in request.files:
        print('No attribute_config file provided')
        return 'No attribute_config file provided', 400
    if 'algorithm_config' not in request.files:
        print('No algorithm_config file provided')
        return 'No algorithm_config file provided', 400

    if 'data' not in request.files:
        return 'No data file provided', 400

    session_key = request.form['session_key']
    callback_url = request.form['callback']

    # Initialize status file
    file_path_status = _status_file_path(session_key)
    initialize_status_file(file_path_status, session_key, synthesizer_name)

    # Get the files from the request
    attribute_config = request.files['attribute_config']
    algorithm_config = request.files['algorithm_config']
    data = request.files['data']
    original_data = request.files.get('original_data')

    # Read the content of the files
    try:
        attribute_config = _load_yaml_file(attribute_config, 'attribute_config', required_keys=('configurations',))
        _validate_attribute_config(attribute_config)

        algorithm_config = _load_yaml_file(
            algorithm_config,
            'algorithm_config',
            required_keys=('synthetization_configuration',),
        )
        _validate_algorithm_config(algorithm_config)

        data = _load_csv_file(data, 'data')
        if original_data is not None:
            original_data = _load_csv_file(original_data, 'original_data')
    except ValueError as exc:
        return str(exc), 400

    return session_key, callback_url, file_path_status, attribute_config, algorithm_config, data, original_data


def prepare_callback_data(samples, synthesizer_model):
    """
    Prepare synthetic data and model for callback.

    Args:
        samples (pd.DataFrame): Generated synthetic data.
        synthesizer_model (bytes): Serialized synthesizer model.

    Returns:
        dict: A dictionary of file-like objects for callback POST request.
    """
    csv_synthetic_data = samples.to_csv(index=False)

    synthetic_data = io.BytesIO(csv_synthetic_data.encode('utf-8'))
    synthesizer_model = io.BytesIO(synthesizer_model)

    files = {
        'synthetic_data': ('synthetic_data.csv', synthetic_data),
        'model': ('model.pkl', synthesizer_model),
    }

    return files


def post_callback_request(callback_url, *, files, data, timeout):
    """Send callback requests without inheriting proxy settings from the environment."""
    with requests.Session() as session:
        session.trust_env = False
        return session.post(
            callback_url,
            files=files,
            data=data,
            timeout=timeout,
        )


@lru_cache(maxsize=None)
def load_synthesizer_config(synthesizer_name):
    config_file = os.path.join(SYNTHESIZER_CONFIG_DIR, f"{synthesizer_name}.yaml")
    with open(config_file, "r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


@lru_cache(maxsize=1)
def load_study_config():
    config_file = os.path.join(HYPERPARAMETER_TUNING_DIR, "study.yaml")
    with open(config_file, "r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def get_study_parameter_default(parameter_name):
    study_config = load_study_config()
    parameters = study_config.get("configurations", {}).get("study", {}).get("parameters", [])
    for parameter in parameters:
        if parameter.get("name") == parameter_name and "default_value" in parameter:
            return parameter["default_value"]
    raise RuntimeError(
        f"Missing default_value for hyperparameter tuning parameter '{parameter_name}' in study.yaml."
    )


def get_data_modality(synthesizer_name):
    config = load_synthesizer_config(synthesizer_name)
    capabilities = config.get("processing_capabilities", {})
    data_modality = capabilities.get("data_modality")
    if data_modality not in PROCESSING_MODALITIES:
        raise ValueError(
            f"Invalid data_modality for synthesizer '{synthesizer_name}': {data_modality!r}."
        )
    return data_modality


def is_llm_synthesizer(synthesizer_name: str) -> bool:
    return get_data_modality(synthesizer_name) != PROCESSING_MODALITY_STRUCTURED_ONLY


def split_attribute_configurations(attribute_config):
    all_configurations = attribute_config.get("configurations", [])
    structured = []
    text = []
    for column_config in all_configurations:
        if str(column_config.get("type", "")).upper() == "TEXT":
            text.append(column_config)
        else:
            structured.append(column_config)
    return structured, text


def inject_llm_profile_parameter(config_content):
    configurations = config_content.setdefault("configurations", {})
    profile_names = get_llm_profile_names()
    if not profile_names:
        return config_content

    llm_profile_group = configurations.setdefault(
        "llm_profile",
        {
            "display_name": "LLM Profile",
            "description": "Select which configured LLM instance should be used.",
            "parameters": [],
        },
    )
    parameters = llm_profile_group.get("parameters", [])
    if not isinstance(parameters, list):
        return config_content

    llm_profile_parameter = None
    for parameter in parameters:
        if parameter.get("name") == "llm_profile":
            llm_profile_parameter = parameter
            break

    if llm_profile_parameter is None:
        llm_profile_parameter = {
            "name": "llm_profile",
            "type": "string",
            "label": "LLM Profile",
            "description": "Select the configured LLM profile (model endpoint and credentials).",
            "default_value": "",
            "values": profile_names,
            "mandatory": True,
        }
        parameters.append(llm_profile_parameter)
    else:
        llm_profile_parameter["values"] = profile_names
        llm_profile_parameter["default_value"] = ""
        llm_profile_parameter["mandatory"] = True

    return config_content


def inject_embedding_profile_parameter(config_content):
    profile_names = get_embedding_profile_names()
    if not profile_names:
        return config_content

    model_parameter_group = (config_content.setdefault("configurations", {})).get("model_parameter")
    if not isinstance(model_parameter_group, dict):
        return config_content

    parameters = model_parameter_group.get("parameters", [])
    if not isinstance(parameters, list):
        return config_content

    embedding_model_parameter = None
    for parameter in parameters:
        if parameter.get("name") == "embedding_model":
            embedding_model_parameter = parameter
            break

    if embedding_model_parameter is None:
        return config_content

    embedding_model_parameter["values"] = profile_names
    embedding_model_parameter["default_value"] = ""
    embedding_model_parameter["mandatory"] = False
    return config_content


@lru_cache(maxsize=None)
def load_text_synthesis_defaults(text_synthesizer_name):
    synthesizer_config = load_synthesizer_config(text_synthesizer_name)
    defaults = {
        "llm_profile": {},
        "model_parameter": {},
        "model_fitting": {},
        "sampling": {},
    }
    sections = synthesizer_config.get("configurations", {})
    for section_name in defaults:
        parameters = sections.get(section_name, {}).get("parameters", [])
        for parameter in parameters:
            parameter_name = parameter.get("name")
            if not parameter_name or "default_value" not in parameter:
                continue
            defaults[section_name][parameter_name] = parameter.get("default_value")

    return defaults


def build_text_synthesis_algorithm_config(algorithm_config, synthesizer_name, num_samples):
    config = deepcopy(algorithm_config) if algorithm_config else {}

    algorithm_section = config.setdefault("synthetization_configuration", {}).setdefault("algorithm", {})
    algorithm_section.setdefault("synthesizer", synthesizer_name)
    algorithm_section.setdefault("llm_profile", {})
    algorithm_section.setdefault("model_parameter", {})
    algorithm_section.setdefault("model_fitting", {})
    algorithm_section.setdefault("sampling", {})

    defaults = load_text_synthesis_defaults(synthesizer_name)

    llm_profile = algorithm_section["llm_profile"]
    for key, value in defaults.get("llm_profile", {}).items():
        llm_profile.setdefault(key, value)

    model_parameter = algorithm_section["model_parameter"]
    for key, value in defaults.get("model_parameter", {}).items():
        model_parameter.setdefault(key, value)

    model_fitting = algorithm_section["model_fitting"]
    for key, value in defaults.get("model_fitting", {}).items():
        model_fitting.setdefault(key, value)

    sampling = algorithm_section["sampling"]
    for key, value in defaults.get("sampling", {}).items():
        sampling.setdefault(key, value)
    if sampling.get("num_samples") is None:
        sampling["num_samples"] = num_samples

    return config


def _format_synthesis_exception_message(exc):
    message = str(exc).strip() or exc.__class__.__name__
    lowered_message = message.lower()

    if "llm configuration" in lowered_message or "llm profile" in lowered_message or "llm_profile" in lowered_message:
        return f"LLM configuration error: {message}"
    if "unsupported llm provider" in lowered_message:
        return f"LLM configuration error: {message}"
    if "llm client is not initialized" in lowered_message:
        return f"LLM initialization error: {message}"
    if "unable to reach the configured llm api" in lowered_message:
        return f"LLM connection error: {message}"
    if "llm response" in lowered_message or "llm did not return valid json" in lowered_message:
        return f"LLM response error: {message}"

    return f"Unexpected error occurred: {message}"


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

    if tuning_enabled:
        tuning_metadata = synthesizer_tuning_metadata.get(synthesizer_name, {})
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
            trial_synth = synthesizer_classes[synthesizer_name]['class']()
            trial_synth.set_progress_callback(_handle_progress_update)
            trial_synth.initialize_anonymization_configuration(trial_algorithm_config)
            trial_synth.initialize_attribute_configuration(stage_attribute_config)
            trial_synth.initialize_dataset(pre_processed_data)
            if pre_processed_reference_data is not None:
                if not hasattr(trial_synth, "initialize_reference_dataset"):
                    raise RuntimeError("Synthesizer does not support reference dataset initialization.")
                trial_synth.initialize_reference_dataset(pre_processed_reference_data)
            trial_synth.initialize_synthesizer()
            return trial_synth.fit()

        sampler = tuning_cfg.get("sampler", get_study_parameter_default("sampler"))
        pruner = tuning_cfg.get("pruner", get_study_parameter_default("pruner"))
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

        best_synth = synthesizer_classes[synthesizer_name]['class']()
        best_synth.set_progress_callback(_handle_progress_update)
        best_synth.initialize_anonymization_configuration(best_cfg)
        best_synth.initialize_attribute_configuration(stage_attribute_config)
        best_synth.initialize_dataset(pre_processed_data)
        if pre_processed_reference_data is not None:
            if not hasattr(best_synth, "initialize_reference_dataset"):
                raise RuntimeError("Synthesizer does not support reference dataset initialization.")
            best_synth.initialize_reference_dataset(pre_processed_reference_data)
        best_synth.initialize_synthesizer()
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
        synthesizer_class = synthesizer_classes[synthesizer_name]['class']()
        print(f"[{stage_label}] Synthesizer class initialized: {synthesizer_name}")
        synthesizer_class.set_progress_callback(_handle_progress_update)

        synthesizer_class.initialize_anonymization_configuration(stage_algorithm_config)
        print(f"[{stage_label}] Anonymization configuration initialized.")

        synthesizer_class.initialize_attribute_configuration(stage_attribute_config)
        print(f"[{stage_label}] Attribute configuration initialized.")

        synthesizer_class.initialize_dataset(pre_processed_data)
        if pre_processed_reference_data is not None:
            if not hasattr(synthesizer_class, "initialize_reference_dataset"):
                raise RuntimeError("Synthesizer does not support reference dataset initialization.")
            synthesizer_class.initialize_reference_dataset(pre_processed_reference_data)
        print(f"[{stage_label}] Dataset initialized.")

        synthesizer_class.initialize_synthesizer()
        print(f"[{stage_label}] Synthesizer initialized.")
        
        if session_key is not None and hasattr(synthesizer_class, 'synthesizer') and synthesizer_class.synthesizer is not None:
            if hasattr(synthesizer_class, '_llm_client') and synthesizer_class._llm_client is not None:
                try:
                    synthesizer_class._llm_client.set_session_key(session_key)
                    print(f"[{stage_label}] Prompt logging enabled for session: {session_key}")
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


def synthesize_data(synthesizer_name, file_path_status, attribute_config, algorithm_config, data,
                    original_data,
                    callback_url, session_key):
    """
    Orchestrates the entire data synthesis process and sends error messages to the callback API.

    Args:
        synthesizer_name (str): Name of the synthesizer.
        file_path_status (str): Path to the status file.
        attribute_config (dict): Attribute configuration.
        algorithm_config (dict): Algorithm configuration.
        data (pd.DataFrame): Input dataset.
        callback_url (str): Callback URL to send results or errors.
        session_key (str): Unique session identifier.

    Returns:
        dict: Result of the synthesis process.
    """
    try:
        configure_realtime_logging()
        print('Synthesizer selected:', synthesizer_name)

        if synthesizer_name not in synthesizer_classes:
            error_message = f"Error: Synthesizer '{synthesizer_name}' not found"
            send_callback_error(callback_url, session_key, error_message, 400)
            return {'message': error_message, 'session_key': session_key, 'status_code': 400}

        structured_configs, text_configs = split_attribute_configurations(attribute_config)
        data_modality = get_data_modality(synthesizer_name)
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
            if data_modality != PROCESSING_MODALITY_STRUCTURED_ONLY:
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
                text_name not in synthesizer_classes
                or get_data_modality(text_name) != PROCESSING_MODALITY_MIXED
            ):
                raise ValueError("Select a mixed-data free-text synthesizer in text_synthesis_configuration.")

            structured_algorithm_config = algorithm_config
            if text_configs and text_name == "llm_mixed_data_embedding_nearest_neighbor_synthesis":
                requested_samples = text_config["synthetization_configuration"]["algorithm"].get("sampling", {}).get("num_samples")
                if requested_samples is not None:
                    if (isinstance(requested_samples, bool)
                            or not str(requested_samples).strip().isdigit()
                            or int(requested_samples) <= 0):
                        raise ValueError("Nearest-neighbor num_samples must be a positive integer.")
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
                text_algorithm_config = build_text_synthesis_algorithm_config(text_config, text_name, len(text_input))
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
        elif text_configs and data_modality == PROCESSING_MODALITY_TEXT_ONLY:
            print("Pipeline mode: text-only synthesis.")
            announce_component_synthesis(file_path_status, "llm_synthesis", synthesizer_name)
            text_input = order_dataframe_by_config(data.copy(), attribute_config.get("configurations", []))
            text_algorithm_config = build_text_synthesis_algorithm_config(algorithm_config, synthesizer_name, len(text_input))
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

        try:
            files = prepare_callback_data(final_samples, final_model)
            print(f"Sending success callback to {callback_url} with session_key={session_key}")
            response = post_callback_request(
                callback_url,
                files=files,
                data={'session_key': session_key},
                timeout=CALLBACK_TIMEOUT_SECONDS
            )
            response.raise_for_status()
            print(f"Success callback completed with status={response.status_code}")
            update_status(file_path_status, 'callback', completed=True)
            return {
                'message': 'Synthetization Finished, successfully sent callback notification',
                'session_key': session_key,
                'status_code': 200
            }
        except requests.exceptions.RequestException as e:
            update_status(file_path_status, 'callback', completed=False)
            error_message = f"Synthetization Finished, failed to send callback notification. {str(e)}"
            send_callback_error(callback_url, session_key, error_message, 500)
            return {'message': error_message, 'session_key': session_key, 'status_code': 500}

    except Exception as e:
        error_message = _format_synthesis_exception_message(e)
        send_callback_error(callback_url, session_key, error_message, 500)
        return {'message': error_message, 'session_key': session_key, 'status_code': 500}


@app.route('/start_synthetization_process/<string:synthesizer_name>', methods=['POST'])
def start_synthetization_process(synthesizer_name):
    """
    Starts the data synthesis process in a separate process.

    Args:
        synthesizer_name (str): Name of the synthesizer.

    Returns:
        JSON: Response indicating task start status.
    """
    _prune_finished_tasks()
    task_id = request.form.get('session_key')
    if not task_id:
        return jsonify({'message': 'No session key provided'}), 400

    try:
        configure_realtime_logging()
        # Initialize input data
        input_data = initialize_input_data(synthesizer_name)
        if (
            isinstance(input_data, tuple)
            and len(input_data) == 2
            and isinstance(input_data[0], str)
            and isinstance(input_data[1], int)
        ):
            return jsonify({'message': input_data[0], 'session_key': task_id}), input_data[1]

        session_key, callback_url, file_path_status, attribute_config, algorithm_config, data, original_data = input_data
        print('Data successfully loaded')

        # Create and start the process
        task_process = PROCESS_CONTEXT.Process(
            target=synthesize_data,
            args=(synthesizer_name, file_path_status, attribute_config, algorithm_config,
                  data.copy(), original_data.copy() if original_data is not None else None, callback_url, session_key)
        )

        tasks[task_id] = task_process
        task_process.start()
        pid = task_process.pid

        return jsonify({
            'message': 'Synthetization Started',
            'session_key': task_id,
            'pid': pid
        }), 202

    except Exception as e:
        return jsonify({
            'message': 'Exception occurred during Synthetization',
            'error': str(e),
            'session_key': task_id
        }), 500


@app.route('/<string:module_name>/synthesizer_config/<string:filename>', methods=['GET'])
def get_synthesizer_config(module_name, filename):
    """
    Retrieves the specified synthesizer configuration file.

    Args:
        filename (str): The name of the configuration file.

    Returns:
        The contents of the configuration file if found, or an error message if not found.
        :param filename:
        :param module_name:
    """
    try:
        if not filename.lower().endswith('.yaml'):
            error_message = 'Invalid file type. Only YAML files are allowed.'
            return jsonify({'error': error_message}), 400

        module_directory = os.path.join(os.path.dirname(__file__), module_name)
        config_directory = os.path.join(module_directory, 'synthesizer_config')
        config_path = os.path.join(config_directory, filename)
        print(config_path)

        if not os.path.abspath(config_path).startswith(os.path.abspath(config_directory)):
            error_message = 'Invalid file path. Access to files outside the allowed directory is not allowed.'
            return jsonify({'error': error_message}), 403

        is_dynamic_llm_definition = False
        if module_name == "synthetic_tabular_data_generator":
            with open(config_path, "r", encoding="utf-8") as file:
                config_content = yaml.safe_load(file) or {}
            configurations = config_content.get("configurations", {}) or {}
            is_dynamic_llm_definition = "llm_profile" in configurations
            has_dynamic_embedding_definition = "model_parameter" in configurations
        else:
            has_dynamic_embedding_definition = False
        if not is_dynamic_llm_definition and not has_dynamic_embedding_definition:
            return send_from_directory(config_directory, filename)
        if is_dynamic_llm_definition:
            config_content = inject_llm_profile_parameter(config_content)
        if has_dynamic_embedding_definition:
            config_content = inject_embedding_profile_parameter(config_content)
        return Response(yaml.safe_dump(config_content, sort_keys=False), mimetype='text/yaml')
    except FileNotFoundError:
        error_message = 'The requested file was not found. Please check the filename and try again.'
        return jsonify({'error': error_message}), 404
    except Exception as e:
        error_message = str(e)
        return jsonify({'error': error_message}), 500


@app.route('/hyperparameter_tuning/study.yaml', methods=['GET'])
def get_study_yaml():
    """
    Serves the Optuna study definition (study.yaml) to the frontend
    so it can render the hyperparameter-tuning configuration form.
    """
    try:
        return send_from_directory(HYPERPARAMETER_TUNING_DIR, 'study.yaml')
    except FileNotFoundError:
        return jsonify({'error': 'study.yaml not found'}), 404
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/get_status/<session_key>', methods=['GET'])
def get_status(session_key):
    """
    Retrieve the current status of a running or completed synthetization process.

    Args:
        session_key (str): Unique session identifier for the synthetization process.

    Returns:
        The contents of the configuration file if found, or an error message if not found.
        :param session_key:
    """
    _prune_finished_tasks()
    try:
        file_path = _status_file_path(session_key)
        with open(file_path, 'r') as f:
            status = yaml.safe_load(f)
            return jsonify(status)
    except FileNotFoundError:
        return jsonify({'message': 'Status file not found for session key'}), 404
    except Exception as e:
        return jsonify({'message': 'Error occurred', 'error': str(e)}), 500


@app.route('/get_algorithms', methods=['GET'])
def get_algorithms():
    """
    Retrieve a list of available synthesizer algorithms with metadata.

    Returns:
        YAML: A list of available synthesizer algorithms with details such as:
            - name
            - display_name
            - version
            - type
            - description
            - URL
    """
    _prune_finished_tasks()
    try:
        synthesizer_list = [
            {
                'name': key,
                'display_name': value['display_name'],
                'version': value['version'],
                'type': value['type'],
                'class': str(value['class']),
                'description': value['description'],
                'URL': value['URL'],
                'processing_capabilities': load_synthesizer_config(key).get('processing_capabilities', {})
            }
            for key, value in synthesizer_classes.items()
        ]

        yaml_data = yaml.dump({'algorithms': synthesizer_list}, default_flow_style=False)

        return Response(yaml_data, mimetype='text/yaml')
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/cancel_synthetization_process', methods=['POST'])
def cancel_synthetization():
    """
    Cancel a running synthetization process using its session key and PID.

    Args:
        session_key (str): Unique session key for the synthetization process.
        pid (str): Process ID of the running task.

    Returns:
        JSON: A success message if the process is successfully canceled.
        JSON: An error message if the process cannot be canceled.
    """
    _prune_finished_tasks()
    task_id = request.form.get('session_key')
    task_pid = request.form.get('pid')

    if not task_id:
        return jsonify({'message': 'No session key provided'}), 400
    if not task_pid:
        return jsonify({'message': 'No pid provided', 'session_key': task_id}), 400

    try:
        expected_pid = int(task_pid)
    except (TypeError, ValueError):
        return jsonify({'message': 'Invalid pid provided', 'session_key': task_id}), 400

    task_process = tasks.get(task_id)
    if task_process is None:
        return jsonify({'message': 'No running task found for session key', 'session_key': task_id}), 404

    actual_pid = getattr(task_process, 'pid', None)
    if actual_pid != expected_pid:
        return jsonify({
            'message': 'PID does not match the registered task',
            'session_key': task_id,
            'pid': actual_pid,
        }), 409

    if not _task_is_alive(task_process):
        _cleanup_task_state(task_id)
        return jsonify({
            'message': 'Task is no longer running',
            'session_key': task_id,
            'pid': actual_pid,
        }), 409

    try:
        _terminate_task_process(task_process)
        _mark_task_cancelled(task_id)
        _cleanup_task_state(task_id)
        return jsonify({'message': 'Task canceled', 'session_key': task_id, 'pid': actual_pid}), 200
    except Exception as e:
        return jsonify({'message': 'Task cannot be cancelled', 'error': f'Failed to cancel task: {str(e)}',
                        'session_key': task_id}), 500


@app.route('/test_callback', methods=['POST'])
def test_callback():
    """
    Test endpoint for the callback functionality.

    Returns:
        JSON: A success message containing the session key and details about received files.
    """
    try:
        # Parse JSON data
        if request.is_json:
            data = request.get_json()
            session_key = data.get('session_key', None)
            message = data.get('message', None)
            status_code = data.get('status_code', None)

        else:
            raise ValueError("Request must be in JSON format")

        print('Callback function called with session key:', session_key)
        print('Message:', message)
        print('Status Code:', status_code)

        # Handle file uploads (if any)
        files = request.files
        for file_key in files:
            file = files[file_key]
            print(f"File received: {file_key}, filename: {file.filename}")

        return jsonify({
            'message': 'Callback function called successfully',
            'session_key': session_key,
            'received_files': list(files.keys())  # List all received file keys
        }), 200

    except Exception as e:
        print(f"Error in test_callback: {str(e)}")
        return jsonify({'error': str(e)}), 400


def send_callback_error(callback_url, session_key, message, status_code):
    """
    Sends error messages to the provided callback URL using multipart/form-data.

    Args:
        callback_url (str): The client's callback URL.
        session_key (str): Unique session identifier.
        message (str): The error message to send.
        status_code (int): The HTTP status code to include in the callback.
        part_name (str): Name of the part for the error message (as specified in application.properties).
    """
    # Prepare the error message as a file-like object
    error_message = io.BytesIO(message.encode('utf-8'))  # Convert error message to bytes

    # Prepare the data for the multipart/form-data request
    files = {
        'error_message': ('error_message.txt', error_message, 'text/plain'),  # Error message as a form part
    }
    data = {
        'session_key': session_key,  # Include session key as form data
        'status_code': status_code,  # Include status code as form data
    }

    try:
        print(f"Sending error callback to {callback_url} with data: {data} and files: {files}")
        response = post_callback_request(
            callback_url,
            files=files,
            data=data,
            timeout=ERROR_CALLBACK_TIMEOUT_SECONDS
        )
        print(f"Response status code: {response.status_code}")
        print(f"Response text: {response.text}")
        response.raise_for_status()
    except requests.exceptions.RequestException as e:
        print(f"Failed to send error to callback URL: {str(e)}")


@app.route('/report', methods=['POST'])
def report():
    # TODO create text base on the given config
    configuration = request.form.get('configuration')
    description = ('<p>'
                   'The used generative model was <strong>Conditional Tabular GAN (CTGAN)</strong> with a <strong>batch size of 128</strong>.'
                   'The model was trained with <strong>10 epochs</strong>.'
                   '</p>'
                   '<p>'
                   '<strong>Conditional Tabular GAN (CTGAN)</strong> are a specialized type of generative model designed to create realistic synthetic tabular data, mimicking the statistical properties of original datasets. Leveraging a generator-discriminator framework, CTGANs learn the relationships within your data and generate new rows conditioned on specified parameters, enabling the creation of synthetic datasets that preserve privacy while retaining key analytical insights. This approach is particularly valuable for scenarios requiring data augmentation or secure model development.'
                   '</p>'
                   '<p>'
                   'For further details, look into the dedicated synthetization section of this report.'
                   '</p>')
    return jsonify({'configDescription': description})


@app.route('/actuator/health', methods=['GET'])
def health_check():
    """
    Provides a health status for the application.

    Returns:
        A JSON object indicating the application's health status.
    """
    status = {"status": "UP"}
    return jsonify(status), 200


if __name__ == '__main__':
    app.run(debug=True)
