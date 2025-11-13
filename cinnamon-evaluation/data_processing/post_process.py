import math
import pandas as pd
from datetime import datetime, timedelta
import locale

from data_processing.utils import iso_to_strftime, parse_to_date_format, adjust_date_within_bounds_post
from data_processing.utils import BOOLEAN_MAP


def post_process_dataframe(df: pd.DataFrame, config):
    """
    Post-processes a pandas DataFrame by replacing missing values, converting string columns to the appropriate
    data type, and adjusting date columns within the specified bounds.

    Args:
        df (pd.DataFrame): The pandas DataFrame to be post-processed.
        config (list): A list of dictionaries containing the column name, data type, and bounds for each column.

    Returns:
        pd.DataFrame: The post-processed pandas DataFrame.
    """
    df = df.replace("missing_value", pd.NA)
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


def transform_in_time_distance(number, format_type="DATETIME"):
    """
    Transforms a number into a time distance string based on the specified format type.

    Args:
        number (int or float): The number to be transformed.
        format_type (str, optional): The format type for the time distance string. Defaults to "DATETIME".

    Returns:
        str: The transformed time distance string.
    """
    SECONDS_IN_MINUTE = 60
    SECONDS_IN_HOUR = 3600
    SECONDS_IN_DAY = 86400
    SECONDS_IN_YEAR = 31536000

    if not isinstance(number, (int, float)):
        return number

    if number < SECONDS_IN_MINUTE:
        return f"{number:.0f} Seconds"
    elif number < SECONDS_IN_HOUR:
        minutes = number / SECONDS_IN_MINUTE
        return f"{minutes:.0f} Minutes"
    elif number < SECONDS_IN_DAY:
        hours = number / SECONDS_IN_HOUR
        return f"{hours:.0f} Hours"
    elif number < SECONDS_IN_YEAR:
        days = number / SECONDS_IN_DAY
        return f"{days:.0f} Days"
    else:
        years = number / SECONDS_IN_YEAR
        return f"{years:.0f} Years"

def transform_variance_in_time_distance(number, format_type="DATETIME"):
    """
    Transforms a variance value (expressed in squared seconds) into a human readable
    squared time distance string by selecting the unit that matches the implied
    standard deviation scale.

    Args:
        number (int or float): Variance in squared seconds.
        format_type (str, optional): Unused but kept for compatibility.

    Returns:
        str: Formatted variance with squared time units.
    """
    SECONDS_IN_MINUTE = 60
    SECONDS_IN_HOUR = 3600
    SECONDS_IN_DAY = 86400
    SECONDS_IN_YEAR = 31536000

    if not isinstance(number, (int, float)):
        return number

    if number < 0:
        # Fallback to base transform for unexpected negative variance.
        return transform_in_time_distance(number, format_type)

    std_seconds = math.sqrt(number)

    if std_seconds < SECONDS_IN_MINUTE:
        value = number
        unit = "Seconds"
    elif std_seconds < SECONDS_IN_HOUR:
        value = number / (SECONDS_IN_MINUTE ** 2)
        unit = "Minutes"
    elif std_seconds < SECONDS_IN_DAY:
        value = number / (SECONDS_IN_HOUR ** 2)
        unit = "Hours"
    elif std_seconds < SECONDS_IN_YEAR:
        value = number / (SECONDS_IN_DAY ** 2)
        unit = "Days"
    else:
        value = number / (SECONDS_IN_YEAR ** 2)
        unit = "Years"

    return f"{value:.0f} {unit}"


def transform_in_iso_datetime(number, format_type="DATETIME"):
    """
    Transforms a number into a string representation of a date and time in ISO format.

    Args:
        number (int or float): The number to be transformed.
        format_type (str, optional): The format type. Defaults to "DATETIME".

    Returns:
        str: The transformed string representation of the date and time.
    """
    if not isinstance(number, (int, float)):
        return number

    try:
        if number < 0:
            dt = datetime(1970, 1, 1) + timedelta(seconds=number)
        else:
            dt = datetime.fromtimestamp(number)

        locale.setlocale(locale.LC_TIME, '')

        if format_type.upper() == "DATE":
            return dt.strftime('%x')  # Locale's appropriate date representation
        else:
            return dt.strftime('%c')  # Locale's appropriate date and time representation

    except (ValueError, OSError, OverflowError, locale.Error):
        try:
            if number < 0:
                dt = datetime(1970, 1, 1) + timedelta(seconds=number)
            else:
                dt = datetime.fromtimestamp(number)

            if format_type.upper() == "DATE":
                return dt.strftime('%Y-%m-%d')
            else:
                return dt.strftime('%Y-%m-%d %H:%M:%S')
        except (ValueError, OSError, OverflowError):
            return number
