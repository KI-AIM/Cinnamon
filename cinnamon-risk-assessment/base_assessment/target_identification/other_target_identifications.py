import pandas as pd


def unique_records(data: pd.DataFrame, rounding: bool = True, decimals: int = 0) -> (int, pd.DataFrame):
    """
    Identifies unique records in a DataFrame even after rounding of continuous data.

    Args:
        data (pd.DataFrame): The input pandas DataFrame.
        rounding (bool): Whether to apply rounding to numerical columns. Default is True.
        decimals (int): The number of decimals to round to, if rounding is enabled. Default is 0.

    Returns:
        (int, pd.DataFrame):
            - The number of unique combinations of continuous attributes.
            - A DataFrame of the unique combinations.
    """

    _continuous_columns = data.select_dtypes(include=['number']).columns
    if rounding:
        data[_continuous_columns] = data[_continuous_columns].round(decimals)
    _unique_records = data.drop_duplicates()
    _number_of_combinations = unique_records.shape[0]

    return _number_of_combinations, _unique_records
