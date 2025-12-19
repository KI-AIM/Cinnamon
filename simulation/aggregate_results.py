import argparse
import json
from pathlib import Path
from typing import Any, Dict, List, Tuple

try:
    from openpyxl import Workbook
except ImportError:  # pragma: no cover - optional dependency
    Workbook = None


DEFAULT_SUBFOLDERS = [
    "anon/tech_eval",
    "anon/risk_eval",
    "anon_synth/tech_eval",
    "anon_synth/risk_eval",
    "real_risk",
]

DEFAULT_DATASET = "heart_failure"
BASE_DIR = Path(__file__).resolve().parent
DEFAULT_ROOT = BASE_DIR / "experiment_results" / DEFAULT_DATASET

TECH_EVAL_FOLDERS = {
    "anon": "anon/tech_eval",
    "anon_synth": "anon_synth/tech_eval",
}

TECH_EVAL_METRICS = [
    "overall_resemblance",
    "overall_utility",
]

COMBINED_UTILITY_METRIC = "combined_utility"

RISK_FOLDERS = {
    "anon": "anon/risk_eval",
    "anon_synth": "anon_synth/risk_eval",
    "real": "real_risk",
}

RISK_METRICS = [
    "total_risk_score",
]

PRIVACY_METRIC = "privacy_score"

def load_json_files(folder: Path) -> List[Tuple[Path, Any]]:
    if not folder.exists():
        print(f"Warning: folder not found, skipping: {folder}")
        return []
    if not folder.is_dir():
        raise NotADirectoryError(f"Not a folder: {folder}")

    results: List[Tuple[Path, Any]] = []
    for json_path in sorted(folder.glob("*.json")):
        with json_path.open("r", encoding="utf-8") as handle:
            try:
                data = json.load(handle)
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid JSON in {json_path}") from exc
        results.append((json_path, data))
    return results


def load_all(root: Path, folders: List[str]) -> Dict[str, List[Tuple[Path, Any]]]:
    loaded: Dict[str, List[Tuple[Path, Any]]] = {}
    for folder in folders:
        folder_path = root / folder
        loaded[folder] = load_json_files(folder_path)
    return loaded


def extract_overview_metrics(payload: Any) -> Dict[str, Dict[str, Any]]:
    if not isinstance(payload, dict):
        return {}
    overview = payload.get("Overview") or payload.get("overview")
    if not isinstance(overview, dict):
        return {}
    aggregated = overview.get("aggregated_metrics", [])
    if not isinstance(aggregated, list):
        return {}

    metrics: Dict[str, Dict[str, Any]] = {}
    for item in aggregated:
        if not isinstance(item, dict):
            continue
        for metric in TECH_EVAL_METRICS:
            if metric in metrics:
                continue
            metric_payload = item.get(metric)
            if not isinstance(metric_payload, dict):
                continue
            values = metric_payload.get("values")
            if not isinstance(values, dict):
                continue
            metrics[metric] = {
                "real": values.get("real"),
                "synthetic": values.get("synthetic"),
            }
    return metrics


def extract_risk_metrics(payload: Any) -> Dict[str, Any]:
    if not isinstance(payload, dict):
        return {}
    metrics: Dict[str, Any] = {}
    for metric in RISK_METRICS:
        if metric in payload:
            metrics[metric] = payload.get(metric)
    return metrics


def build_rows(
    anon_items: List[Tuple[Path, Any]],
    anon_synth_items: List[Tuple[Path, Any]],
    anon_risk_items: List[Tuple[Path, Any]],
    anon_synth_risk_items: List[Tuple[Path, Any]],
    real_risk_items: List[Tuple[Path, Any]],
) -> List[Dict[str, Any]]:
    max_runs = max(
        len(anon_items),
        len(anon_synth_items),
        len(anon_risk_items),
        len(anon_synth_risk_items),
        len(real_risk_items),
    )
    rows: List[Dict[str, Any]] = []
    for run_index in range(max_runs):
        row: Dict[str, Any] = {"run": run_index + 1}
        anon_metrics = (
            extract_overview_metrics(anon_items[run_index][1])
            if run_index < len(anon_items)
            else {}
        )
        anon_synth_metrics = (
            extract_overview_metrics(anon_synth_items[run_index][1])
            if run_index < len(anon_synth_items)
            else {}
        )
        anon_risk = (
            extract_risk_metrics(anon_risk_items[run_index][1])
            if run_index < len(anon_risk_items)
            else {}
        )
        anon_synth_risk = (
            extract_risk_metrics(anon_synth_risk_items[run_index][1])
            if run_index < len(anon_synth_risk_items)
            else {}
        )
        real_risk = (
            extract_risk_metrics(real_risk_items[run_index][1])
            if run_index < len(real_risk_items)
            else {}
        )

        for metric in TECH_EVAL_METRICS:
            real_value = None
            if metric in anon_metrics and anon_metrics[metric].get("real") is not None:
                real_value = anon_metrics[metric].get("real")
            elif metric in anon_synth_metrics:
                real_value = anon_synth_metrics[metric].get("real")

            row[f"{metric}_real"] = real_value
            row[f"{metric}_anon"] = anon_metrics.get(metric, {}).get("synthetic")
            row[f"{metric}_anon_synth"] = anon_synth_metrics.get(metric, {}).get("synthetic")

        def combined_value(resemblance: Any, utility: Any) -> Any:
            if resemblance is None or utility is None:
                return None
            return (resemblance + utility) / 2

        row[f"{COMBINED_UTILITY_METRIC}_real"] = combined_value(
            row.get("overall_resemblance_real"), row.get("overall_utility_real")
        )
        row[f"{COMBINED_UTILITY_METRIC}_anon"] = combined_value(
            row.get("overall_resemblance_anon"), row.get("overall_utility_anon")
        )
        row[f"{COMBINED_UTILITY_METRIC}_anon_synth"] = combined_value(
            row.get("overall_resemblance_anon_synth"), row.get("overall_utility_anon_synth")
        )

        for metric in RISK_METRICS:
            real_value = real_risk.get(metric)
            anon_value = anon_risk.get(metric)
            anon_synth_value = anon_synth_risk.get(metric)
            row[f"{PRIVACY_METRIC}_real"] = (
                1.0 - real_value if real_value is not None else None
            )
            row[f"{PRIVACY_METRIC}_anon"] = (
                1.0 - anon_value if anon_value is not None else None
            )
            row[f"{PRIVACY_METRIC}_anon_synth"] = (
                1.0 - anon_synth_value if anon_synth_value is not None else None
            )

        rows.append(row)
    return rows


def write_excel(rows: List[Dict[str, Any]], output_path: Path) -> None:
    if Workbook is None:
        raise RuntimeError("openpyxl is required to write Excel files. Install it with: pip install openpyxl")
    workbook = Workbook()
    sheet = workbook.active
    sheet.title = "evaluation"

    headers = ["run"]
    for metric in TECH_EVAL_METRICS:
        headers.extend(
            [
                f"{metric}_real",
                f"{metric}_anon",
                f"{metric}_anon_synth",
            ]
        )
    headers.extend(
        [
            f"{COMBINED_UTILITY_METRIC}_real",
            f"{COMBINED_UTILITY_METRIC}_anon",
            f"{COMBINED_UTILITY_METRIC}_anon_synth",
        ]
    )
    headers.extend(
        [
            f"{PRIVACY_METRIC}_real",
            f"{PRIVACY_METRIC}_anon",
            f"{PRIVACY_METRIC}_anon_synth",
        ]
    )
    sheet.append(headers)
    for row in rows:
        sheet.append([row.get(header) for header in headers])

    output_path.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(output_path)


def main() -> None:
    parser = argparse.ArgumentParser(description="Load JSON result files for aggregation.")
    parser.add_argument(
        "--root",
        default=str(DEFAULT_ROOT),
        help="Base folder containing result subfolders (e.g., simulation/experiment_results/heart_failure).",
    )
    parser.add_argument(
        "--folders",
        nargs="*",
        default=DEFAULT_SUBFOLDERS,
        help="Subfolders under --root to scan for JSON files.",
    )
    parser.add_argument(
        "--output",
        help="Output Excel file path for the evaluation summary.",
    )
    args = parser.parse_args()

    root = Path(args.root)
    folders = args.folders
    output = Path(args.output) if args.output else root / "evaluation_summary.xlsx"

    loaded = load_all(root, folders)
    for folder, items in loaded.items():
        print(f"{folder}: {len(items)} JSON files")

    anon_items = loaded.get(TECH_EVAL_FOLDERS["anon"], [])
    anon_synth_items = loaded.get(TECH_EVAL_FOLDERS["anon_synth"], [])
    anon_risk_items = loaded.get(RISK_FOLDERS["anon"], [])
    anon_synth_risk_items = loaded.get(RISK_FOLDERS["anon_synth"], [])
    real_risk_items = loaded.get(RISK_FOLDERS["real"], [])
    rows = build_rows(
        anon_items,
        anon_synth_items,
        anon_risk_items,
        anon_synth_risk_items,
        real_risk_items,
    )
    write_excel(rows, output)
    print(f"Wrote evaluation summary to {output}")


if __name__ == "__main__":
    main()
