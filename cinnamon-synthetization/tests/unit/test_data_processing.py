import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from data_processing.post_process import post_process_dataframe
from data_processing.pre_process import pre_process_dataframe
from data_processing.utils import (
    MISSING_VALUE_STRING,
    TEXT_PENDING_LLM,
    adjust_date_within_bounds_post,
    iso_to_strftime,
    parse_to_date_format,
    parse_to_unix,
)


def _base_config() -> list[dict]:
    return [
        {"name": "name", "type": "STRING", "index": 0},
        {"name": "active", "type": "BOOLEAN", "index": 1},
        {"name": "event_date", "type": "DATE", "index": 2, "configurations": [{"dateFormatter": "yyyy-MM-dd"}]},
        {"name": "score", "type": "DECIMAL", "index": 3},
        {"name": "age", "type": "INTEGER", "index": 4},
    ]


def test_pre_process_dataframe_happy_path_converts_and_drops_all_missing():
    df = pd.DataFrame(
        {
            "name": ["Alice", np.nan, ""],
            "active": ["true", "false", np.nan],
            "event_date": ["2024-01-01", "2024-01-03", np.nan],
            "score": ["1.5", np.nan, "2.5"],
            "age": ["10", np.nan, "13"],
            "all_missing": [np.nan, np.nan, np.nan],
        }
    )

    processed, dropped = pre_process_dataframe(df, _base_config())

    assert dropped == ["all_missing"]
    assert "all_missing" not in processed.columns
    assert processed["name"].tolist() == ["Alice", MISSING_VALUE_STRING, MISSING_VALUE_STRING]
    assert processed["active"].tolist() == [True, False, False]

    assert processed["score"].isna().sum() == 0
    assert processed["score"].iloc[1] == pytest.approx(2.0)

    assert processed["age"].dtype == int
    assert processed["age"].tolist() == [10, 12, 13]

    event_values = processed["event_date"].astype(float).tolist()
    assert all(pd.notna(v) for v in event_values)
    assert event_values[2] == pytest.approx((event_values[0] + event_values[1]) / 2)


def test_pre_process_dataframe_raises_when_config_column_missing():
    df = pd.DataFrame({"name": ["Alice"]})
    config = [{"name": "missing_col", "type": "STRING"}]

    with pytest.raises(ValueError) as exc:
        pre_process_dataframe(df, config)

    assert "does not exist in the dataframe" in str(exc.value)


def test_pre_process_dataframe_raises_for_invalid_column_type():
    df = pd.DataFrame({"name": ["Alice"]})
    config = [{"name": "name", "type": "UNSUPPORTED"}]

    with pytest.raises(Exception) as exc:
        pre_process_dataframe(df, config)

    assert "Invalid column type 'UNSUPPORTED'" in str(exc.value)


def test_pre_process_dataframe_integer_non_numeric_raises():
    df = pd.DataFrame({"age": ["x", "y"]})
    config = [{"name": "age", "type": "INTEGER"}]

    with pytest.raises(Exception) as exc:
        pre_process_dataframe(df, config)

    assert "Error processing column 'age'" in str(exc.value)


def test_pre_process_dataframe_sets_text_column_to_pending_llm_placeholder_by_default():
    df = pd.DataFrame({"notes": ["Long text", np.nan, "Another value"]})
    config = [{"name": "notes", "type": "TEXT", "index": 0}]

    processed, dropped = pre_process_dataframe(df, config)

    assert dropped == []
    assert processed["notes"].tolist() == [TEXT_PENDING_LLM, TEXT_PENDING_LLM, TEXT_PENDING_LLM]


def test_pre_process_dataframe_keeps_text_when_pending_replacement_disabled():
    df = pd.DataFrame({"notes": ["Long text", np.nan, "Another value"]})
    config = [{"name": "notes", "type": "TEXT", "index": 0}]

    processed, dropped = pre_process_dataframe(df, config, replace_text_with_pending=False)

    assert dropped == []
    assert processed["notes"].tolist() == ["Long text", pd.NA, "Another value"]


def test_post_process_dataframe_recreates_columns_casts_and_orders():
    df = pd.DataFrame(
        {
            "score": [1.2, 2.3],
            "name": [MISSING_VALUE_STRING, "Bob"],
            "active": ["true", "false"],
            "event_date": [1704067200, 1704153600],
            "age": [10.2, 11.6],
        }
    )

    config = _base_config() + [{"name": "extra_missing", "type": "STRING", "index": 5}]
    result = post_process_dataframe(df, config, all_missing_values_column=["all_missing"])

    assert result.columns.tolist() == ["name", "active", "event_date", "score", "age", "extra_missing"]
    assert "all_missing" not in result.columns
    assert str(result["name"].dtype) == "string"
    assert pd.isna(result["name"].iloc[0])
    assert str(result["active"].dtype) == "boolean"
    assert result["active"].tolist() == [True, False]
    assert str(result["event_date"].dtype) == "string"
    assert result["event_date"].tolist() == ["2024-01-01", "2024-01-02"]
    assert str(result["score"].dtype) == "Float64"
    assert str(result["age"].dtype) == "Int64"
    assert pd.isna(result["extra_missing"]).all()


def test_post_process_dataframe_sets_missing_text_to_pending_placeholder_by_default():
    df = pd.DataFrame({"notes": ["Generated", pd.NA]})
    config = [{"name": "notes", "type": "TEXT", "index": 0}]

    result = post_process_dataframe(df, config, all_missing_values_column=[])

    assert result.columns.tolist() == ["notes"]
    assert str(result["notes"].dtype) == "string"
    assert result["notes"].tolist() == ["Generated", TEXT_PENDING_LLM]


def test_post_process_dataframe_keeps_missing_text_na_when_pending_fill_disabled():
    df = pd.DataFrame({"notes": ["Generated", pd.NA]})
    config = [{"name": "notes", "type": "TEXT", "index": 0}]

    result = post_process_dataframe(df, config, all_missing_values_column=[], fill_text_with_pending=False)

    assert result.columns.tolist() == ["notes"]
    assert str(result["notes"].dtype) == "string"
    assert result["notes"].iloc[0] == "Generated"
    assert pd.isna(result["notes"].iloc[1])


def test_post_process_dataframe_missing_date_format_keeps_column_without_crashing():
    df = pd.DataFrame({"event_date": [1704067200]})
    config = [{"name": "event_date", "type": "DATE", "index": 0, "configurations": []}]

    with pytest.raises(ValueError) as exc:
        post_process_dataframe(df, config, all_missing_values_column=[])

    assert "Date format not specified for DATE column 'event_date'" in str(exc.value)


def test_post_process_dataframe_keeps_missing_date_values_missing():
    result = post_process_dataframe(
        pd.DataFrame({"event_date": [pd.NA]}),
        [{
            "name": "event_date",
            "type": "DATE",
            "index": 0,
            "configurations": [{"dateFormatter": "yyyy-MM-dd"}],
        }],
        all_missing_values_column=[],
    )

    assert pd.isna(result.loc[0, "event_date"])


def test_iso_to_strftime_converts_expected_tokens():
    assert iso_to_strftime("yyyy-MM-dd'T'HH:mm:ss.SSSSSS") == "%Y-%m-%dT%H:%M:%S.%f"


@pytest.mark.parametrize(
    ("date_format", "value"),
    [
        ("EEEE, MMMM d, yyyy", "Saturday, March 14, 1998"),
        ("yyyy-MM-dd", "1998-03-14"),
        ("yyyy:MM:dd", "1998:03:14"),
        ("yyyy.MM.dd", "1998.03.14"),
        ("dd-MM-yyyy", "14-03-1998"),
        ("dd:MM:yyyy", "14:03:1998"),
        ("dd.MM.yyyy", "14.03.1998"),
        ("dd/MM/yyyy", "14/03/1998"),
        ("MM-dd-yyyy", "03-14-1998"),
        ("MM.dd.yyyy", "03.14.1998"),
        ("MM:dd:yyyy", "03:14:1998"),
        ("MM/dd/yyyy", "03/14/1998"),
        ("dd.MM.yy", "14.03.98"),
    ],
)
def test_date_round_trip_supports_cinnamon_formats(date_format, value):
    config = [{
        "name": "event_date",
        "type": "DATE",
        "index": 0,
        "configurations": [{"name": "DateFormatConfiguration", "dateFormatter": date_format}],
    }]

    processed, dropped = pre_process_dataframe(pd.DataFrame({"event_date": [value]}), config)
    result = post_process_dataframe(processed, config, dropped)

    assert result.loc[0, "event_date"] == value


@pytest.mark.parametrize(
    ("date_format", "value"),
    [
        ("yyyy-MM-dd'T'HH:mm:ss", "1998-03-14T14:03:51"),
        ("dd.MM.yyyy HH:mm", "14.03.1998 14:03"),
        ("MM/dd/yyyy hh:mm:ss a", "03/14/1998 02:03:51 PM"),
        ("yyyy-MM-dd hh:mm:ss", "1998-03-14 08:03:51"),
        ("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "1998-03-14T14:03:51.123456"),
    ],
)
def test_date_time_round_trip_supports_cinnamon_formats(date_format, value):
    config = [{
        "name": "event_time",
        "type": "DATE_TIME",
        "index": 0,
        "configurations": [{"name": "DateTimeFormatConfiguration", "dateTimeFormatter": date_format}],
    }]

    processed, dropped = pre_process_dataframe(pd.DataFrame({"event_time": [value]}), config)
    result = post_process_dataframe(processed, config, dropped)

    assert result.loc[0, "event_time"] == value


@pytest.mark.parametrize(
    ("date_format", "exported_value", "expected_value"),
    [
        ("yyyy-MM-dd'T'HH:mm:ssX", "2017-01-01T06:15:00", "2017-01-01T06:15:00Z"),
        ("yyyy-MM-dd'T'HH:mm:ssXX", "2017-01-01T06:15:00", "2017-01-01T06:15:00Z"),
        ("yyyy-MM-dd'T'HH:mm:ssXXX", "2017-01-01T06:15:00", "2017-01-01T06:15:00Z"),
        ("yyyy-MM-dd'T'HH:mm:ss'Z'", "2017-01-01T06:15:00Z", "2017-01-01T06:15:00Z"),
        ("yyyy-MM-dd'T'HH:mm:ss VV", "2017-01-01T06:15:00 ", "2017-01-01T06:15:00 UTC"),
        ("E, y-M-d 'at' h:m:s a z", "Sun, 2017-1-1 at 6:15:0 AM ",
         "Sun, 2017-1-1 at 6:15:0 AM UTC"),
        ("E yyyy.MM.dd 'at' hh:mm:ss a zzz", "Sun 2017.01.01 at 06:15:00 AM ",
         "Sun 2017.01.01 at 06:15:00 AM UTC"),
    ],
)
def test_date_time_round_trip_restores_timezone_removed_by_cinnamon_export(
    date_format, exported_value, expected_value,
):
    config = [{
        "name": "event_time",
        "type": "DATE_TIME",
        "index": 0,
        "configurations": [{"name": "DateTimeFormatConfiguration", "dateTimeFormatter": date_format}],
    }]

    processed, dropped = pre_process_dataframe(pd.DataFrame({"event_time": [exported_value]}), config)
    result = post_process_dataframe(processed, config, dropped)

    assert result.loc[0, "event_time"] == expected_value


@pytest.mark.parametrize(
    ("column_type", "format_name", "format_key", "date_format", "minimum", "maximum", "sampled", "expected"),
    [
        ("DATE", "DateFormatConfiguration", "dateFormatter", "yyyy-MM-dd",
         "2000-01-01", "2030-01-01", "2040-01-01", "2030-01-01"),
        ("DATE_TIME", "DateTimeFormatConfiguration", "dateTimeFormatter", "yyyy-MM-dd'T'HH:mm:ss",
         "2000-01-01T00:00:00", "2030-01-01T12:00:00", "1990-01-01T00:00:00", "2000-01-01T00:00:00"),
    ],
)
def test_post_process_clamps_dates_to_cinnamon_range(
    column_type, format_name, format_key, date_format, minimum, maximum, sampled, expected,
):
    config = [{
        "name": "value",
        "type": column_type,
        "index": 0,
        "configurations": [
            {"name": format_name, format_key: date_format},
            {"name": "RangeConfiguration", "minValue": minimum, "maxValue": maximum},
        ],
    }]
    sampled_timestamp = pd.Timestamp(sampled).timestamp()

    result = post_process_dataframe(pd.DataFrame({"value": [sampled_timestamp]}), config, [])

    assert result.loc[0, "value"] == expected


def test_parse_to_unix_invalid_entry_returns_na():
    value = parse_to_unix("not-a-date", "%Y-%m-%d")
    assert pd.isna(value)


def test_adjust_date_within_bounds_post_clamps_out_of_range_values():
    assert adjust_date_within_bounds_post(-10_000_000_000) == float(-9214560000)
    assert adjust_date_within_bounds_post(10_000_000_000) == float(9214560000)
    assert adjust_date_within_bounds_post(1704067200) == 1704067200


def test_parse_to_date_format_invalid_entry_returns_original():
    assert parse_to_date_format("invalid", "%Y-%m-%d") == "invalid"
