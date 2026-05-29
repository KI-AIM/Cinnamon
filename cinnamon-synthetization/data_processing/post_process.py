import pandas as pd
from typing import List, Dict, Any

from data_processing.utils import (
    BOOLEAN_MAP,
    MISSING_VALUE_STRING,
    TEXT_PENDING_LLM,
    adjust_date_within_bounds_post,
    get_column_name,
    get_column_type,
    get_date_format,
    normalize_missing_dataframe,
    normalize_string_series,
    order_dataframe_by_config,
    parse_to_date_format,
    validate_column_configurations,
)


def post_process_dataframe(
    df: pd.DataFrame,
    config: List[Dict[str, Any]],
    all_missing_values_column: List[str],
    fill_text_with_pending: bool = True,
) -> pd.DataFrame:
    validate_column_configurations(config)

    for column_name in all_missing_values_column:
        df[column_name] = pd.NA

    df = normalize_missing_dataframe(df.replace(MISSING_VALUE_STRING, pd.NA))

    for column_config in config:
        column_name = get_column_name(column_config)
        column_type = get_column_type(column_config)

        if column_name not in df.columns:
            df[column_name] = pd.NA

        try:
            if column_type in {"STRING", "ID"}:
                df[column_name] = normalize_string_series(df[column_name])
                continue

            if column_type == "TEXT":
                df[column_name] = _post_process_text(df[column_name], fill_text_with_pending)
                continue

            if column_type == "BOOLEAN":
                df[column_name] = _post_process_boolean(df[column_name])
                continue

            if column_type == "INTEGER":
                df[column_name] = pd.to_numeric(df[column_name], errors="coerce").round().astype("Int64")
                continue

            if column_type == "DECIMAL":
                df[column_name] = pd.to_numeric(df[column_name], errors="coerce").astype("Float64")
                continue

            if column_type == "DATE":
                df[column_name] = _post_process_date(df[column_name], column_config)
                continue

        except Exception as exc:
            raise type(exc)(f"Error processing column '{column_name}': {exc}") from exc

    return order_dataframe_by_config(df, config)


def _post_process_text(series: pd.Series, fill_text_with_pending: bool) -> pd.Series:
    normalized = normalize_string_series(series)
    if fill_text_with_pending:
        normalized = normalized.fillna(TEXT_PENDING_LLM)
    return normalized.astype("string")


def _post_process_boolean(series: pd.Series) -> pd.Series:
    normalized = normalize_string_series(series)
    return normalized.map(BOOLEAN_MAP).astype("boolean")


def _post_process_date(series: pd.Series, column_config: Dict[str, Any]) -> pd.Series:
    numeric = pd.to_numeric(series, errors="coerce")
    numeric = numeric.apply(adjust_date_within_bounds_post)
    date_format = get_date_format(column_config)
    formatted = numeric.apply(parse_to_date_format, args=(date_format,))
    return formatted.astype("string")
