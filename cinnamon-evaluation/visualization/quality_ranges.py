from typing import Dict, List, Optional, Union

QUALITY_RANGES: Dict[str, List[Dict[str, Union[str, float]]]] = {
    "overall_resemblance": [
        {
            "quality": "High",
            "range_label": "High resemblance range",
            "range_low": 0.85,
            "range_high": 1.0,
        },
        {
            "quality": "Medium",
            "range_label": "Moderate resemblance range",
            "range_low": 0.7,
            "range_high": 0.85,
        },
        {
            "quality": "Low",
            "range_label": "Low resemblance range",
            "range_low": 0.0,
            "range_high": 0.7,
        },
    ],
    "overall_utility": [
        {
            "quality": "High",
            "range_label": "High utility range",
            "range_low": 0.85,
            "range_high": 1.0,
        },
        {
            "quality": "Medium",
            "range_label": "Moderate utility range",
            "range_low": 0.7,
            "range_high": 0.85,
        },
        {
            "quality": "Low",
            "range_label": "Low utility range",
            "range_low": 0.0,
            "range_high": 0.7,
        },
    ],
    "default": [
        {
            "quality": "High",
            "range_label": "High performance range",
            "range_low": 0.85,
            "range_high": 1.0,
        },
        {
            "quality": "Medium",
            "range_label": "Moderate performance range",
            "range_low": 0.7,
            "range_high": 0.85,
        },
        {
            "quality": "Low",
            "range_label": "Low performance range",
            "range_low": 0.0,
            "range_high": 0.7,
        },
    ],
}

UNKNOWN_QUALITY_RANGE: Dict[str, Optional[Union[str, float]]] = {
    "quality": "Unknown",
    "range_label": "Quality range unavailable",
    "range_low": None,
    "range_high": None,
}


def determine_quality_range(
    metric_key: str, value: Optional[Union[int, float]]
) -> Dict[str, Optional[Union[str, float]]]:
    """
    Determine the qualitative assessment for a metric value based on configured ranges.

    Args:
        metric_key: Key identifying the metric (e.g., 'overall_resemblance').
        value: Metric value to classify.

    Returns:
        dict: Quality information including label and numeric range.
    """
    ranges = QUALITY_RANGES.get(metric_key, QUALITY_RANGES.get("default", []))

    if value is None:
        return UNKNOWN_QUALITY_RANGE.copy()

    try:
        numeric_value = float(value)
    except (TypeError, ValueError):
        return UNKNOWN_QUALITY_RANGE.copy()

    numeric_ranges = [
        range_info
        for range_info in ranges
        if isinstance(range_info.get("range_low"), (int, float))
        and isinstance(range_info.get("range_high"), (int, float))
    ]

    if numeric_ranges:
        min_low = min(range_info["range_low"] for range_info in numeric_ranges)
        max_high = max(range_info["range_high"] for range_info in numeric_ranges)
        numeric_value = max(min(numeric_value, max_high), min_low)

    for index, range_info in enumerate(ranges):
        lower = range_info.get("range_low")
        upper = range_info.get("range_high")
        if isinstance(lower, (int, float)) and isinstance(upper, (int, float)):
            is_last_range = index == len(ranges) - 1
            in_range = lower <= numeric_value < upper or (is_last_range and numeric_value <= upper)
            if in_range:
                return {
                    "quality": range_info["quality"],
                    "range_label": range_info["range_label"],
                    "range_low": lower,
                    "range_high": upper,
                }

    if ranges:
        fallback = ranges[-1]
        return {
            "quality": fallback.get("quality"),
            "range_label": fallback.get("range_label"),
            "range_low": fallback.get("range_low"),
            "range_high": fallback.get("range_high"),
        }

    return UNKNOWN_QUALITY_RANGE.copy()


