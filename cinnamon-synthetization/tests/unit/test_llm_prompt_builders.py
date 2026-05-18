import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from synthetic_tabular_data_generator.llm.prompt_builders import build_tabular_generation_prompt


def test_build_tabular_generation_prompt_uses_singular_for_one_row():
    prompt = build_tabular_generation_prompt(
        ordered_columns=["age", "group"],
        profile_lines=["- age (INTEGER): min=18, max=80", "- group (STRING): frequent values [A, B]"],
        num_rows=1,
        missing_value_string="MISSING",
    )

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
