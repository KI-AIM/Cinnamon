import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from data_processing.utils import MISSING_VALUE_STRING, TEXT_PENDING_LLM
from synthetic_tabular_data_generator.llm.response_validation import require_first_dict_row
from synthetic_tabular_data_generator.llm.synthesizer_support import (
    ColumnProfileOptions,
    LlmSynthesizerSupport,
)


class _SupportHarness(LlmSynthesizerSupport):
    def __init__(self):
        self.updates = []

    def _report_remaining_time(self, stage, remaining_time):
        self.updates.append((stage, remaining_time))


def test_build_column_profile_for_text_excludes_configured_placeholder_values():
    support = _SupportHarness()
    df = pd.DataFrame(
        {
            "notes": [
                "actual note",
                "",
                MISSING_VALUE_STRING,
                TEXT_PENDING_LLM,
                "second note",
            ]
        }
    )

    profile = support.build_column_profile(
        df,
        "notes",
        "TEXT",
        options=ColumnProfileOptions(
            include_text_examples=True,
            text_example_limit=3,
            excluded_text_values=(MISSING_VALUE_STRING, TEXT_PENDING_LLM),
        ),
    )

    assert profile["kind"] == "text"
    assert profile["available"] is True
    assert profile["example_snippets"] == ["actual note", "second note"]


def test_parse_json_with_fallback_extracts_json_from_surrounding_text():
    parsed = LlmSynthesizerSupport.parse_json_with_fallback(
        'preface text {"rows": [{"age": 42, "group": "A"}]} trailing text'
    )

    assert parsed["rows"][0]["age"] == 42


def test_require_first_dict_row_prefers_row_key_and_falls_back_to_rows():
    row_from_row_key = require_first_dict_row({"row": {"age": 42}})
    row_from_rows_key = require_first_dict_row({"rows": [{"age": 43}]})

    assert row_from_row_key == {"age": 42}
    assert row_from_rows_key == {"age": 43}


def test_coerce_numeric_uses_fallback_and_clamps_to_profile_bounds():
    support = _SupportHarness()
    profiles = {
        "age": {
            "kind": "numeric",
            "available": True,
            "min": 18.0,
            "max": 65.0,
            "mean": 40.0,
        }
    }

    value = support.coerce_numeric("age", "INTEGER", "200", profiles)
    fallback_value = support.coerce_numeric("age", "INTEGER", None, profiles, fallback_value="15")
    default_value = support.coerce_numeric("age", "INTEGER", None, profiles)

    assert value == 65
    assert fallback_value == 18
    assert default_value == 40


def test_report_remaining_time_reports_zero_when_finished():
    support = _SupportHarness()
    support.report_remaining_time(sample_start_time=1.0, generated=3, total=3)

    assert support.updates[-1] == ("sampling", 0)


def test_serialize_row_for_prompt_formats_date_columns_using_configured_date_format():
    support = _SupportHarness()

    serialized = support.serialize_row_for_prompt(
        {"event_date": 1704067200, "group": "A"},
        [
            {"name": "event_date", "type": "DATE", "configurations": [{"dateFormatter": "yyyy-MM-dd"}]},
            {"name": "group", "type": "STRING"},
        ],
    )

    assert serialized == {"event_date": "2024-01-01", "group": "A"}


def test_coerce_date_accepts_human_readable_date_strings():
    support = _SupportHarness()

    value = support.coerce_date(
        "event_date",
        "2024-01-01",
        {},
        column_config={"name": "event_date", "type": "DATE", "configurations": [{"dateFormatter": "yyyy-MM-dd"}]},
    )

    assert value == 1704067200
