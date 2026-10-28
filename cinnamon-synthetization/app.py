import io
import hashlib
import json
import shutil
import tempfile
from pathlib import Path
from threading import RLock
import os
import sys
from functools import wraps
from multiprocessing import get_context

import pandas as pd
import requests
import yaml
from flask import Flask, request, jsonify, send_from_directory, Response
from flask_cors import CORS

from api_utility.status.status_updater import initialize_status_file, set_job_status
import synthesizer_classes as synthesizer_registry
import synthesis_configuration as configuration
import synthesis_pipeline as pipeline


app = Flask(__name__)
tasks = {}
TASKS_LOCK = RLock()
MAX_SYNTHESIS_JOBS = int(os.getenv("CINNAMON_MAX_SYNTHESIS_JOBS", "1"))
if MAX_SYNTHESIS_JOBS < 1:
    raise ValueError("CINNAMON_MAX_SYNTHESIS_JOBS must be at least 1.")
RESULTS_DIR = Path(__file__).parent / "outputs" / "pending_callbacks"
CORS(app)


PROCESS_CONTEXT = get_context("spawn")

CALLBACK_TIMEOUT_SECONDS = 30.0
ERROR_CALLBACK_TIMEOUT_SECONDS = 5.0
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


def _locked_tasks(function):
    @wraps(function)
    def locked(*args, **kwargs):
        with TASKS_LOCK:
            return function(*args, **kwargs)
    return locked


def _cleanup_task_state(task_id):
    task_process = tasks.pop(task_id, None)
    if task_process is not None:
        join = getattr(task_process, "join", None)
        if callable(join):
            join(timeout=0)
        close = getattr(task_process, "close", None)
        if callable(close):
            close()


def _finish_job(file_path, state, error=None):
    try:
        set_job_status(file_path, state, error)
        return True
    except OSError as exc:
        print(f"Failed to write job state {state}: {exc}")
        return False


@_locked_tasks
def _prune_finished_tasks():
    for task_id, task_process in list(tasks.items()):
        if _task_is_alive(task_process):
            continue
        file_path = _status_file_path(task_id)
        if os.path.exists(file_path):
            with open(file_path, encoding="utf-8") as handle:
                status = yaml.safe_load(handle)
            if status.get("job_state") in {"running", "delivering"}:
                state = "delivery_failed" if _result_directory(task_id).exists() else "failed"
                _finish_job(file_path, state, f"Worker exited without completing the job (exitcode={getattr(task_process, 'exitcode', None)}).")
        _cleanup_task_state(task_id)


def _mark_task_cancelled(task_id):
    file_path = _status_file_path(task_id)
    if os.path.exists(file_path):
        with open(file_path, encoding="utf-8") as handle:
            status = yaml.safe_load(handle)
        if status.get("job_state") in {"succeeded", "failed", "delivery_failed"}:
            return False
    return _finish_job(file_path, "cancelled")


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

    # Get the files from the request
    attribute_config = request.files['attribute_config']
    algorithm_config = request.files['algorithm_config']
    data = request.files['data']
    original_data = request.files.get('original_data')

    # Read the content of the files
    try:
        attribute_config = _load_yaml_file(attribute_config, 'attribute_config', required_keys=('configurations',))
        configuration.validate_attribute_config(attribute_config)

        algorithm_config = _load_yaml_file(
            algorithm_config,
            'algorithm_config',
            required_keys=('synthetization_configuration',),
        )
        configuration.validate_algorithm_config(algorithm_config)

        data = _load_csv_file(data, 'data')
        if original_data is not None:
            original_data = _load_csv_file(original_data, 'original_data')
    except ValueError as exc:
        return str(exc), 400

    initialize_status_file(file_path_status, session_key, synthesizer_name)
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


def _result_directory(session_key):
    return RESULTS_DIR / hashlib.sha256(session_key.encode("utf-8")).hexdigest()


def _save_callback_result(session_key, callback_url, samples, model):
    RESULTS_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = Path(tempfile.mkdtemp(dir=RESULTS_DIR))
    try:
        files = prepare_callback_data(samples, model)
        for filename, content in files.values():
            (temporary / filename).write_bytes(content.getvalue())
        (temporary / "callback.json").write_text(json.dumps({"callback_url": callback_url}), encoding="utf-8")
        os.rename(temporary, _result_directory(session_key))
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)


def _deliver_saved_result(session_key, file_path_status):
    directory = _result_directory(session_key)
    _finish_job(file_path_status, "delivering")
    try:
        metadata = json.loads((directory / "callback.json").read_text(encoding="utf-8"))
        files = {
            "synthetic_data": ("synthetic_data.csv", io.BytesIO((directory / "synthetic_data.csv").read_bytes())),
            "model": ("model.pkl", io.BytesIO((directory / "model.pkl").read_bytes())),
        }
        response = post_callback_request(
            metadata["callback_url"], files=files, data={"session_key": session_key},
            timeout=CALLBACK_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
    except Exception as exc:
        message = f"Synthesis finished, but result delivery failed: {exc}"
        _finish_job(file_path_status, "delivery_failed", message)
        # The receiver is not idempotent: ambiguous HTTP failures require an explicit retry.
        return {"message": message, "session_key": session_key, "status_code": 502}

    if not _finish_job(file_path_status, "succeeded"):
        return {"message": "Result delivered but status could not be saved; check the receiver before retrying.",
                "session_key": session_key, "status_code": 500}
    try:
        shutil.rmtree(directory)
    except OSError as exc:
        print(f"Delivered result could not be removed: {exc}")
    return {"message": "Synthetization Finished, successfully sent callback notification",
            "session_key": session_key, "status_code": 200}


@app.route('/retry_synthetization_callback', methods=['POST'])
@_locked_tasks
def retry_synthetization_callback():
    _prune_finished_tasks()
    session_key = request.form.get("session_key")
    if not session_key:
        return jsonify({"message": "No session key provided"}), 400
    if session_key in tasks:
        return jsonify({"message": "Session already has a running task"}), 409
    if not _result_directory(session_key).exists():
        return jsonify({"message": "No saved result found"}), 404
    with open(_status_file_path(session_key), encoding="utf-8") as handle:
        status = yaml.safe_load(handle)
    if status.get("job_state") != "delivery_failed":
        return jsonify({"message": "Only a failed result delivery can be retried"}), 409
    if len(tasks) >= MAX_SYNTHESIS_JOBS:
        return jsonify({"message": "Synthesis job limit reached"}), 429
    process = PROCESS_CONTEXT.Process(target=_deliver_saved_result, args=(session_key, _status_file_path(session_key)))
    try:
        process.start()
    except Exception as exc:
        return jsonify({"message": "Callback retry could not be started", "error": str(exc)}), 500
    tasks[session_key] = process
    return jsonify({"message": "Callback retry started", "session_key": session_key, "pid": process.pid}), 202


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

        if synthesizer_name not in synthesizer_registry.synthesizer_classes:
            error_message = f"Error: Synthesizer '{synthesizer_name}' not found"
            _finish_job(file_path_status, "failed", error_message)
            send_callback_error(callback_url, session_key, error_message, 400)
            return {'message': error_message, 'session_key': session_key, 'status_code': 400}

        final_samples, final_model = pipeline.run_pipeline(
            synthesizer_name, file_path_status, attribute_config, algorithm_config,
            data, original_data, session_key,
        )

        _save_callback_result(session_key, callback_url, final_samples, final_model)
        return _deliver_saved_result(session_key, file_path_status)

    except Exception as e:
        error_message = _format_synthesis_exception_message(e)
        _finish_job(file_path_status, "failed", error_message)
        send_callback_error(callback_url, session_key, error_message, 500)
        return {'message': error_message, 'session_key': session_key, 'status_code': 500}


@app.route('/start_synthetization_process/<string:synthesizer_name>', methods=['POST'])
@_locked_tasks
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
    if task_id in tasks:
        return jsonify({'message': 'Session already has a running task', 'session_key': task_id}), 409
    if _result_directory(task_id).exists():
        return jsonify({'message': 'Session has an undelivered result; retry its callback', 'session_key': task_id}), 409
    if len(tasks) >= MAX_SYNTHESIS_JOBS:
        return jsonify({'message': 'Synthesis job limit reached', 'session_key': task_id}), 429

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

        task_process.start()
        tasks[task_id] = task_process
        pid = task_process.pid

        return jsonify({
            'message': 'Synthetization Started',
            'session_key': task_id,
            'pid': pid
        }), 202

    except Exception as e:
        _finish_job(_status_file_path(task_id), 'failed', str(e))
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
            config_content = configuration.inject_llm_profile_parameter(config_content)
        if has_dynamic_embedding_definition:
            config_content = configuration.inject_embedding_profile_parameter(config_content)
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
        return send_from_directory(configuration.HYPERPARAMETER_TUNING_DIR, 'study.yaml')
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
                'processing_capabilities': configuration.load_synthesizer_config(key).get('processing_capabilities', {})
            }
            for key, value in synthesizer_registry.synthesizer_classes.items()
        ]

        yaml_data = yaml.dump({'algorithms': synthesizer_list}, default_flow_style=False)

        return Response(yaml_data, mimetype='text/yaml')
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/cancel_synthetization_process', methods=['POST'])
@_locked_tasks
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
        marked_cancelled = _mark_task_cancelled(task_id)
        _cleanup_task_state(task_id)
        if not marked_cancelled:
            return jsonify({'message': 'Process stopped, but cancellation was not recorded; check job status',
                            'session_key': task_id, 'pid': actual_pid}), 409
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
    app.run(debug=True, use_reloader=False)
