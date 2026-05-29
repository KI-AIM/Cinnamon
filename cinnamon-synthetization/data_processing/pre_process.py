import pandas as pd
from typing import Tuple, List, Dict, Any

from data_processing.utils import (
    BOOLEAN_MAP,
    MISSING_BOOLEAN,
    MISSING_VALUE_STRING,
    TEXT_PENDING_LLM,
    get_column_name,
    get_column_type,
    get_date_format,
    handle_date_column,
    normalize_string_series,
    set_text_columns_to_pending,
    validate_column_configurations,
)


def pre_process_dataframe(
    df: pd.DataFrame,
    config: List[Dict[str, Any]],
    replace_text_with_pending: bool = True,
) -> Tuple[pd.DataFrame, List[str]]:
    validate_column_configurations(config, dataframe_columns=df.columns)
    all_missing_values_column = [column for column in df.columns if df[column].isna().all()]
    if all_missing_values_column:
        df = df.drop(columns=all_missing_values_column)

    if replace_text_with_pending:
        df = set_text_columns_to_pending(df, config)

    for column_config in config:
        column_name = get_column_name(column_config)
        column_type = get_column_type(column_config)

        if column_name in all_missing_values_column:
            continue

        try:
            if column_type in {"STRING", "ID"}:
                df[column_name] = _preprocess_string_like(df[column_name])
                continue

            if column_type == "TEXT":
                if replace_text_with_pending:
                    df[column_name] = pd.Series(TEXT_PENDING_LLM, index=df.index, dtype="string")
                else:
                    df[column_name] = normalize_string_series(df[column_name])
                continue

            if column_type == "BOOLEAN":
                df[column_name] = _preprocess_boolean(df[column_name])
                continue

            if column_type == "DATE":
                df[column_name] = _preprocess_date(df[column_name], column_config)
                continue

            if column_type == "DECIMAL":
                df[column_name] = _preprocess_decimal(df[column_name], column_name)
                continue

            if column_type == "INTEGER":
                df[column_name] = _preprocess_integer(df[column_name], column_name)
                continue

        except Exception as exc:
            raise type(exc)(f"Error processing column '{column_name}': {exc}") from exc

    return df, all_missing_values_column


def _preprocess_string_like(series: pd.Series) -> pd.Series:
    normalized = normalize_string_series(series)
    return normalized.fillna(MISSING_VALUE_STRING)


def _preprocess_boolean(series: pd.Series) -> pd.Series:
    normalized = normalize_string_series(series)
    mapped = normalized.map(BOOLEAN_MAP)
    return mapped.apply(lambda value: MISSING_BOOLEAN if pd.isna(value) else bool(value)).astype(bool)


def _preprocess_date(series: pd.Series, column_config: Dict[str, Any]) -> pd.Series:
    working_series = series.copy()
    date_format = get_date_format(column_config)
    working_df = pd.DataFrame({"value": working_series})
    handle_date_column(working_df, "value", date_format)
    numeric = pd.to_numeric(working_df["value"], errors="coerce")
    return _fill_numeric_missing_with_mean(numeric, get_column_name(column_config), "DATE")


def _preprocess_decimal(series: pd.Series, column_name: str) -> pd.Series:
    numeric = pd.to_numeric(series, errors="coerce")
    return _fill_numeric_missing_with_mean(numeric, column_name, "DECIMAL")


def _preprocess_integer(series: pd.Series, column_name: str) -> pd.Series:
    numeric = pd.to_numeric(series, errors="coerce")
    filled = _fill_numeric_missing_with_mean(numeric, column_name, "INTEGER")
    return filled.round().astype(int)


def _fill_numeric_missing_with_mean(series: pd.Series, column_name: str, column_type: str) -> pd.Series:
    column_mean = series.mean()
    if pd.isna(column_mean):
        raise ValueError(f"Cannot calculate mean for {column_type} column '{column_name}', all values are non-numeric")
    return series.fillna(column_mean)
