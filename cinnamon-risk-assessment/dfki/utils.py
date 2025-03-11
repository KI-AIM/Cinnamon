from datetime import datetime
import pandas as pd

BOOLEAN_MAP = {
    'True': True, 'true': True, '1': True, 1: True, '1.0': True, 1.0: True, 'YES': True, 'yes': True, 'Y': True,
    'y': True,
    'False': False, 'false': False, '0': False, 0: False, '0.0': False, 0.0: False, 'NO': False, 'no': False,
    'N': False, 'n': False
}


def handle_date_column(dataset, column_name, date_format):
    """
    Converts date columns in the dataset to UNIX timestamps based on the provided format.

    Args:
        dataset: The dataset containing the column to be processed
        column_name: The name of the column containing date values.
        date_format: The format string for parsing dates.
    """
    if '%Y' in date_format:
        dataset[column_name] = dataset[column_name].apply(adjust_date_within_bounds, args=(date_format,))
    if '%y' in date_format:
        dataset[column_name] = dataset[column_name].apply(parse_to_unix, args=(date_format, True,))
    else:
        dataset[column_name] = dataset[column_name].apply(parse_to_unix, args=(date_format,))


def parse_to_unix(entry, datetime_format, two_digit_year=False):
    """
    Converts a date string to a UNIX timestamp based on the specified format.

    Args:
        entry: The date string to be parsed.
        datetime_format: The format string used for parsing the date.
        two_digit_year: Whether to adjust for two-digit years. Defaults to False.

    Returns:
        int or pandas.NA
    """
    try:
        dt_object = pd.to_datetime(entry, format=datetime_format, errors='raise')
        if two_digit_year:
            dt_object = interpret_two_digit_year(dt_object)
        timestamp = int(dt_object.timestamp())
    except (ValueError, TypeError, OSError):
        timestamp = pd.NA
    return timestamp


def adjust_date_within_bounds(entry, datetime_format):
    """
    Adjusts dates to fall within the valid range by replacing years outside the acceptable bounds.

    Args:
        entry: The date string to be adjusted.
        datetime_format: The format string used for parsing the date.

    Returns:
        The adjusted date string formatted according to `datetime_format`. If adjustment fails,
        the original date string is returned.
    """
    try:
        dt = datetime.strptime(entry, datetime_format)
        if dt.year < 1678:
            dt = dt.replace(year=1678)
        if dt.year > 2261:
            dt = dt.replace(year=2261)
    except (ValueError, TypeError, OSError):
        return entry
    return str(dt.strftime(datetime_format))


def adjust_date_within_bounds_post(entry):
    """
    Adjusts dates to fall within the valid range by replacing years outside the acceptable bounds.

    Args:
        entry: The unix timestamp to be adjusted.

    Returns:
        The adjusted unix timestamp,
        the original entry is returned.
    """
    try:
        if entry < -9214560000:
            return float(-9214560000)
        if entry > 9214560000:
            return float(9214560000)
    except (ValueError, TypeError, OSError):
        return entry
    return entry


def interpret_two_digit_year(dt_object, reference_date=pd.Timestamp.now()):
    """
    Adjusts a date with a two-digit year based on the reference date. Default: dates in the future are adjusted to
    fall to the past.

    Args:
        dt_object: The date to adjust.
        reference_date: The reference date for interpretation. Defaults to the current date.

    Returns: adjusted date
    """
    try:
        if dt_object > reference_date:
            dt_object -= pd.DateOffset(years=100)
    except (ValueError, TypeError, OSError):
        return None
    return dt_object


def iso_to_strftime(iso_format):
    """
    Convert an ISO-like format string into strftime-compatible format,
    including week and day-of-year options.

    Parameters:
    - iso_format (str): An ISO-like format string with placeholders like 'yyyy', 'mm', 'dd', 'Www', 'DDD'.

    Returns:
    - str: The strftime-compatible format string.
    """
    # Mapping from ISO-like placeholders to strftime format codes
    format_mapping = {
        "yyyy": "%Y",  # Full year, e.g., 2023
        "yy": "%y",  # Short year, e.g., 23
        "MM": "%m",  # Month, e.g., 01
        "dd": "%d",  # Day, e.g., 01
        "HH": "%H",  # Hour, e.g., 14 (24-hour format)
        "mm": "%M",  # Minute, e.g., 05
        "SS": "%S",  # Second, e.g., 09
        "sss": "%f",  # Microsecond, e.g., 123456 (only first three digits considered as milliseconds)
        "Www": "%V",  # ISO week number, e.g., 01-53
        "DDD": "%j",  # Day of the year, e.g., 001-366
        "D": "%u",  # ISO weekday, where Monday is 1 and Sunday is 7
        "GGGG": "%G"  # ISO year, aligned with ISO week number (for use with %V)
    }
    # Replace ISO placeholders with strftime codes
    strftime_format = iso_format
    for iso_placeholder, strftime_code in format_mapping.items():
        strftime_format = strftime_format.replace(iso_placeholder, strftime_code)
    return strftime_format


def parse_to_date_format(entry, date_format: str):
    try:
        dt_object = pd.to_datetime(entry, unit='s')
        formatted_date = dt_object.strftime(date_format)
        return formatted_date
    except (ValueError, TypeError, OSError):
        return entry
