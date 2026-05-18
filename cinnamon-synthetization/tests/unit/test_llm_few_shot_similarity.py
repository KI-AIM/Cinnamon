import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.llm.few_shot_similarity import (
    StructuredAttributeNearestNeighborIndex,
    select_structured_attribute_neighbors,
)


def test_select_structured_attribute_neighbors_prefers_closest_mixed_type_rows():
    reference_df = pd.DataFrame(
        {
            "age": [40, 52, 61],
            "group": ["A", "B", "A"],
            "notes": [
                "Patient stable after treatment.",
                "Requires follow-up in two weeks.",
                "No acute findings and good recovery.",
            ],
        }
    )
    column_configs = [
        {"name": "age", "type": "INTEGER"},
        {"name": "group", "type": "STRING"},
        {"name": "notes", "type": "TEXT"},
    ]

    neighbors = select_structured_attribute_neighbors(
        base_row={"age": 53, "group": "B", "notes": "__MISSING_VALUE__"},
        reference_df=reference_df,
        column_configs=column_configs,
        k=2,
    )

    assert len(neighbors) == 2
    assert neighbors[0]["age"] == 52
    assert neighbors[0]["group"] == "B"
    assert neighbors[0]["notes"] == "Requires follow-up in two weeks."
    assert neighbors[1]["age"] == 61


def test_select_structured_attribute_neighbors_uses_date_and_boolean_distance():
    reference_df = pd.DataFrame(
        {
            "event_date": [1_700_000_000, 1_700_086_400, 1_700_604_800],
            "critical": [False, True, True],
            "notes": [
                "Routine check.",
                "Urgent follow-up required.",
                "Delayed but stable review.",
            ],
        }
    )
    column_configs = [
        {"name": "event_date", "type": "DATE"},
        {"name": "critical", "type": "BOOLEAN"},
        {"name": "notes", "type": "TEXT"},
    ]

    neighbors = select_structured_attribute_neighbors(
        base_row={"event_date": 1_700_120_000, "critical": True, "notes": "__MISSING_VALUE__"},
        reference_df=reference_df,
        column_configs=column_configs,
        k=1,
    )

    assert len(neighbors) == 1
    assert neighbors[0]["critical"] is True
    assert neighbors[0]["notes"] == "Urgent follow-up required."


def test_select_structured_attribute_neighbors_can_reuse_precomputed_index():
    reference_df = pd.DataFrame(
        {
            "age": [40, 52, 61],
            "group": ["A", "B", "A"],
            "notes": [
                "Patient stable after treatment.",
                "Requires follow-up in two weeks.",
                "No acute findings and good recovery.",
            ],
        }
    )
    column_configs = [
        {"name": "age", "type": "INTEGER"},
        {"name": "group", "type": "STRING"},
        {"name": "notes", "type": "TEXT"},
    ]
    neighbor_index = StructuredAttributeNearestNeighborIndex(
        reference_df=reference_df,
        column_configs=column_configs,
    )

    neighbors = select_structured_attribute_neighbors(
        base_row={"age": 60, "group": "A", "notes": "__MISSING_VALUE__"},
        reference_df=reference_df,
        column_configs=column_configs,
        k=1,
        neighbor_index=neighbor_index,
    )

    assert len(neighbors) == 1
    assert neighbors[0]["age"] == 61
