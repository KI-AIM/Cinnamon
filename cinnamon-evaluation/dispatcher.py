def process_metrics(metric_type, real_data, synthetic_data, config, metric_functions, metric_results):
    """
    Processes the metrics for a specific type (resemblance or utility) and updates the metric_results dictionary.
    """
    metrics = config.get('evaluation_configuration', {}).get(metric_type, {})
    functions = metric_functions.get(metric_type, {})

    if not metrics:
        print(f"No {metric_type} metrics found in the configuration.")
        return

    for metric, params in metrics.items():
        if metric in functions:
            if not params:
                result = functions[metric](real_data, synthetic_data)
                metric_results[metric_type][metric] = result
            else:
                if isinstance(params, dict):
                    result = functions[metric](real_data, synthetic_data, **params)
                    metric_results[metric_type][metric] = result
                else:
                    print(f"Warning: Unexpected parameter format for metric '{metric}'.")
        else:
            print(f"Warning: Metric '{metric}' not found in the functions dictionary and will be skipped.")


def dispatch_metrics(real_data, synthetic_data, config, metric_functions):
    """
    Dispatches the metrics for resemblance and utility by processing them and returning the results.
    """
    metric_results = {
        'resemblance': {},
        'utility': {}
    }

    process_metrics('resemblance', real_data, synthetic_data, config, metric_functions, metric_results)
    process_metrics('utility', real_data, synthetic_data, config, metric_functions, metric_results)

    return metric_results
