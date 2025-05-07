import pandas as pd
from typing import List, Dict, Any

from data_processing.utils import iso_to_strftime, parse_to_date_format, adjust_date_within_bounds_post
from data_processing.utils import BOOLEAN_MAP, MISSING_VALUE_STRING, MISSING_BOOLEAN


def post_process_dataframe(df: pd.DataFrame, config: List[Dict[str, Any]], all_missing_values_column: List[str]) -> pd.DataFrame:
    """
    Post-process a DataFrame based on the provided configuration.

    This function replaces missing value indicators with `pd.NA`, converts columns
    to specified data types, and applies custom transformations based on the
    configuration. It also recreates columns that were previously dropped due to
    having 100% missing values.

    Args:
        df (pd.DataFrame): The DataFrame to be post-processed.
        config (List[Dict[str, Any]]): A list of dictionaries containing column configurations.
            Each dictionary should include:
                - name (str): The column name.
                - type (str): The target data type ("STRING", "BOOLEAN", "INTEGER",
                  "DECIMAL", or "DATE").
                - configurations (List[Dict[str, Any]], optional): Additional configurations for
                  specific data types, such as date formatting.
        all_missing_values_column (List[str]): List of columns that were dropped during pre-processing
            due to having 100% missing values.

    Returns:
        pd.DataFrame: The post-processed DataFrame with updated column values and data types.
    """
    # First, recreate all columns that were dropped due to having 100% missing values
    for column_name in all_missing_values_column:
        df[column_name] = pd.NA
    
    # Replace custom missing value indicators with pd.NA
    df = df.replace(MISSING_VALUE_STRING, pd.NA)
    
    # Also replace empty strings and common missing value indicators
    df = df.replace({"": pd.NA, "null": pd.NA, "NULL": pd.NA, "NaN": pd.NA, "nan": pd.NA})

    try:
        for column_config in config:
            column_name = column_config['name']
            column_type = column_config['type']
            
            # Skip columns that don't exist in the dataframe
            if column_name not in df.columns:
                continue

            try:
                if column_type == 'STRING':
                    df[column_name] = df[column_name].astype(dtype='string')
                    continue

                if column_type == 'BOOLEAN':
                    df[column_name] = df[column_name].astype(str)
                    df[column_name] = df[column_name].map(BOOLEAN_MAP).astype('boolean')
                    continue

                if column_type == 'INTEGER':
                    df[column_name] = pd.to_numeric(df[column_name], errors='coerce')
                    df[column_name] = df[column_name].round().astype('Int64')
                    continue

                if column_type == 'DECIMAL':
                    df[column_name] = pd.to_numeric(df[column_name], errors='coerce').astype('Float64')
                    continue

                if column_type == 'DATE':
                    try:
                        df[column_name] = pd.to_numeric(df[column_name], errors='coerce')
                        df[column_name] = df[column_name].apply(adjust_date_within_bounds_post)
                        date_format = next(
                            (cfg.get('dateFormatter') or cfg.get('dateTimeFormatter') 
                             for cfg in column_config.get('configurations', [])),
                            None)
                        if date_format is None:
                            raise ValueError(f"Date format not specified for DATE column '{column_name}'")
                        date_format = iso_to_strftime(date_format)
                        df[column_name] = df[column_name].apply(parse_to_date_format, args=(date_format,))
                        df[column_name] = df[column_name].astype(dtype='string')
                    except Exception as e:
                        raise ValueError(f"Error processing DATE column '{column_name}': {str(e)}")
                    continue
                    
            except Exception as e:
                # Log error and continue with other columns
                print(f"Error processing column '{column_name}': {str(e)}")
                # Keep the column as is to avoid data loss
                
    except Exception as e:
        raise Exception(f"Error during post-processing: {str(e)}")

    return df