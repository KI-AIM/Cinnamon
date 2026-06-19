import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.llm.prompt_builders import build_tabular_generation_prompt
from synthetic_tabular_data_generator.llm.prompt_builders import build_text_enrichment_prompt_prefix
from synthetic_tabular_data_generator.llm.prompt_builders import build_text_enrichment_prompt_from_prefix


def test_build_tabular_generation_prompt_uses_singular_for_one_row():
    prompt = build_tabular_generation_prompt(
        ordered_columns=["age", "group"],
        profile_lines=["- age (INTEGER): min=18, max=80", "- group (STRING): frequent values [A, B]"],
        num_rows=1,
        missing_value_string="MISSING",
    )

    assert "You generate new synthetic tabular rows." in prompt
    assert "You are not reconstructing original records." in prompt
    assert "GENERATION TASK" in prompt
    assert "Generate exactly 1 row." in prompt
    assert "Return exactly 1 row in the rows array." in prompt


def test_build_tabular_generation_prompt_uses_plural_for_multiple_rows():
    prompt = build_tabular_generation_prompt(
        ordered_columns=["age", "group"],
        profile_lines=["- age (INTEGER): min=18, max=80", "- group (STRING): frequent values [A, B]"],
        num_rows=3,
        missing_value_string="MISSING",
    )

    assert "Generate exactly 3 rows." in prompt
    assert "Return exactly 3 rows in the rows array." in prompt
    assert "Reference examples from original data" not in prompt
    assert "REFERENCE EXAMPLES" not in prompt


def test_prompt_builders_keep_umlauts_readable_in_json_blocks():
    prompt = build_text_enrichment_prompt_from_prefix(
        "prefix\n",
        base_row={"geschlecht": "männlich"},
        reference_examples=[{"geschlecht": "weiblich"}],
    )

    assert '"geschlecht": "männlich"' in prompt
    assert '"geschlecht": "weiblich"' in prompt
    assert "\\u00e4" not in prompt


def test_text_enrichment_prompt_prefix_marks_repaired_structured_values_as_ground_truth():
    prompt = build_text_enrichment_prompt_prefix(
        column_order=["aufnahme_datum", "geschlecht", "dokument_text"],
        text_columns=["dokument_text"],
        missing_value_string="MISSING",
    )

    assert "Treat the repaired non-TEXT fields as ground truth for this row." in prompt
    assert (
        "If a repaired structured value is the kind of fact that is typically mentioned explicitly in texts of this kind, the generated TEXT must use that same value."
        in prompt
    )
