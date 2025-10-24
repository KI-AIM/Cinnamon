from typing import Dict, Union, Optional, Any, List, Tuple

import numpy as np
import pandas as pd
from scipy import stats
from scipy.spatial import distance
from scipy.stats import gaussian_kde
import phik 
import math


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


def calculate_fifth_percentile(real: pd.DataFrame, synthetic: pd.DataFrame) -> Dict[str, Dict[str, float]]:
    """
    Calculate 5th percentile for each numeric attribute of the two tables.
    Marks columns that are entirely missing with 'NA'.

    Args:
        real (pandas.DataFrame): The real data.
        synthetic (pandas.DataFrame): The synthetic data.

    Returns:
        dict: A dictionary with the 5th percentile values for each attribute,
              which can be a float or the string 'NA'.
    """
    fifth_percentile_dict = {'real': {}, 'synthetic': {}}
    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)
        for column in real_numeric.columns:
            real_col_data = real_numeric[column]
            if real_col_data.isna().all():
                fifth_percentile_dict['real'][column] = 'NA'
            else:
                fifth_percentile_dict['real'][column] = float(real_col_data.quantile(0.05))

            synthetic_col_data = synthetic_numeric[column]
            if synthetic_col_data.isna().all():
                fifth_percentile_dict['synthetic'][column] = 'NA'
            else:
                fifth_percentile_dict['synthetic'][column] = float(synthetic_col_data.quantile(0.05))

        return fifth_percentile_dict
    except Exception as e:
        raise Exception(f"Error calculating 5th percentile: {str(e)}")

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


def calculate_density(real: pd.DataFrame, synthetic: pd.DataFrame, num_points: int = 50) -> Dict[str, Dict[str, Union[str, List[float]]]]:
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

            len_real = len(real_col_data)
            len_synthetic = len(synthetic_col_data)

            # Determine the range for x_values based on available data
            min_val = None
            max_val = None
            use_real_range = len_real >= 2
            use_synthetic_range = len_synthetic >= 2

            if use_real_range and use_synthetic_range:
                min_val = min(real_col_data.min(), synthetic_col_data.min())
                max_val = max(real_col_data.max(), synthetic_col_data.max())
            elif use_real_range:
                min_val = real_col_data.min()
                max_val = real_col_data.max()
            elif use_synthetic_range:
                min_val = synthetic_col_data.min()
                max_val = synthetic_col_data.max()
            else:
                # Neither has sufficient data, use a default range
                min_val = 0.0
                max_val = 1.0

            # Add a buffer if min and max are identical to avoid issues with linspace/KDE
            if min_val == max_val:
                buffer = 0.5 if min_val == 0 else abs(min_val) * 0.1 + 0.5 # Small buffer relative to value or default
                min_val -= buffer
                max_val += buffer


            x_values = np.linspace(min_val, max_val, num_points).tolist()

            # Calculate real density or assign zeros
            if len_real < 2:
                density_real = np.zeros(num_points).tolist()
            else:
                try:
                    kde_real = gaussian_kde(real_col_data)
                    density_real = kde_real(x_values).tolist()
                except Exception:
                    # KDE calculation failed even with >= 2 points (e.g., all same value)
                    density_real = np.zeros(num_points).tolist()

            # Calculate synthetic density or assign zeros
            if len_synthetic < 2:
                density_synthetic = np.zeros(num_points).tolist()
            else:
                try:
                    kde_synthetic = gaussian_kde(synthetic_col_data)
                    density_synthetic = kde_synthetic(x_values).tolist()
                except Exception:
                    # KDE calculation failed even with >= 2 points
                    density_synthetic = np.zeros(num_points).tolist()

            # Assign results for the column
            density_results['real'][column] = {
                'x_values': x_values,
                'density': density_real,
                'x_axis': column,
                'y_axis': "Frequency",
                'color_index': 0
            }
            density_results['synthetic'][column] = {
                'x_values': x_values,
                'density': density_synthetic,
                'x_axis': column,
                'y_axis': "Frequency",
                'color_index': 1
            }

        return density_results

    except Exception as e:
        raise Exception(f"Error calculating density functions: {str(e)}")


def calculate_histogram(real: pd.DataFrame, synthetic: pd.DataFrame, method: str = 'auto', max_bins: int = 15) -> dict:
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
    
    def get_optimal_format(min_val: float, max_val: int, num_bins: int) -> callable:
        range_span = max_val - min_val
        bin_width = range_span / num_bins if num_bins > 0 else range_span

        if bin_width == 0 or not np.isfinite(bin_width):
            decimal_places_for_all = 2
        else:
            if abs(bin_width) < 0.0001 and abs(bin_width) > 0:
                decimal_places_for_all = max(0, int(-math.log10(bin_width) + 2))
                if decimal_places_for_all > 6:
                    decimal_places_for_all = 6
            elif range_span < 10:
                decimal_places_for_all = 2
            elif range_span < 50:
                decimal_places_for_all = 1
            elif range_span < 1000:
                decimal_places_for_all = 0
            else:
                decimal_places_for_all = 0

        use_scientific_notation = range_span > 10000 or abs(min_val) > 100000 or abs(max_val) > 100000

        def format_value(value: float) -> str:
            if not np.isfinite(value):
                if np.isposinf(value):
                    return "Infinity"
                elif np.isneginf(value):
                    return "-Infinity"
                else:
                    return "NaN"

            if use_scientific_notation or (abs(value) > 0 and abs(value) < 0.0001) or abs(value) >= 1000000:
                return f"{value:.2e}"

            return f"{value:.{decimal_places_for_all}f}"

        return format_value

    histogram_results = {'real': {}, 'synthetic': {}}

    try:
        real_numeric, synthetic_numeric = validate_numeric_dataframes(real, synthetic)

        for column in real_numeric.columns:
            try:
                real_data = real_numeric[column].dropna()
                synthetic_data = synthetic_numeric[column].dropna()

                real_finite = real_data[np.isfinite(real_data)]
                synthetic_finite = synthetic_data[np.isfinite(synthetic_data)]

                len_real_finite = len(real_finite)
                len_synthetic_finite = len(synthetic_finite)

                bins = None
                data_min = None
                data_max = None

                if len_real_finite == 0:
                    # Real data is missing or all non-finite, cannot determine bins from real.
                    # Assign empty result for both.
                    empty_result = {
                        'frequencies': [],
                        'x_axis': column,
                        'y_axis': "Percentage"
                    }
                    histogram_results['real'][column] = empty_result
                    histogram_results['synthetic'][column] = empty_result
                    continue # Move to the next column

                elif len_synthetic_finite == 0:
                    # Real data is available and finite, synthetic is missing or all non-finite.
                    # Calculate bins from real data only.
                    data_min = real_finite.min()
                    data_max = real_finite.max()

                    try:
                        initial_bins = np.histogram_bin_edges(real_finite, bins=method)
                    except Exception:
                         initial_bins = np.linspace(data_min, data_max, min(max_bins, len(np.unique(real_finite))) + 1)

                    bins = initial_bins if len(initial_bins) - 1 <= max_bins else \
                         np.linspace(data_min, data_max, max_bins + 1)

                    # Calculate real histogram
                    real_hist, _ = np.histogram(real_finite, bins=bins)
                    real_percentages = (real_hist / len_real_finite * 100) if len_real_finite > 0 else real_hist * 0

                    # Synthetic histogram is all zeros using the same bins
                    synthetic_hist = np.zeros_like(real_hist)
                    synthetic_percentages = np.zeros_like(real_percentages) # Will be zeros


                else:
                    # Both real and synthetic have finite data.
                    # Calculate bins from combined finite data.
                    combined_finite = np.concatenate([real_finite, synthetic_finite])

                    if len(combined_finite) == 0:
                         empty_result = {
                            'frequencies': [],
                            'x_axis': column,
                            'y_axis': "Percentage"
                        }
                         histogram_results['real'][column] = empty_result
                         histogram_results['synthetic'][column] = empty_result
                         continue # Move to the next column


                    data_min = combined_finite.min()
                    data_max = combined_finite.max()

                    try:
                        initial_bins = np.histogram_bin_edges(combined_finite, bins=method)
                    except Exception:
                        initial_bins = np.linspace(data_min, data_max, min(max_bins, len(np.unique(combined_finite))) + 1)


                    bins = initial_bins if len(initial_bins) - 1 <= max_bins else \
                        np.linspace(data_min, data_max, max_bins + 1)

                    # Calculate both real and synthetic histograms using combined bins
                    real_hist, _ = np.histogram(real_finite, bins=bins)
                    synthetic_hist, _ = np.histogram(synthetic_finite, bins=bins)

                    real_percentages = (real_hist / len_real_finite * 100) if len_real_finite > 0 else real_hist * 0
                    synthetic_percentages = (
                        synthetic_hist / len_synthetic_finite * 100) if len_synthetic_finite > 0 else synthetic_hist * 0

                # If bins were calculated (i.e., not the len_real_finite == 0 case)
                if bins is not None:
                    num_bins = len(bins) - 1

                    # Get the adaptive formatter function based on the data range used for bins
                    format_value = get_optimal_format(data_min, data_max, num_bins)

                    # Create bin labels with adaptive formatting
                    bin_labels = [f"{format_value(bins[i])} | {format_value(bins[i + 1])}"
                                  for i in range(len(bins) - 1)]

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
                 # Assign empty result for both on per-column error
                 print(f"Warning: Error calculating histograms for column {column}: {str(e)}. Skipping column.")
                 empty_result = {
                    'frequencies': [],
                    'x_axis': column,
                    'y_axis': "Percentage"
                }
                 histogram_results['real'][column] = empty_result
                 histogram_results['synthetic'][column] = empty_result


        return histogram_results

    except Exception as e:
        # Handle errors that occur outside the per-column calculation loop (e.g. in validate_numeric_dataframes)
        print(f"Error calculating histograms: {str(e)}. Returning empty results.")
        return {'real': {}, 'synthetic': {}}


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


def _compute_correlation_matrix(
    df: pd.DataFrame,
    dataset_name: str
) -> Tuple[List[str], List[List[float]]]:
    """
    Compute a full Phi-K correlation matrix for the provided dataframe.

    Args:
        df: Dataset to analyse.
        dataset_name: Identifier used for warning messages.

    Returns:
        Tuple containing the ordered column labels and a square matrix of correlation values.
    """
    labels: List[str] = list(df.columns)
    size = len(labels)
    if size == 0:
        return labels, []

    constant_cols = [col for col in labels if df[col].nunique(dropna=False) <= 1]
    varying_cols = [col for col in labels if col not in constant_cols]

    full_matrix = pd.DataFrame()
    if len(varying_cols) > 1:
        try:
            full_matrix = df[varying_cols].phik_matrix()
        except Exception as e:
            print(
                f"Warning: Error calculating full phik_matrix for {dataset_name} data: {e}. "
                "Falling back to pairwise correlation calculation."
            )
            full_matrix = pd.DataFrame()

    correlation_matrix: List[List[float]] = [[0.0 for _ in range(size)] for _ in range(size)]

    for row_idx, current_col in enumerate(labels):
        for col_idx, other_col in enumerate(labels):
            if current_col == other_col:
                value = 1.0
            elif current_col in constant_cols or other_col in constant_cols:
                value = 0.0
            else:
                value = np.nan

                if (
                    not full_matrix.empty
                    and current_col in full_matrix.index
                    and other_col in full_matrix.columns
                ):
                    try:
                        value = float(full_matrix.loc[current_col, other_col])
                    except Exception as inner_e:
                        print(
                            f"Warning: Could not read correlation for {current_col}-{other_col} "
                            f"from pre-calculated matrix in {dataset_name}: {inner_e}. Attempting pairwise calculation."
                        )

                if np.isnan(value):
                    try:
                        temp_df = df[[current_col, other_col]]
                        pair_matrix = temp_df.phik_matrix()
                        value = float(pair_matrix.loc[current_col, other_col])
                    except Exception as fallback_e:
                        print(
                            f"Warning: Pairwise Phi-K correlation failed for {current_col}-{other_col} "
                            f"in {dataset_name}: {fallback_e}. Setting correlation to 0.0."
                        )
                        value = 0.0

            if isinstance(value, float) and np.isnan(value):
                value = 0.0

            correlation_matrix[row_idx][col_idx] = float(value)

    return labels, correlation_matrix


def _compute_matrix_distance(
    real_matrix: List[List[float]],
    synthetic_matrix: List[List[float]]
) -> Optional[float]:
    """
    Calculate the normalized average absolute difference between two correlation matrices.

    Args:
        real_matrix: Correlation matrix from real data.
        synthetic_matrix: Correlation matrix from synthetic data.

    Returns:
        float | None: Distance value between 0 and 1 (0 indicates perfect alignment).
    """
    if not real_matrix or not synthetic_matrix:
        return None

    try:
        real_array = np.array(real_matrix, dtype=float)
        synthetic_array = np.array(synthetic_matrix, dtype=float)
    except (TypeError, ValueError):
        return None

    if real_array.shape != synthetic_array.shape:
        raise ValueError("Correlation matrices must share the same shape to compute distance.")

    if real_array.size == 0:
        return 0.0

    diff = np.abs(real_array - synthetic_array)

    if diff.ndim == 2 and diff.shape[0] == diff.shape[1]:
        mask = ~np.eye(diff.shape[0], dtype=bool)
        if mask.sum() == 0:
            distance = 0.0
        else:
            distance = float(diff[mask].mean())
    else:
        distance = float(diff.mean())

    if math.isnan(distance):
        return None

    return float(max(0.0, min(1.0, distance)))


def calculate_full_correlation_matrix(
    processed_real_data: pd.DataFrame,
    processed_synthetic_data: pd.DataFrame
) -> Dict[str, Any]:
    """
    Calculate the full Phi-K correlation matrix for both real and synthetic processed datasets.

    Args:
        processed_real_data: Pre-processed real dataset.
        processed_synthetic_data: Pre-processed synthetic dataset.

    Returns:
        dict: Mapping containing the ordered column labels and correlation matrices for each dataset.
    """
    if not isinstance(processed_real_data, pd.DataFrame) or not isinstance(processed_synthetic_data, pd.DataFrame):
        raise TypeError("Both processed_real_data and processed_synthetic_data must be pandas DataFrames.")

    real_columns = list(processed_real_data.columns)
    synthetic_columns = list(processed_synthetic_data.columns)

    if set(real_columns) != set(synthetic_columns):
        raise ValueError(
            "Real and synthetic processed datasets must contain the same columns to compute correlation matrices."
        )

    if real_columns != synthetic_columns:
        processed_synthetic_data = processed_synthetic_data[real_columns]

    labels_real, real_matrix = _compute_correlation_matrix(processed_real_data, "real")
    labels_syn, synthetic_matrix = _compute_correlation_matrix(processed_synthetic_data, "synthetic")

    if labels_real != labels_syn:
        raise ValueError("Column order mismatch detected after alignment when computing correlation matrices.")

    distance_value = _compute_matrix_distance(real_matrix, synthetic_matrix)

    return {
        "labels": labels_real,
        "real": real_matrix,
        "synthetic": synthetic_matrix,
        "distance": distance_value
    }


def calculate_columnwise_correlations(
    real: pd.DataFrame, synthetic: pd.DataFrame
) -> Dict[str, Dict[str, float]]:
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
    column_correlations = {"real": {}, "synthetic": {}}

    try:
        for df, df_name in [(real, "real"), (synthetic, "synthetic")]:
            constant_cols = [
                col for col in df.columns if df[col].nunique(dropna=False) <= 1
            ]
            # Identify columns that are NOT constant
            varying_cols = [col for col in df.columns if col not in constant_cols]

            # Calculate the full phik matrix once for all varying columns
            # This is the primary optimization for speed
            full_phik_matrix = pd.DataFrame()
            if len(varying_cols) > 1:
                try:
                    full_phik_matrix = df[varying_cols].phik_matrix()
                except Exception as e:
                    print(
                        f"Warning: Error calculating full phik_matrix for {df_name} data: {e}. "
                        "Falling back to pairwise calculation where possible."
                    )
                    full_phik_matrix = pd.DataFrame() # Reset to empty if full matrix calculation fails

            for current_col in df.columns:
                if current_col in constant_cols:
                    column_correlations[df_name][current_col] = 0.0
                else:
                    correlation_values = []
                    for other_col in df.columns:
                        if current_col == other_col:
                            continue # Skip self-correlation

                        if other_col in constant_cols:
                            correlation_values.append(0.0)
                            continue

                        # Prioritize fetching from the pre-calculated full matrix
                        if (
                            not full_phik_matrix.empty
                            and current_col in full_phik_matrix.index
                            and other_col in full_phik_matrix.columns
                        ):
                            try:
                                corr_val = float(
                                    full_phik_matrix.loc[current_col, other_col]
                                )
                                correlation_values.append(corr_val)
                            except Exception as inner_e:
                                # This block handles cases where value might be missing from matrix,
                                # even if index/columns are present (e.g., NaN internal to matrix)
                                print(
                                    f"Warning: Could not get correlation for {current_col}-{other_col} "
                                    f"from pre-calculated matrix in {df_name}: {inner_e}. Falling back."
                                )
                                # Fallback to direct pairwise calculation
                                try:
                                    temp_df = df[[current_col, other_col]]
                                    pair_corr_matrix = temp_df.phik_matrix()
                                    pair_corr_val = float(
                                        pair_corr_matrix.loc[current_col, other_col]
                                    )
                                    correlation_values.append(pair_corr_val)
                                except Exception as fallback_e:
                                    print(
                                        f"Warning: Fallback pairwise calculation failed for {current_col}-{other_col} "
                                        f"in {df_name}: {fallback_e}. Setting to NaN."
                                    )
                                    correlation_values.append('NA')
                        else:
                            try:
                                temp_df = df[[current_col, other_col]]
                                pair_corr_matrix = temp_df.phik_matrix()
                                pair_corr_val = float(
                                    pair_corr_matrix.loc[current_col, other_col]
                                )
                                correlation_values.append(pair_corr_val)
                            except Exception as fallback_e:
                                print(
                                    f"Warning: Direct pairwise calculation failed for {current_col}-{other_col} "
                                    f"in {df_name}: {fallback_e}. Setting to NaN."
                                )
                                correlation_values.append('NA')

                    # Calculate the mean, ignoring NaNs introduced by problematic correlations
                    if correlation_values:
                        mean_val = float(np.mean(correlation_values))
                        if np.isnan(mean_val):
                            column_correlations[df_name][current_col] = 0.0
                        else:
                            column_correlations[df_name][current_col] = mean_val
                    else:
                        column_correlations[df_name][current_col] = 0.0

        return column_correlations

    except Exception as e:
        print(
            f"Warning: Critical error during correlation calculation: {str(e)}. Setting all correlations to 'NA'."
        )
        for df_name, df_data in [("real", real), ("synthetic", synthetic)]:
            for column in df_data.columns:
                column_correlations[df_name][column] = "NA"
        return column_correlations

def calculate_columnwise_correlations_distance(
    real: pd.DataFrame, synthetic: pd.DataFrame
) -> Dict[str, Dict[str, float]]:
    """
    Calculate the mean absolute difference in pairwise correlations for each attribute
    between the real and synthetic datasets, structured for frontend consumption.

    For each attribute (column), the value under the 'synthetic' key represents
    the average of the absolute differences between its pairwise correlations
    (with all other attributes) in the real dataset versus in the synthetic dataset.
    The 'real' key will contain 0.0 for all attributes.

    Handles columns with constant values by assigning them a correlation of 0.0.
    If a pairwise correlation cannot be calculated (e.g., due to constant columns
    or other Phik issues), its difference is treated as 0 for averaging purposes.

    Args:
        real (pd.DataFrame): The real data table.
        synthetic (pd.DataFrame): The synthetic data table.

    Returns:
        Dict[str, Dict[str, float]]: A nested dictionary with 'real' (all 0.0)
                                     and 'synthetic' (mean absolute differences)
                                     as top-level keys, and column names as
                                     second-level keys.
    """
    real_phik_matrix = pd.DataFrame()
    synthetic_phik_matrix = pd.DataFrame()

    real_varying_cols = [col for col in real.columns if real[col].nunique(dropna=False) > 1]
    synthetic_varying_cols = [col for col in synthetic.columns if synthetic[col].nunique(dropna=False) > 1]

    if len(real_varying_cols) > 1:
        try:
            real_phik_matrix = real[real_varying_cols].phik_matrix()
        except Exception as e:
            print(f"Warning: Error calculating full phik_matrix for real data: {e}.")
            real_phik_matrix = pd.DataFrame()

    if len(synthetic_varying_cols) > 1:
        try:
            synthetic_phik_matrix = synthetic[synthetic_varying_cols].phik_matrix()
        except Exception as e:
            print(f"Warning: Error calculating full phik_matrix for synthetic data: {e}.")
            synthetic_phik_matrix = pd.DataFrame()

    final_correlations = {"real": {}, "synthetic": {}}
    all_columns = sorted(list(set(real.columns) | set(synthetic.columns)))

    for current_col in all_columns:
        final_correlations["real"][current_col] = 0.0

        pairwise_differences = []

        for other_col in all_columns:
            if current_col == other_col:
                continue # Skip self-correlation

            real_corr_val = 0.0 # Default if column is constant or not present in real
            synthetic_corr_val = 0.0 # Default if column is constant or not present in synthetic

            if current_col in real.columns and other_col in real.columns:
                if real[current_col].nunique(dropna=False) <= 1 or real[other_col].nunique(dropna=False) <= 1:
                    real_corr_val = 0.0
                elif not real_phik_matrix.empty and current_col in real_phik_matrix.index and other_col in real_phik_matrix.columns:
                    try:
                        real_corr_val = float(real_phik_matrix.loc[current_col, other_col])
                    except ValueError: # phik might return NaN or non-numeric if calculation failed
                        real_corr_val = 0.0 # Treat failed correlation as 0 for difference
                else: # Fallback if full matrix not used or failed
                    try:
                        temp_df = real[[current_col, other_col]]
                        pair_corr_matrix = temp_df.phik_matrix()
                        real_corr_val = float(pair_corr_matrix.loc[current_col, other_col])
                    except Exception:
                        real_corr_val = 0.0 # Treat failed correlation as 0 for difference

            if current_col in synthetic.columns and other_col in synthetic.columns:
                if synthetic[current_col].nunique(dropna=False) <= 1 or synthetic[other_col].nunique(dropna=False) <= 1:
                    synthetic_corr_val = 0.0
                elif not synthetic_phik_matrix.empty and current_col in synthetic_phik_matrix.index and other_col in synthetic_phik_matrix.columns:
                    try:
                        synthetic_corr_val = float(synthetic_phik_matrix.loc[current_col, other_col])
                    except ValueError:
                        synthetic_corr_val = 0.0 
                else: 
                    try:
                        temp_df = synthetic[[current_col, other_col]]
                        pair_corr_matrix = temp_df.phik_matrix()
                        synthetic_corr_val = float(pair_corr_matrix.loc[current_col, other_col])
                    except Exception:
                        synthetic_corr_val = 0.0 

            pairwise_differences.append(abs(real_corr_val - synthetic_corr_val))

        if pairwise_differences:
            valid_diffs = [d for d in pairwise_differences if not np.isnan(d)]
            if valid_diffs:
                mean_abs_difference = float(np.mean(valid_diffs))
            else:
                mean_abs_difference = 0.0 
        else:
            mean_abs_difference = 0.0 

        final_correlations["synthetic"][current_col] = mean_abs_difference

    return final_correlations


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
        matrix_data = calculate_full_correlation_matrix(real, synthetic)
        labels = matrix_data.get("labels", [])
        real_matrix = matrix_data.get("real", [])
        synthetic_matrix = matrix_data.get("synthetic", [])

        for idx, column in enumerate(labels):
            visualization_data['real'][column] = {
                "x_values": labels,
                "correlation_values": real_matrix[idx] if idx < len(real_matrix) else [0.0] * len(labels),
                "x_axis": "Attributes",
                "y_axis": "Correlation Strength",
            }
            visualization_data['synthetic'][column] = {
                "x_values": labels,
                "correlation_values": synthetic_matrix[idx] if idx < len(synthetic_matrix) else [0.0] * len(labels),
                "x_axis": "Attributes",
                "y_axis": "Correlation Strength",
            }

        return visualization_data

    except Exception as e:
        print(f"Warning: Critical error during visualization data generation: {str(e)}. "
              "Returning fallback empty data structure with all correlations as 0.0.")
        for df_data, df_name_key in [(real, 'real'), (synthetic, 'synthetic')]:
            for column in df_data.columns:
                all_columns = list(df_data.columns)
                visualization_data[df_name_key][column] = {
                    'x_values': all_columns,
                    'correlation_values': [0.0] * len(all_columns), # Set all to 0.0 on error
                    'x_axis': 'Attributes',
                    'y_axis': 'Correlation Strength',
                }
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

