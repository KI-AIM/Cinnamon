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


def _json_block(payload: Dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2)


def _reference_examples_block(examples: Optional[Sequence[Dict[str, Any]]], heading: str) -> str:
    if not examples:
        return ""
    return f"{_section_heading(heading)}{_json_block({'rows': list(examples)})}\n\n"


def _single_row_block(row: Dict[str, Any], heading: str) -> str:
    return f"{_section_heading(heading)}{_json_block({'row': row})}\n\n"


def _knowledge_block(chunks: Optional[Sequence[str]], source_type: str) -> str:
    if not chunks:
        return ""
    normalized_source_type = source_type.strip() if source_type else "knowledge"
    return (
        f"{_section_heading(f'KNOWLEDGE GROUNDING ({normalized_source_type.upper()})')}"
        f"{_json_block({'knowledge_chunks': list(chunks)})}\n\n"
    )


def build_tabular_non_text_generation_prompt_prefix(
    *,
    ordered_columns: Sequence[str],
    non_text_columns: Sequence[str],
    text_columns: Sequence[str],
    profile_lines: Sequence[str],
    missing_value_string: str,
    domain_context: str = "",
) -> str:
    shape_example = {column_name: "<value>" for column_name in ordered_columns}
    shape_text = json.dumps({"rows": [shape_example]}, ensure_ascii=False)
    profile_block = "\n".join(profile_lines)

    return (
        "You generate non-TEXT fields for synthetic tabular rows.\n"
        f"{_domain_context_block(domain_context)}"
        "Important:\n"
        "- Do not reconstruct an original record.\n"
        "- You are creating a new synthetic record in the same content category.\n"
        "- Reference rows are examples, not ground truth.\n"
        "- Column profiles describe plausibility, not strict rules.\n"
        "Task:\n"
        "- Generate only the non-TEXT fields first.\n"
        "- TEXT fields must not be generated in this step.\n"
        f"- Set TEXT columns ({', '.join(text_columns)}) to '{missing_value_string}'.\n"
        "Reference usage:\n"
        "- Column schema and profiles define the allowed structure, value types, value ranges, and typical distributions.\n"
        "- Reference examples are supporting examples for realistic structured combinations.\n"
        "- You can use them as inspiration and orientation, but do not copy exact combinations of details.\n"
        "Consistency rules:\n"
        f"- Generate coherent values for these non-TEXT columns: {list(non_text_columns)}\n"
        "- Check for numerical contradictions.\n"
        "- Check for impossible or illogical date/order relations.\n"
        "- Check for boolean inconsistencies.\n"
        "- Check for other implausible combinations of structured values.\n"
        "- Determine the dominant content category of the row from the non-TEXT fields, especially keywords.\n"
        "- The generated non-TEXT values must be internally coherent.\n"
        "- If one chosen value implies constraints on other fields, satisfy those constraints consistently.\n"
        "- Avoid derived, temporal, categorical, or semantic contradictions.\n"
        "- Keep unusual but plausible combinations possible.\n"
        "Output rules:\n"
        "- Return ONLY valid JSON with this exact shape:\n"
        f"{shape_text}\n"
        "- Use one top-level key only: rows.\n"
        "- No markdown, no comments, no code fences, no extra keys.\n"
        f"- Use exactly these columns: {list(ordered_columns)}\n"
        "Type rules:\n"
        "- INTEGER: integer number\n"
        "- DECIMAL: decimal number\n"
        "- DATE: human-readable date string in the same format shown in the examples\n"
        "- BOOLEAN: true or false\n"
        f"- STRING: plain text, use '{missing_value_string}' for missing\n"
        f"- TEXT: always use '{missing_value_string}' in this step\n"
        "Column profiles:\n"
        f"{profile_block}\n"
        "Model realistic relationships between columns based on the profiles.\n"
    )


def build_tabular_non_text_generation_prompt_from_prefix(
    prompt_prefix: str,
    *,
    num_rows: int,
    few_shot_examples: Optional[Sequence[Dict[str, Any]]] = None,
) -> str:
    requested_row_count = _row_count_phrase(num_rows)
    reference_block = ""
    if few_shot_examples:
        reference_block = f"{_section_heading('REFERENCE EXAMPLES')}{json.dumps(list(few_shot_examples), ensure_ascii=False, indent=2)}\n\n"

    return (
        f"{prompt_prefix}"
        f"{reference_block}"
        f"{_section_heading('GENERATION TASK')}"
        f"Generate exactly {requested_row_count}.\n"
        f"Return exactly {requested_row_count} in the rows array."
    )


def build_tabular_text_completion_prompt_prefix(
    *,
    column_order: Sequence[str],
    text_columns: Sequence[str],
    missing_value_string: str,
    domain_context: str = "",
) -> str:
    return (
        "You generate TEXT fields for repaired synthetic tabular rows.\n"
        f"{_domain_context_block(domain_context)}"
        "Important:\n"
        "- The non-TEXT fields are already fixed for this row.\n"
        "- Do not modify any non-TEXT field in this step.\n"
        "- Treat the structured fields as ground truth for this row.\n"
        "- You are not reconstructing an original record.\n"
        "- You are creating a new synthetic record in the same content category.\n"
        "Reference usage:\n"
        "- The synthetic row defines the fixed structured constraints for the generated TEXT.\n"
        "- Reference examples are supporting examples for structure, style, wording, and level of detail.\n"
        "- You can orient yourself to the references, but do not copy sentences, exact values, exact sequences, placeholder names, or complete section content.\n"
        "Text generation rules:\n"
        f"- Generate realistic values for TEXT columns: {', '.join(text_columns)}\n"
        "- Determine the dominant content category of the row from the non-TEXT fields, especially keywords.\n"
        "- Every generated TEXT field must match this dominant content category.\n"
        "- If a structured value is the kind of fact that is typically mentioned explicitly in texts of this kind, the generated TEXT must use that same value.\n"
        "- If such a fact is normally verbalized, do not omit it and do not replace it with a conflicting value.\n"
        "- Use an appropriate length. Do not imitate the reference length.\n"
        "- Avoid contradictions between the structured row and the generated TEXT.\n"
        "Output rules:\n"
        "- Return ONLY valid JSON.\n"
        "- Use exactly this shape: {\"row\": { ... }}\n"
        f"- Include all columns exactly in this list: {list(column_order)}\n"
        f"- For missing strings/text use '{missing_value_string}'\n"
        "- BOOLEAN values must be true/false.\n"
        "- DATE values must use the same human-readable date format shown in the examples.\n"
        "\n"
    )


def build_tabular_text_completion_prompt_from_prefix(
    prompt_prefix: str,
    *,
    base_row: Dict[str, Any],
    reference_examples: Optional[Sequence[Dict[str, Any]]] = None,
) -> str:
    reference_block = _reference_examples_block(reference_examples, "REFERENCE EXAMPLES")
    return (
        f"{prompt_prefix}"
        f"{reference_block}"
        f"{_single_row_block(base_row, 'SYNTHETIC EXAMPLE')}"
    )


def build_tabular_generation_prompt_prefix(
    *,
    ordered_columns: Sequence[str],
    profile_lines: Sequence[str],
    missing_value_string: str,
    domain_context: str = "",
) -> str:
    shape_example = {column_name: "<value>" for column_name in ordered_columns}
    shape_text = json.dumps({"rows": [shape_example]}, ensure_ascii=False)
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
        "Phase 1 rules:\n"
        "- Do not reconstruct an original record.\n"
        "- Keep values unchanged unless they are clearly inconsistent.\n"
        "- Reference rows are examples, not ground truth.\n"
        "- Column profiles describe plausibility, not strict rules.\n"
        "- Make the smallest number of changes necessary.\n"
        "Reference usage:\n"
        "- Column schema and profiles define the allowed structure, value types, value ranges, and typical distributions.\n"
        "- Reference examples are supporting examples for structure, style, realistic combinations, and level of detail.\n"
        "- You can use them as inspiration and orientation, but do not copy sentences, exact values, exact sequences, placeholder names, complete section content, or characteristic combinations of details.\n"
        "Row repair rules:\n"
        "- Each generated row must be internally coherent across structured and TEXT columns.\n"
        "- Check for numerical contradictions.\n"
        "- Check for impossible or illogical date/order relations.\n"
        "- Check for boolean inconsistencies.\n"
        "- Check for other implausible combinations of structured values.\n"
        "- Before generating any TEXT field, determine the dominant content category of the row from the non-TEXT fields, especially keywords.\n"
        "- Fix clearly derived or calculated fields when they are inconsistent.\n"
        "- Fix impossible temporal relations.\n"
        "- Fix categorical or semantic contradictions.\n"
        "- Avoid changing identity-like fields unless clearly necessary.\n"
        "- Keep unusual but plausible values unchanged.\n"
        "- If you generate TEXT for a domain, minimally correct only the contradictory non-TEXT fields needed to support a coherent final row.\n"
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
        "- DATE: human-readable date string in the same format shown in the examples\n"
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
        reference_block = f"{_section_heading('REFERENCE EXAMPLES')}{json.dumps(list(few_shot_examples), ensure_ascii=False, indent=2)}\n\n"

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

