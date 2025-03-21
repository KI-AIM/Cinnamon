import io
import os
import sys
import time
import logging
from multiprocessing import Process
from threading import Event

import pandas as pd
import requests
import yaml
from flask import Flask, request, jsonify, send_from_directory, Response
from flask_cors import CORS

import api_utility.logging.logger
from api_utility.status.status_updater import initialize_status_file
from api_utility.status.status_updater import update_status
from api_utility.status.status_updater import InterceptStdOut
from synthesizer_classes import synthesizer_classes
from data_processing.post_process import post_process_dataframe


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


def prepare_callback_data(samples, train_dataset, test_dataset, synthesizer_model):
    """
    Prepare synthetic data and model for callback.

    Args:
        samples (pd.DataFrame): Generated synthetic data.
        train_dataset (pd.DataFrame): Training dataset.
        test_dataset (pd.DataFrame): Testing dataset.
        synthesizer_model (bytes): Serialized synthesizer model.

    Returns:
        dict: A dictionary of file-like objects for callback POST request.
    """
    # Convert DataFrames to CSV strings
    csv_synthetic_data = samples.to_csv(index=False)
    csv_train = train_dataset.to_csv(index=False)
    csv_test = test_dataset.to_csv(index=False)

    # Store CSV data in in-memory file-like objects
    synthetic_data = io.BytesIO(csv_synthetic_data.encode('utf-8'))
    train = io.BytesIO(csv_train.encode('utf-8'))
    test = io.BytesIO(csv_test.encode('utf-8'))
    synthesizer_model = io.BytesIO(synthesizer_model)

    # Prepare files for POST request
    files = {
        'synthetic_data': ('synthetic_data.csv', synthetic_data),
        'train': ('train.csv', train),
        'test': ('test.csv', test),
        'model': ('model.pkl', synthesizer_model),
    }

    return files


def synthesize_data(synthesizer_name, file_path_status, attribute_config, algorithm_config, data,
                    callback_url, session_key):
    """
    Orchestrates the entire data synthesis process.

    Args:
        synthesizer_name (str): Name of the synthesizer.
        file_path_status (str): Path to the status file.
        attribute_config (dict): Attribute configuration.
        algorithm_config (dict): Algorithm configuration.
        data (pd.DataFrame): Input dataset.
        callback_url (str): Callback URL to send results.
        session_key (str): Unique session identifier.

    Returns:
        dict: Result of the synthesis process.
    """
    try:
        print('Synthesizer selected: ', synthesizer_name)
        # Synthesize Data
        if synthesizer_name not in synthesizer_classes:
            return {
                'message': f"Error: Synthesizer '{synthesizer_name}' not found",
                'session_key': session_key,
                'status_code': 400
            }

        print('Synthesizer found: ', synthesizer_name)
        init_time = time.time()
        synthesizer_class = synthesizer_classes[synthesizer_name]['class']()
        print('Synthesizer selected: ', synthesizer_name)

        # Initialize anonymization configuration
        synthesizer_class.initialize_anonymization_configuration(algorithm_config)
        print('Anonymization configuration initialized')

        # Initialize Attribute Configuration
        synthesizer_class.initialize_attribute_configuration(attribute_config)
        print('Attribute configuration initialized')

        # Initialize Dataset
        synthesizer_class.initialize_dataset(data)
        print('Dataset initialized')

        # Initialize Synthesizer
        synthesizer_class.initialize_synthesizer()

        # Get time duration for init
        init_time = time.time() - init_time
        update_status(file_path_status, step='initialization', duration=init_time, completed=True)

        print('Synthesizer initialized')

        # Fit Synthesizer
        fit_time = time.time()
        sys.stdout = InterceptStdOut(file_path_status, 'fitting')
        synthesizer_class.fit()

        # Get time duration for fit
        fit_time = time.time() - fit_time
        update_status(file_path_status, 'fitting', duration=fit_time, completed=True, remaining_time="0")
        print('Synthesizer fitted')

        # Sample data from synthesizer
        sample_time = time.time()
        sys.stdout = sys.__stdout__
        sys.stdout = InterceptStdOut(file_path_status, 'sampling')
        samples = synthesizer_class.sample()

        # Get time duration for sample
        sample_time = time.time() - sample_time
        update_status(file_path_status, 'sampling', duration=sample_time, completed=True, remaining_time="0")
        print('Data sampled')

        # Postprocessing -> pd.Dataframe
        samples = post_process_dataframe(samples, attribute_config['configurations'])

        # Get Model
        synthesizer_model = synthesizer_class.get_model()
        print('Model retrieved')

        # Prepare Callback
        files = prepare_callback_data(samples, synthesizer_class.trainDataset,
                                      synthesizer_class.validateDataset, synthesizer_model)
        try:
            requests.post(callback_url, files=files, data={'session_key': session_key})
            update_status(file_path_status, 'callback', completed=True)
            return {
                'message': 'Synthetization Finished, successfully sent callback notification',
                'session_key': session_key
            }

        except requests.exceptions.RequestException as e:
            return {
                'message': 'Synthetization Finished, failed to send callback notification',
                'error': str(e),
                'session_key': session_key
            }

    except Exception as e:
        return {
            'message': f'Error during synthetization: {str(e)}',
            'session_key': session_key
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


# Test for correct callback function
@app.route('/test_callback', methods=['POST'])
def test_callback():
    """
    Test endpoint for the callback functionality.

    Args:
        session_key (str): Unique session key for the synthetization process.

    Returns:
        JSON: A success message containing the session key.
    """
    session_key = request.form['session_key']
    print('Callback function called with session key:', session_key)

    files = request.files
    for file_key in files:
        # Print filename
        print('File:', file_key)

    return jsonify({'message': 'Callback function called', 'session_key': session_key}), 200


if __name__ == '__main__':
    api_utility.logging.logger.setup_logging()
    logger = logging.getLogger()
    logger.info('Starting API server')

    app.run(debug=True)

