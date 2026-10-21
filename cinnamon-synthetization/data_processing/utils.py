from datetime import date, datetime, time, timezone
import re
from typing import Any, Dict, Iterable, List, Sequence

import pandas as pd


MISSING_VALUE_STRING = "__MISSING_VALUE__"
MISSING_BOOLEAN = False
TEXT_PENDING_LLM = "[TEXT_PENDING_LLM]"
FAILED_TEXT_GENERATION = "[FAILED TO GENERATE TEXT]"
SUPPORTED_COLUMN_TYPES = frozenset(
    {"STRING", "TEXT", "BOOLEAN", "ID", "DATE", "DATE_TIME", "DECIMAL", "INTEGER"}
)
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


def get_date_pattern(column_config: Dict[str, Any]) -> str:
    column_name = get_column_name(column_config)
    configurations = column_config.get("configurations", [])
    date_pattern = next(
        (cfg.get("dateFormatter") or cfg.get("dateTimeFormatter") for cfg in configurations),
        None,
    )
    if not date_pattern:
        column_type = str(column_config.get("type", "DATE")).upper()
        raise ValueError(f"Date format not specified for {column_type} column '{column_name}'")
    return str(date_pattern)


def get_date_format(column_config: Dict[str, Any]) -> str:
    return iso_to_strftime(get_date_pattern(column_config))


def get_date_input_formats(column_config: Dict[str, Any]) -> List[str]:
    pattern = get_date_pattern(column_config)
    formats = [iso_to_strftime(pattern)]
    if get_column_type(column_config) == "DATE_TIME":
        stripped = iso_to_strftime(pattern, strip_timezone=True)
        formats.extend((stripped, stripped.strip()))
    return list(dict.fromkeys(formats))


def get_date_range(column_config: Dict[str, Any]) -> tuple[float, float]:
    minimum = TIMESTAMP_MIN_SECONDS
    maximum = TIMESTAMP_MAX_SECONDS
    range_config = next(
        (
            config
            for config in column_config.get("configurations", [])
            if config.get("name") == "RangeConfiguration"
            or ("minValue" in config and "maxValue" in config)
        ),
        None,
    )
    if range_config is None:
        return minimum, maximum

    try:
        configured_minimum = _iso_range_value_to_timestamp(range_config["minValue"])
        configured_maximum = _iso_range_value_to_timestamp(range_config["maxValue"])
    except (KeyError, ValueError, TypeError, OSError, AttributeError) as exc:
        raise ValueError(f"Invalid date range for column '{get_column_name(column_config)}'") from exc

    minimum = max(minimum, configured_minimum)
    maximum = min(maximum, configured_maximum)
    if minimum > maximum:
        raise ValueError(f"Invalid date range for column '{get_column_name(column_config)}': minimum exceeds maximum")
    return minimum, maximum


def _iso_range_value_to_timestamp(value: Any) -> float:
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return float(value)
    text = str(value).strip()
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        parsed = datetime.combine(date.fromisoformat(text), time.min)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.timestamp()


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

        if column_type in {"DATE", "DATE_TIME"}:
            get_date_input_formats(column_config)
            get_date_range(column_config)


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


def handle_date_column(dataset: pd.DataFrame, column_name: str, date_formats: str | Sequence[str]) -> None:
    formats = [date_formats] if isinstance(date_formats, str) else list(date_formats)
    dataset[column_name] = dataset[column_name].apply(parse_to_unix, args=(formats,))


def parse_to_unix(entry: Any, datetime_format: str | Sequence[str], two_digit_year: bool = False) -> Any:
    if pd.isna(entry):
        return pd.NA
    formats = [datetime_format] if isinstance(datetime_format, str) else datetime_format
    for candidate in formats:
        try:
            normalized_entry = adjust_date_within_bounds(entry, candidate) if "%Y" in candidate else entry
            dt_object = pd.to_datetime(normalized_entry, format=candidate, errors="raise", utc=True)
            if two_digit_year or "%y" in candidate:
                dt_object = interpret_two_digit_year(dt_object)
            return dt_object.timestamp()
        except (ValueError, TypeError, OSError, AttributeError, re.error):
            continue
    return pd.NA


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
        reference_date = pd.Timestamp.now(tz=getattr(dt_object, "tz", None))
    elif getattr(dt_object, "tz", None) is not None and getattr(reference_date, "tz", None) is None:
        reference_date = pd.Timestamp(reference_date, tz=dt_object.tz)
    try:
        if dt_object > reference_date:
            dt_object -= pd.DateOffset(years=100)
    except (ValueError, TypeError, OSError):
        return None
    return dt_object


def _tokenize_java_date_pattern(pattern: str) -> List[tuple[bool, str]]:
    tokens: List[tuple[bool, str]] = []
    literal = []
    quoted = False
    index = 0
    while index < len(pattern):
        character = pattern[index]
        if character == "'":
            if index + 1 < len(pattern) and pattern[index + 1] == "'":
                literal.append("'")
                index += 2
                continue
            quoted = not quoted
            index += 1
            continue
        if not quoted and character.isalpha():
            if literal:
                tokens.append((False, "".join(literal)))
                literal = []
            end = index + 1
            while end < len(pattern) and pattern[end] == character:
                end += 1
            tokens.append((True, pattern[index:end]))
            index = end
            continue
        literal.append(character)
        index += 1
    if quoted:
        raise ValueError(f"Unterminated quote in date format '{pattern}'")
    if literal:
        tokens.append((False, "".join(literal)))
    return tokens


def _java_token_to_strftime(token: str, strip_timezone: bool) -> str:
    character = token[0]
    length = len(token)
    if character in "XxZOvVz":
        if strip_timezone:
            return ""
        return "%z" if character in "XxZ" else "%Z"
    if character in "yu":
        return "%y" if length == 2 else "%Y"
    if character in "ML":
        return "%m" if length <= 2 else "%b" if length == 3 else "%B"
    if character == "d":
        return "%d"
    if character == "D":
        return "%j"
    if character in "Eec":
        return "%a" if length <= 3 else "%A"
    if character in "Hk":
        return "%H"
    if character in "hK":
        return "%I"
    if character == "m":
        return "%M"
    if character == "s":
        return "%S"
    if character == "S":
        return "%f"
    if character == "a":
        return "%p"
    if character in "wW":
        return "%V"
    if character == "Y":
        return "%G"
    raise ValueError(f"Unsupported date format token '{token}'")


def iso_to_strftime(iso_format: str, strip_timezone: bool = False) -> str:
    if "%" in iso_format:
        return iso_format
    return "".join(
        _java_token_to_strftime(value, strip_timezone) if is_pattern else value.replace("%", "%%")
        for is_pattern, value in _tokenize_java_date_pattern(iso_format)
    )


def format_timestamp(entry: Any, java_pattern: str) -> Any:
    if pd.isna(entry):
        return pd.NA
    try:
        value = datetime.fromtimestamp(float(entry), tz=timezone.utc)
    except (ValueError, TypeError, OSError, OverflowError):
        return entry

    if "%" in java_pattern:
        return value.strftime(java_pattern)

    result = []
    for is_pattern, token in _tokenize_java_date_pattern(java_pattern):
        if not is_pattern:
            result.append(token)
            continue
        character = token[0]
        length = len(token)
        if character in "yu":
            result.append(f"{value.year % 100:02d}" if length == 2 else str(value.year).zfill(length))
        elif character in "ML":
            result.append(
                str(value.month) if length == 1 else f"{value.month:02d}" if length == 2
                else value.strftime("%b") if length == 3 else value.strftime("%B")
            )
        elif character == "d":
            result.append(str(value.day) if length == 1 else f"{value.day:02d}")
        elif character == "D":
            result.append(str(value.timetuple().tm_yday).zfill(length))
        elif character in "Eec":
            result.append(value.strftime("%a" if length <= 3 else "%A"))
        elif character in "Hk":
            hour = value.hour if character == "H" or value.hour else 24
            result.append(str(hour) if length == 1 else f"{hour:02d}")
        elif character in "hK":
            hour = value.hour % 12 if character == "K" else value.hour % 12 or 12
            result.append(str(hour) if length == 1 else f"{hour:02d}")
        elif character == "m":
            result.append(str(value.minute) if length == 1 else f"{value.minute:02d}")
        elif character == "s":
            result.append(str(value.second) if length == 1 else f"{value.second:02d}")
        elif character == "S":
            result.append(f"{value.microsecond:06d}"[:length].ljust(length, "0"))
        elif character == "a":
            result.append(value.strftime("%p"))
        elif character == "X":
            result.append("Z")
        elif character == "x":
            result.append("+00" if length == 1 else "+0000" if length in {2, 4} else "+00:00")
        elif character == "Z":
            result.append("GMT" if length == 4 else "Z" if length == 5 else "+0000")
        elif character == "O":
            result.append("GMT")
        elif character in "vVz":
            result.append("UTC")
        elif character in "wW":
            result.append(str(value.isocalendar().week).zfill(length))
        elif character == "Y":
            result.append(str(value.isocalendar().year).zfill(length))
        else:
            raise ValueError(f"Unsupported date format token '{token}'")
    return "".join(result)


def parse_to_date_format(entry: Any, date_format: str) -> Any:
    try:
        dt_object = pd.to_datetime(entry, unit="s")
        formatted_date = dt_object.strftime(date_format)
        return formatted_date
    except (ValueError, TypeError, OSError):
        return entry
