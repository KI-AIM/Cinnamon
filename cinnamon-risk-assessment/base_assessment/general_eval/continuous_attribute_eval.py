import pandas as pd


def outlier_records(column: pd.Series, threshold_factor: float = 1.5, rounding: bool = True, decimals: int = 0) -> (bool, list):
    """
    Identifies outlier values in a pandas Series using the IQR method.

    Args:
        column (pd.Series): The input pandas Series containing categorical data.
        threshold_factor (float): The factor with which the IQR is multiplied to define the thresholds.
        rounding (bool): Whether to apply rounding to numerical columns. Default is True.
        decimals (int): The number of decimals to round to, if rounding is enabled. Default is 0.

    Returns:
        (bool, list):
            - A boolean indicating if outliers exist.
            - A list of IDs of the outlier records.
    """

    continuous_data = column.copy()
    if rounding:
        continuous_data = continuous_data.round(decimals)

    Q1 = column.quantile(0.25)
    Q3 = column.quantile(0.75)
    IQR = Q3 - Q1

    # Define the lower and upper bounds
    lower_bound = Q1 - threshold_factor * IQR
    upper_bound = Q3 + threshold_factor * IQR

    lower_outliers = continuous_data[continuous_data < lower_bound]
    upper_outliers = continuous_data[continuous_data > upper_bound]

    outlier_record_IDs = []
    if len(lower_outliers.index) > 0:
        outlier_record_IDs.extend(lower_outliers.index.values)
    if len(upper_outliers.index) > 0:
       outlier_record_IDs.extend(upper_outliers.index.values)

    has_outlier_records = len(outlier_record_IDs) > 0

    return has_outlier_records, outlier_record_IDs


def number_of_continuous_attributes(data: pd.DataFrame) -> int:
    """
    Counts the number of continuous attributes in a DataFrame.

    Args:
        data (pd.DataFrame): The input pandas DataFrame.

    Returns:
        int: The number of continuous attributes in the DataFrame.
    """

    categorical_columns = data.select_dtypes(include=['number']).columns
    return len(categorical_columns)


def unique_combinations_of_continuous_attributes(data: pd.DataFrame, rounding: bool = True, decimals: int = 0) -> (int, pd.DataFrame):
    """
    Identifies unique combinations of continuous attributes in a DataFrame.

    Args:
        data (pd.DataFrame): The input pandas DataFrame.
        rounding (bool): Whether to apply rounding to numerical columns. Default is True.
        decimals (int): The number of decimals to round to, if rounding is enabled. Default is 0.

    Returns:
        (int, pd.DataFrame):
            - The number of unique combinations of continuous attributes.
            - A DataFrame of the unique combinations.
    """

    continuous_data = data.select_dtypes(include=['number']).copy()
    if rounding:
        continuous_data = continuous_data.round(decimals)
    unique_records = continuous_data.drop_duplicates(keep=False)
    number_of_combinations = unique_records.shape[0]

    return number_of_combinations, unique_records


def identify_columns_with_unique_entries(data: pd.DataFrame, rounding: bool = True, decimals: int = 0) -> list:
    """
    Identifies numerical columns with unique entries across all records/in all rows in a DataFrame.
    Optionally applies rounding to numerical columns before checking.

    Args:
        data (pd.DataFrame): The input pandas DataFrame.
        rounding (bool): Whether to apply rounding to numerical columns. Default is True.
        decimals (int): The number of decimals to round to, if rounding is enabled. Default is 0.

    Returns:
        list: A list of numerical column names where all entries are unique.
    """

    numerical_columns = data.select_dtypes(include=['number']).copy()

    if rounding:
        numerical_columns = numerical_columns.round(decimals)

    unique_columns = [col for col in numerical_columns.columns if
                      numerical_columns[col].nunique() == len(numerical_columns)]

    return unique_columns

