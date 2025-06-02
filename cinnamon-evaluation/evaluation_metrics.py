from resemblance.tabular.metrics import (mean, standard_deviation, calculate_variance, calculate_fifth_percentile,
                                         calculate_median, calculate_q1, calculate_q3,
                                         calculate_ninety_fifth_percentile,
                                         skewness, kurtosis, calculate_min, calculate_max,
                                         calculate_density,
                                         calculate_histogram,
                                         calculate_kolmogorov_smirnov, calculate_distinct_values,
                                         calculate_frequencies_plot,
                                         calculate_hellinger_distance,
                                         calculate_columnwise_correlations,
                                         calculate_columnwise_correlations_distance,
                                         visualize_columnwise_correlations,
                                         missing_values_count, 
                                         calculate_mode)

from resemblance.longitudinal.metrics import calculate_observation_length_distribution
from utility.tabular.machine_learning_eval import calculate_machine_learning_utility
from utility.tabular.machine_learning_eval import discriminator_based_evaluation


metric_functions_descriptive = {
    'resemblance': {
        'mean': mean,
        'standard_deviation': standard_deviation,
        'variance': calculate_variance,
        #'skewness': skewness,
        'mode': calculate_mode,
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
        'mode': calculate_mode,
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
        'calculate_columnwise_correlations': calculate_columnwise_correlations,
        'calculate_columnwise_correlations_distance': calculate_columnwise_correlations_distance,
        'missing_values_count': missing_values_count,
        'visualize_columnwise_correlations': visualize_columnwise_correlations
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
        'observation_length_distribution': calculate_observation_length_distribution,
        'missing_values_count': missing_values_count, 
        'mode': calculate_mode,
    },
    'utility': {}
}
