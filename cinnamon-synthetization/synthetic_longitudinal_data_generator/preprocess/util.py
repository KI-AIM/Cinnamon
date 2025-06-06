from datetime import datetime
import pandas as pd
import numpy as np


def process_dataset(dataset, config):
    """
    Preprocesses the dataset based on the provided configuration.

    Args:
        dataset: dataset to be processed
        config: Configuration for each column in the dataset

    Returns:
        pandas.Dataframe
        list of entity columns
        list of context columns
        list of data types
        sequence index
    """
    entity_columns = []
    context_columns = []
    data_types = []
    sequence_index = None
    for column_config in config:
        column_name = column_config['name']
        column_type = column_config['type']

        if column_type in ['STRING', 'BOOLEAN']:
            data_types.append(column_name + ":" + 'categorical')
            continue

        if column_type == 'COUNT':
            data_types.append(column_name + ":" + 'count')
            continue

        if column_type == 'ID':
            entity_columns.append(column_name)
            continue

        if column_type == 'CONTEXT':
            context_columns.append(column_name)
            data_types.append(column_name + ":" + 'categorical')
            continue

        if column_type == 'INDEX':
            sequence_index = column_name
            continue

        if column_type == 'DATE':
            date_format = next(
                (cfg.get('dateFormatter') or cfg.get('dateTimeFormatter') for cfg in column_config['configurations']),
                None)
            date_format = iso_to_strftime(date_format)
            handle_date_column(dataset, column_name, date_format)

        if column_type in ['DECIMAL', 'DATE', 'INTEGER']:
            column_mean = dataset[column_name].mean()
            dataset[column_name].fillna(column_mean, inplace=True)

    return dataset, entity_columns, context_columns, data_types, sequence_index


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


def split_train_test(fitting_config, dataset, entity_columns):
    """
    Splits the dataset into train and test data, respecting entity columns for longitudinal data.

    :param fitting_config: The configuration of the algorithm provided by the user in the YAML format.
    :param dataset: The dataset to be split in DataFrame format.
    :param entity_columns: The columns that define the entities in the dataset.
    :return:
        train_dataset: The train dataset.
        validate_dataset: The test dataset.
    """

    # Ensure that the dataset is grouped by the entity columns
    grouped = dataset.groupby(entity_columns)

    # Shuffle the groups (entities) rather than individual rows
    groups = list(grouped.groups.keys())
    shuffled_groups = np.random.default_rng().choice(groups, size=len(groups), replace=False)

    # Determine the split index based on the train proportion
    train_size = int(fitting_config['train'] * len(shuffled_groups))

    # Split the groups into train and validate sets
    train_groups = shuffled_groups[:train_size]
    validate_groups = shuffled_groups[train_size:]

    # Use the grouped objects to create train and validate datasets
    train_dataset = grouped.filter(lambda x: tuple(x[entity_columns].iloc[0]) in train_groups)
    validate_dataset = grouped.filter(lambda x: tuple(x[entity_columns].iloc[0]) in validate_groups)

    return train_dataset, validate_dataset


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

