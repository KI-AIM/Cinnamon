import io
import json
import os
from threading import Event
import multiprocessing
from typing import Optional, Tuple, Union, Any, Dict, List, BinaryIO

import pandas as pd
import requests
import yaml
from flask import Flask, request, jsonify, Response
from flask_cors import CORS

from evaluation_metrics import (
    metric_functions_cross_sectional,
    metric_functions_longitudinal,
    metric_functions_process_oriented,
    metric_functions_descriptive
)

from data_processing.pre_process import pre_process_dataframe
from data_processing.utils import validate_and_extract_metrics
from dispatcher import dispatch_metrics
from visualization.vis_converter import (
    group_metrics_by_visualization_type,
    create_empty_config,
    add_metrics_to_config,
    enrich_metrics_with_descriptions,
    add_value_differences,
    remove_synthetic_and_difference,
    convert_attributes_to_date,
    extract_and_enrich_utility_metrics, add_resembance_description, add_overview_to_config
)

app = Flask(__name__)
tasks = {}
task_locks = {}
CORS(app)
app_dir = os.path.dirname(os.path.abspath(__file__))


def get_metric_metadata(data_format: str, metric_type: str, evaluation_metadata: dict) -> Optional[dict]:
    """
    Retrieves and matches metadata for metrics based on data format and metric type.

    Args:
        data_format (str): Format of the data ("cross-sectional", "longitudinal", or "process-oriented")
        metric_type (str): Type of metric to retrieve metadata for
        evaluation_metadata (dict): Dictionary containing metadata for all metrics

    Returns:
        Optional[dict]: Matched metadata for metrics, where each key is a metric name containing:
            - display_name (str): Human readable name for the metric
            - description (str): Detailed description of the metric
            - version (str): Version number of the metric
            - parameters (dict, optional): Configuration parameters for the metric
            Returns None if data_format is invalid or metric_type not found in evaluation_metadata
    """
    format_to_metrics = {
        "cross-sectional": metric_functions_cross_sectional,
        "longitudinal": metric_functions_longitudinal,
        "process-oriented": metric_functions_process_oriented
    }

    metrics_dict = format_to_metrics.get(data_format, {}).get(metric_type, {})
    if not metrics_dict:
        return None

    metrics_metadata = evaluation_metadata.get(metric_type, {}).get('metrics', [])
    if not metrics_metadata:
        return None

    matched_metadata = {}
    for metric_key in metrics_dict:
        for metric in metrics_metadata:
            if metric['function_name'] == metric_key:
                metric_data = {
                    'display_name': metric['display_name'],
                    'description': metric['description'],
                    'version': metric['version']
                }

                if metric.get('parameters'):
                    metric_data['parameters'] = metric['parameters']

                matched_metadata[metric_key] = metric_data

    return matched_metadata


def initialize_input_evaluation() -> Union[Tuple[str, str, dict, dict, pd.DataFrame, pd.DataFrame], Tuple[str, int]]:
    """
    Initialize and validate input data for evaluation process.

    Required form fields:
        - session_key: Unique identifier for the evaluation session
        - callback: URL for callback notifications

    Required files:
        - attribute_config: YAML configuration for attributes
        - evaluation_config: YAML configuration for evaluation
        - real_data: CSV file containing real dataset
        - synthetic_data: CSV file containing synthetic dataset

    Returns:
        If successful:
            Tuple containing:
            - session_key (str): Session identifier
            - callback_url (str): Callback URL
            - attribute_config (dict): Parsed attribute configuration
            - evaluation_config (dict): Parsed evaluation configuration
            - real_data (pd.DataFrame): Processed real dataset
            - synthetic_data (pd.DataFrame): Processed synthetic dataset
        If validation fails:
            Tuple containing:
            - error_message (str): Description of the error
            - status_code (int): HTTP status code (400)
    """
    required_fields = {
        'session_key': 'No session key provided',
        'callback': 'No callback URL provided'
    }

    required_files = {
        'attribute_config': 'No attribute_config file provided',
        'evaluation_config': 'No evaluation_config file provided',
        'real_data': 'No real data file provided',
        'synthetic_data': 'No synthetic data file provided'
    }

    for field, error_msg in required_fields.items():
        if field not in request.form:
            return error_msg, 400

    for file, error_msg in required_files.items():
        if file not in request.files:
            return error_msg, 400

    session_key = request.form['session_key']
    callback_url = request.form['callback']

    attribute_config = yaml.safe_load(request.files['attribute_config'].read())
    evaluation_config = yaml.safe_load(request.files['evaluation_config'].read())
    real_data = pd.read_csv(io.StringIO(request.files['real_data'].read().decode('utf-8')))
    synthetic_data = pd.read_csv(io.StringIO(request.files['synthetic_data'].read().decode('utf-8')))

    return session_key, callback_url, attribute_config, evaluation_config, real_data, synthetic_data


def initialize_input_statistics() -> Union[Tuple[str, str, dict, dict, pd.DataFrame], Tuple[str, int]]:
    """
    Initialize and validate input data for statistical analysis.

    Required form fields:
        - session_key: Unique identifier for the statistics session
        - callback: URL for callback notifications

    Required files:
        - attribute_config: YAML configuration for attributes
        - real_data: CSV file containing real dataset

    Returns:
        If successful:
            Tuple containing:
            - session_key (str): Session identifier
            - callback_url (str): Callback URL
            - attribute_config (dict): Parsed attribute configuration
            - evaluation_config (dict): Parsed evaluation configuration from static file
            - real_data (pd.DataFrame): Processed real dataset
        If validation fails:
            Tuple containing:
            - error_message (str): Description of the error
            - status_code (int): HTTP status code (400)
    """
    required_fields = {
        'session_key': 'No session key provided',
        'callback': 'No callback URL provided'
    }

    required_files = {
        'attribute_config': 'No attribute_config file provided',
        'real_data': 'No real data file provided'
    }

    for field, error_msg in required_fields.items():
        if field not in request.form:
            return error_msg, 400

    for file, error_msg in required_files.items():
        if file not in request.files:
            return error_msg, 400

    session_key = request.form['session_key']
    callback_url = request.form['callback']

    eval_conig_path = os.path.join(app_dir, 'static_configs', 'evaluation_config_descriptive.yaml')
    with open(eval_conig_path, 'r') as file:
        evaluation_config = yaml.safe_load(file)

    attribute_config = yaml.safe_load(request.files['attribute_config'].read())
    real_data = pd.read_csv(io.StringIO(request.files['real_data'].read().decode('utf-8')))

    return session_key, callback_url, attribute_config, evaluation_config, real_data


def make_serializable(item: Any) -> Union[Dict, List, Any]:
    """
    Convert non-serializable objects into JSON serializable format.

    Args:
        item (Any): Object to be converted, can be DataFrame, dict, list or other types

    Returns:
        Union[Dict, List, Any]: Serializable version of the input item where:
            - DataFrames are converted to list of records
            - Dictionaries have values recursively converted
            - Lists have elements recursively converted
            - Other types are returned as-is if already serializable
    """
    if isinstance(item, pd.DataFrame):
        return item.to_dict(orient='records')
    elif isinstance(item, dict):
        return {k: make_serializable(v) for k, v in item.items()}
    elif isinstance(item, list):
        return [make_serializable(i) for i in item]
    return item


def prepare_callback_data(metrics: Dict) -> Dict[str, Tuple[str, BinaryIO, str]]:
    """
    Prepare metrics data for HTTP POST request by converting to JSON format.

    Args:
        metrics (Dict): Dictionary containing metrics data, potentially including
            DataFrames and other non-serializable objects

    Returns:
        Dict[str, Tuple[str, BinaryIO, str]]: Dictionary containing:
            - Key: 'metrics_file'
            - Value: Tuple of (filename, file-like object, content-type)
            Suitable for use as the 'files' parameter in requests.post
    """
    serializable_metrics = make_serializable(metrics)
    metrics_json = json.dumps(serializable_metrics)
    metrics_bytes = metrics_json.encode('utf-8')
    file_like_object = io.BytesIO(metrics_bytes)

    return {'metrics_file': ('metrics.json', file_like_object, 'application/json')}


def statistics_data(session_key, callback_url, attribute_config, evaluation_config, real_data, synthetic_data):
    """
    Evaluates the data using the specified evaluation configuration and returns the results.

    Args:
        session_key (str): The session key for the current evaluation.
        callback_url (str): The callback URL for the current evaluation.
        attribute_config (dict): The attribute configuration for the current evaluation.
        evaluation_config (dict): The evaluation configuration for the current evaluation.
        real_data (pandas.DataFrame): The real data for the current evaluation.
        synthetic_data (pandas.DataFrame): The synthetic data for the current evaluation.

    Returns:
        dict: A dictionary containing the results of the evaluation.
    """
    print("Evaluating Data")

    print("Initializing Input Data")
    processed_real_data, discrete_values_real = pre_process_dataframe(real_data, attribute_config['configurations'])
    processed_synthetic_data, discreate_values_synthetic = pre_process_dataframe(synthetic_data,
                                                                                 attribute_config['configurations'])
    data_format, metrics = validate_and_extract_metrics(evaluation_config)

    overview_resemblance_path = os.path.join(app_dir, 'resemblance', 'overview_resemblance_metrics.yaml')
    with open(overview_resemblance_path, 'r') as file:
        overview_metrics = yaml.safe_load(file)

    time_attributes_path = os.path.join(app_dir, 'static_configs', 'evaluation_config_time_attributes.yaml')
    with open(time_attributes_path, 'r') as file:
        time_attributes_config = yaml.safe_load(file)

    print("Data format:", data_format)

    print("Calculating Metrics")
    metrics_result = dispatch_metrics(processed_real_data, processed_synthetic_data, evaluation_config,
                                      metric_functions_descriptive)

    empty_config = create_empty_config(attribute_config)

    # organize the metrics by visualization type and group them by visualization type
    grouped_metrics = group_metrics_by_visualization_type(overview_metrics)

    # Add metrics to config
    metrics_dict = add_metrics_to_config(empty_config, metrics_result, grouped_metrics)

    # Enrich Metrics
    enriched_metrics = enrich_metrics_with_descriptions(metrics_dict, overview_metrics, add_interpretation=True)

    # Add value differences
    enriched_metrics = add_value_differences(enriched_metrics)

    # Remove synthetic and difference
    enriched_metrics = remove_synthetic_and_difference(enriched_metrics)

    # transform time attributes to date
    enriched_metrics = convert_attributes_to_date(enriched_metrics, time_attributes_config)

    # Add the description of the resemblance utility metric to the enriched dictionary
    enriched_metrics = add_resembance_description(enriched_metrics, overview_metrics)

    try:
        files = prepare_callback_data(enriched_metrics)
        requests.post(callback_url, files=files, data={'session_key': session_key})
        print("Callback made successfully")

    except requests.exceptions.RequestException as e:
        print(f"Error while making callback: {e}")


def evaluate_data(session_key, callback_url, attribute_config, evaluation_config, real_data, synthetic_data):
    """
    Evaluates the data using the specified evaluation configuration and returns the results.

    Args:
        session_key (str): The session key for the current evaluation.
        callback_url (str): The callback URL for the current evaluation.
        attribute_config (dict): The attribute configuration for the current evaluation.
        evaluation_config (dict): The evaluation configuration for the current evaluation.
        real_data (pandas.DataFrame): The real data for the current evaluation.
        synthetic_data (pandas.DataFrame): The synthetic data for the current evaluation.

    Returns:
        dict: A dictionary containing the results of the evaluation.
    """
    print("Evaluating Data")

    print("Initializing Input Data")
    processed_real_data, discrete_values_real = pre_process_dataframe(real_data, attribute_config['configurations'])
    processed_synthetic_data, discrete_values_synthetic = pre_process_dataframe(synthetic_data,
                                                                                attribute_config['configurations'])
    data_format, metrics = validate_and_extract_metrics(evaluation_config)

    # Load Resemblance Metrics
    overview_resemblance_path = os.path.join(app_dir, 'resemblance', 'overview_resemblance_metrics.yaml')
    with open(overview_resemblance_path, 'r') as file:
        overview_metrics = yaml.safe_load(file)

    # Load Utility Metrics
    overview_utility_metrics = os.path.join(app_dir, 'utility', 'overview_utility_metrics.yaml')
    with open(overview_utility_metrics, 'r') as file:
        utility_metrics = yaml.safe_load(file)

    time_attributes_path = os.path.join(app_dir, 'static_configs', 'evaluation_config_time_attributes.yaml')
    with open(time_attributes_path, 'r') as file:
        time_attributes_config = yaml.safe_load(file)

    print("Data format:", data_format)

    print("Calculating Metrics")
    if data_format == 'cross-sectional':
        metrics_result = dispatch_metrics(processed_real_data, processed_synthetic_data, evaluation_config,
                                          metric_functions_cross_sectional)
    elif data_format == 'longitudinal':
        metrics_result = dispatch_metrics(processed_real_data, processed_synthetic_data, evaluation_config,
                                          metric_functions_longitudinal)
    elif data_format == 'process-oriented':
        metrics_result = dispatch_metrics(processed_real_data, processed_synthetic_data, evaluation_config,
                                          metric_functions_process_oriented)
    else:
        print("Data format not supported")
        return json.dumps({'error': 'Data format not supported'}), 400

    # Manage Utility Metrics
    utility_metrics_result = metrics_result['utility']
    enriched_utility_metrics = extract_and_enrich_utility_metrics(utility_metrics, utility_metrics_result)

    # Manage Resemblance Metrics
    empty_config = create_empty_config(attribute_config)

    # organize the metrics by visualization type and group them by visualization type
    grouped_metrics = group_metrics_by_visualization_type(overview_metrics)

    # Add metrics to config
    metrics_dict = add_metrics_to_config(empty_config, metrics_result, grouped_metrics)

    # Enrich Metrics
    enriched_metrics = enrich_metrics_with_descriptions(metrics_dict, overview_metrics, add_interpretation=True)

    # Add value differences
    enriched_metrics = add_value_differences(enriched_metrics)

    # transform time attributes to date
    enriched_metrics = convert_attributes_to_date(enriched_metrics, time_attributes_config)

    # Combine Utility and resemblance in one dictionary
    enriched_metrics.update(enriched_utility_metrics)

    # Add the description of the resemblance utility metric to the enriched dictionary
    enriched_metrics = add_resembance_description(enriched_metrics, overview_metrics)

    # Add overview to Config 
    enriched_metrics = add_overview_to_config(enriched_metrics)

    try:
        files = prepare_callback_data(enriched_metrics)
        requests.post(callback_url, files=files, data={'session_key': session_key})
        print("Callback made successfully")

    except requests.exceptions.RequestException as e:
        print(f"Error while making callback: {e}")


@app.route('/get_evaluation_metrics/<string:data_format>', methods=['GET'])
def get_evaluation_metrics(data_format):
    
    file_path_resemblance = os.path.join(app_dir, 'resemblance', 'overview_resemblance_metrics.yaml')
    with open(file_path_resemblance, 'r') as res_file:
        resemblance_config_yaml = yaml.safe_load(res_file)

    file_path_utility = os.path.join(app_dir, 'utility', 'overview_utility_metrics.yaml')
    with open(file_path_utility, 'r') as uti_file:
        utility_config_yaml = yaml.safe_load(uti_file)

    resemblance_metadata = get_metric_metadata(data_format, "resemblance", evaluation_metadata=resemblance_config_yaml)
    utility_metadata = get_metric_metadata(data_format, "utility", evaluation_metadata=utility_config_yaml)

    if resemblance_metadata is None and utility_metadata is None:
        return jsonify({'error': 'Invalid data format provided.'}), 400

    metadata = {
        'name': 'Evaluation',
        'type': data_format,
        'display_name': 'Evaluation',
        'description': 'Metrics used to evaluate the resemblance and utility of synthetic data compared to real data.',
        'URL': '/start_evaluation',
        'configurations': {
            'resemblance': {
                'display_name': 'Resemblance Metrics',
                'description': 'Metrics that evaluate the resemblance between real and synthetic data.',
                'options': resemblance_metadata
            },
            'utility': {
                'display_name': 'Utility Metrics',
                'description': 'Metrics that evaluate the utility of anonymized data in comparision with the real data.',
                'options': utility_metadata
            }
        }
    }

    json_data = json.dumps(metadata, indent=2)

    yaml_data = yaml.safe_dump(json.loads(json_data), sort_keys=False, default_style=None)

    return Response(yaml_data, mimetype='text/yaml')


@app.route('/start_evaluation', methods=['POST'])
def start_evaluation():
    task_id = request.form['session_key']
    stop_event = Event()
    task_locks[task_id] = stop_event
    try:
        session_key, callback_url, attribute_config, evaluation_config, real_data, synthetic_data = initialize_input_evaluation()
        print('Data succesfully loaded')

        task_process = multiprocessing.Process(target=evaluate_data, args=(
            session_key, callback_url, attribute_config, evaluation_config, real_data, synthetic_data))
        tasks[task_id] = task_process
        task_process.start()
        pid = task_process.pid

        return jsonify({'message': 'Evaluation Started', 'session_key': task_id, 'pid': pid}), 202

    except Exception as e:
        return jsonify(
            {'message': 'Exeption occured during Evaluation', 'error': str(e), 'session_key': task_id}), 500


@app.route('/calculate_descriptive_statistics', methods=['POST'])
def calculate_descriptive_statistics():
    task_id = request.form['session_key']
    stop_event = Event()
    task_locks[task_id] = stop_event

    try:
        session_key, callback_url, attribute_config, evaluation_config, real_data = initialize_input_statistics()
        synthetic_data = real_data.copy()
        print('Data succesfully loaded')

        task_process = multiprocessing.Process(target=statistics_data, args=(
            session_key, callback_url, attribute_config, evaluation_config, real_data, synthetic_data))
        tasks[task_id] = task_process
        task_process.start()
        pid = task_process.pid

        return jsonify({'message': 'Statistics Calculation Started', 'session_key': task_id, 'pid': pid}), 202

    except Exception as e:
        return jsonify(
            {'message': 'Exception occurred during Statistics Calculation', 'error': str(e),
             'session_key': task_id}), 500


@app.route('/cancel_evaluation', methods=['POST'])
def cancel_evaluation():
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
    session_key = request.form['session_key']
    print('Callback function called with session key:', session_key)

    if session_key is None:
        return jsonify({'message': 'session_key not provided in JSON'}), 400

    return jsonify({'message': 'Callback function called', 'session_key': session_key}), 200


if __name__ == '__main__':
    app.run(debug=True)
