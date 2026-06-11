import io
import os
import sys
import time
from multiprocessing import Process
from threading import Event

import pandas as pd
import requests
import yaml
from flask import Flask, request, jsonify, send_from_directory, Response
from flask_cors import CORS

from api_utility.status.status_updater import initialize_status_file
from api_utility.status.status_updater import update_status
from api_utility.status.status_updater import InterceptStdOut
from synthesizer_classes import synthesizer_classes
from data_processing.post_process import post_process_dataframe
from data_processing.pre_process import pre_process_dataframe


app = Flask(__name__)
tasks = {}
task_locks = {}
CORS(app)


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
        print('No attribute_config file provided')
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

    # Read the content of the files
    attribute_config = yaml.safe_load(attribute_config.read())
    algorithm_config = yaml.safe_load(algorithm_config.read())
    data = pd.read_csv(io.StringIO(data.read().decode('utf-8')))

    return session_key, callback_url, file_path_status, attribute_config, algorithm_config, data


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


def synthesize_data(synthesizer_name, file_path_status, attribute_config, algorithm_config, data,
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
        print('Synthesizer selected:', synthesizer_name)
        init_time = time.time()

        # Step 0: Check if the synthesizer exists
        if synthesizer_name not in synthesizer_classes:
            error_message = f"Error: Synthesizer '{synthesizer_name}' not found"
            send_callback_error(callback_url, session_key, error_message, 400)
            return {
                'message': error_message,
                'session_key': session_key,
                'status_code': 400
            }

        # Step 1: Initialize the synthesizer
        try:
            synthesizer_class = synthesizer_classes[synthesizer_name]['class']()
            print('Synthesizer class initialized:', synthesizer_name)
        except RuntimeError as e:
            error_message = f"Error during initialization of synthesizer. {str(e)}"
            send_callback_error(callback_url, session_key, error_message, 400)
            return {
                'message': error_message,
                'session_key': session_key,
                'status_code': 400
            }

        # Determine HPT early so Steps 2/3/5/6 can be skipped when appropriate.
        # When HPT is enabled the outer synthesizer_class is never fitted/sampled;
        # each Optuna trial creates a fresh synthesizer with a complete per-trial config.
        algo_cfg_dict = (algorithm_config or {}).get("synthetization_configuration", {}).get("algorithm", {})
        tuning_cfg = algo_cfg_dict.get("hyperparameter_tuning", {}) if isinstance(algo_cfg_dict, dict) else {}
        tuning_enabled = isinstance(tuning_cfg, dict) and bool(tuning_cfg.get("enabled", False))

        # Step 2: Initialize anonymization configuration
        # Skipped for HPT: when HPT is on the frontend omits model_parameter/model_fitting/
        # sampling from the payload (the form is hidden), so accessing those keys here would
        # raise a KeyError. Each Optuna trial calls initialize_anonymization_configuration
        # itself with a complete per-trial config.
        if not tuning_enabled:
            try:
                synthesizer_class.initialize_anonymization_configuration(algorithm_config)
                print('Anonymization configuration initialized.')
            except RuntimeError as e:
                error_message = f"Error during initialization of anonymization configuration. {str(e)}"
                send_callback_error(callback_url, session_key, error_message, 400)
                return {
                    'message': error_message,
                    'session_key': session_key,
                    'status_code': 400
                }

        # Step 3: Initialize attribute configuration
        # Skipped for HPT: each trial handles this on its own fresh synthesizer.
        if not tuning_enabled:
            try:
                synthesizer_class.initialize_attribute_configuration(attribute_config)
                print('Attribute configuration initialized.')
            except RuntimeError as e:
                error_message = f"Error during attribute configuration. {str(e)}"
                send_callback_error(callback_url, session_key, error_message, 400)
                return {
                    'message': error_message,
                    'session_key': session_key,
                    'status_code': 400
                }

        # Step 4: Pre-process sampled data (always runs — result shared by all HPT trials)
        try:
            pre_processed_data, all_missing_values_column = pre_process_dataframe(data, attribute_config['configurations'])
            print("Dataset preprocessed.")
        except Exception as e:
            error_message = f"Error during pre-processing. {str(e)}"
            send_callback_error(callback_url, session_key, error_message, 500)
            return {
                'message': error_message,
                'session_key': session_key,
                'status_code': 500
            }

        # Step 5: Initialize dataset
        # Skipped for HPT: each trial handles this on its own fresh synthesizer.
        if not tuning_enabled:
            try:
                synthesizer_class.initialize_dataset(pre_processed_data)
                print('Dataset initialized.')
            except RuntimeError as e:
                print("Error in Dataset Initialoization")
                error_message = f"Error during dataset initialization. {str(e)}"
                send_callback_error(callback_url, session_key, error_message, 400)
                return {
                    'message': error_message,
                    'session_key': session_key,
                    'status_code': 400
                }

        # Step 6: Initialize synthesizer
        # Skipped for HPT: each trial handles this on its own fresh synthesizer.
        # For HPT we still mark initialization complete (class creation + pre-processing).
        if not tuning_enabled:
            try:
                synthesizer_class.initialize_synthesizer()
                print('Synthesizer initialized.')
                init_time = time.time() - init_time
                update_status(file_path_status, step='initialization', duration=init_time, completed=True)
            except RuntimeError as e:
                error_message = f"Error during synthesizer initialization. {str(e)}"
                send_callback_error(callback_url, session_key, error_message, 500)
                return {
                    'message': error_message,
                    'session_key': session_key,
                    'status_code': 500
                }
        else:
            init_time = time.time() - init_time
            update_status(file_path_status, step='initialization', duration=init_time, completed=True)

        # Step 6.5: Branch on the hyperparameter-tuning toggle from the request payload.
        # When enabled the linear fit+sample (Steps 7-8) is replaced by an Optuna study;
        # otherwise the original single-shot path runs unchanged.

        if tuning_enabled:
            try:
                from hyperparameter_tuning.optuna_tuning import (
                    optimize as run_optuna_study,
                    DEFAULT_ARTIFACT_DIR,
                )

                sampler = tuning_cfg.get("sampler", "tpe")
                pruner = tuning_cfg.get("pruner", "median")
                n_trials = int(tuning_cfg.get("n_trials", 50))
                raw_timeout = tuning_cfg.get("timeout_minutes")
                try:
                    timeout_seconds = float(raw_timeout) * 60.0 if raw_timeout else None
                except (TypeError, ValueError):
                    timeout_seconds = None
                target_variable = tuning_cfg.get("target_variable") or data.columns[-1]

                def _fit_one_trial(trial_algorithm_config):
                    """Per-trial fit used by Optuna. Fresh state every call.

                    Returns the synthesizer's fit metric (loss or score) that
                    Optuna optimizes — no sampling/scoring of synthetic data.
                    """
                    trial_synth = synthesizer_classes[synthesizer_name]['class']()
                    trial_synth.initialize_anonymization_configuration(trial_algorithm_config)
                    trial_synth.initialize_attribute_configuration(attribute_config)
                    trial_synth.initialize_dataset(pre_processed_data)
                    trial_synth.initialize_synthesizer()
                    return trial_synth.fit()

                # Direction comes from the synthesizer registry: a training loss
                # is minimized, a goodness-of-fit score is maximized. Unsupported
                # synthesizers (ctgan/tvae) have no direction — their trials
                # return no metric and prune, so the study yields no result and
                # the error below is surfaced to the caller.
                tuning_direction = (
                    synthesizer_classes[synthesizer_name].get('tuning_direction') or 'minimize'
                )

                tuning_start = time.time()
                update_status(file_path_status, "hyperparameter_tuning",
                              duration="0", completed=False, remaining_time="Waiting")
                sys.stdout = InterceptStdOut(file_path_status, 'hyperparameter_tuning')

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
                    artifact_dir=DEFAULT_ARTIFACT_DIR,
                )

                sys.stdout = sys.__stdout__
                tuning_time = time.time() - tuning_start
                update_status(file_path_status, "hyperparameter_tuning",
                              duration=tuning_time, completed=True, remaining_time="0")

                # Re-fit the best config on a fresh synthesizer so Step 10 (get_model)
                # produces model bytes that correspond to the returned samples.
                best_cfg = result.best_algorithm_config
                if best_cfg is None:
                    raise RuntimeError("Optuna study finished without a best algorithm config.")
                best_synth = synthesizer_classes[synthesizer_name]['class']()
                best_synth.initialize_anonymization_configuration(best_cfg)
                best_synth.initialize_attribute_configuration(attribute_config)
                best_synth.initialize_dataset(pre_processed_data)
                best_synth.initialize_synthesizer()
                best_synth.fit()
                synthesizer_class = best_synth  # so Step 10 uses the best trial's model

                samples = result.best_synthetic if result.best_synthetic is not None else best_synth.sample()

                # The original fitting + sampling status entries were absorbed into the
                # hyperparameter_tuning step; mark them complete with zero duration so
                # the UI still ticks them off.
                update_status(file_path_status, 'fitting', duration="0", completed=True, remaining_time="0")
                update_status(file_path_status, 'sampling', duration="0", completed=True, remaining_time="0")
            except Exception as e:
                sys.stdout = sys.__stdout__
                error_message = f"Error during hyperparameter tuning. {str(e)}"
                send_callback_error(callback_url, session_key, error_message, 500)
                return {
                    'message': error_message,
                    'session_key': session_key,
                    'status_code': 500
                }
        else:
            # Step 7: Fit the synthesizer
            try:
                fit_time = time.time()
                sys.stdout = InterceptStdOut(file_path_status, 'fitting')
                synthesizer_class.fit()
                fit_time = time.time() - fit_time
                update_status(file_path_status, 'fitting', duration=fit_time, completed=True, remaining_time="0")
                print('Synthesizer fitted.')
            except RuntimeError as e:
                error_message = f"Error during synthesizer fitting. {str(e)}"
                send_callback_error(callback_url, session_key, error_message, 500)
                return {
                    'message': error_message,
                    'session_key': session_key,
                    'status_code': 500
                }

            # Step 8: Sample data
            try:
                sample_time = time.time()
                sys.stdout = sys.__stdout__
                sys.stdout = InterceptStdOut(file_path_status, 'sampling')
                samples = synthesizer_class.sample()
                sample_time = time.time() - sample_time
                update_status(file_path_status, 'sampling', duration=sample_time, completed=True, remaining_time="0")
                print('Data sampled.')
            except RuntimeError as e:
                error_message = f"Error during data sampling. {str(e)}"
                send_callback_error(callback_url, session_key, error_message, 500)
                return {
                    'message': error_message,
                    'session_key': session_key,
                    'status_code': 500
                }

        # Step 9: Post-process sampled data
        try:
            print('Starting Post-processing')
            samples = post_process_dataframe(samples, attribute_config['configurations'], all_missing_values_column)
            print('Data Post-processed')
        except Exception as e:
            error_message = f"Error during post-processing. {str(e)}"
            print(error_message)
            send_callback_error(callback_url, session_key, error_message, 500)
            return {
                'message': error_message,
                'session_key': session_key,
                'status_code': 500
            }

        # Step 10: Retrieve the model
        try:
            synthesizer_model = synthesizer_class.get_model()
            print('Model retrieved.')
        except RuntimeError as e:
            error_message = f"Error during model retrieval. {str(e)}"
            send_callback_error(callback_url, session_key, error_message, 500)
            return {
                'message': error_message,
                'session_key': session_key,
                'status_code': 500
            }

        # Step 11: Send callback
        try:
            files = prepare_callback_data(samples, synthesizer_model)
            requests.post(callback_url, files=files, data={'session_key': session_key})
            update_status(file_path_status, 'callback', completed=True)
            return {
                'message': 'Synthetization Finished, successfully sent callback notification',
                'session_key': session_key,
                'status_code': 200
            }
        except requests.exceptions.RequestException as e:
            error_message = f"Synthetization Finished, failed to send callback notification. {str(e)}"
            send_callback_error(callback_url, session_key, error_message, 500)
            return {
                'message': error_message,
                'session_key': session_key,
                'status_code': 500
            }

    except Exception as e:
        # Catch any unexpected errors
        error_message = f"Unexpected error occurred: {str(e)}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }


@app.route('/start_synthetization_process/<string:synthesizer_name>', methods=['POST'])
def start_synthetization_process(synthesizer_name):
    """
    Starts the data synthesis process in a separate process.

    Args:
        synthesizer_name (str): Name of the synthesizer.

    Returns:
        JSON: Response indicating task start status.
    """
    # Initialize Task
    task_id = request.form['session_key']
    stop_event = Event()
    task_locks[task_id] = stop_event

    try:
        # Initialize input data
        session_key, callback_url, file_path_status, attribute_config, algorithm_config, data = initialize_input_data(
            synthesizer_name)
        print('Data successfully loaded')

        # Create and start the process
        task_process = Process(
            target=synthesize_data,
            args=(synthesizer_name, file_path_status, attribute_config, algorithm_config,
                  data.copy(), callback_url, session_key)  # Note the data.copy()
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

        config_directory = os.path.join(module_name, 'synthesizer_config')
        config_path = os.path.join(config_directory, filename)
        print(config_path)

        if not os.path.abspath(config_path).startswith(os.path.abspath(config_directory)):
            error_message = 'Invalid file path. Access to files outside the allowed directory is not allowed.'
            return jsonify({'error': error_message}), 403

        return send_from_directory(config_directory, filename)
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
        directory = os.path.join(os.path.dirname(__file__), 'hyperparameter_tuning')
        return send_from_directory(directory, 'study.yaml')
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
                'URL': value['URL']
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
        response = requests.post(callback_url, files=files, data=data, timeout=5)
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
