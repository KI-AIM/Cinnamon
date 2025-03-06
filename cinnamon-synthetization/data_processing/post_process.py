import pandas as pd

from data_processing.utils import iso_to_strftime, parse_to_date_format, adjust_date_within_bounds_post
from data_processing.utils import BOOLEAN_MAP


def post_process_dataframe(df: pd.DataFrame, config):
    """
        Post-process a DataFrame based on the provided configuration.

        This function replaces missing value indicators with `NaN`, converts columns
        to specified data types, and applies custom transformations based on the
        configuration. Supported data types include STRING, BOOLEAN, INTEGER,
        DECIMAL, and DATE.

        Args:
            df (pd.DataFrame): The DataFrame to be post-processed.
            config (list[dict]): A list of dictionaries containing column configurations.
                Each dictionary should include:
                    - name (str): The column name.
                    - type (str): The target data type ("STRING", "BOOLEAN", "INTEGER",
                      "DECIMAL", or "DATE").
                    - configurations (list[dict], optional): Additional configurations for
                      specific data types, such as date formatting.

        Returns:
            pd.DataFrame: The post-processed DataFrame with updated column values and data types.
    """
    # Replace "missing_value" with NaN
    df = df.replace("missing_value", pd.NA)

    # Also replace empty strings and common missing value indicators
    df = df.replace({"": pd.NA, "null": pd.NA, "NULL": pd.NA, "NaN": pd.NA, "nan": pd.NA})

    for column_config in config:
        column_name = column_config['name']
        column_type = column_config['type']

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

        if column_type == 'DECIMAL':
            df[column_name] = pd.to_numeric(df[column_name], errors='coerce').astype('Float64')

        if column_type == 'DATE':
            df[column_name] = pd.to_numeric(df[column_name], errors='coerce')
            df[column_name] = df[column_name].apply(adjust_date_within_bounds_post)
            date_format = next(
                (cfg.get('dateFormatter') or cfg.get('dateTimeFormatter') for cfg in column_config['configurations']),
                None)
            date_format = iso_to_strftime(date_format)
            df[column_name] = df[column_name].apply(parse_to_date_format, args=(date_format,))
            df[column_name] = df[column_name].astype(dtype='string')

    return df

