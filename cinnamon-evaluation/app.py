import io
import json
import os
from threading import Event
import multiprocessing
import sys
from typing import Optional, Tuple, Union, Any, Dict, List, BinaryIO
from datetime import datetime

import pandas as pd
import requests
import yaml
from flask import Flask, request, jsonify, Response
from flask_cors import CORS

from evaluation_metrics import (
    metric_functions_cross_sectional,
    metric_functions_longitudinal,
    metric_functions_descriptive
)

from data_processing.pre_process import preprocess_datasets
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
    extract_and_enrich_utility_metrics,
    add_resembance_description, 
    add_overview_to_config
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
        data_format (str): Format of the data ("cross-sectional" or "longitudinal")
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
        "longitudinal": metric_functions_longitudinal
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
    try:
        processed_real_data, processed_synthetic_data = preprocess_datasets(real_data, synthetic_data, attribute_config['configurations'])
    except Exception as e:
        error_message = f"Error during data preprocessing: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        data_format, metrics = validate_and_extract_metrics(evaluation_config)
    except Exception as e:
        error_message = f"Error during validation and metric extraction: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    overview_resemblance_path = os.path.join(app_dir, 'resemblance', 'overview_resemblance_metrics.yaml')
    try:
        with open(overview_resemblance_path, 'r') as file:
            overview_metrics = yaml.safe_load(file)
    except FileNotFoundError as e:
        error_message = f"Resemblance metrics file not found: {overview_resemblance_path}"
        send_callback_error(callback_url, session_key, error_message, 404)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 404
        }
    except yaml.YAMLError as e:
        error_message = f"Error loading resemblance metrics YAML from {overview_resemblance_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }
    except Exception as e:
        error_message = f"Unexpected error loading resemblance metrics from {overview_resemblance_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    time_attributes_path = os.path.join(app_dir, 'static_configs', 'evaluation_config_time_attributes.yaml')
    try:
        with open(time_attributes_path, 'r') as file:
            time_attributes_config = yaml.safe_load(file)
    except FileNotFoundError as e:
        error_message = f"Time attributes config file not found: {time_attributes_path}"
        send_callback_error(callback_url, session_key, error_message, 404)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 404
        }
    except yaml.YAMLError as e:
        error_message = f"Error loading time attributes config YAML from {time_attributes_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }
    except Exception as e:
        error_message = f"Unexpected error loading time attributes config from {time_attributes_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }


    print("Data format:", data_format)

    print("Calculating Metrics")
    metrics_result = None
    try:
        # Assuming dispatch_metrics in statistics_data always uses metric_functions_descriptive
        metrics_result = dispatch_metrics(processed_real_data, processed_synthetic_data, evaluation_config,
                                          metric_functions_descriptive)
    except Exception as e:
        error_message = f"Error during metric calculation: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    if metrics_result is None:
        error_message = "Metric calculation failed to produce a result (metrics_result is None)."
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        empty_config = create_empty_config(attribute_config)
    except Exception as e:
        error_message = f"Error creating empty config: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        grouped_metrics = group_metrics_by_visualization_type(overview_metrics)
    except Exception as e:
        error_message = f"Error grouping metrics by visualization type: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        metrics_dict = add_metrics_to_config(empty_config, metrics_result, grouped_metrics)
    except Exception as e:
        error_message = f"Error adding metrics to config: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        enriched_metrics = enrich_metrics_with_descriptions(metrics_dict, overview_metrics, add_interpretation=True)
    except Exception as e:
        error_message = f"Error enriching metrics with descriptions: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        enriched_metrics = add_value_differences(enriched_metrics)
    except Exception as e:
        error_message = f"Error adding value differences: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # MISSING FUNCTION CALL 1: remove_synthetic_and_difference
    try:
        enriched_metrics = remove_synthetic_and_difference(enriched_metrics)
    except Exception as e:
        error_message = f"Error removing synthetic and difference metrics: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        enriched_metrics = convert_attributes_to_date(enriched_metrics, time_attributes_config)
    except Exception as e:
        error_message = f"Error converting time attributes to date: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        enriched_metrics = add_resembance_description(enriched_metrics, overview_metrics)
    except Exception as e:
        error_message = f"Error adding resemblance description: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # MISSING FUNCTION CALL 2: add_overview_to_config
    try:
        enriched_metrics = add_overview_to_config(enriched_metrics)
    except Exception as e:
        error_message = f"Error adding overview to config: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        files = prepare_callback_data(enriched_metrics)
        requests.post(callback_url, files=files, data={'session_key': session_key})
        print("Callback made successfully")

    except requests.exceptions.RequestException as e:
        print(f"Failed to send final results callback to URL {callback_url}: {e}", file=sys.stderr)
        pass # As per evaluate_data, it doesn't return an error here
    except Exception as e:
        print(f"Unexpected error preparing or sending final results callback to URL {callback_url}: {e}", file=sys.stderr)
        pass

    return enriched_metrics


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
    try:
        processed_real_data, processed_synthetic_data = preprocess_datasets(real_data, synthetic_data, attribute_config['configurations'])
    except Exception as e:
        error_message = f"Error during data preprocessing: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        data_format, metrics = validate_and_extract_metrics(evaluation_config)
    except Exception as e:
        error_message = f"Error during validation and metric extraction: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # Load Resemblance Metrics
    overview_resemblance_path = os.path.join(app_dir, 'resemblance', 'overview_resemblance_metrics.yaml')
    try:
        with open(overview_resemblance_path, 'r') as file:
            overview_metrics = yaml.safe_load(file)
    except FileNotFoundError as e:
        error_message = f"Resemblance metrics file not found: {overview_resemblance_path}"
        send_callback_error(callback_url, session_key, error_message, 404) # 404 Not Found
        return {
             'message': error_message,
             'session_key': session_key,
             'status_code': 404
        }
    except yaml.YAMLError as e:
        error_message = f"Error loading resemblance metrics YAML from {overview_resemblance_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }
    except Exception as e:
        error_message = f"Unexpected error loading resemblance metrics from {overview_resemblance_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }


    # Load Utility Metrics
    overview_utility_metrics_path = os.path.join(app_dir, 'utility', 'overview_utility_metrics.yaml')
    try:
        with open(overview_utility_metrics_path, 'r') as file:
            utility_metrics = yaml.safe_load(file)
    except FileNotFoundError as e:
        error_message = f"Utility metrics file not found: {overview_utility_metrics_path}"
        send_callback_error(callback_url, session_key, error_message, 404) # 404 Not Found
        return {
             'message': error_message,
             'session_key': session_key,
             'status_code': 404
        }
    except yaml.YAMLError as e:
        error_message = f"Error loading utility metrics YAML from {overview_utility_metrics_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }
    except Exception as e:
        error_message = f"Unexpected error loading utility metrics from {overview_utility_metrics_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    time_attributes_path = os.path.join(app_dir, 'static_configs', 'evaluation_config_time_attributes.yaml')
    try:
        with open(time_attributes_path, 'r') as file:
            time_attributes_config = yaml.safe_load(file)
    except FileNotFoundError as e:
        error_message = f"Time attributes config file not found: {time_attributes_path}"
        send_callback_error(callback_url, session_key, error_message, 404) # 404 Not Found
        return {
             'message': error_message,
             'session_key': session_key,
             'status_code': 404
        }
    except yaml.YAMLError as e:
        error_message = f"Error loading time attributes config YAML from {time_attributes_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }
    except Exception as e:
        error_message = f"Unexpected error loading time attributes config from {time_attributes_path}: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }


    print("Data format:", data_format)

    print("Calculating Metrics")
    metrics_result = None 
    try:
        if data_format == 'cross-sectional':
            metrics_result = dispatch_metrics(processed_real_data, processed_synthetic_data, evaluation_config,
                                             metric_functions_cross_sectional)
        elif data_format == 'longitudinal':
            metrics_result = dispatch_metrics(processed_real_data, processed_synthetic_data, evaluation_config,
                                             metric_functions_longitudinal)
        else:
            error_message = f"Data format not supported: {data_format}"
            send_callback_error(callback_url, session_key, error_message, 400) # 400 Bad Request
            return {'message': error_message, 'session_key': session_key, 'status_code': 400}

    except Exception as e:
        error_message = f"Error during metric calculation ({data_format}): {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    if metrics_result is None:
         error_message = "Metric calculation failed to produce a result (metrics_result is None)."
         send_callback_error(callback_url, session_key, error_message, 500)
         return {
             'message': error_message,
             'session_key': session_key,
             'status_code': 500
         }

    # Manage Utility Metrics
    try:
        utility_metrics_result = metrics_result.get('utility', {}) # Use .get() for safety
        enriched_utility_metrics = extract_and_enrich_utility_metrics(utility_metrics, utility_metrics_result)
    except Exception as e:
        error_message = f"Error managing utility metrics: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }


    # Manage Resemblance Metrics
    try:
        empty_config = create_empty_config(attribute_config)
    except Exception as e:
        error_message = f"Error creating empty config: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # organize the metrics by visualization type and group them by visualization type
    try:
        grouped_metrics = group_metrics_by_visualization_type(overview_metrics)
    except Exception as e:
        error_message = f"Error grouping metrics by visualization type: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # Add metrics to config
    try:
        metrics_dict = add_metrics_to_config(empty_config, metrics_result, grouped_metrics)
    except Exception as e:
        error_message = f"Error adding metrics to config: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # Enrich Metrics
    try:
        enriched_metrics = enrich_metrics_with_descriptions(metrics_dict, overview_metrics, add_interpretation=True)
    except Exception as e:
        error_message = f"Error enriching metrics with descriptions: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # Add value differences
    try:
        enriched_metrics = add_value_differences(enriched_metrics)
    except Exception as e:
        error_message = f"Error adding value differences: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # transform time attributes to date
    try:
        enriched_metrics = convert_attributes_to_date(enriched_metrics, time_attributes_config)
    except Exception as e:
        error_message = f"Error converting time attributes to date: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # Combine Utility and resemblance in one dictionary
    try:
       if enriched_metrics is None:
           error_message = "Processing error before combining utility metrics (enriched_metrics is None)."
           send_callback_error(callback_url, session_key, error_message, 500)
           return {
               'message': error_message,
               'session_key': session_key,
               'status_code': 500
           }
       enriched_metrics.update(enriched_utility_metrics)
    except Exception as e:
        error_message = f"Error combining utility and resemblance metrics: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # Add the description of the resemblance utility metric to the enriched dictionary
    try:
        enriched_metrics = add_resembance_description(enriched_metrics, overview_metrics)
    except Exception as e:
        error_message = f"Error adding resemblance description: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    # Add overview to Config
    try:
        enriched_metrics = add_overview_to_config(enriched_metrics)
    except Exception as e:
        error_message = f"Error adding overview to config: {e}"
        send_callback_error(callback_url, session_key, error_message, 500)
        return {
            'message': error_message,
            'session_key': session_key,
            'status_code': 500
        }

    try:
        files = prepare_callback_data(enriched_metrics)
        requests.post(callback_url, files=files, data={'session_key': session_key})
        print("Callback made successfully")

    except requests.exceptions.RequestException as e:
        print(f"Failed to send final results callback to URL {callback_url}: {e}", file=sys.stderr)
        pass 

    except Exception as e:
         print(f"Unexpected error preparing or sending final results callback to URL {callback_url}: {e}", file=sys.stderr)
         pass

    return enriched_metrics


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
        'description': 'Metrics used to evaluate the resemblance and utility of protected data compared to real data.',
        'URL': '/start_evaluation',
        'configurations': {
            'resemblance': {
                'display_name': 'Resemblance Metrics',
                'description': 'Metrics that evaluate the statistical similarity between protected and real data.',
                'options': resemblance_metadata
            },
            'utility': {
                'display_name': 'Utility Metrics',
                'description': 'Metrics that evaluate the utility of protedted data in comparision with the real data.',
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


def send_callback_error(callback_url, session_key, error_message, error_code, error_details=None):
    """
    Sends error messages to the provided callback URL as a JSON payload.

    Args:
        callback_url (str): The client's callback URL.
        session_key (str): Unique session identifier. This will be part of errorDetails.
        error_message (str): The human-readable error message.
        error_code (str): The error code in the format [SOURCE]_[ExceptionTypeCode]_[ExceptionClassCode]_[ExceptionCode].
        error_details (dict, optional): Additional information for the error as JSON.
                                        Defaults to None.
    """
    # Prepare the error_details if not provided
    if error_details is None:
        error_details = {}

    # Add session_key to error_details as it's not a direct field in ErrorRequest
    error_details['sessionKey'] = session_key

    # Prepare the JSON payload according to the ErrorRequest structure
    payload = {
        "type": "about:blank",  # Default value as per ErrorRequest
        "timestamp": datetime.now().isoformat(timespec='milliseconds') + 'Z',  
        "errorCode": error_code,
        "errorMessage": error_message,
        "errorDetails": error_details
    }

    headers = {
        'Content-Type': 'application/json'
    }

    try:
        print(f"Sending error callback to {callback_url} with JSON payload: {json.dumps(payload, indent=2)}")
        response = requests.post(callback_url, headers=headers, json=payload, timeout=5)
        print(f"Response status code: {response.status_code}")
        print(f"Response text: {response.text}")
        response.raise_for_status()
    except requests.exceptions.RequestException as e:
        print(f"Failed to send error to callback URL: {e}")




if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5010)