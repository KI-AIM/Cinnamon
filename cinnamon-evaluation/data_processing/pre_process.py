import numpy as np
import pandas as pd
from typing import Tuple, List, Dict, Any

from data_processing.utils import iso_to_strftime, handle_date_column
from data_processing.utils import BOOLEAN_MAP

pd.set_option('future.no_silent_downcasting', True)


def pre_process_dataframe(dataset: pd.DataFrame, config: List[Dict[str, Any]]) -> Tuple[pd.DataFrame, List[str]]:
    """
    Preprocesses the dataset based on the provided configuration.

    Args:
        dataset: dataset to be processed
        config: Configuration for each column in the dataset

    Returns:
        Processed pandas DataFrame and list of discrete columns
    """
    discrete_values = []
    for column_config in config:
        try:
            column_name = column_config['name']
            column_type = column_config['type']

            # Skip processing if column doesn't exist in the dataset
            if column_name not in dataset.columns:
                continue

            if column_type == 'STRING':
                try:
                    dataset[column_name] = dataset[column_name].astype(str)
                    dataset[column_name] = dataset[column_name].replace(['nan', ''], np.nan)
                    discrete_values.append(column_name)
                except Exception:
                    # If string conversion fails, keep the column as is
                    if column_name not in discrete_values:
                        discrete_values.append(column_name)
                continue

            if column_type == 'BOOLEAN':
                try:
                    dataset[column_name] = dataset[column_name].astype(str)
                    dataset[column_name] = dataset[column_name].replace(['nan', ''], np.nan)
                    dataset[column_name] = dataset[column_name].map(BOOLEAN_MAP)
                    discrete_values.append(column_name)
                except Exception:
                    # If boolean conversion fails, keep the column as is
                    if column_name not in discrete_values:
                        discrete_values.append(column_name)
                continue

            if column_type == 'ID':
                try:
                    dataset.drop(columns=[column_name], inplace=True)
                except Exception:
                    pass
                continue

            if column_type == 'DATE':
                try:
                    date_format = None
                    if 'configurations' in column_config and isinstance(column_config['configurations'], list):
                        for cfg in column_config['configurations']:
                            if cfg.get('dateFormatter') is not None:
                                date_format = cfg.get('dateFormatter')
                                break
                            if cfg.get('dateTimeFormatter') is not None:
                                date_format = cfg.get('dateTimeFormatter')
                                break
                    
                    if date_format is not None:
                        date_format = iso_to_strftime(date_format)
                        handle_date_column(dataset, column_name, date_format)
                    else:
                        # If no format is provided, try to convert to datetime without format
                        dataset[column_name] = pd.to_datetime(dataset[column_name], errors='coerce')
                except Exception:
                    # If date conversion fails, treat as numeric or NaN
                    try:
                        dataset[column_name] = pd.to_numeric(dataset[column_name], errors='coerce')
                    except Exception:
                        pass
                continue

            if column_type in ['DECIMAL', 'INTEGER', 'DATE']:
                try:
                    dataset[column_name] = pd.to_numeric(dataset[column_name], errors='coerce')
                except Exception:
                    pass
                continue
        except Exception:
            # Skip column if any top-level error occurs
            continue

    return dataset, discrete_values
