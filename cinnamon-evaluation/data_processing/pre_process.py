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
    # Get column names from config
    config_columns = [col_config['name'] for col_config in config]
    
    # Step 1: Merge datasets and save the split point
    split_point = len(real_df)
    combined_df = pd.concat([real_df, synthetic_df], axis=0, ignore_index=True)
    
    # Step 2: Align columns with config
    # Remove columns not in config
    columns_to_keep = [col for col in combined_df.columns if col in config_columns]
    combined_df = combined_df[columns_to_keep]
    
    # Add missing columns from config
    for col_name in config_columns:
        if col_name not in combined_df.columns:
            combined_df[col_name] = np.nan
    
    # Step 3: Transform all columns based on config
    for column_config in config:
        column_name = column_config['name']
        column_type = column_config['type']
        
        # Skip if column doesn't exist
        if column_name not in combined_df.columns:
            continue
            
        if column_type in ['STRING', 'BOOLEAN']:
            # Create a temporary series for conversion
            temp_series = pd.Series(combined_df[column_name])
            try:
                temp_series = temp_series.astype(str)
                temp_series = temp_series.replace(['nan', ''], np.nan)
                combined_df[column_name] = temp_series
            except Exception:
                # If conversion fails, set the entire column to NaN
                combined_df[column_name] = np.nan
                
        elif column_type == 'DATE':
            temp_series = pd.Series(combined_df[column_name])
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
                    # Apply date formatting but ensure NaN for failed conversions
                    try:
                        handle_date_column(combined_df, column_name, date_format)
                    except Exception:
                        combined_df[column_name] = np.nan
                else:
                    # Convert to datetime, coercing errors to NaN
                    combined_df[column_name] = pd.to_datetime(combined_df[column_name], errors='coerce')
            except Exception:
                # If date conversion fails completely, try numeric
                try:
                    combined_df[column_name] = pd.to_numeric(combined_df[column_name], errors='coerce')
                except Exception:
                    # If all conversions fail, set to NaN
                    combined_df[column_name] = np.nan
                    
        elif column_type in ['DECIMAL', 'INTEGER', 'DATE']:
            try:
                # Use to_numeric with coerce to convert invalid values to NaN
                combined_df[column_name] = pd.to_numeric(combined_df[column_name], errors='coerce')
            except Exception:
                # If conversion completely fails, set the entire column to NaN
                combined_df[column_name] = np.nan
    
    # Step 4: Split datasets again
    real_df_processed = combined_df.iloc[:split_point].reset_index(drop=True)
    synthetic_df_processed = combined_df.iloc[split_point:].reset_index(drop=True)
    
    # Verify both datasets have the same columns
    if len(real_df_processed.columns) != len(synthetic_df_processed.columns):
        raise ValueError("Processed datasets have different number of columns")
    
    # Additional check to ensure column names match
    if not real_df_processed.columns.equals(synthetic_df_processed.columns):
        raise ValueError("Processed datasets have different column names")
    
    # Print verification information
    print("Real dataset (processed) - First 5 rows:")
    print(real_df_processed.head().to_string())
    print("\nSynthetic dataset (processed) - First 5 rows:")
    print(synthetic_df_processed.head().to_string())
    print(f"\nDataset shapes - Real: {real_df_processed.shape}, Synthetic: {synthetic_df_processed.shape}")
    print(f"Columns: {real_df_processed.columns.tolist()}")
    
    return real_df_processed, synthetic_df_processed