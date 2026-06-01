import io
import os
import sys
import time
from copy import deepcopy
from functools import lru_cache
from multiprocessing import get_context
from threading import Event

import pandas as pd
import requests
import yaml
from flask import Flask, request, jsonify, send_from_directory, Response
from flask_cors import CORS

from api_utility.status.status_updater import initialize_status_file
from api_utility.status.status_updater import intercept_standard_streams
from api_utility.status.status_updater import update_component_status
from api_utility.status.status_updater import update_status
from synthesizer_classes import synthesizer_classes
from data_processing.post_process import post_process_dataframe
from data_processing.pre_process import pre_process_dataframe
from data_processing.utils import (
    order_dataframe_by_config,
    set_text_columns_to_pending,
)
from synthetic_tabular_data_generator.llm import get_llm_profile_names


app = Flask(__name__)
tasks = {}
task_locks = {}
CORS(app)

PROCESS_CONTEXT = get_context("spawn")

CALLBACK_TIMEOUT_SECONDS = 30.0
ERROR_CALLBACK_TIMEOUT_SECONDS = 5.0
SYNTHESIZER_CONFIG_DIR = os.path.join(
    os.path.dirname(__file__),
    "synthetic_tabular_data_generator",
    "synthesizer_config",
)


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
    file_path_status = os.path.join(os.path.dirname(__file__), 'outputs', 'status', f"{session_key}.yaml")
    initialize_status_file(file_path_status, session_key, synthesizer_name)

    # Get the files from the request
    attribute_config = request.files['attribute_config']
    algorithm_config = request.files['algorithm_config']
    data = request.files['data']
    original_data = request.files.get('original_data')

    # Read the content of the files
    attribute_config = yaml.safe_load(attribute_config.read())
    algorithm_config = yaml.safe_load(algorithm_config.read())
    data = pd.read_csv(io.StringIO(data.read().decode('utf-8')))
    if original_data is not None:
        original_data = pd.read_csv(io.StringIO(original_data.read().decode('utf-8')))

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


def _to_bool(value, default):
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "yes", "y", "on"}:
            return True
        if normalized in {"0", "false", "no", "n", "off"}:
            return False
    return default


@lru_cache(maxsize=None)
def load_synthesizer_config(synthesizer_name):
    config_file = os.path.join(SYNTHESIZER_CONFIG_DIR, f"{synthesizer_name}.yaml")
    with open(config_file, "r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def get_processing_capabilities(synthesizer_name):
    config = load_synthesizer_config(synthesizer_name)
    capabilities = config.get("processing_capabilities", {})
    supports_structured = _to_bool(capabilities.get("supports_structured_data"), True)
    supports_free_text = _to_bool(capabilities.get("supports_free_text_data"), False)
    return supports_structured, supports_free_text


def is_llm_synthesizer(synthesizer_name: str) -> bool:
    _, supports_free_text = get_processing_capabilities(synthesizer_name)
    return supports_free_text


DEFAULT_TEXT_SYNTHESIZER_NAME = "llm_nearest_neighbor_few_shot_text_synthesis"


@lru_cache(maxsize=1)
def get_text_synthesizer_name():
    if DEFAULT_TEXT_SYNTHESIZER_NAME in synthesizer_classes:
        supports_structured, supports_free_text = get_processing_capabilities(DEFAULT_TEXT_SYNTHESIZER_NAME)
        if not supports_structured and supports_free_text:
            return DEFAULT_TEXT_SYNTHESIZER_NAME

    candidates = []
    for name in synthesizer_classes:
        supports_structured, supports_free_text = get_processing_capabilities(name)
        if not supports_structured and supports_free_text:
            candidates.append(name)

    if not candidates:
        raise RuntimeError(
            "No text-only synthesizer found. Expected one synthesizer_config with "
            "supports_structured_data=false and supports_free_text_data=true."
        )
    if len(candidates) > 1:
        raise RuntimeError(
            f"Ambiguous text-only synthesizers found: {candidates}. "
            "Please keep exactly one text-only synthesizer in the registry."
        )
    return candidates[0]


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


def build_attribute_config(column_configurations):
    return {"configurations": column_configurations}


def create_text_synthesis_input(dataframe, full_attribute_config):
    text_input = set_text_columns_to_pending(dataframe, full_attribute_config.get("configurations", []))
    return order_dataframe_by_config(text_input, full_attribute_config.get("configurations", []))


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
            "default_value": profile_names[0],
            "values": profile_names,
            "mandatory": False,
        }
        parameters.append(llm_profile_parameter)
    else:
        llm_profile_parameter["values"] = profile_names
        if not llm_profile_parameter.get("default_value"):
            llm_profile_parameter["default_value"] = profile_names[0]

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


def build_text_synthesis_algorithm_config(algorithm_config, synthesizer_name, text_synthesizer_name, num_samples):
    if synthesizer_name == text_synthesizer_name:
        config = deepcopy(algorithm_config)
    else:
        config = deepcopy(algorithm_config.get("text_synthesis_configuration", {}))

    if not config:
        config = {}

    algorithm_section = config.setdefault("synthetization_configuration", {}).setdefault("algorithm", {})
    algorithm_section.setdefault("synthesizer", text_synthesizer_name)
    algorithm_section.setdefault("llm_profile", {})
    algorithm_section.setdefault("model_parameter", {})
    algorithm_section.setdefault("model_fitting", {})
    algorithm_section.setdefault("sampling", {})

    defaults = load_text_synthesis_defaults(text_synthesizer_name)

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
):
    stage_init_time = time.time()

    synthesizer_class = synthesizer_classes[synthesizer_name]['class']()
    print(f"[{stage_label}] Synthesizer class initialized: {synthesizer_name}")

    def _handle_progress_update(step, remaining_time):
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

    synthesizer_class.set_progress_callback(_handle_progress_update)

    synthesizer_class.initialize_anonymization_configuration(stage_algorithm_config)
    print(f"[{stage_label}] Anonymization configuration initialized.")

    synthesizer_class.initialize_attribute_configuration(stage_attribute_config)
    print(f"[{stage_label}] Attribute configuration initialized.")

    pre_processed_data, all_missing_values_column = pre_process_dataframe(
        input_data.copy(),
        stage_attribute_config['configurations'],
        replace_text_with_pending=replace_text_with_pending,
    )
    print(f"[{stage_label}] Input data preprocessed.")

    pre_processed_reference_data = None
    if reference_data is not None:
        pre_processed_reference_data, _ = pre_process_dataframe(
            reference_data.copy(),
            stage_attribute_config['configurations'],
            replace_text_with_pending=False,
        )
        print(f"[{stage_label}] Reference data preprocessed.")

    synthesizer_class.initialize_dataset(pre_processed_data)
    if pre_processed_reference_data is not None:
        if not hasattr(synthesizer_class, "initialize_reference_dataset"):
            raise RuntimeError("Synthesizer does not support reference dataset initialization.")
        synthesizer_class.initialize_reference_dataset(pre_processed_reference_data)
    print(f"[{stage_label}] Dataset initialized.")

    synthesizer_class.initialize_synthesizer()
    print(f"[{stage_label}] Synthesizer initialized.")
    
    # Set session key for prompt logging if this is an LLM synthesizer
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

        text_synthesizer_name = get_text_synthesizer_name()
        if text_synthesizer_name not in synthesizer_classes:
            error_message = f"Error: Required text synthesizer '{text_synthesizer_name}' not found"
            send_callback_error(callback_url, session_key, error_message, 500)
            return {'message': error_message, 'session_key': session_key, 'status_code': 500}

        structured_configs, text_configs = split_attribute_configurations(attribute_config)
        supports_structured, supports_free_text = get_processing_capabilities(synthesizer_name)

        print(
            "Processing capabilities resolved: "
            f"supports_structured={supports_structured}, supports_free_text={supports_free_text}, "
            f"structured_columns={len(structured_configs)}, text_columns={len(text_configs)}"
        )

        total_init_duration = 0.0
        total_fit_duration = 0.0
        total_sample_duration = 0.0

        final_samples = None
        final_model = None

        # 1) Standalone text synthesis (no structured synthesis).
        if synthesizer_name == text_synthesizer_name:
            print("Pipeline mode: text-only synthesis.")
            text_input = create_text_synthesis_input(data, attribute_config)
            text_algorithm_config = build_text_synthesis_algorithm_config(
                algorithm_config,
                synthesizer_name,
                text_synthesizer_name,
                len(text_input),
            )

            final_samples, final_model, init_duration, fit_duration, sample_duration = run_synthesizer_stage(
                stage_label="TEXT_SYNTHESIS",
                synthesizer_name=text_synthesizer_name,
                stage_attribute_config=attribute_config,
                stage_algorithm_config=text_algorithm_config,
                input_data=text_input,
                reference_data=data,
                file_path_status=file_path_status,
                replace_text_with_pending=False,
                fill_text_with_pending=False,
                session_key=session_key,
                status_component_name="llm_synthesis",
            )
            total_init_duration += init_duration
            total_fit_duration += fit_duration
            total_sample_duration += sample_duration
            update_pipeline_totals(
                file_path_status,
                total_init_duration,
                total_fit_duration,
                total_sample_duration,
                completed=True,
            )

        # 2) Selected synthesizer does not support free text:
        #    first synthesize structured columns, then synthesize text via the default few-shot text synthesizer.
        elif text_configs and not supports_free_text:
            print("Pipeline mode: two-stage (structured -> text synthesis).")

            structured_base = None
            structured_model = None

            if structured_configs and supports_structured:
                structured_attribute_config = build_attribute_config(structured_configs)
                structured_base, structured_model, init_duration, fit_duration, sample_duration = run_synthesizer_stage(
                    stage_label="STRUCTURED_SYNTHESIS",
                    synthesizer_name=synthesizer_name,
                    stage_attribute_config=structured_attribute_config,
                    stage_algorithm_config=algorithm_config,
                    input_data=data[ [cfg["name"] for cfg in structured_configs] ].copy(),
                    reference_data=None,
                    file_path_status=file_path_status,
                    replace_text_with_pending=True,
                    fill_text_with_pending=True,
                    session_key=session_key,
                    status_component_name="structured_synthesis",
                )
                total_init_duration += init_duration
                total_fit_duration += fit_duration
                total_sample_duration += sample_duration
                update_pipeline_totals(
                    file_path_status,
                    total_init_duration,
                    total_fit_duration,
                    total_sample_duration,
                    completed=False,
                )

            if structured_base is None:
                print("No structured synthesis executed. Using input dataset as base for text synthesis.")
                structured_base = data.copy()
                structured_model = None

            text_input = create_text_synthesis_input(structured_base, attribute_config)
            text_algorithm_config = build_text_synthesis_algorithm_config(
                algorithm_config,
                synthesizer_name,
                text_synthesizer_name,
                len(text_input),
            )

            final_samples, final_model, init_duration, fit_duration, sample_duration = run_synthesizer_stage(
                stage_label="TEXT_SYNTHESIS",
                synthesizer_name=text_synthesizer_name,
                stage_attribute_config=attribute_config,
                stage_algorithm_config=text_algorithm_config,
                input_data=text_input,
                reference_data=data,
                file_path_status=file_path_status,
                replace_text_with_pending=False,
                fill_text_with_pending=False,
                session_key=session_key,
                status_component_name="llm_synthesis",
            )
            total_init_duration += init_duration
            total_fit_duration += fit_duration
            total_sample_duration += sample_duration
            update_pipeline_totals(
                file_path_status,
                total_init_duration,
                total_fit_duration,
                total_sample_duration,
                completed=True,
            )

            if final_model is None:
                final_model = structured_model

        # 3) Selected synthesizer can handle target data directly (single-stage).
        else:
            print("Pipeline mode: single-stage synthesis.")
            final_samples, final_model, init_duration, fit_duration, sample_duration = run_synthesizer_stage(
                stage_label="SINGLE_STAGE",
                synthesizer_name=synthesizer_name,
                stage_attribute_config=attribute_config,
                stage_algorithm_config=algorithm_config,
                input_data=data,
                reference_data=None,
                file_path_status=file_path_status,
                replace_text_with_pending=not is_llm_synthesizer(synthesizer_name),
                fill_text_with_pending=not is_llm_synthesizer(synthesizer_name),
                session_key=session_key,
                status_component_name="llm_synthesis" if is_llm_synthesizer(synthesizer_name) else "structured_synthesis",
            )
            total_init_duration += init_duration
            total_fit_duration += fit_duration
            total_sample_duration += sample_duration
            update_pipeline_totals(
                file_path_status,
                total_init_duration,
                total_fit_duration,
                total_sample_duration,
                completed=True,
            )

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
            response = requests.post(
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
    task_id = request.form.get('session_key')
    if not task_id:
        return jsonify({'message': 'No session key provided'}), 400

    stop_event = Event()
    task_locks[task_id] = stop_event

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
            if task_id in task_locks:
                del task_locks[task_id]
            return jsonify({'message': input_data[0], 'session_key': task_id}), input_data[1]

        session_key, callback_url, file_path_status, attribute_config, algorithm_config, data, original_data = input_data
        print('Data successfully loaded')

        # Create and start the process
        task_process = PROCESS_CONTEXT.Process(
            target=synthesize_data,
            args=(synthesizer_name, file_path_status, attribute_config, algorithm_config,
                  data.copy(), original_data.copy() if original_data is not None else None, callback_url, session_key)  # Note the data.copy()
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
        if task_id in task_locks:
            del task_locks[task_id]
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

        is_dynamic_llm_definition = (
            module_name == "synthetic_tabular_data_generator"
            and filename.lower() in {
                "llm_tabular.yaml",
                "llm_nearest_neighbor_few_shot_text_synthesis.yaml",
                "llm_nearest_neighbor_knowledge_grounded_text_synthesis.yaml",
            }
        )
        if not is_dynamic_llm_definition:
            return send_from_directory(config_directory, filename)

        with open(config_path, "r", encoding="utf-8") as file:
            config_content = yaml.safe_load(file) or {}
        config_content = inject_llm_profile_parameter(config_content)
        return Response(yaml.safe_dump(config_content, sort_keys=False), mimetype='text/yaml')
    except FileNotFoundError:
        error_message = 'The requested file was not found. Please check the filename and try again.'
        return jsonify({'error': error_message}), 404
    except Exception as e:
        error_message = str(e)
        return jsonify({'error': error_message}), 500


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
    try:
        file_path = os.path.join(os.path.dirname(__file__), 'outputs', 'status', f"{session_key}.yaml")
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
    task_id = request.form.get('session_key')
    task_pid = request.form.get('pid')

    try:
        os.kill(int(task_pid), 9)

        return jsonify({'message': 'Task canceled', 'session_id': task_id, "pid": task_pid}), 200

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
        response = requests.post(
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
