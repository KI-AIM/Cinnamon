import pandas as pd


def calculate_observation_length_distribution(real: pd.DataFrame, synthetic: pd.DataFrame, identifier_column: str) -> (
        dict):
    """
    Return the count of each trace length in the real and synthetic data as a dictionary.

    Parameters:
    real: pd.DataFrame - A DataFrame containing the real event data.
    synthetic: pd.DataFrame - A DataFrame containing the synthetic event data.
    case_identifier_column: str - The name of the column in the DataFrame that contains the case identifiers.

    Returns:
    dict: A dictionary with 'real' and 'synthetic' as keys and value counts of trace lengths as values.
    """
    distribution = {'real': {}, 'synthetic': {}}

    # Calculate trace length distribution for real data
    count_real = real[identifier_column].value_counts()
    count_real = count_real.value_counts().sort_index()
    count_real.index = pd.to_numeric(count_real.index)
    distribution['real'] = count_real.to_dict()

    # Calculate trace length distribution for synthetic data
    count_synthetic = synthetic[identifier_column].value_counts()
    count_synthetic = count_synthetic.value_counts().sort_index()
    count_synthetic.index = pd.to_numeric(count_synthetic.index)
    distribution['synthetic'] = count_synthetic.to_dict()

    return distribution



