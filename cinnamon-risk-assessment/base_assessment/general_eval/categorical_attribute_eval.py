import pandas as pd


def rare_categories(column: pd.Series, threshold: float = 0.05) -> (bool, list):
    """
    Identifies rare categories in a pandas Series based on a given threshold.

    Args:
        column (pd.Series): The input pandas Series containing categorical data.
        threshold (float): The frequency threshold to define a rare category. Default is 0.05 (5%).

    Returns:
        (bool, list):
            - A boolean indicating if rare categories exist.
            - A list of names of the rare categories.
    """

    value_counts = column.value_counts(normalize=True)
    rare_categories_list = value_counts[value_counts <= threshold].index.tolist()
    has_rare_categories = len(rare_categories_list) > 0

    return has_rare_categories, rare_categories_list


def number_of_categorical_attributes(data: pd.DataFrame) -> int:
    """
    Counts the number of categorical attributes in a DataFrame.

    Args:
        data (pd.DataFrame): The input pandas DataFrame.

    Returns:
        int: The number of categorical attributes in the DataFrame.
    """

    categorical_columns = data.select_dtypes(include=['object', 'category']).columns
    return len(categorical_columns)


def unique_combinations_of_categorical_attributes(data: pd.DataFrame) -> (int, pd.DataFrame):
    """
    Identifies unique combinations of categorical attributes in a DataFrame.

    Args:
        data (pd.DataFrame): The input pandas DataFrame.

    Returns:
        (int, pd.DataFrame):
            - The number of unique combinations of categorical attributes.
            - A DataFrame of the unique combinations.
    """

    categorical_data = data.select_dtypes(include=['object', 'category'])
    unique_records = categorical_data.drop_duplicates(keep=False)
    number_of_combinations = unique_records.shape[0]

    return number_of_combinations, unique_records


def identify_columns_with_unique_entries(data: pd.DataFrame) -> list:
    """
    Identifies columns with unique entries across all records in a DataFrame.

    Args:
        data (pd.DataFrame): The input pandas DataFrame.

    Returns:
        list: A list of column names where all entries are unique.
    """

    _data = data.select_dtypes(include=['object', 'category'])
    unique_columns = [col for col in _data.columns if _data[col].nunique() == len(_data)]

    return unique_columns
