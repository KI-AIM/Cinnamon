import pandas as pd

from resemblance.tabular.metrics import extract_categorical_dataframes



def pairwise_correlation(real, synthetic):
    """
    Calculates the pairwise correlation between categorical columns in a dataframe.

    Args:
        real (pandas.DataFrame): The real dataframe.
        synthetic (pandas.DataFrame): The synthetic dataframe.

    Returns:
        dict: A dictionary containing the pairwise correlation between each categorical column in the dataframes.
    """
    try:
        correlation = {'real': {}, 'synthetic': {}}

        real_categorical, synthetic_categorical = extract_categorical_dataframes(real, synthetic)

        real_encoded = pd.get_dummies(real_categorical)
        synthetic_encoded = pd.get_dummies(synthetic_categorical)
        real_encoded, synthetic_encoded = real_encoded.align(synthetic_encoded, join='outer', axis=1, fill_value=0)

        correlation_real = real_encoded.corr()
        correlation_synthetic = synthetic_encoded.corr()
        #correlation_diff = correlation_real - correlation_synthetic # Will not be displayed for now

        correlation['real'] = correlation_real.to_dict 
        correlation['synthetic'] = correlation_synthetic
        print(correlation)

        return correlation

    except Exception as e:
        raise ValueError(f"Error calculating correlations: {str(e)}")
    


def correlation_color_index(): 
    pass