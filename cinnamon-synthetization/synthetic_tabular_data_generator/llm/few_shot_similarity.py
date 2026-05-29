from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Sequence

import numpy as np
import pandas as pd

from data_processing.utils import BOOLEAN_MAP, MISSING_VALUE_STRING
from synthetic_tabular_data_generator.llm.synthesizer_support import LlmSynthesizerSupport


STRUCTURED_EXCLUDED_TYPES = {"TEXT"}


@dataclass(frozen=True)
class _StructuredColumnSpec:
    name: str
    column_type: str
    numeric_min: float = 0.0
    numeric_range: float = 0.0


class StructuredAttributeNearestNeighborIndex:
    def __init__(
        self,
        *,
        reference_df: pd.DataFrame,
        column_configs: Sequence[Dict[str, Any]],
        missing_value_string: str = MISSING_VALUE_STRING,
    ) -> None:
        if reference_df.empty:
            raise ValueError("reference_df must not be empty.")

        self._missing_value_string = missing_value_string
        self._reference_df = reference_df.reset_index(drop=True).copy()
        self._structured_column_specs = self._build_column_specs(self._reference_df, column_configs)

        self._reference_records = self._reference_df.to_dict(orient="records")
        self._numeric_arrays: Dict[str, np.ndarray] = {}
        self._categorical_arrays: Dict[str, np.ndarray] = {}
        self._row_count = len(self._reference_df)

        self._prepare_reference_arrays()

    def select_neighbors(self, base_row: Dict[str, Any], k: int) -> List[Dict[str, Any]]:
        if k <= 0 or self._row_count == 0:
            return []

        if not self._structured_column_specs:
            return self._reference_records[: min(k, self._row_count)]

        distances = self._compute_distances(base_row)
        top_k = min(k, self._row_count)
        ordered_indices = np.argsort(distances, kind="stable")[:top_k]
        return [self._reference_records[int(index)] for index in ordered_indices]

    @staticmethod
    def _build_column_specs(
        reference_df: pd.DataFrame,
        column_configs: Sequence[Dict[str, Any]],
    ) -> List[_StructuredColumnSpec]:
        specs: List[_StructuredColumnSpec] = []
        for config in column_configs:
            column_type = str(config.get("type", "STRING")).upper()
            if column_type in STRUCTURED_EXCLUDED_TYPES:
                continue

            column_name = config["name"]
            if column_type in LlmSynthesizerSupport.NUMERIC_TYPES and column_name in reference_df.columns:
                numeric_series = pd.to_numeric(reference_df[column_name], errors="coerce").dropna()
                if numeric_series.empty:
                    specs.append(_StructuredColumnSpec(name=column_name, column_type=column_type))
                    continue

                numeric_min = float(numeric_series.min())
                numeric_range = float(numeric_series.max() - numeric_min)
                specs.append(
                    _StructuredColumnSpec(
                        name=column_name,
                        column_type=column_type,
                        numeric_min=numeric_min,
                        numeric_range=numeric_range,
                    )
                )
                continue

            specs.append(_StructuredColumnSpec(name=column_name, column_type=column_type))

        return specs

    def _prepare_reference_arrays(self) -> None:
        for spec in self._structured_column_specs:
            if spec.column_type in LlmSynthesizerSupport.NUMERIC_TYPES:
                self._numeric_arrays[spec.name] = self._build_numeric_array(spec.name)
                continue
            self._categorical_arrays[spec.name] = self._build_categorical_array(spec)

    def _compute_distances(self, base_row: Dict[str, Any]) -> np.ndarray:
        total_distance = np.zeros(self._row_count, dtype=float)
        compared_columns = np.zeros(self._row_count, dtype=float)

        for spec in self._structured_column_specs:
            if spec.column_type in LlmSynthesizerSupport.NUMERIC_TYPES:
                self._accumulate_numeric_distance(spec, base_row.get(spec.name), total_distance, compared_columns)
                continue
            self._accumulate_categorical_distance(spec, base_row.get(spec.name), total_distance, compared_columns)

        return np.divide(
            total_distance,
            compared_columns,
            out=np.zeros(self._row_count, dtype=float),
            where=compared_columns > 0,
        )

    def _accumulate_numeric_distance(
        self,
        spec: _StructuredColumnSpec,
        query_value: Any,
        total_distance: np.ndarray,
        compared_columns: np.ndarray,
    ) -> None:
        reference_values = self._numeric_arrays[spec.name]
        reference_present = ~np.isnan(reference_values)
        query_numeric = LlmSynthesizerSupport.to_float(query_value)

        if query_numeric is None:
            total_distance[reference_present] += 1.0
            compared_columns[reference_present] += 1.0
            return

        compared_columns += 1.0

        missing_mask = ~reference_present
        total_distance[missing_mask] += 1.0

        if spec.numeric_range <= 0.0:
            equal_mask = np.isclose(reference_values, query_numeric, equal_nan=False)
            total_distance[reference_present & ~equal_mask] += 1.0
            return

        normalized_distance = np.abs(reference_values - query_numeric) / spec.numeric_range
        np.clip(normalized_distance, 0.0, 1.0, out=normalized_distance)
        total_distance[reference_present] += normalized_distance[reference_present]

    def _accumulate_categorical_distance(
        self,
        spec: _StructuredColumnSpec,
        query_value: Any,
        total_distance: np.ndarray,
        compared_columns: np.ndarray,
    ) -> None:
        reference_values = self._categorical_arrays[spec.name]
        query_normalized = _normalize_structured_value(
            query_value,
            spec.column_type,
            self._missing_value_string,
        )

        if query_normalized is None:
            reference_present = reference_values != None  # noqa: E711
            total_distance[reference_present] += 1.0
            compared_columns[reference_present] += 1.0
            return

        compared_columns += 1.0

        reference_missing = reference_values == None  # noqa: E711
        total_distance[reference_missing] += 1.0

        mismatched = (~reference_missing) & (reference_values != query_normalized)
        total_distance[mismatched] += 1.0

    def _build_numeric_array(self, column_name: str) -> np.ndarray:
        values = np.full(self._row_count, np.nan, dtype=float)
        if column_name not in self._reference_df.columns:
            return values

        source_values = self._reference_df[column_name].tolist()
        for index, value in enumerate(source_values):
            numeric_value = LlmSynthesizerSupport.to_float(value)
            if numeric_value is not None:
                values[index] = numeric_value
        return values

    def _build_categorical_array(self, spec: _StructuredColumnSpec) -> np.ndarray:
        values = np.empty(self._row_count, dtype=object)
        values[:] = None
        if spec.name not in self._reference_df.columns:
            return values

        source_values = self._reference_df[spec.name].tolist()
        for index, value in enumerate(source_values):
            values[index] = _normalize_structured_value(value, spec.column_type, self._missing_value_string)
        return values


def select_structured_attribute_neighbors(
    *,
    base_row: Dict[str, Any],
    reference_df: pd.DataFrame,
    column_configs: Sequence[Dict[str, Any]],
    k: int,
    missing_value_string: str = MISSING_VALUE_STRING,
    neighbor_index: Optional[StructuredAttributeNearestNeighborIndex] = None,
) -> List[Dict[str, Any]]:
    if k <= 0 or reference_df.empty:
        return []

    index = neighbor_index or StructuredAttributeNearestNeighborIndex(
        reference_df=reference_df,
        column_configs=column_configs,
        missing_value_string=missing_value_string,
    )
    return index.select_neighbors(base_row, k)


def _normalize_structured_value(value: Any, column_type: str, missing_value_string: str) -> Any:
    if _is_missing(value, missing_value_string):
        return None

    if column_type == "BOOLEAN":
        return _normalize_boolean(value)

    return str(value).strip().casefold()


def _normalize_boolean(value: Any) -> bool | None:
    if isinstance(value, bool):
        return value
    if value is None:
        return None

    try:
        if value in BOOLEAN_MAP:
            return bool(BOOLEAN_MAP[value])
    except TypeError:
        return None

    text = str(value).strip()
    if text in BOOLEAN_MAP:
        return bool(BOOLEAN_MAP[text])

    lowered = text.lower()
    if lowered in BOOLEAN_MAP:
        return bool(BOOLEAN_MAP[lowered])
    return None


def _is_missing(value: Any, missing_value_string: str) -> bool:
    if value is None:
        return True
    if isinstance(value, str):
        stripped = value.strip()
        return not stripped or stripped == missing_value_string or stripped.lower() in LlmSynthesizerSupport._NULL_LIKE_STRINGS
    try:
        return bool(pd.isna(value))
    except TypeError:
        return False
