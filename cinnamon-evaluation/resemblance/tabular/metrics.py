from typing import Dict, Union, Optional, Any

import numpy as np
import pandas as pd
from scipy import stats
from scipy.spatial import distance
from scipy.stats import gaussian_kde
import phik 


def validate_numeric_dataframes(real: pd.DataFrame, synthetic: pd.DataFrame) -> tuple:
    """
    Validates two dataframes for numeric comparison operations.

    Args:
        real: Original dataset to validate
        synthetic: Synthetic dataset to validate

    Returns:
        tuple: (real_numeric, synthetic_numeric) Validated numeric dataframes

    Raises:
        TypeError: If inputs aren't DataFrames or numeric conversion fails
        ValueError: If no numeric columns or mismatched columns
    """
    if not all(isinstance(df, pd.DataFrame) for df in [real, synthetic]):
        raise TypeError("Both inputs must be pandas DataFrames")

    try:
        real_numeric = real.select_dtypes(include=['number'])
        synthetic_numeric = synthetic.select_dtypes(include=['number'])
    except TypeError as e:
        raise TypeError(f"Error selecting numeric columns: {str(e)}")

    real_cols = set(real_numeric.columns)
    synthetic_cols = set(synthetic_numeric.columns)
    if real_cols != synthetic_cols:
        raise ValueError(
            f"Mismatched numeric columns. \nReal columns: {real_cols}\nSynthetic columns: {synthetic_cols}")

    return real_numeric, synthetic_numeric


def validate_categorical_dataframes(real: pd.DataFrame, synthetic: pd.DataFrame) -> tuple:
    """
    Validates two dataframes for categorical comparison operations.

    Args:
        real: Original dataset to validate
        synthetic: Synthetic dataset to validate

    Returns:
        tuple: (real_categorical, synthetic_categorical) Validated categorical dataframes

    Raises:
        TypeError: If inputs aren't DataFrames or categorical conversion fails
        ValueError: If no categorical columns or mismatched columns
    """
    if not all(isinstance(df, pd.DataFrame) for df in [real, synthetic]):
        raise TypeError("Both inputs must be pandas DataFrames")

    try:
        real_categorical = real.select_dtypes(include=['object'])
        synthetic_categorical = synthetic.select_dtypes(include=['object'])
    except TypeError as e:
        raise TypeError(f"Error selecting categorical columns: {str(e)}")

    real_cols = set(real_categorical.columns)
    synthetic_cols = set(synthetic_categorical.columns)
    if real_cols != synthetic_cols:
        raise ValueError(
            f"Mismatched categorical columns. \nReal columns: {real_cols}\nSynthetic columns: {synthetic_cols}")

    return real_categorical, synthetic_categorical


def mean(real, synthetic):
    """
    Calculate the mean for each numeric attribute of the two tables. Returns a dictionary with the mean values for each
    attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the mean values for each attribute.
    """
    means = {'real': {}, 'synthetic': {}}

    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)

        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                means['real'][column] = 'NA'
            else:
                means['real'][column] = float(real_col_data.mean())

            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                means['synthetic'][column] = 'NA'
            else:
                means['synthetic'][column] = float(synthetic_col_data.mean())

        return means

    except Exception as e:
        raise Exception(f"Error calculating means: {str(e)}")


def standard_deviation(real, synthetic):
    """
    Calculate the standard deviation for each numeric attribute of the two tables
    and return a dictionary with column names as keys and standard deviations as values.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the standard deviations for each attribute.
    """
    stds = {'real': {}, 'synthetic': {}}

    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)

        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                stds['real'][column] = 'NA'
            else:
                stds['real'][column] = float(real_col_data.std())

            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                stds['synthetic'][column] = 'NA'
            else:
                stds['synthetic'][column] = float(synthetic_col_data.std())

        return stds

    except Exception as e:
        raise Exception(f"Error calculating standard deviations: {str(e)}")


def calculate_variance(real, synthetic):
    """
    Calculate the variance for each numeric attribute of the two tables. Returns a dictionary with the variance values
    for each attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the variance values for each attribute.
    """
    variances = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                variances['real'][column] = 'NA'
            else:
                variances['real'][column] = float(real_col_data.var())
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                variances['synthetic'][column] = 'NA'
            else:
                variances['synthetic'][column] = float(synthetic_col_data.var())
        return variances
    except Exception as e:
        raise Exception(f"Error calculating variances: {str(e)}")


def calculate_fifth_percentile(real, synthetic):
    """
    Calculate 5th percentile for each numeric attribute of the two tables. Returns a dictionary with the 5th percentile
    values for each attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the 5th percentile values for each attribute.
    """
    fifth_percentile_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            fifth_percentile_dict['real'][column] = float(real_numeric[column].quantile(0.05))
            fifth_percentile_dict['synthetic'][column] = float(synthetic_numeric[column].quantile(0.05))
            return fifth_percentile_dict
    except Exception as e:
        raise Exception(f"Error calculating variances: {str(e)}")


def calculate_q1(real, synthetic):
    """
    Calculate the first quartile for each numeric attribute of the two tables. Returns a dictionary with the first
    quartile values for each attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the first quartile values for each attribute.
    """
    q1_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                q1_dict['real'][column] = 'NA'
            else:
                q1_dict['real'][column] = float(real_col_data.quantile(0.25))
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                q1_dict['synthetic'][column] = 'NA'
            else:
                q1_dict['synthetic'][column] = float(synthetic_col_data.quantile(0.25))
        return q1_dict
    except Exception as e:
        raise Exception(f"Error calculating Q1: {str(e)}")


def calculate_median(real, synthetic):
    """
    Calculate the median for each numeric attribute of the two tables. Returns a dictionary with the median values for
    each attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the median values for each attribute.
    """
    median_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                median_dict['real'][column] = 'NA'
            else:
                median_dict['real'][column] = float(real_col_data.quantile(0.5))
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                median_dict['synthetic'][column] = 'NA'
            else:
                median_dict['synthetic'][column] = float(synthetic_col_data.quantile(0.5))
        return median_dict
    except Exception as e:
        raise Exception(f"Error calculating median: {str(e)}")


def calculate_q3(real, synthetic):
    """
    Calculate the third quartile for each numeric attribute of the two tables. Returns a dictionary with the third
    quartile values for each attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the third quartile values for each attribute.
    """
    q3_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                q3_dict['real'][column] = 'NA'
            else:
                q3_dict['real'][column] = float(real_col_data.quantile(0.75))
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                q3_dict['synthetic'][column] = 'NA'
            else:
                q3_dict['synthetic'][column] = float(synthetic_col_data.quantile(0.75))
        return q3_dict
    except Exception as e:
        raise Exception(f"Error calculating Q3: {str(e)}")


def calculate_ninety_fifth_percentile(real, synthetic):
    """
    Calculate the 95th percentile for each numeric attribute of the two tables. Returns a dictionary with the 95th
    percentile values for each attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the 95th percentile values for each attribute.
    """
    ninety_fifth_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                ninety_fifth_dict['real'][column] = 'NA'
            else:
                ninety_fifth_dict['real'][column] = float(real_col_data.quantile(0.95))
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                ninety_fifth_dict['synthetic'][column] = 'NA'
            else:
                ninety_fifth_dict['synthetic'][column] = float(synthetic_col_data.quantile(0.95))
        return ninety_fifth_dict
    except Exception as e:
        raise Exception(f"Error calculating 95th percentile: {str(e)}")


def skewness(real, synthetic):
    """
    Calculate the skewness for each numeric attribute of the two tables. Returns a dictionary with the skewness values
    for each attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the skewness values for each attribute.
    """
    skews = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                skews['real'][column] = 'NA'
            else:
                skews['real'][column] = float(real_col_data.skew())
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                skews['synthetic'][column] = 'NA'
            else:
                skews['synthetic'][column] = float(synthetic_col_data.skew())
        return skews
    except Exception as e:
        raise Exception(f"Error calculating skewness: {str(e)}")


def kurtosis(real, synthetic):
    """
    Calculate the kurtosis for each numeric attribute of the two tables. Returns a dictionary with the kurtosis values
    for each attribute.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the kurtosis values for each attribute.
    """
    kurtosis_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                kurtosis_dict['real'][column] = 'NA'
            else:
                kurtosis_dict['real'][column] = float(real_col_data.kurtosis())
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                kurtosis_dict['synthetic'][column] = 'NA'
            else:
                kurtosis_dict['synthetic'][column] = float(synthetic_col_data.kurtosis())
        return kurtosis_dict
    except Exception as e:
        raise Exception(f"Error calculating kurtosis: {str(e)}")


def calculate_min(real, synthetic):
    """
    Calculates the minimum value for each column in the real and synthetic dataframes.

    Args:
        real (pandas.DataFrame): The real dataframe.
        synthetic (pandas.DataFrame): The synthetic dataframe.

    Returns:
        dict: A dictionary containing the minimum values for each column in the real and synthetic dataframes.
    """
    min_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                min_dict['real'][column] = 'NA'
            else:
                min_dict['real'][column] = float(real_col_data.min())
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                min_dict['synthetic'][column] = 'NA'
            else:
                min_dict['synthetic'][column] = float(synthetic_col_data.min())
        return min_dict
    except Exception as e:
        raise Exception(f"Error calculating minimum values: {str(e)}")


def calculate_max(real, synthetic):
    """
    Calculates the maximum values for each column in the real and synthetic dataframes.

    Args:
        real (pandas.DataFrame): The real dataframe.
        synthetic (pandas.DataFrame): The synthetic dataframe.

    Returns:
        dict: A dictionary containing the maximum values for each column in the real and synthetic dataframes.
    """
    max_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                max_dict['real'][column] = 'NA'
            else:
                max_dict['real'][column] = float(real_col_data.max())
            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                max_dict['synthetic'][column] = 'NA'
            else:
                max_dict['synthetic'][column] = float(synthetic_col_data.max())
        return max_dict
    except Exception as e:
        raise Exception(f"Error calculating maximum values: {str(e)}")


def calculate_kolmogorov_smirnov(real, synthetic):
    """
    Calculates the Kolmogorov-Smirnov test for each column in the real and synthetic dataframes.

    Args:
        real (pandas.DataFrame): The real dataframe.
        synthetic (pandas.DataFrame): The synthetic dataframe.

    Returns:
        dict: A dictionary containing the Kolmogorov-Smirnov test results for each column in the real and synthetic dataframes.
    """
    ks_results = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column].dropna()
            synthetic_col_data = synthetic_numeric[column].dropna()

            if len(real_col_data) == 0 or len(synthetic_col_data) == 0:
                ks_results['real'][column] = 'NA'
                ks_results['synthetic'][column] = 'NA'
            else:
                try:
                    statistic, p_value = stats.ks_2samp(real_col_data, synthetic_col_data)
                    ks_results['real'][column] = 0
                    ks_results['synthetic'][column] = float(statistic)

                except:
                    ks_results['real'][column] = 'NA'
                    ks_results['synthetic'][column] = 'NA'

        return ks_results
    except Exception as e:
        raise Exception(f"Error calculating Kolmogorov-Smirnov test: {str(e)}")


def calculate_density(real, synthetic, num_points=50):
    """
    Calculates the density function for each numeric attribute with error handling and validation.

    Args:
        real (pandas.DataFrame): The real dataframe.
        synthetic (pandas.DataFrame): The synthetic dataframe.
        num_points (int, optional): The number of points to use for the density function. Defaults to 50.

    Returns:
        dict: A dictionary containing the density function results for each column in the real and synthetic dataframes.
    """
    density_results = {'real': {}, 'synthetic': {}}

    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)

        for column in real_numeric.columns:
            real_col_data = real_numeric[column].dropna()
            synthetic_col_data = synthetic_numeric[column].dropna()

            if len(real_col_data) < 2 or len(synthetic_col_data) < 2:
                density_results['real'][column] = {
                    'x_values': 'NA',
                    'density': 'NA',
                    'x_axis': column,
                    'y_axis': "Frequency",
                    'color_index': 0
                }
                density_results['synthetic'][column] = {
                    'x_values': 'NA',
                    'density': 'NA',
                    'x_axis': column,
                    'y_axis': "Frequency",
                    'color_index': 1
                }
                continue

            try:
                min_val = min(real_col_data.min(), synthetic_col_data.min())
                max_val = max(real_col_data.max(), synthetic_col_data.max())

                if min_val == max_val:
                    min_val -= 0.5
                    max_val += 0.5

                x_values = np.linspace(min_val, max_val, num_points)

                try:
                    kde_real = gaussian_kde(real_col_data)
                    density_real = kde_real(x_values)
                except Exception:
                    density_real = np.zeros(num_points)

                try:
                    kde_synthetic = gaussian_kde(synthetic_col_data)
                    density_synthetic = kde_synthetic(x_values)
                except Exception:
                    density_synthetic = np.zeros(num_points)

                density_results['real'][column] = {
                    'x_values': x_values.tolist(),
                    'density': density_real.tolist(),
                    'x_axis': column,
                    'y_axis': "Frequency",
                    'color_index': 0
                }
                density_results['synthetic'][column] = {
                    'x_values': x_values.tolist(),
                    'density': density_synthetic.tolist(),
                    'x_axis': column,
                    'y_axis': "Frequency",
                    'color_index': 1
                }

            except Exception as e:
                density_results['real'][column] = {
                    'x_values': 'Error',
                    'density': f'Error calculating density: {str(e)}',
                    'x_axis': column,
                    'y_axis': "Frequency",
                    'color_index': 0
                }
                density_results['synthetic'][column] = {
                    'x_values': 'Error',
                    'density': f'Error calculating density: {str(e)}',
                    'x_axis': column,
                    'y_axis': "Frequency",
                    'color_index': 1
                }

        return density_results

    except Exception as e:
        raise Exception(f"Error calculating density functions: {str(e)}")


def calculate_histogram(real: pd.DataFrame, synthetic: pd.DataFrame, method: str = 'auto', max_bins: int = 25) -> dict:
    """
    Calculates histogram configurations for numeric columns with error handling, using percentages instead of counts.
    Uses adaptive bin formatting based on data range and scientific notation for extreme values.

    Args:
        real: The real dataframe
        synthetic: The synthetic dataframe
        method: The method to use for calculating the histogram
        max_bins: Maximum number of bins to use

    Returns:
        A dictionary containing the histogram configurations for each column in the real and synthetic dataframes
    """

    def get_color_index(perc_difference: float) -> int:
        capped_diff = min(100, max(0, perc_difference))
        return min(10, max(1, int(capped_diff / 10) + 1))

    def calculate_percentage_diff(orig_value: float, syn_value: float) -> float:
        if orig_value == 0:
            return 100 if syn_value > 0 else 0
        return abs((syn_value - orig_value) / orig_value * 100)
    
    def get_optimal_format(min_val: float, max_val: float, num_bins: int) -> callable:
        """
        Determine optimal formatting based on data range and bin density.
        
        Args:
            min_val: Minimum value in the dataset
            max_val: Maximum value in the dataset
            num_bins: Number of bins in the histogram
            
        Returns:
            A formatting function that accepts a value and returns properly formatted string
        """
        range_span = max_val - min_val
        bin_width = range_span / num_bins if num_bins > 0 else range_span
        
        # Calculate appropriate significant digits based on bin width
        if bin_width == 0 or not np.isfinite(bin_width):  # Handle edge cases
            sig_digits = 2
        else:
            # Use logarithmic scale to determine significant digits
            sig_digits = max(2, min(6, int(-np.log10(bin_width) + 3)))
        
        def format_value(value: float) -> str:
            # Handle infinity and NaN
            if not np.isfinite(value):
                if np.isposinf(value):
                    return "Infinity"
                elif np.isneginf(value):
                    return "-Infinity"
                else:
                    return "NaN"
            
            abs_val = abs(value)
            
            try:
                # Use scientific notation only for:
                # 1. Very small numbers (< 0.0001)
                # 2. Very large numbers (>= 10000)
                
                # Only use scientific notation for very small numbers (< 0.0001)
                very_small = abs_val > 0 and abs_val < 0.0001
                very_large = abs_val >= 10000
                
                if very_small or very_large:
                    # Use only 2 significant digits for scientific notation
                    return f"{value:.2e}"
                
                # For regular numbers, use appropriate decimal places
                if abs_val < 1:
                    # For small numbers, use appropriate decimal places
                    if abs_val < 0.01:
                        decimal_places = 4
                    elif abs_val < 0.1:
                        decimal_places = 3
                    else:
                        decimal_places = 2
                elif abs_val < 10:
                    decimal_places = 2
                elif abs_val < 100:
                    decimal_places = 1
                elif abs_val < 1000:
                    decimal_places = 0
                else:  # For larger numbers that don't need scientific notation
                    decimal_places = 0
                    
                # Round to multiple of 5 for larger values (100-9999)
                if abs_val >= 100 and abs_val < 10000:
                    multiplier = 5 if abs_val < 1000 else 50
                    value = round(value / multiplier) * multiplier
                    decimal_places = 0
                    
                return f"{value:.{decimal_places}f}"
            
            except Exception:
                # Fallback for any unexpected formatting errors
                return str(value)
        
        return format_value

    histogram_results = {'real': {}, 'synthetic': {}}

    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)

        for column in real_numeric.columns:
            try:
                real_data = real_numeric[column].dropna()
                synthetic_data = synthetic_numeric[column].dropna()

                # Skip if not enough data points
                if len(real_data) == 0 or len(synthetic_data) == 0:
                    empty_result = {
                        'frequencies': [],
                        'x_axis': column,
                        'y_axis': "Percentage"
                    }
                    histogram_results['real'][column] = empty_result
                    histogram_results['synthetic'][column] = empty_result
                    continue

                combined_data = np.concatenate([real_data, synthetic_data])
                
                # Filter out infinities and NaNs before calculating bin edges
                finite_data = combined_data[np.isfinite(combined_data)]
                
                # If all values are non-finite, create empty result
                if len(finite_data) == 0:
                    empty_result = {
                        'frequencies': [],
                        'x_axis': column,
                        'y_axis': "Percentage"
                    }
                    histogram_results['real'][column] = empty_result
                    histogram_results['synthetic'][column] = empty_result
                    continue
                
                try:
                    initial_bins = np.histogram_bin_edges(finite_data, bins=method)
                except Exception:
                    # Fallback to linear bins if auto method fails
                    initial_bins = np.linspace(finite_data.min(), finite_data.max(),
                                              min(max_bins, len(np.unique(finite_data))) + 1)

                bins = initial_bins if len(initial_bins) - 1 <= max_bins else \
                    np.linspace(finite_data.min(), finite_data.max(), max_bins + 1)
                
                # Get data range information for adaptive formatting
                data_min = finite_data.min()
                data_max = finite_data.max()
                num_bins = len(bins) - 1
                
                # Get the adaptive formatter function
                format_value = get_optimal_format(data_min, data_max, num_bins)
                
                # Create bin labels with adaptive formatting
                bin_labels = [f"{format_value(bins[i])} | {format_value(bins[i + 1])}"
                             for i in range(len(bins) - 1)]

                # Continue using original bins for histogram calculation to maintain accuracy
                # Handle potential infinity issues by only counting finite values
                real_finite = real_data[np.isfinite(real_data)]
                synthetic_finite = synthetic_data[np.isfinite(synthetic_data)]
                
                real_hist, _ = np.histogram(real_finite, bins=bins)
                synthetic_hist, _ = np.histogram(synthetic_finite, bins=bins)

                real_total = len(real_finite)
                synthetic_total = len(synthetic_finite)
                real_percentages = (real_hist / real_total * 100) if real_total > 0 else real_hist * 0
                synthetic_percentages = (
                    synthetic_hist / synthetic_total * 100) if synthetic_total > 0 else synthetic_hist * 0

                color_indices = {
                    bin_labels[i]: get_color_index(
                        calculate_percentage_diff(real_perc, syn_perc)
                    )
                    for i, (real_perc, syn_perc) in enumerate(zip(real_percentages, synthetic_percentages))
                }

                real_frequencies = [
                    {
                        'label': str(bin_label),
                        'value': float(percentage),
                        'color_index': 0
                    }
                    for bin_label, percentage in zip(bin_labels, real_percentages)
                ]

                synthetic_frequencies = [
                    {
                        'label': str(bin_label),
                        'value': float(percentage),
                        'color_index': color_indices[bin_label]
                    }
                    for bin_label, percentage in zip(bin_labels, synthetic_percentages)
                ]

                histogram_results['real'][column] = {
                    'frequencies': real_frequencies,
                    'x_axis': column,
                    'y_axis': "Percentage"
                }

                histogram_results['synthetic'][column] = {
                    'frequencies': synthetic_frequencies,
                    'x_axis': column,
                    'y_axis': "Percentage"
                }

            except Exception as e:
                raise Exception(f"Error calculating histograms for column {column}: {str(e)}")

        return histogram_results

    except Exception as e:
        raise Exception(f"Error calculating histograms: {str(e)}")


def calculate_frequencies(real, synthetic):
    """
    Calculates frequency percentages for each categorical attribute of the two tables and returns a dictionary with
    column names as keys and percentage data as values.

    Args:
        real (pandas.DataFrame): The real dataframe.
        synthetic (pandas.DataFrame): The synthetic dataframe.

    Returns:
        dict: A dictionary containing the frequency percentages for each column in the real and synthetic dataframes.
    """
    freq_results = {'real': {}, 'synthetic': {}}

    real_categorical = real.select_dtypes(include=['object'])
    synthetic_categorical = synthetic.select_dtypes(include=['object'])

    for column in real_categorical.columns:
        freq_real = (real_categorical[column].value_counts() / len(real_categorical)) * 100
        freq_synthetic = (synthetic_categorical[column].value_counts() / len(synthetic_categorical)) * 100

        freq_results['real'][column] = freq_real.to_dict()
        freq_results['synthetic'][column] = freq_synthetic.to_dict()

    return freq_results


def calculate_hellinger_distance(real, synthetic):
    """
    Calculate the Hellinger distance for each categorical attribute of the two tables and return a dictionary with
    column names as keys and Hellinger distance values.

    Args:
        real (pandas.DataFrame): The real data table.
        synthetic (pandas.DataFrame): The synthetic data table.

    Returns:
        dict: A dictionary with column names as keys and Hellinger distance values.
    """

    hellinger_distances = {'real': {}, 'synthetic': {}}
    freq_results = calculate_frequencies(real, synthetic)

    for column in freq_results['real']:
        freq_real = freq_results['real'][column]
        freq_synthetic = freq_results['synthetic'][column]

        total_real = sum(freq_real.values())
        total_synthetic = sum(freq_synthetic.values())

        prob_real = {k: v / total_real for k, v in freq_real.items()}
        prob_synthetic = {k: v / total_synthetic for k, v in freq_synthetic.items()}

        all_categories = set(prob_real.keys()).union(set(prob_synthetic.keys()))
        vec_real = [prob_real.get(k, 0) for k in all_categories]
        vec_synthetic = [prob_synthetic.get(k, 0) for k in all_categories]

        hellinger_dist = float(distance.euclidean(np.sqrt(vec_real), np.sqrt(vec_synthetic)) / np.sqrt(2))

        hellinger_distances['real'][column] = 0

        hellinger_distances['synthetic'][column] = hellinger_dist

    return hellinger_distances


def calculate_frequencies_plot(real, synthetic):
    """
    Calculate frequency percentages for each categorical attribute of the two tables. Only including the top 25 most
    common categories and grouping the rest as 'other'. Returns a dictionary with column names as keys and percentage
    data as values, including additional keys for axis labels.

    Args:
        real (pandas.DataFrame): The real data table.
        synthetic (pandas.DataFrame): The synthetic data table.

    Returns:
        dict: A dictionary with column names as keys and percentage data as values, including additional keys for
        axis labels.
    """

    def get_color_index(perc_difference):
        capped_diff = min(100, max(0, perc_difference))
        index = min(10, max(1, int(capped_diff / 10) + 1))
        return index

    def calculate_percentage_diff(orig_value, synthetic_value):
        if orig_value == 0:
            return 100 if synthetic_value > 0 else 0
        return abs((synthetic_value - orig_value) / orig_value * 100)

    # Number of categories to keep
    top_categories = 25
    freq_results = {'real': {}, 'synthetic': {}}

    real_categorical = real.select_dtypes(include=['object'])
    synthetic_categorical = synthetic.select_dtypes(include=['object'])

    for column in real_categorical.columns:
        real_counts = real_categorical[column].value_counts()
        synthetic_counts = synthetic_categorical[column].value_counts()
        real_percentages = (real_counts / len(real_categorical)) * 100
        synthetic_percentages = (synthetic_counts / len(synthetic_categorical)) * 100
        real_top = real_percentages.nlargest(top_categories)
        real_other = real_percentages[~real_percentages.index.isin(real_top.index)].sum()
        top_categories_list = list(real_top.index)

        real_frequencies = []
        synthetic_frequencies = []

        for category in top_categories_list:
            real_value = real_percentages[category]
            syn_value = synthetic_percentages.get(category, 0)
            perc_diff = calculate_percentage_diff(real_value, syn_value)
            color_index = get_color_index(perc_diff)

            real_frequencies.append({
                'label': str(category),
                'value': float(real_value),
                'color_index': 0
            })

            synthetic_frequencies.append({
                'label': str(category),
                'value': float(syn_value),
                'color_index': color_index
            })

        if real_other > 0:
            synthetic_other = synthetic_percentages[~synthetic_percentages.index.isin(real_top.index)].sum()
            other_perc_diff = calculate_percentage_diff(real_other, synthetic_other)
            other_color_index = get_color_index(other_perc_diff)

            real_frequencies.append({
                'label': 'Other',
                'value': float(real_other),
                'color_index': 0
            })

            synthetic_frequencies.append({
                'label': 'Other',
                'value': float(synthetic_other),
                'color_index': other_color_index
            })

        freq_results['real'][column] = {
            'frequencies': real_frequencies,
            'x_axis': column,
            'y_axis': "Percentage"
        }
        freq_results['synthetic'][column] = {
            'frequencies': synthetic_frequencies,
            'x_axis': column,
            'y_axis': "Percentage"
        }

    return freq_results


def calculate_mode(real, synthetic):
    """
    Calculates the mode of a categorical column in a dataframe.

    Args:
        real (pandas.DataFrame): The real dataframe.
        synthetic (pandas.DataFrame): The synthetic dataframe.

    Returns:
        dict: A dictionary containing the mode of each categorical column in the dataframes.
    """
    try:
        real_categorical, synthetic_categorical = validate_categorical_dataframes(real, synthetic)
        modes = {'real': {}, 'synthetic': {}}

        for column in real_categorical.columns:
            modes['real'][column] = real_categorical[column].mode().iloc[0] if not real_categorical[
                column].mode().empty else None
            modes['synthetic'][column] = synthetic_categorical[column].mode().iloc[0] if not synthetic_categorical[
                column].mode().empty else None

        return modes
    except Exception as e:
        raise ValueError(f"Error calculating modes: {str(e)}")


def calculate_distinct_values(real, synthetic):
    """
    Calculates the number of distinct values in a categorical column in a dataframe.

    Args:
        real (pandas.DataFrame): The real dataframe.
        synthetic (pandas.DataFrame): The synthetic dataframe.

    Returns:
        dict: A dictionary containing the number of distinct values in each categorical column in the dataframes.
    """
    try:
        real_categorical, synthetic_categorical = validate_categorical_dataframes(real, synthetic)
        return {
            'real': real_categorical.nunique().to_dict(),
            'synthetic': synthetic_categorical.nunique().to_dict()
        }
    except Exception as e:
        raise ValueError(f"Error calculating distinct values: {str(e)}")


def calculate_columnwise_correlations(real: pd.DataFrame, synthetic: pd.DataFrame) -> Dict[str, Dict[str, float]]:
    """
    Calculate a single correlation value for each attribute that represents its
    correlation with all other attributes combined.
    
    Handles columns with constant values by assigning them a correlation of 0.0,
    properly managing both numerical and categorical data types.
    
    Args:
        real (pd.DataFrame): The real data table.
        synthetic (pd.DataFrame): The synthetic data table.
        
    Returns:
        Dict: A nested dictionary with 'real' and 'synthetic' as top-level keys,
              column names as second-level keys, and a single correlation value for each column.
    """
    # Initialize result dictionary
    column_correlations = {'real': {}, 'synthetic': {}}
    
    try:
        # Check for constant columns in both datasets
        real_constant_cols = [col for col in real.columns if real[col].nunique() <= 1]
        synthetic_constant_cols = [col for col in synthetic.columns if synthetic[col].nunique() <= 1]
        
        # For phik correlation calculation, simply exclude constant columns
        # rather than trying to add noise
        real_varying = real.drop(columns=real_constant_cols)
        synthetic_varying = synthetic.drop(columns=synthetic_constant_cols)
        
        # If there are multiple varying columns, calculate phik matrix
        if len(real_varying.columns) > 1:
            real_corr = real_varying.phik_matrix()
        else:
            real_corr = pd.DataFrame()
            
        if len(synthetic_varying.columns) > 1:
            synthetic_corr = synthetic_varying.phik_matrix()
        else:
            synthetic_corr = pd.DataFrame()
        
        # Process real data correlations
        for column in real.columns:
            if column in real_constant_cols:
                # Constant columns have no meaningful correlation with other attributes
                column_correlations['real'][column] = 0.0
            else:
                # Only include this column if it's in our correlation matrix
                if not real_corr.empty and column in real_corr.index:
                    # Get correlations for non-constant columns
                    real_column_corrs = [
                        real_corr.loc[column, other_col] 
                        for other_col in real_varying.columns 
                        if column != other_col
                    ]
                    
                    if real_column_corrs:
                        column_correlations['real'][column] = float(np.mean(real_column_corrs))
                    else:
                        column_correlations['real'][column] = 0.0
                else:
                    # If this column wasn't in our matrix (e.g., only 1 varying column)
                    column_correlations['real'][column] = 0.0
        
        # Process synthetic data correlations
        for column in synthetic.columns:
            if column in synthetic_constant_cols:
                column_correlations['synthetic'][column] = 0.0
            else:
                if not synthetic_corr.empty and column in synthetic_corr.index:
                    synthetic_column_corrs = [
                        synthetic_corr.loc[column, other_col] 
                        for other_col in synthetic_varying.columns 
                        if column != other_col
                    ]
                    
                    if synthetic_column_corrs:
                        column_correlations['synthetic'][column] = float(np.mean(synthetic_column_corrs))
                    else:
                        column_correlations['synthetic'][column] = 0.0
                else:
                    column_correlations['synthetic'][column] = 0.0
                    
        return column_correlations
        
    except Exception as e:
        # If phik calculation fails completely, handle gracefully
        if not column_correlations['real'] or not column_correlations['synthetic']:
            # Fallback: set all correlations to 0
            for column in real.columns:
                column_correlations['real'][column] = 0.0
                
            for column in synthetic.columns:
                column_correlations['synthetic'][column] = 0.0
                
            # Log the error
            print(f"Warning: Using fallback correlation calculation due to error: {str(e)}")
            
        return column_correlations


def visualize_columnwise_correlations(real: pd.DataFrame, synthetic: pd.DataFrame) -> dict:
    """
    Creates a visualization format for column-wise correlations that can be interpreted
    as a heat map in the frontend.

    Args:
        real (pd.DataFrame): The real data table.
        synthetic (pd.DataFrame): The synthetic data table.

    Returns:
        dict: A dictionary containing visualization data for real and synthetic correlations.
            Each dataset has x_values (column names) and correlation_values.
    """
    # Initialize result dictionary
    visualization_data = {'real': {}, 'synthetic': {}}

    try:
        # Check for constant columns in both datasets
        real_constant_cols = [col for col in real.columns if real[col].nunique() <= 1]
        synthetic_constant_cols = [col for col in synthetic.columns if synthetic[col].nunique() <= 1]

        # For phik correlation calculation, simply exclude constant columns
        # rather than trying to add noise
        real_varying = real.drop(columns=real_constant_cols)
        synthetic_varying = synthetic.drop(columns=synthetic_constant_cols)

        # If there are multiple varying columns, calculate phik matrix
        if len(real_varying.columns) > 1:
            real_corr = real_varying.phik_matrix()
        else:
            real_corr = pd.DataFrame()  # Changed to empty DataFrame
            
        if len(synthetic_varying.columns) > 1:
            synthetic_corr = synthetic_varying.phik_matrix()
        else:
            synthetic_corr = pd.DataFrame() # Changed to empty DataFrame

        # Process real data correlations
        for column in real.columns:
            if column in real_constant_cols:
                # Constant columns have no meaningful correlation with other attributes
                visualization_data['real'][column] = {
                    'x_values': [oc for oc in real_varying.columns if oc != column],
                    'correlation_values': [0.0] * (len(real_varying.columns) -1) if len(real_varying.columns) > 1 else [],
                    'x_axis': 'Attributes',
                    'y_axis': 'Correlation Strength',
                }
            else:
                # Only include this column if it's in our correlation matrix
                if not real_corr.empty and column in real_corr.index:
                    # Get correlations for non-constant columns
                    real_column_corrs = [
                        float(real_corr.loc[column, other_col])  # Ensure float
                        for other_col in real_varying.columns
                        if column != other_col
                    ]

                    if real_column_corrs:
                        visualization_data['real'][column] = {
                            'x_values': [oc for oc in real_varying.columns if oc != column],
                            'correlation_values': real_column_corrs,
                            'x_axis': 'Attributes',
                            'y_axis': 'Correlation Strength',
                        }
                    else:
                         visualization_data['real'][column] = {
                            'x_values': [oc for oc in real_varying.columns if oc != column],
                            'correlation_values': [0.0] * (len(real_varying.columns) -1) if len(real_varying.columns) > 1 else [],
                            'x_axis': 'Attributes',
                            'y_axis': 'Correlation Strength',
                        }
                else:
                    # If this column wasn't in our matrix (e.g., only 1 varying column)
                    visualization_data['real'][column] = {
                        'x_values': [oc for oc in real_varying.columns if oc != column],
                        'correlation_values': [0.0] * (len(real_varying.columns) -1) if len(real_varying.columns) > 1 else [],
                        'x_axis': 'Attributes',
                        'y_axis': 'Correlation Strength',
                    }

        # Process synthetic data correlations
        for column in synthetic.columns:
            if column in synthetic_constant_cols:
                visualization_data['synthetic'][column] = {
                    'x_values': [oc for oc in synthetic_varying.columns if oc != column],
                    'correlation_values': [0.0] * (len(synthetic_varying.columns) -1) if len(synthetic_varying.columns) > 1 else [],
                    'x_axis': 'Attributes',
                    'y_axis': 'Correlation Strength',
                }
            else:
                if not synthetic_corr.empty and column in synthetic_corr.index:
                    synthetic_column_corrs = [
                        float(synthetic_corr.loc[column, other_col]) # Ensure float
                        for other_col in synthetic_varying.columns
                        if column != other_col
                    ]

                    if synthetic_column_corrs:
                        visualization_data['synthetic'][column] = {
                            'x_values': [oc for oc in synthetic_varying.columns if oc != column],
                            'correlation_values': synthetic_column_corrs,
                            'x_axis': 'Attributes',
                            'y_axis': 'Correlation Strength',
                        }
                    else:
                        visualization_data['synthetic'][column] = {
                            'x_values': [oc for oc in synthetic_varying.columns if oc != column],
                            'correlation_values': [0.0] * (len(synthetic_varying.columns) -1) if len(synthetic_varying.columns) > 1 else [],
                            'x_axis': 'Attributes',
                            'y_axis': 'Correlation Strength',
                        }
                else:
                    visualization_data['synthetic'][column] = {
                        'x_values': [oc for oc in synthetic_varying.columns if oc != column],
                        'correlation_values': [0.0] * (len(synthetic_varying.columns) -1) if len(synthetic_varying.columns) > 1 else [],
                        'x_axis': 'Attributes',
                        'y_axis': 'Correlation Strength',
                    }
        return visualization_data
        
    except Exception as e:
        if not visualization_data['real'] or not visualization_data['synthetic']:
            # Fallback: set all correlations to 0
            for column in real.columns:
                visualization_data['real'][column] = {
                        'x_values': [oc for oc in synthetic_varying.columns if oc != column],
                        'correlation_values': [0.0] * (len(synthetic_varying.columns) -1) if len(synthetic_varying.columns) > 1 else [],
                        'x_axis': 'Attributes',
                        'y_axis': 'Correlation Strength',
                    }
                
            for column in synthetic.columns:
                visualization_data['synthetic'][column] = {
                        'x_values': [oc for oc in synthetic_varying.columns if oc != column],
                        'correlation_values': [0.0] * (len(synthetic_varying.columns) -1) if len(synthetic_varying.columns) > 1 else [],
                        'x_axis': 'Attributes',
                        'y_axis': 'Correlation Strength',
                    }
                
            print(f"Warning: Using fallback correlation calculation due to error: {str(e)}")
            
        return visualization_data



def missing_values_count(real, synthetic):
    """
    Calculate the count of missing values for each column in real and synthetic DataFrames.

    Args:
        real (pd.DataFrame): The real DataFrame
        synthetic (pd.DataFrame): The synthetic DataFrame

    Returns:
        dict: Dictionary containing missing value counts for both DataFrames
    """
    try:
        return {
            'real': real.isnull().sum().to_dict(),
            'synthetic': synthetic.isnull().sum().to_dict()
        }
    except Exception as e:
        raise ValueError(f"Error calculating missing values count: {str(e)}")

