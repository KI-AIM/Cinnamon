import numpy as np
import pandas as pd

from data_processing.utils import iso_to_strftime, handle_date_column
from data_processing.utils import BOOLEAN_MAP

pd.set_option('future.no_silent_downcasting', True)


def pre_process_dataframe(dataset, config):
    """
    Preprocesses the dataset based on the provided configuration.

    Args:
        dataset: dataset to be processed
        config: Configuration for each column in the dataset

    Returns:
        pandas.Dataframe
        list of discrete columns
    """
    discrete_values = []
    for column_config in config:
        column_name = column_config['name']
        column_type = column_config['type']

        if column_type == 'STRING':
            dataset[column_name] = dataset[column_name].astype(str)
            dataset[column_name] = dataset[column_name].replace(['nan', ''], np.nan)
            discrete_values.append(column_name)
            continue

        if column_type == 'BOOLEAN':
            dataset[column_name] = dataset[column_name].astype(str)
            dataset[column_name] = dataset[column_name].replace(['nan', ''], np.nan)
            dataset[column_name] = dataset[column_name].map(BOOLEAN_MAP)
            discrete_values.append(column_name)
            continue

        if column_type == 'ID':
            dataset.drop(columns=[column_name], inplace=True)
            continue

        if column_type == 'DATE':
            date_format = next(
                (cfg.get('dateFormatter') or cfg.get('dateTimeFormatter') for cfg in column_config['configurations']),
                None)
            date_format = iso_to_strftime(date_format)
            handle_date_column(dataset, column_name, date_format)

        if column_type in ['DECIMAL', 'INTEGER', 'DATE']:
            dataset[column_name] = pd.to_numeric(dataset[column_name], errors='coerce')
            continue

    return dataset, discrete_values
