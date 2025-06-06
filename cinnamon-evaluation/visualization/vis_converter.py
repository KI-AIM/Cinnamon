from data_processing.post_process import transform_in_iso_datetime, transform_in_time_distance


def group_metrics_by_visualization_type(overview_metrics):
    """
    Organizes metrics by visualization type. Returns a dictionary with visualization types as keys and lists of
    function names as values.

    Args:
        overview_metrics (dict): A dictionary containing the metrics for each visualization type.

    Returns:
        dict: A dictionary with visualization types as keys and lists of function names as values.
    """
    grouped_metrics = {}
    category_key = next(iter(overview_metrics))
    metrics_config = overview_metrics[category_key]['metrics']

    # Loop through each metric configuration
    for metric_info in metrics_config:
        visualization_type = metric_info['visualization_type']
        function_name = metric_info['function_name']

        # Initialize the visualization type list if not already present
        if visualization_type not in grouped_metrics:
            grouped_metrics[visualization_type] = []

        grouped_metrics[visualization_type].append(function_name)

    return grouped_metrics


def create_empty_config(attribute_config):
    """
    Creates an empty configuration for resemblance and utility based on the attribute configuration.

    Args:
        attribute_config (dict): A dictionary containing 'configurations', which is a list of attribute metadata.

    Returns:
        dict: A dictionary with empty resemblance and utility configurations for each attribute.
    """
    if isinstance(attribute_config, dict) and 'configurations' in attribute_config:
        attribute_list = attribute_config['configurations']
    else:
        raise ValueError("attribute_config should be a dictionary with a key 'configurations' containing a list.")

    config = {
        "resemblance": [],
        "utility": []
    }

    for attr in attribute_list:
        if all(key in attr for key in ['name', 'index', 'type', 'scale']):
            config_entry = {
                'attribute_information': {
                    'name': attr['name'],
                    'index': attr['index'],
                    'type': attr['type'],
                    'scale': attr['scale']
                },
                'important_metrics': {},
                'details': {},
                'plot': {}
            }
            config['resemblance'].append(config_entry)
        else:
            raise ValueError(f"Attribute {attr} is missing required keys: 'name', 'index', 'type', 'scale'.")

    return config


def add_metrics_to_config(config, metrics, metric_categories):
    """
    Populates the config dictionary with metrics based on the attribute names and metric categories.

    Args:
        config (dict): The target configuration dictionary (e.g., resemblance structure).
        metrics (dict): A dictionary containing metric values (e.g., resemblance->metric->real/synthetic->attribute:value).
        metric_categories (dict): A dictionary mapping metric names to their categories (e.g., important_metrics, details,
            etc.).

    Returns:
        dict: The updated configuration dictionary with metrics added.
    """
    for attribute_entry in config["resemblance"]:
        attribute_name = attribute_entry["attribute_information"]["name"]
        for metric_category, metric_list in metric_categories.items():
            for metric in metric_list:
                if metric in metrics["resemblance"]:
                    real_values = metrics["resemblance"][metric].get("real", {})
                    synthetic_values = metrics["resemblance"][metric].get("synthetic", {})

                    if attribute_name in real_values or attribute_name in synthetic_values:
                        attribute_entry[metric_category][metric] = {
                            "real": real_values.get(attribute_name),
                            "synthetic": synthetic_values.get(attribute_name)
                        }

    return config


def enrich_metrics_with_descriptions(metrics_dict, yaml_config, add_interpretation=True):
    """
    Adds descriptions, display names, and optionally interpretations to metrics from YAML configuration.

    Args:
        metrics_dict (dict): Dictionary containing metrics results
        yaml_config (dict): YAML configuration containing descriptions and interpretations
        add_interpretation (bool): Whether to add interpretation field (default: True)

    Returns:
        dict: Enriched metrics dictionary
    """
    # Create a lookup dictionary for quick access to metric information
    metric_info = {}
    for metric in yaml_config['resemblance']['metrics']:
        metric_info[metric['function_name']] = {
            'display_name': metric['display_name'],
            'description': metric['description'],
            'interpretation': metric.get('interpretation', '')
        }

    def enrich_section(section):
        enriched_section = {}
        for metric_name, metric_value in section.items():
            enriched_metric = {
                'value': metric_value,
                'display_name': metric_info.get(metric_name, {}).get('display_name', metric_name),
                'description': metric_info.get(metric_name, {}).get('description', 'No description available')
            }
            if add_interpretation:
                enriched_metric['interpretation'] = metric_info.get(metric_name, {}).get('interpretation',
                                                                                         'No interpretation available')

            enriched_section[metric_name] = enriched_metric
        return enriched_section

    # Process the entire metrics dictionary
    enriched_dict = {}
    for category, content in metrics_dict.items():
        enriched_dict[category] = []

        for item in content:
            enriched_item = {}
            for key, value in item.items():
                if key in ['important_metrics', 'details', 'display_name']:
                    enriched_item[key] = enrich_section(value)
                else:
                    enriched_item[key] = value
            enriched_dict[category].append(enriched_item)

    return enriched_dict


def add_value_differences(metrics_dict):
    """
    Calculates differences between real and synthetic values, adds color coding based on percentage differences.

    Color index rules:
    1: 0% - 10%
    2: 10% - 20%
    3: 20% - 30%
    ...
    10: 90% - 100%

    Args:
        metrics_dict (dict): Dictionary containing the metrics to be displayed.

    Returns:
        dict: Dictionary with the added value differences.
    """

    def get_color_index(percentage_diff):
        """Determine color index based on percentage difference (1-10)."""
        # Ensure percentage is between 0 and 100
        capped_diff = min(100, max(0, percentage_diff))
        # Calculate index (1-10)
        index = min(10, max(1, int(capped_diff / 10) + 1))
        return index

    def calculate_differences(real, synthetic):
        """Calculate absolute and percentage differences between real and synthetic values."""
        if isinstance(real, (int, float)) and isinstance(synthetic, (int, float)):
            abs_diff = abs(real - synthetic)
            
            is_distance_metric = abs(real) == 0 and 0 <= synthetic <= 1
            
            if is_distance_metric:
                normalized_diff = synthetic
            else:
                if real == 0 and synthetic == 0:
                    normalized_diff = 0
                elif (real > 0 and synthetic < 0) or (real < 0 and synthetic > 0):
                    normalized_diff = abs_diff / (abs(real) + abs(synthetic))
                elif real < 0 and synthetic < 0:
                    abs_real = abs(real)
                    abs_synthetic = abs(synthetic)
                    normalized_diff = abs(abs_real - abs_synthetic) / (abs_real + abs_synthetic)
                else:
                    normalized_diff = abs_diff / (abs(real) + abs(synthetic))
            
            pct_diff = normalized_diff * 100
                
            return {
                'absolute': abs_diff,
                'percentage': pct_diff,
                'color_index': get_color_index(pct_diff)
            }
        else:
            return {
                'absolute': 'NA',
                'percentage': 'NA',
                'color_index': 'NA'
            }
        
    def process_metric(metric_data):
        """Process a single metric."""
        if not isinstance(metric_data, dict):
            return None

        if 'value' in metric_data:
            real_val = metric_data['value'].get('real')
            synthetic_val = metric_data['value'].get('synthetic')
        else:
            return None

        if real_val is None or synthetic_val is None:
            return None

        processed_metric = {
            'values': {
                'real': real_val,
                'synthetic': synthetic_val
            }
        }

        differences = calculate_differences(real_val, synthetic_val)
        if differences:
            processed_metric['difference'] = differences

        processed_metric['description'] = metric_data.get('description', 'No description available')
        processed_metric['interpretation'] = metric_data.get('interpretation', 'No interpretation available')
        processed_metric['display_name'] = metric_data.get('display_name', 'No display name available')

        return processed_metric

    def process_metrics_section(section_data):
        """Process a section of metrics (important_metrics or details)."""
        if not section_data:
            return {}

        processed_section = {}
        for metric_name, metric_data in section_data.items():
            processed_metric = process_metric(metric_data)
            if processed_metric:
                processed_section[metric_name] = processed_metric
        return processed_section

    result = {'resemblance': []}
    for item in metrics_dict['resemblance']:
        processed_item = {
            'attribute_information': item['attribute_information']
        }

        if 'important_metrics' in item:
            processed_item['important_metrics'] = process_metrics_section(item.get('important_metrics', {}))
        if 'details' in item:
            processed_item['details'] = process_metrics_section(item.get('details', {}))
        if 'plot' in item:
            processed_item['plot'] = item['plot']

        result['resemblance'].append(processed_item)

    return result


def remove_synthetic_and_difference(config_dict):
    """
    Removes the synthetic and difference keys from the config dictionary.

    Args:
        config_dict (dict): The dictionary to remove the synthetic and difference keys from.

    Returns:
        dict: The updated dictionary with the synthetic and difference keys removed.
    """
    if 'resemblance' in config_dict:
        for item in config_dict['resemblance']:
            if 'important_metrics' in item:
                for metric in item['important_metrics'].values():
                    if 'values' in metric:
                        if 'synthetic' in metric['values']:
                            del metric['values']['synthetic']
                    if 'difference' in metric:
                        del metric['difference']

            if 'details' in item:
                for detail in item['details'].values():
                    if 'values' in detail:
                        if 'synthetic' in detail['values']:
                            del detail['values']['synthetic']
                    if 'difference' in detail:
                        del detail['difference']

            if 'plot' in item:
                for plot_type in item['plot'].values():
                    if 'synthetic' in plot_type:
                        del plot_type['synthetic']

    return config_dict


def convert_important_metrics_to_date(metric, transformation_map, attr_type):
    """
    Converts the important_metrics values to the specified date format.

    Args:
        metric (dict): The metric dictionary to convert the important_metrics values.
        transformation_map (dict): The mapping of important_metrics keys to the corresponding transformation function.
        attr_type (str): The attribute type of the metric.

    Returns:
        dict: The updated metric dictionary with the important_metrics values converted to the specified date format.
    """
    for metric_name, metric_data in metric.get('important_metrics', {}).items():
        if metric_name in transformation_map:
            transform_func = globals()[transformation_map[metric_name]]
            if isinstance(metric_data, dict):
                if 'values' in metric_data:
                    if 'real' in metric_data['values']:
                        metric_data['values']['real'] = transform_func(metric_data['values']['real'], attr_type)
                    if 'synthetic' in metric_data['values']:
                        metric_data['values']['synthetic'] = transform_func(
                            metric_data['values']['synthetic'], attr_type)
                if 'difference' in metric_data and isinstance(metric_data['difference'], dict):
                    if 'absolute' in metric_data['difference']:
                        metric_data['difference']['absolute'] = transform_in_time_distance(
                            metric_data['difference']['absolute'])
    return metric


def convert_details_to_date(metric, transformation_map, attr_type):
    """
    Converts the details values to the specified date format.

    Args:
        metric (dict): The metric dictionary to convert the details values.
        transformation_map (dict): The mapping of details keys to the corresponding transformation function.
        attr_type (str): The attribute type of the metric.

    Returns:
        dict: The updated metric dictionary with the details values converted to the specified date format.
    """
    if 'details' in metric:
        for detail_name, detail_data in metric['details'].items():
            if detail_name in transformation_map:
                transform_func = globals()[transformation_map[detail_name]]
                if isinstance(detail_data, dict):
                    if 'values' in detail_data:
                        if 'real' in detail_data['values']:
                            detail_data['values']['real'] = transform_func(detail_data['values']['real'], attr_type)
                        if 'synthetic' in detail_data['values']:
                            detail_data['values']['synthetic'] = transform_func(
                                detail_data['values']['synthetic'], attr_type)
                    if 'difference' in detail_data and isinstance(detail_data['difference'], dict):
                        if 'absolute' in detail_data['difference']:
                            detail_data['difference']['absolute'] = transform_in_time_distance(
                                detail_data['difference']['absolute'])
    return metric


def convert_plots_to_date(metric, transformation_map, attr_type):
    """
    Converts the plots values to the specified date format.

    Args:
        metric (dict): The metric dictionary to convert the plots values.
        transformation_map (dict): The mapping of plots keys to the corresponding transformation function.
        attr_type (str): The attribute type of the metric.

    Returns:
        dict: The updated metric dictionary with the plots values converted to the specified date format.
    """
    if 'plot' in metric:
        for plot_type, plot_data in metric['plot'].items():
            if isinstance(plot_data, dict):
                if plot_type == 'density':
                    if 'real' in plot_data and 'x_values' in plot_data['real']:
                        transform_func = globals()[transformation_map.get('x_values', 'transform_in_iso_datetime')]
                        plot_data['real']['x_values'] = [
                            transform_func(x, attr_type) for x in plot_data['real']['x_values']
                        ]

                    if 'synthetic' in plot_data and 'x_values' in plot_data['synthetic']:
                        transform_func = globals()[transformation_map.get('x_values', 'transform_in_iso_datetime')]
                        plot_data['synthetic']['x_values'] = [
                            transform_func(x, attr_type) for x in plot_data['synthetic']['x_values']
                        ]
                if plot_type == 'histogram':
                    if 'real' in plot_data and 'frequencies' in plot_data['real']:
                        transform_func = globals()[transformation_map.get('frequencies', 'transform_in_iso_datetime')]
                        for freq in plot_data['real']['frequencies']:
                            start, end = map(float, freq['label'].split('|'))
                            freq['label'] = f"{transform_func(start, attr_type)} | {transform_func(end, attr_type)}"

                    if 'synthetic' in plot_data and 'frequencies' in plot_data['synthetic']:
                        transform_func = globals()[transformation_map.get('frequencies', 'transform_in_iso_datetime')]
                        for freq in plot_data['synthetic']['frequencies']:
                            start, end = map(float, freq['label'].split('|'))
                            freq['label'] = f"{transform_func(start, attr_type)} | {transform_func(end, attr_type)}"

    return metric


def convert_attributes_to_date(enriched_metrics, time_attributes_config):
    """
    Converts the metrics to date format based on the configuration. This is done by converting the important metrics
    to date format, converting the details to date format, and converting the plots to date format.

    Args:
        enriched_metrics (dict): The enriched metrics dictionary.
        time_attributes_config (dict): The time attributes configuration dictionary.

    Returns:
        dict: The enriched metrics dictionary with the metrics converted to date format.
    """
    modified_metrics = enriched_metrics.copy()
    transformation_map = time_attributes_config['evaluation_configuration']['resemblance']

    for i, metric in enumerate(modified_metrics['resemblance']):
        if isinstance(metric, dict):
            attr_type = metric.get('attribute_information', {}).get('type')
            if attr_type == 'DATE' or attr_type == 'DATETIME':
                modified_metric = convert_important_metrics_to_date(metric, transformation_map, attr_type)
                modified_metric = convert_details_to_date(modified_metric, transformation_map, attr_type)
                modified_metric = convert_plots_to_date(modified_metric, transformation_map, attr_type)
                modified_metrics['resemblance'][i] = modified_metric

    return modified_metrics


def extract_and_enrich_utility_metrics(utility_metrics, calculated_metrics):
    """
    Extracts and enriches the utility metrics from the calculated_metrics dictionary.

    Args:
        utility_metrics (dict): The utility metrics dictionary.
        calculated_metrics (dict): The calculated metrics dictionary.

    Returns:
        dict: The enriched utility metrics dictionary.
    """
    enriched_metrics = {'utility': {}}
    enriched_metrics['utility']['display_name'] = utility_metrics['utility']['display_name']
    enriched_metrics['utility']['description'] = utility_metrics['utility']['description']
    enriched_metrics['utility']['methods'] = []  # Initialize methods list

    # Create a mapping of function_name to metric info for easier lookup
    metric_info_map = {
        metric['function_name']: {
            'display_name': metric['display_name'],
            'description': metric['description'],
            'interpretation': metric['interpretation']
        }
        for metric in utility_metrics['utility']['metrics']
    }

    # Enrich the metrics with the extracted information
    for metric_name, metric_data in calculated_metrics.items():
        if metric_name in metric_info_map:
            method = {
                metric_name: {  # Use metric_name as the key
                    'display_name': metric_info_map[metric_name]['display_name'],
                    'description': metric_info_map[metric_name]['description'],
                    'interpretation': metric_info_map[metric_name]['interpretation'],
                    **metric_data
                }
            }
            enriched_metrics['utility']['methods'].append(method)

    return enriched_metrics


def add_resembance_description(enriched_dict, yaml_config):
    """
    Add the description of the resemblance utility metric to the enriched dictionary.

    :param enriched_dict: The enriched dictionary containing the metrics.
    :param yaml_config: The YAML configuration file containing the metrics.
    :return: The enriched dictionary with the description added.
    """
    # Get the description of the resemblance
    resemblance_description = yaml_config['resemblance'].get('description', "")
    resemblance_display_name = yaml_config['resemblance'].get('display_name', "")

    # Store the original list
    original_list = enriched_dict['resemblance']

    # Create new structure
    enriched_dict['resemblance'] = {
        'description': resemblance_description,
        'display_name': resemblance_display_name,
        'attributes': original_list
    }

    return enriched_dict
