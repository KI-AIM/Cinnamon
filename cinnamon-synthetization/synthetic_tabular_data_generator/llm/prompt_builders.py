import json
from typing import Any, Dict, List, Optional, Sequence


def _row_count_phrase(num_rows: int) -> str:
    row_label = "row" if num_rows == 1 else "rows"
    return f"{num_rows} {row_label}"


def _domain_context_block(domain_context: str) -> str:
    if not domain_context:
        return ""
    return f"Domain context: {domain_context}\n"


def _reference_examples_block(examples: Optional[Sequence[Dict[str, Any]]], heading: str) -> str:
    if not examples:
        return ""
    return f"{heading}\n{json.dumps({'rows': list(examples)}, ensure_ascii=True)}\n"


def _knowledge_block(chunks: Optional[Sequence[str]], source_type: str) -> str:
    if not chunks:
        return ""
    normalized_source_type = source_type.strip() if source_type else "knowledge"
    return (
        f"Knowledge grounding ({normalized_source_type}):\n"
        f"{json.dumps({'knowledge_chunks': list(chunks)}, ensure_ascii=True)}\n"
    )


def build_text_enrichment_prompt_prefix(
    *,
    column_order: Sequence[str],
    text_columns: Sequence[str],
    profile_lines: Optional[Sequence[str]] = None,
    missing_value_string: str,
    domain_context: str = "",
) -> str:
    text_columns_text = ", ".join(text_columns)
    profile_section = ""
    if profile_lines:
        profile_block = "\n".join(profile_lines)
        profile_section = f"Column profiles derived from original data:\n{profile_block}\n"

    return (
        "You enrich one synthetic tabular row.\n"
        f"{_domain_context_block(domain_context)}"
        "Primary objective:\n"
        f"- Generate realistic values for TEXT columns: {text_columns_text}\n"
        "- Keep non-TEXT values unchanged unless they are clearly implausible in combination.\n"
        "- If you correct non-TEXT fields, use minimal changes and stay close to the original synthetic row.\n"
        "Output rules:\n"
        "- Return ONLY valid JSON.\n"
        "- Use exactly this shape: {\"row\": { ... }}\n"
        f"- Include all columns exactly in this list: {list(column_order)}\n"
        f"- For missing strings/text use '{missing_value_string}'\n"
        "- BOOLEAN values must be true/false.\n"
        "- DATE values must be UNIX timestamps in seconds.\n"
        f"{profile_section}"
    )


def build_text_enrichment_prompt_from_prefix(
    prompt_prefix: str,
    *,
    base_row: Dict[str, Any],
    reference_examples: Optional[Sequence[Dict[str, Any]]] = None,
    reference_heading: str = "Reference rows from original data (learn semantics and writing style, never copy):",
    primary_reference_row: Optional[Dict[str, Any]] = None,
    primary_reference_heading: str = (
        "Closest reference row from original data "
        "(use TEXT semantics and writing style from this row, never copy):"
    ),
    structural_reference_examples: Optional[Sequence[Dict[str, Any]]] = None,
    structural_reference_heading: str = (
        "Structured neighbor rows from original data "
        "(use only non-TEXT patterns from these rows; TEXT fields are masked on purpose):"
    ),
    knowledge_chunks: Optional[Sequence[str]] = None,
    knowledge_source_type: str = "none",
) -> str:
    primary_reference_block = ""
    if primary_reference_row:
        primary_reference_block = _reference_examples_block([primary_reference_row], primary_reference_heading)

    structural_reference_block = _reference_examples_block(
        structural_reference_examples,
        structural_reference_heading,
    )
    reference_block = _reference_examples_block(reference_examples, reference_heading)
    knowledge_block = _knowledge_block(knowledge_chunks, knowledge_source_type)

    return (
        f"{prompt_prefix}"
        f"{primary_reference_block}"
        f"{structural_reference_block}"
        f"{reference_block}"
        f"{knowledge_block}"
        "Current synthetic row:\n"
        f"{json.dumps({'row': base_row}, ensure_ascii=True)}"
    )


def build_text_enrichment_prompt(
    *,
    column_order: Sequence[str],
    text_columns: Sequence[str],
    profile_lines: Sequence[str],
    base_row: Dict[str, Any],
    missing_value_string: str,
    domain_context: str = "",
    reference_examples: Optional[Sequence[Dict[str, Any]]] = None,
    reference_heading: str = "Reference rows from original data (learn semantics and writing style, never copy):",
    primary_reference_row: Optional[Dict[str, Any]] = None,
    primary_reference_heading: str = (
        "Closest reference row from original data "
        "(use TEXT semantics and writing style from this row, never copy):"
    ),
    structural_reference_examples: Optional[Sequence[Dict[str, Any]]] = None,
    structural_reference_heading: str = (
        "Structured neighbor rows from original data "
        "(use only non-TEXT patterns from these rows; TEXT fields are masked on purpose):"
    ),
    knowledge_chunks: Optional[Sequence[str]] = None,
    knowledge_source_type: str = "none",
) -> str:
    prompt_prefix = build_text_enrichment_prompt_prefix(
        column_order=column_order,
        text_columns=text_columns,
        profile_lines=profile_lines,
        missing_value_string=missing_value_string,
        domain_context=domain_context,
    )
    return build_text_enrichment_prompt_from_prefix(
        prompt_prefix,
        base_row=base_row,
        reference_examples=reference_examples,
        reference_heading=reference_heading,
        primary_reference_row=primary_reference_row,
        primary_reference_heading=primary_reference_heading,
        structural_reference_examples=structural_reference_examples,
        structural_reference_heading=structural_reference_heading,
        knowledge_chunks=knowledge_chunks,
        knowledge_source_type=knowledge_source_type,
    )


def build_tabular_generation_prompt_prefix(
    *,
    ordered_columns: Sequence[str],
    profile_lines: Sequence[str],
    missing_value_string: str,
    domain_context: str = "",
) -> str:
    shape_example = {column_name: "<value>" for column_name in ordered_columns}
    shape_text = json.dumps({"rows": [shape_example]}, ensure_ascii=True)
    profile_block = "\n".join(profile_lines)

    return (
        "You are generating synthetic tabular rows.\n"
        f"{_domain_context_block(domain_context)}"
        "Return ONLY valid JSON with this exact shape:\n"
        f"{shape_text}\n"
        "Use one top-level key only: rows.\n"
        "No markdown, no comments, no code fences, no extra keys.\n"
        f"Use exactly these columns: {list(ordered_columns)}\n"
        "Never use generic column names like column_a, column_b, feature_1, field_1.\n"
        "Generation order constraint (single output step):\n"
        "- First determine all non-TEXT column values.\n"
        "- Then generate TEXT column values conditioned on those non-TEXT values.\n"
        "- Return only the final JSON rows output, no intermediate reasoning.\n"
        "Type rules:\n"
        "- INTEGER: integer number\n"
        "- DECIMAL: decimal number\n"
        "- DATE: UNIX timestamp in seconds as number\n"
        "- BOOLEAN: true or false\n"
        f"- STRING: plain text, use '{missing_value_string}' for missing\n"
        f"- TEXT: realistic free text, use '{missing_value_string}' for missing\n"
        "Column profiles:\n"
        f"{profile_block}\n"
        "Model realistic relationships between columns based on the profiles.\n"
    )


def build_tabular_generation_prompt_from_prefix(
    prompt_prefix: str,
    *,
    num_rows: int,
    few_shot_examples: Optional[Sequence[Dict[str, Any]]] = None,
) -> str:
    requested_row_count = _row_count_phrase(num_rows)
    reference_block = ""
    if few_shot_examples:
        reference_block = (
            "Reference examples (learn structure only, do not copy rows):\n"
            f"{json.dumps(list(few_shot_examples), ensure_ascii=True)}\n"
        )

    return (
        f"{prompt_prefix}"
        f"{reference_block}"
        "Generation task:\n"
        f"Generate exactly {requested_row_count}.\n"
        f"Return exactly {requested_row_count} in the rows array."
    )


def build_tabular_generation_prompt(
    *,
    ordered_columns: Sequence[str],
    profile_lines: Sequence[str],
    num_rows: int,
    missing_value_string: str,
    domain_context: str = "",
    few_shot_examples: Optional[Sequence[Dict[str, Any]]] = None,
) -> str:
    prompt_prefix = build_tabular_generation_prompt_prefix(
        ordered_columns=ordered_columns,
        profile_lines=profile_lines,
        missing_value_string=missing_value_string,
        domain_context=domain_context,
    )
    return build_tabular_generation_prompt_from_prefix(
        prompt_prefix,
        num_rows=num_rows,
        few_shot_examples=few_shot_examples,
    )
