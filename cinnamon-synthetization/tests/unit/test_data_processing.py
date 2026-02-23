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
from data_processing.train_test import split_train_test_cross_sectional
from data_processing.utils import (
    MISSING_VALUE_STRING,
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


def test_post_process_dataframe_missing_date_format_keeps_column_without_crashing():
    df = pd.DataFrame({"event_date": [1704067200]})
    config = [{"name": "event_date", "type": "DATE", "index": 0, "configurations": []}]

    result = post_process_dataframe(df, config, all_missing_values_column=[])

    assert result.columns.tolist() == ["event_date"]
    assert result["event_date"].iloc[0] == 1704067200


def test_split_train_test_cross_sectional_is_reproducible_and_size_correct():
    dataset = pd.DataFrame({"id": range(10), "val": range(100, 110)})
    fitting_config = {"train": 0.7}

    train_a, validate_a = split_train_test_cross_sectional(fitting_config, dataset, seed=7)
    train_b, validate_b = split_train_test_cross_sectional(fitting_config, dataset, seed=7)

    assert len(train_a) == 7
    assert len(validate_a) == 3
    assert train_a.index.tolist() == train_b.index.tolist()
    assert validate_a.index.tolist() == validate_b.index.tolist()
    assert set(train_a.index).isdisjoint(set(validate_a.index))
    assert set(train_a.index) | set(validate_a.index) == set(dataset.index)


def test_iso_to_strftime_converts_expected_tokens():
    assert iso_to_strftime("yyyy-MM-dd HH:mm:SS") == "%Y-%m-%d %H:%M:%S"


def test_parse_to_unix_invalid_entry_returns_na():
    value = parse_to_unix("not-a-date", "%Y-%m-%d")
    assert pd.isna(value)


def test_adjust_date_within_bounds_post_clamps_out_of_range_values():
    assert adjust_date_within_bounds_post(-10_000_000_000) == float(-9214560000)
    assert adjust_date_within_bounds_post(10_000_000_000) == float(9214560000)
    assert adjust_date_within_bounds_post(1704067200) == 1704067200


def test_parse_to_date_format_invalid_entry_returns_original():
    assert parse_to_date_format("invalid", "%Y-%m-%d") == "invalid"
