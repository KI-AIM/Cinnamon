import numpy as np
import pandas as pd
from typing import Tuple, List, Dict, Any

from data_processing.utils import iso_to_strftime, handle_date_column

def preprocess_datasets(real_df: pd.DataFrame, 
                        synthetic_df: pd.DataFrame, 
                        config: List[Dict[str, Any]]) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    Preprocesses real and synthetic datasets by merging, transforming, and splitting.
    
    Args:
        real_df: The real dataset
        synthetic_df: The synthetic dataset
        config: Configuration for each column in the dataset
        
    Returns:
        Tuple containing processed real and synthetic dataframes
    """
    config_columns = [col_config['name'] for col_config in config]

    split_point = len(real_df)
    combined_df = pd.concat([real_df, synthetic_df], axis=0, ignore_index=True)
    
    columns_to_keep = [col for col in combined_df.columns if col in config_columns]
    combined_df = combined_df[columns_to_keep]
    
    for col_name in config_columns:
        if col_name not in combined_df.columns:
            combined_df[col_name] = np.nan
    
    for column_config in config:
        column_name = column_config['name']
        column_type = column_config['type']
        
        if column_name not in combined_df.columns:
            continue
            
        if column_type in ['STRING', 'BOOLEAN']:
            temp_series = pd.Series(combined_df[column_name])
            try:
                temp_series = temp_series.astype(str)
                temp_series = temp_series.replace(['nan', 'NaN', 'None', ''], np.nan) 
                combined_df[column_name] = temp_series 
            except Exception as e:
                print(f"Warning: Error during specific STRING or BOOLEAN handling for column '{column_name}': {e}.")
                combined_df[column_name] = np.nan
                
        if column_type == 'DATE':
            try:
                date_format = next(
                    (cfg.get('dateFormatter') or cfg.get('dateTimeFormatter') for cfg in column_config['configurations']),
                    None)
                date_format = iso_to_strftime(date_format)
                handle_date_column(combined_df, column_name, date_format)
            except Exception as e: 
                print(f"Warning: Error during specific date handling for column '{column_name}': {e}.")
                        
        if column_type in ['DECIMAL', 'INTEGER', 'DATE']: 
            try:
                combined_df[column_name] = pd.to_numeric(combined_df[column_name], errors='coerce')
            except Exception as e:
                print(f"Warning: Error during specific DECIMAL, INTEGER or DATE handling for column '{column_name}': {e}.")
                combined_df[column_name] = np.nan
    
    real_df_processed = combined_df.iloc[:split_point].reset_index(drop=True)
    synthetic_df_processed = combined_df.iloc[split_point:].reset_index(drop=True)
    
    if len(real_df_processed.columns) != len(synthetic_df_processed.columns):
        raise ValueError("Processed datasets have different number of columns")
    
    if not real_df_processed.columns.equals(synthetic_df_processed.columns):
        raise ValueError("Processed datasets have different column names")
    
    print("Real dataset (processed) - First 5 rows:")
    print(real_df_processed.head().to_string())
    print("\nSynthetic dataset (processed) - First 5 rows:")
    print(synthetic_df_processed.head().to_string())
    print(f"\nDataset shapes - Real: {real_df_processed.shape}, Synthetic: {synthetic_df_processed.shape}")
    print(f"Columns: {real_df_processed.columns.tolist()}")
    
    return real_df_processed, synthetic_df_processed