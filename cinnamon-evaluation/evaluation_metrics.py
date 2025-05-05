from resemblance.tabular.metrics import (mean, standard_deviation, calculate_variance, calculate_fifth_percentile,
                                         calculate_median, calculate_q1, calculate_q3,
                                         calculate_ninety_fifth_percentile,
                                         skewness, kurtosis, calculate_min, calculate_max,
                                         calculate_density,
                                         calculate_histogram,
                                         calculate_kolmogorov_smirnov, calculate_distinct_values,
                                         calculate_frequencies_plot,
                                         calculate_hellinger_distance,
                                         pairwise_correlation,
                                         missing_values_count)

from resemblance.longitudinal.metrics import calculate_observation_length_distribution

from resemblance.process_oriented.metrics import (event_distribution, calculate_trace_length_distribution,
                                                  calculate_throughput_time, calculate_end_event_distribution,
                                                  calculate_start_event_distribution,
                                                  calculate_trace_variant_distribution)

from utility.tabular.machine_learning_eval import calculate_machine_learning_utility
from utility.tabular.machine_learning_eval import discriminator_based_evaluation


metric_functions_descriptive = {
    'resemblance': {
        'mean': mean,
        'standard_deviation': standard_deviation,
        'variance': calculate_variance,
        #'skewness': skewness,
        'distinct_values': calculate_distinct_values,
        'fifth_percentile': calculate_fifth_percentile,
        'median': calculate_median,
        'q1': calculate_q1,
        'q3': calculate_q3,
        'ninety_fifth_percentile': calculate_ninety_fifth_percentile,
        #'kurtosis': kurtosis,
        'minimum': calculate_min,
        'maximum': calculate_max,
        'density': calculate_density,
        'histogram': calculate_histogram,
        'frequency_plot': calculate_frequencies_plot,
        'missing_values_count': missing_values_count
    },
    "utility": {}
}


metric_functions_cross_sectional = {
    'resemblance': {
        'mean': mean,
        'standard_deviation': standard_deviation,
        'variance': calculate_variance,
        #'skewness': skewness,
        'distinct_values': calculate_distinct_values,
        'fifth_percentile': calculate_fifth_percentile,
        'median': calculate_median,
        'q1': calculate_q1,
        'q3': calculate_q3,
        'ninety_fifth_percentile': calculate_ninety_fifth_percentile,
        #'kurtosis': kurtosis,
        'minimum': calculate_min,
        'maximum': calculate_max,
        'density': calculate_density,
        'histogram': calculate_histogram,
        'kolmogorov_smirnov': calculate_kolmogorov_smirnov,
        'hellinger_distance': calculate_hellinger_distance,
        'frequency_plot': calculate_frequencies_plot,
        'correlation': pairwise_correlation,
        'missing_values_count': missing_values_count
    },
    "utility": {
        'machine_learning': calculate_machine_learning_utility,
        'discriminator_based_evaluation': discriminator_based_evaluation

    }
}

metric_functions_longitudinal = {
    'resemblance': {
        'mean': mean,
        'standard_deviation': standard_deviation,
        #'skewness': skewness,
        'fifth_percentile': calculate_fifth_percentile,
        'median': calculate_median,
        'q1': calculate_q1,
        'q3': calculate_q3,
        'ninety_fifth_percentile': calculate_ninety_fifth_percentile,
        #'kurtosis': kurtosis,
        'minimum': calculate_min,
        'maximum': calculate_max,
        'histogram': calculate_histogram,
        'kolmogorov_smirnov': calculate_kolmogorov_smirnov,
        'hellinger_distance': calculate_hellinger_distance,
        'frequency_plot': calculate_frequencies_plot,
        'correlation': pairwise_correlation,
        'observation_length_distribution': calculate_observation_length_distribution,
        'missing_values_count': missing_values_count
    },
    'utility': {}
}

metric_functions_process_oriented = {
    'resemblance': {
        'mean': mean,
        'standard_deviation': standard_deviation,
        #'skewness': skewness,
        'fifth_percentile': calculate_fifth_percentile,
        'median': calculate_median,
        'q1': calculate_q1,
        'q3': calculate_q3,
        'ninety_fifth_percentile': calculate_ninety_fifth_percentile,
        #'kurtosis': kurtosis,
        'minimum': calculate_min,
        'maximum': calculate_max,
        'histogram': calculate_histogram,
        'kolmogorov_smirnov': calculate_kolmogorov_smirnov,
        'hellinger_distance': calculate_hellinger_distance,
        'frequency_plot': calculate_frequencies_plot,
        'event_distribution': event_distribution,
        'trace_length_distribution': calculate_trace_length_distribution,
        'throughput_time': calculate_throughput_time,
        'start_event_distribution': calculate_start_event_distribution,
        'end_event_distribution': calculate_end_event_distribution,
        'trace_variant_distribution': calculate_trace_variant_distribution,
        'missing_values_count': missing_values_count
    },
    'utility': {}
}
