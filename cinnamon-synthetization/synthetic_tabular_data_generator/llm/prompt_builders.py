import json
from typing import Any, Dict, List, Optional, Sequence


def _row_count_phrase(num_rows: int) -> str:
    row_label = "row" if num_rows == 1 else "rows"
    return f"{num_rows} {row_label}"


def _domain_context_block(domain_context: str) -> str:
    if not domain_context:
        return ""
    return f"Domain context: {domain_context}\n"


def _section_heading(title: str) -> str:
    return f"{title}\n{'-' * 40}\n\n"


def _reference_examples_block(examples: Optional[Sequence[Dict[str, Any]]], heading: str) -> str:
    if not examples:
        return ""
    return f"{_section_heading(heading)}{json.dumps({'rows': list(examples)}, ensure_ascii=True)}\n\n"


def _single_row_block(row: Dict[str, Any], heading: str) -> str:
    return f"{_section_heading(heading)}{json.dumps({'row': row}, ensure_ascii=True)}\n\n"


def _knowledge_block(chunks: Optional[Sequence[str]], source_type: str) -> str:
    if not chunks:
        return ""
    normalized_source_type = source_type.strip() if source_type else "knowledge"
    return (
        f"{_section_heading(f'KNOWLEDGE GROUNDING ({normalized_source_type.upper()})')}"
        f"{json.dumps({'knowledge_chunks': list(chunks)}, ensure_ascii=True)}\n\n"
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
        "You generate a new synthetic text value for a synthetic table row.\n"
        f"{_domain_context_block(domain_context)}"
        "Important:\n"
        "- You are not reconstructing an original record.\n"
        "- You are creating a new synthetic record in the same content category.\n"
        "Task structure:\n"
        "- Perform the task in two internal phases.\n"
        "- Phase 1: Repair the non-TEXT row if needed.\n"
        "- Phase 2: Generate the TEXT using the repaired row.\n"
        "- Return only the final repaired row with generated TEXT as JSON.\n"
        "Reference usage:\n"
        "- The current synthetic row defines the starting constraints of the new synthetic record.\n"
        "- The most similar neighboring record is the strongest semantic reference.\n"
        "- Additional neighboring records are weaker references for structure, style, and variation.\n"
        "- The closest reference text is not the target. It is only inspiration.\n"
        "- You can orient yourself to the references, but do not copy sentences, exact values, exact sequences, placeholder names, or complete section content.\n"
        "- Do not mix specific facts from multiple neighboring records into one record.\n"
        "Row repair rules:\n"
        "- Before generating the TEXT, determine the dominant content category of the synthetic row using all non-TEXT fields, especially keywords.\n"
        "- Keep non-TEXT values unchanged unless they are clearly implausible in combination.\n"
        "- If the structured row is obviously inconsistent, determine the most coherent values for the structured attributes from:\n"
        "  1. the most similar reference text,\n"
        "  2. the remaining structured fields.\n"
        "- Then minimally correct the contradictory non-TEXT fields so that they fit the coherent record.\n"
        "- If you generate TEXT for a domain, all non-TEXT fields that contradict this domain must be corrected in the JSON output.\n"
        "Text generation rules:\n"
        f"- Generate realistic values for TEXT columns: {text_columns_text}\n"
        "- The generated TEXT must match the dominant content category of the repaired row.\n"
        "- Use an appropriate length. Do not imitate the reference length.\n"
        "- Vary concrete details when they are not fixed by the repaired row.\n"
        "- Do not generate text from a different content domain.\n"
        "- Avoid contradictions between the repaired non-TEXT fields and the generated TEXT.\n"
        "Safety rules:\n"
        "- Do not generate direct identifiers.\n"
        "- Do not use exact dates from the texts unless they are explicitly provided in the current synthetic row.\n"
        "Output rules:\n"
        "- Return ONLY valid JSON.\n"
        "- Use exactly this shape: {\"row\": { ... }}\n"
        f"- Include all columns exactly in this list: {list(column_order)}\n"
        f"- For missing strings/text use '{missing_value_string}'\n"
        "- BOOLEAN values must be true/false.\n"
        "- DATE values must be UNIX timestamps in seconds.\n"
        "\n"
        f"{profile_section}"
    )


def build_text_enrichment_prompt_from_prefix(
    prompt_prefix: str,
    *,
    base_row: Dict[str, Any],
    reference_examples: Optional[Sequence[Dict[str, Any]]] = None,
    reference_heading: str = "NEIGHBORING EXAMPLES",
    primary_reference_row: Optional[Dict[str, Any]] = None,
    primary_reference_heading: str = "MOST SIMILAR EXAMPLE",
    knowledge_chunks: Optional[Sequence[str]] = None,
    knowledge_source_type: str = "none",
) -> str:
    primary_reference_block = ""
    if primary_reference_row:
        primary_reference_block = _single_row_block(primary_reference_row, primary_reference_heading)

    reference_block = _reference_examples_block(reference_examples, reference_heading)
    knowledge_block = _knowledge_block(knowledge_chunks, knowledge_source_type)

    return (
        f"{prompt_prefix}"
        f"{primary_reference_block}"
        f"{reference_block}"
        f"{knowledge_block}"
        f"{_single_row_block(base_row, 'SYNTHETIC EXAMPLE')}"
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
    reference_heading: str = "NEIGHBORING EXAMPLES",
    primary_reference_row: Optional[Dict[str, Any]] = None,
    primary_reference_heading: str = "MOST SIMILAR EXAMPLE",
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
        "You generate new synthetic tabular rows.\n"
        f"{_domain_context_block(domain_context)}"
        "Important:\n"
        "- You are not reconstructing original records.\n"
        "- You are creating new synthetic records in the same content category.\n"
        "Task structure:\n"
        "- Perform the task in two internal phases for each row.\n"
        "- Phase 1: Determine or repair the non-TEXT fields.\n"
        "- Phase 2: Generate the TEXT fields from the repaired row.\n"
        "- Return only the final rows JSON.\n"
        "Reference usage:\n"
        "- Column schema and profiles define the allowed structure, value types, value ranges, and typical distributions.\n"
        "- Reference examples are supporting examples for structure, style, realistic combinations, and level of detail.\n"
        "- You can use them as inspiration and orientation, but do not copy sentences, exact values, exact sequences, placeholder names, complete section content, or characteristic combinations of details.\n"
        "Row repair rules:\n"
        "- Each generated row must be internally coherent across structured and TEXT columns.\n"
        "- Before generating any TEXT field, determine the dominant content category of the row from the non-TEXT fields, especially keywords.\n"
        "- If a row is obviously inconsistent, determine the most coherent structured values from the strongest reference signals and the remaining structured fields first.\n"
        "- If you generate TEXT for a domain, all non-TEXT fields that contradict this domain must be corrected in the JSON output.\n"
        "- Then minimally correct the contradictory non-TEXT fields before generating TEXT.\n"
        "Text generation rules:\n"
        "- Every generated TEXT field must match the dominant content category.\n"
        "- Use an appropriate length. Do not imitate the reference length.\n"
        "- Do not generate text from a different content domain.\n"
        "- Avoid contradictions between structured data and generated text.\n"
        "Return ONLY valid JSON with this exact shape:\n"
        f"{shape_text}\n"
        "Use one top-level key only: rows.\n"
        "No markdown, no comments, no code fences, no extra keys.\n"
        f"Use exactly these columns: {list(ordered_columns)}\n"
        "Never use generic column names like column_a, column_b, feature_1, field_1.\n"
        "Safety rules:\n"
        "- Do not generate direct identifiers unless they are clearly part of the intended synthetic schema.\n"
        "- Do not use exact dates from reference examples unless such dates are plausibly generated as new synthetic values.\n"
        "Generation order constraint (single output step):\n"
        "- First determine all non-TEXT column values.\n"
        "- Then determine the dominant content category of the row from those non-TEXT values.\n"
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
        reference_block = f"{_section_heading('REFERENCE EXAMPLES')}{json.dumps(list(few_shot_examples), ensure_ascii=True)}\n\n"

    return (
        f"{prompt_prefix}"
        f"{reference_block}"
        f"{_section_heading('GENERATION TASK')}"
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
