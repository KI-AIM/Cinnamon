from datetime import datetime
import pandas as pd
from typing import Union, Optional

BOOLEAN_MAP = {
    'True': True, 'true': True, '1': True, 1: True, '1.0': True, 1.0: True, 'YES': True, 'yes': True, 'Y': True,
    'y': True,
    'False': False, 'false': False, '0': False, 0: False, '0.0': False, 0.0: False, 'NO': False, 'no': False,
    'N': False, 'n': False
}


def handle_date_column(dataset: pd.DataFrame, column_name: str, date_format: str) -> None:
    """
    Converts date columns in the dataset to UNIX timestamps based on the provided format.

    Args:
        dataset: The dataset containing the column to be processed
        column_name: The name of the column containing date values
        date_format: The format string for parsing dates
    """
    # Create a copy to avoid reference issues
    original_column = dataset[column_name].copy()
    
    try:
        if '%Y' in date_format:
            dataset[column_name] = original_column.apply(
                lambda x: adjust_date_within_bounds(x, date_format))
            
        if '%y' in date_format:
            dataset[column_name] = original_column.apply(
                lambda x: parse_to_unix(x, date_format, True))
        else:
            dataset[column_name] = original_column.apply(
                lambda x: parse_to_unix(x, date_format))
    except Exception:
        # If overall conversion fails, set entire column to NA
        dataset[column_name] = pd.NA


def parse_to_unix(entry: Union[str, int, float], 
                 datetime_format: str, 
                 two_digit_year: bool = False) -> Union[int, float]:
    """
    Converts a date string to a UNIX timestamp based on the specified format.

    Args:
        entry: The date string to be parsed
        datetime_format: The format string used for parsing the date
        two_digit_year: Whether to adjust for two-digit years. Defaults to False

    Returns:
        Unix timestamp as int or pd.NA if conversion fails
    """
    if pd.isna(entry):
        return pd.NA
        
    try:
        dt_object = pd.to_datetime(entry, format=datetime_format, errors='raise')
        if two_digit_year:
            dt_object = interpret_two_digit_year(dt_object)
            if dt_object is None:
                return pd.NA
                
        timestamp = int(dt_object.timestamp())
        # Validate timestamp is within acceptable bounds
        timestamp = adjust_date_within_bounds_post(timestamp)
        return timestamp
    except (ValueError, TypeError, OSError, AttributeError):
        return pd.NA


def adjust_date_within_bounds(entry: Union[str, int, float], datetime_format: str):
    """
    Adjusts dates to fall within the valid range by replacing years outside the acceptable bounds.

    Args:
        entry: The date string to be adjusted
        datetime_format: The format string used for parsing the date

    Returns:
        The adjusted date string formatted according to `datetime_format` or pd.NA if adjustment fails
    """
    if pd.isna(entry):
        return pd.NA
        
    try:
        dt = datetime.strptime(str(entry), datetime_format)
        if dt.year < 1678:
            dt = dt.replace(year=1678)
        if dt.year > 2261:
            dt = dt.replace(year=2261)
        return str(dt.strftime(datetime_format))
    except (ValueError, TypeError, OSError):
        return pd.NA


def adjust_date_within_bounds_post(entry: Union[int, float]):
    """
    Adjusts dates to fall within the valid range by replacing years outside the acceptable bounds.

    Args:
        entry: The unix timestamp to be adjusted

    Returns:
        The adjusted unix timestamp or pd.NA if adjustment fails
    """
    if pd.isna(entry):
        return pd.NA
        
    try:
        entry_val = float(entry)
        if entry_val < -9214560000:
            return float(-9214560000)
        if entry_val > 9214560000:
            return float(9214560000)
        return entry_val
    except (ValueError, TypeError, OSError):
        return pd.NA


def interpret_two_digit_year(dt_object: pd.Timestamp, 
                            reference_date: pd.Timestamp = None) -> Optional[pd.Timestamp]:
    """
    Adjusts a date with a two-digit year based on the reference date.

    Args:
        dt_object: The date to adjust
        reference_date: The reference date for interpretation. Defaults to the current date

    Returns: 
        Adjusted date or None if adjustment fails
    """
    if pd.isna(dt_object):
        return None
        
    if reference_date is None:
        reference_date = pd.Timestamp.now()
        
    try:
        if dt_object > reference_date:
            dt_object -= pd.DateOffset(years=100)
        return dt_object
    except (ValueError, TypeError, OSError, AttributeError):
        return None


def iso_to_strftime(iso_format: str) -> str:
    """
    Convert an ISO-like format string into strftime-compatible format,
    including week and day-of-year options.

    Args:
        iso_format: An ISO-like format string with placeholders like 'yyyy', 'mm', 'dd', 'Www', 'DDD'

    Returns:
        The strftime-compatible format string
    """
    format_mapping = {
        "yyyy": "%Y",  # Full year, e.g., 2023
        "yy": "%y",    # Short year, e.g., 23
        "MM": "%m",    # Month, e.g., 01
        "dd": "%d",    # Day, e.g., 01
        "HH": "%H",    # Hour, e.g., 14 (24-hour format)
        "mm": "%M",    # Minute, e.g., 05
        "SS": "%S",    # Second, e.g., 09
        "sss": "%f",   # Microsecond, e.g., 123456 (only first three digits considered as milliseconds)
        "Www": "%V",   # ISO week number, e.g., 01-53
        "DDD": "%j",   # Day of the year, e.g., 001-366
        "D": "%u",     # ISO weekday, where Monday is 1 and Sunday is 7
        "GGGG": "%G"   # ISO year, aligned with ISO week number (for use with %V)
    }

    strftime_format = iso_format

    for iso_placeholder, strftime_code in format_mapping.items():
        strftime_format = strftime_format.replace(iso_placeholder, strftime_code)

    return strftime_format


def parse_to_date_format(entry: Union[int, float, str], date_format: str):
    """
    Converts a Unix timestamp to a formatted date string.

    Args:
        entry: Unix timestamp to convert
        date_format: Target date format string

    Returns:
        Formatted date string or pd.NA if conversion fails
    """
    if pd.isna(entry):
        return pd.NA
        
    try:
        dt_object = pd.to_datetime(entry, unit='s')
        return dt_object.strftime(date_format)
    except (ValueError, TypeError, OSError):
        return pd.NA

def validate_and_extract_metrics(config: dict):
    """
    Validates data format and extracts metrics from configuration.

    Args:
        config: Configuration dictionary containing evaluation settings

    Returns:
        tuple: (data_format, metrics_dict) where metrics_dict contains
               resemblance, utility, and privacy metrics

    Raises:
        ValueError: If data_format is not one of the valid formats
    """
    data_format = config.get('evaluation_configuration', {}).get('data_format')
    valid_formats = ["cross-sectional", "longitudinal", "process-oriented"]

    if data_format not in valid_formats:
        raise ValueError(f"Unvalid Dataformat: {data_format}. Erwartet eines von {valid_formats}.")

    metrics = {}
    sections = ['resemblance', 'utility', 'privacy']

    for section in sections:
        section_metrics = config.get('evaluation_configuration', {}).get(section, {})
        metrics[section] = section_metrics

    return data_format, metrics
