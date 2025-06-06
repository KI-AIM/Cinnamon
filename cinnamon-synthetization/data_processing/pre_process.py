import numpy as np
import pandas as pd
from typing import Tuple, List, Dict, Any

from data_processing.utils import iso_to_strftime, handle_date_column
from data_processing.utils import BOOLEAN_MAP, MISSING_VALUE_STRING, MISSING_BOOLEAN


def pre_process_dataframe(df: pd.DataFrame, config: List[Dict[str, Any]]) -> Tuple[pd.DataFrame, List[str]]:
    """
    Preprocess a df based on the provided configuration.

    This function applies column-specific preprocessing steps such as type
    conversion, handling missing values, and dropping unnecessary columns.
    It also identifies and removes columns with 100% missing values.

    Args:
        df (pd.DataFrame): The df to be preprocessed.
        config (List[Dict[str, Any]]): A list of dictionaries containing column configurations.
            Each dictionary should include:
                - name (str): The column name.
                - type (str): The target data type or category of the column
                  ("STRING", "BOOLEAN", "ID", "DATE", "DECIMAL", or "INTEGER").
                - configurations (List[Dict[str, Any]], optional): Additional configurations for
                  specific column types, such as date formatting.

    Returns:
        Tuple[pd.DataFrame, List[str]]:
            - pd.DataFrame: The preprocessed df with updated column values and data types.
            - List[str]: A list of column names that had 100% missing values and were dropped.
            
    Raises:
        ValueError: If a required column is missing or if column type is invalid.
        TypeError: If data type conversion fails.
        Exception: For other unexpected preprocessing errors.
    """
    all_missing_values_column = []
    
    # Check for columns with 100% missing values
    for column in df.columns:
        if df[column].isna().all():
            all_missing_values_column.append(column)
    
    # Drop columns with 100% missing values
    if all_missing_values_column:
        df = df.drop(columns=all_missing_values_column)
        print(f"Dropped columns with 100% missing values: {all_missing_values_column}")
    
    for column_config in config:
        column_name = column_config['name']
        column_type = column_config['type']
        
        # Skip processing if column was dropped due to all missing values
        if column_name in all_missing_values_column:
            continue
            
        # Check if column exists in dataframe
        if column_name not in df.columns:
            raise ValueError(f"Column '{column_name}' specified in config does not exist in the dataframe")

        try:
            if column_type == 'STRING':
                df[column_name] = df[column_name].astype(str)
                df[column_name] = df[column_name].replace(['nan', ''], MISSING_VALUE_STRING)
                continue

            if column_type == 'BOOLEAN':
                df[column_name] = df[column_name].astype(str)
                df[column_name] = df[column_name].replace(['nan', ''], MISSING_BOOLEAN)
                df[column_name] = df[column_name].map(BOOLEAN_MAP)
                continue

            if column_type == 'DATE':
                try:
                    date_format = next(
                        (cfg.get('dateFormatter') or cfg.get('dateTimeFormatter') for cfg in column_config.get('configurations', [])),
                        None)
                    if date_format is None:
                        raise ValueError(f"Date format not specified for DATE column '{column_name}'")
                    date_format = iso_to_strftime(date_format)
                    handle_date_column(df, column_name, date_format)
                    
                    # Check if date values are imputed
                    if df[column_name].isna().any():
                        column_mean = df[column_name].mean()
                        df[column_name].fillna(column_mean, inplace=True)
                except Exception as e:
                    raise ValueError(f"Error processing DATE column '{column_name}': {str(e)}")
                continue

            if column_type == 'DECIMAL':
                try:
                    df[column_name] = pd.to_numeric(df[column_name], errors='coerce')
                    column_mean = df[column_name].mean()
                    if pd.isna(column_mean):
                        raise ValueError(f"Cannot calculate mean for DECIMAL column '{column_name}', all values are non-numeric")
                    df[column_name].fillna(column_mean, inplace=True)
                except Exception as e:
                    raise TypeError(f"Error converting '{column_name}' to DECIMAL: {str(e)}")
                continue

            if column_type == 'INTEGER':
                try:
                    df[column_name] = pd.to_numeric(df[column_name], errors='coerce')
                    column_mean = df[column_name].mean()
                    if pd.isna(column_mean):
                        raise ValueError(f"Cannot calculate mean for INTEGER column '{column_name}', all values are non-numeric")
                    df[column_name].fillna(round(column_mean), inplace=True)
                    df[column_name] = df[column_name].astype(float).astype(int)  # Use regular int instead of Int64
                except Exception as e:
                    raise TypeError(f"Error converting '{column_name}' to INTEGER: {str(e)}")
                continue
                
            # If column type is not recognized
            raise ValueError(f"Invalid column type '{column_type}' for column '{column_name}'")
            
        except Exception as e:
            # Catch-all for unexpected errors during processing of each column
            raise Exception(f"Error processing column '{column_name}': {str(e)}")
    

    return df, all_missing_values_column