from typing import List

import numpy as np
import pandas as pd


def df_consistency_eval(data_frames: List[pd.DataFrame], try_correction: bool = False):
    """
    Evaluate and optionally correct consistency in a list of DataFrames.
    Checks column names, and data types, and tries correction when specified.

    Args:
        data_frames (List[pd.DataFrame]): List of DataFrames to evaluate.
        try_correction (bool): Flag to attempt correction of mismatches.

    Returns:
        None
    """
    print("\nChecking column name consistency...")
    column_names_equal = check_column_name_consistency(data_frames, try_correction)

    print("\nChecking data type consistency...")
    dtypes_equal = check_dtype_consistency(data_frames, try_correction)

    if column_names_equal and dtypes_equal:
        print("\nAll DataFrames are consistent.")
    else:
        print("\nDataFrames have inconsistencies.")
        if try_correction:
            print("Corrections have been applied where possible.")
        else:
            print("No corrections applied. Enable `try_correction=True` to attempt fixes.")


def check_column_name_consistency(data_frames: List[pd.DataFrame], try_correction: bool = False) -> bool:
    """
    Check if column names are consistent across DataFrames. Optionally align them.

    Args:
        data_frames (List[pd.DataFrame]): List of DataFrames.
        try_correction (bool): Flag to attempt aligning column names. if True, the data_frames list is directly updated...
        TODO: Find better solution to update data_frames

    Returns:
        bool: True if column names are consistent, False otherwise.
    """
    base_columns = set(data_frames[0].columns)
    consistent = True

    for i in range(1, len(data_frames)):
        df = data_frames[i]
        if set(df.columns) != base_columns:
            print(f"Column name mismatch in DataFrame {i}.")
            consistent = False
            missing_cols = base_columns - set(df.columns)
            if len(missing_cols) > 0:
                print(f"The following columns are missing in DataFrame {i}: {missing_cols}")

            added_cols = set(df.columns) - base_columns
            if len(added_cols) > 0:
                print(f"The following columns are additionally in DataFrame {i}: {added_cols}")

            if try_correction:
                df = df.reindex(columns=data_frames[0].columns, fill_value=np.nan)
                data_frames[i] = df
                consistent = True
    return consistent


def check_dtype_consistency(data_frames: List[pd.DataFrame], try_correction: bool = False) -> bool:
    """
    Check if data types are consistent across DataFrames. Optionally align dtypes.

    Args:
        data_frames (List[pd.DataFrame]): List of DataFrames.
        try_correction (bool): Flag to attempt aligning dtypes. if True, the data_frames list is directly updated...
        TODO: Find better solution to update data_frames

    Returns:
        bool: True if dtypes are consistent, False otherwise.
    """
    base_dtypes = data_frames[0].dtypes
    consistent = True

    for i in range(1, len(data_frames)):
        df = data_frames[i]
        dtype_comparison = compare_dtypes(data_frames[0], df)
        if not dtype_comparison["equal"].all():
            print(f"Dtype mismatch in DataFrame {i}:")
            print(dtype_comparison[~dtype_comparison["equal"]])
            consistent = False
            if try_correction:
                print(f"Aligning dtypes for DataFrame {i} to match the base DataFrame.")
                for col, correct_dtype in base_dtypes.items():
                    if col in df.columns:
                        try:
                            df[col] = df[col].astype(correct_dtype)
                        except Exception as e:
                            print(f"Could not convert column '{col}' to {correct_dtype}: {e}")
                data_frames[i] = df
                consistent = True
    return consistent


def compare_dtypes(df1: pd.DataFrame, df2: pd.DataFrame) -> pd.DataFrame:
    """
    Compare dtypes between two DataFrames.

    Args:
        df1 (pd.DataFrame): First DataFrame.
        df2 (pd.DataFrame): Second DataFrame.

    Returns:
        pd.DataFrame: DataFrame showing dtype comparisons.
    """
    df_dtypes = pd.DataFrame(index=df1.columns)
    df_dtypes["dtypes 1"] = df1.dtypes
    df_dtypes["dtypes 2"] = df2.dtypes.reindex(df1.columns)
    df_dtypes["equal"] = df_dtypes["dtypes 1"] == df_dtypes["dtypes 2"]
    return df_dtypes
