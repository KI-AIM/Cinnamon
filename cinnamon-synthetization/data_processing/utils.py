from datetime import datetime
from typing import Any, Dict, Iterable, List, Sequence

import pandas as pd


MISSING_VALUE_STRING = "__MISSING_VALUE__"
MISSING_BOOLEAN = False
TEXT_PENDING_LLM = "[TEXT_PENDING_LLM]"
SUPPORTED_COLUMN_TYPES = frozenset({"STRING", "TEXT", "BOOLEAN", "ID", "DATE", "DECIMAL", "INTEGER"})
TIMESTAMP_MIN_SECONDS = -9214560000.0
TIMESTAMP_MAX_SECONDS = 9214560000.0
MISSING_TEXT_MARKERS = {"", "null", "NULL", "NaN", "nan"}
BOOLEAN_MAP = {
    "True": True,
    "true": True,
    "1": True,
    1: True,
    "1.0": True,
    1.0: True,
    "YES": True,
    "yes": True,
    "Y": True,
    "y": True,
    "False": False,
    "false": False,
    "0": False,
    0: False,
    "0.0": False,
    0.0: False,
    "NO": False,
    "no": False,
    "N": False,
    "n": False,
}


def normalize_missing_markers(series: pd.Series) -> pd.Series:
    normalized = series.replace(list(MISSING_TEXT_MARKERS), pd.NA)
    if pd.api.types.is_string_dtype(normalized.dtype) or normalized.dtype == object:
        normalized = normalized.replace("None", pd.NA)
        normalized = normalized.replace("<NA>", pd.NA)
    return normalized


def normalize_missing_dataframe(df: pd.DataFrame) -> pd.DataFrame:
    normalized = df.replace(list(MISSING_TEXT_MARKERS), pd.NA)
    normalized = normalized.replace("None", pd.NA)
    normalized = normalized.replace("<NA>", pd.NA)
    return normalized


def normalize_string_series(series: pd.Series) -> pd.Series:
    normalized = series.astype("string")
    return normalize_missing_markers(normalized)


def get_column_name(column_config: Dict[str, Any]) -> str:
    column_name = column_config.get("name")
    if not isinstance(column_name, str) or not column_name.strip():
        raise ValueError("Each column configuration must define a non-empty 'name'.")
    return column_name


def get_column_type(column_config: Dict[str, Any]) -> str:
    column_type = str(column_config.get("type", "")).upper()
    if column_type not in SUPPORTED_COLUMN_TYPES:
        column_name = column_config.get("name", "<unknown>")
        raise ValueError(f"Invalid column type '{column_type}' for column '{column_name}'")
    return column_type


def get_date_format(column_config: Dict[str, Any]) -> str:
    column_name = get_column_name(column_config)
    configurations = column_config.get("configurations", [])
    date_format = next(
        (cfg.get("dateFormatter") or cfg.get("dateTimeFormatter") for cfg in configurations),
        None,
    )
    if not date_format:
        raise ValueError(f"Date format not specified for DATE column '{column_name}'")
    return iso_to_strftime(str(date_format))


def validate_column_configurations(
    config: Sequence[Dict[str, Any]],
    dataframe_columns: Iterable[str] | None = None,
) -> None:
    seen_names = set()
    seen_indexes = set()
    dataframe_column_set = set(dataframe_columns) if dataframe_columns is not None else None

    for column_config in config:
        column_name = get_column_name(column_config)
        column_type = get_column_type(column_config)

        if column_name in seen_names:
            raise ValueError(f"Duplicate column configuration for '{column_name}'")
        seen_names.add(column_name)

        column_index = column_config.get("index")
        if column_index is not None:
            if column_index in seen_indexes:
                raise ValueError(f"Duplicate column index '{column_index}' in attribute configuration")
            seen_indexes.add(column_index)

        if dataframe_column_set is not None and column_name not in dataframe_column_set:
            raise ValueError(f"Column '{column_name}' specified in config does not exist in the dataframe")

        if column_type == "DATE":
            get_date_format(column_config)


def get_ordered_column_names(config: Sequence[Dict[str, Any]]) -> List[str]:
    ordered = sorted(config, key=lambda item: item.get("index", float("inf")))
    return [get_column_name(item) for item in ordered]


def order_dataframe_by_config(df: pd.DataFrame, config: Sequence[Dict[str, Any]]) -> pd.DataFrame:
    ordered_names = get_ordered_column_names(config)
    for column_name in ordered_names:
        if column_name not in df.columns:
            df[column_name] = pd.NA
    return df[ordered_names] if ordered_names else df


def set_text_columns_to_pending(df: pd.DataFrame, config: Sequence[Dict[str, Any]]) -> pd.DataFrame:
    updated = df.copy()
    for column_config in config:
        column_name = get_column_name(column_config)
        column_type = get_column_type(column_config)
        if column_type == "TEXT":
            updated[column_name] = TEXT_PENDING_LLM
    return updated


def handle_date_column(dataset: pd.DataFrame, column_name: str, date_format: str) -> None:
    if "%Y" in date_format:
        dataset[column_name] = dataset[column_name].apply(adjust_date_within_bounds, args=(date_format,))

    if "%y" in date_format:
        dataset[column_name] = dataset[column_name].apply(parse_to_unix, args=(date_format, True))
        return

    dataset[column_name] = dataset[column_name].apply(parse_to_unix, args=(date_format,))


def parse_to_unix(entry: Any, datetime_format: str, two_digit_year: bool = False) -> Any:
    try:
        dt_object = pd.to_datetime(entry, format=datetime_format, errors="raise")
        if two_digit_year:
            dt_object = interpret_two_digit_year(dt_object)
        timestamp = int(dt_object.timestamp())
    except (ValueError, TypeError, OSError, AttributeError):
        timestamp = pd.NA
    return timestamp


def adjust_date_within_bounds(entry: Any, datetime_format: str) -> Any:
    try:
        dt = datetime.strptime(entry, datetime_format)
        if dt.year < 1678:
            dt = dt.replace(year=1678)
        if dt.year > 2261:
            dt = dt.replace(year=2261)
    except (ValueError, TypeError, OSError):
        return entry
    return str(dt.strftime(datetime_format))


def adjust_date_within_bounds_post(entry: Any) -> Any:
    try:
        if entry < TIMESTAMP_MIN_SECONDS:
            return TIMESTAMP_MIN_SECONDS
        if entry > TIMESTAMP_MAX_SECONDS:
            return TIMESTAMP_MAX_SECONDS
    except (ValueError, TypeError, OSError):
        return entry
    return entry


def interpret_two_digit_year(dt_object: Any, reference_date: Any = None) -> Any:
    if reference_date is None:
        reference_date = pd.Timestamp.now()
    try:
        if dt_object > reference_date:
            dt_object -= pd.DateOffset(years=100)
    except (ValueError, TypeError, OSError):
        return None
    return dt_object


def iso_to_strftime(iso_format: str) -> str:
    format_mapping = {
        "yyyy": "%Y",
        "yy": "%y",
        "MM": "%m",
        "dd": "%d",
        "HH": "%H",
        "mm": "%M",
        "SS": "%S",
        "sss": "%f",
        "Www": "%V",
        "DDD": "%j",
        "D": "%u",
        "GGGG": "%G",
    }
    strftime_format = iso_format
    for iso_placeholder, strftime_code in format_mapping.items():
        strftime_format = strftime_format.replace(iso_placeholder, strftime_code)
    return strftime_format


def parse_to_date_format(entry: Any, date_format: str) -> Any:
    try:
        dt_object = pd.to_datetime(entry, unit="s")
        formatted_date = dt_object.strftime(date_format)
        return formatted_date
    except (ValueError, TypeError, OSError):
        return entry
