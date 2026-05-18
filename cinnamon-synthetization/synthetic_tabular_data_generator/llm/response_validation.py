from typing import Any, Dict, List


def require_non_empty_rows(rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    if rows:
        return rows
    raise ValueError("No rows were found in the LLM response.")


def require_first_dict_row(parsed_json: Any) -> Dict[str, Any]:
    if isinstance(parsed_json, dict):
        row = parsed_json.get("row")
        if isinstance(row, dict):
            return row

        rows = parsed_json.get("rows")
        if isinstance(rows, list):
            for candidate in rows:
                if isinstance(candidate, dict):
                    return candidate

    if isinstance(parsed_json, list):
        for candidate in parsed_json:
            if isinstance(candidate, dict):
                return candidate

    raise ValueError("No row object found in LLM response.")
