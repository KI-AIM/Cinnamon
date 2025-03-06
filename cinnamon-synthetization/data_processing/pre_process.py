import numpy as np
import pandas as pd

from data_processing.utils import iso_to_strftime, handle_date_column
from data_processing.utils import BOOLEAN_MAP


def pre_process_dataframe(df: pd.DataFrame, config):
    """
        Preprocess a df based on the provided configuration.

        This function applies column-specific preprocessing steps such as type
        conversion, handling missing values, and dropping unnecessary columns.
        It also identifies discrete columns in the df.

        Args:
            df (pd.DataFrame): The df to be preprocessed.
            config (list[dict]): A list of dictionaries containing column configurations.
                Each dictionary should include:
                    - name (str): The column name.
                    - type (str): The target data type or category of the column
                      ("STRING", "BOOLEAN", "ID", "DATE", "DECIMAL", or "INTEGER").
                    - configurations (list[dict], optional): Additional configurations for
                      specific column types, such as date formatting.

        Returns:
            tuple:
                - pd.DataFrame: The preprocessed df with updated column values and data types.
                - list[str]: A list of column names identified as discrete values.

    """
    discrete_values = []
    for column_config in config:
        column_name = column_config['name']
        column_type = column_config['type']

        if column_type == 'STRING':
            df[column_name] = df[column_name].astype(str)
            df[column_name] = df[column_name].replace(['nan', ''], np.nan)
            discrete_values.append(column_name)
            continue

        if column_type == 'BOOLEAN':
            df[column_name] = df[column_name].astype(str)
            df[column_name] = df[column_name].replace(['nan', ''], np.nan)
            df[column_name] = df[column_name].map(BOOLEAN_MAP)
            discrete_values.append(column_name)
            continue

        if column_type == 'ID':
            df.drop(columns=[column_name], inplace=True)
            continue

        if column_type == 'DATE':
            date_format = next(
                (cfg.get('dateFormatter') or cfg.get('dateTimeFormatter') for cfg in column_config['configurations']),
                None)
            date_format = iso_to_strftime(date_format)
            handle_date_column(df, column_name, date_format)

        if column_type in ['DECIMAL', 'DATE']:
            column_mean = df[column_name].mean()
            df[column_name].fillna(column_mean, inplace=True)
            continue

        if column_type == 'INTEGER':
            column_mean = round(df[column_name].mean())
            df[column_name].fillna(column_mean, inplace=True)
            continue

    return df, discrete_values
