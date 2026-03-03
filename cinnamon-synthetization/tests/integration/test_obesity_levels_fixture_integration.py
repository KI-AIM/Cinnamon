from pathlib import Path

import pandas as pd
import yaml


FIXTURES_ROOT = Path(__file__).resolve().parent / "test_fixtures"
DATASET_NAME = "obesity_levels"
DATA_PATH = FIXTURES_ROOT / "datasets" / DATASET_NAME / "original-dataset.csv"
CONFIG_PATH = FIXTURES_ROOT / "configs" / DATASET_NAME / "all-configurations.yaml"


def _load_config() -> dict:
    with CONFIG_PATH.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def test_obesity_levels_fixture_files_exist():
    assert DATA_PATH.exists(), f"Dataset not found: {DATA_PATH}"
    assert CONFIG_PATH.exists(), f"Config not found: {CONFIG_PATH}"


def test_obesity_levels_row_count_matches_sampling_num_samples():
    df = pd.read_csv(DATA_PATH)
    config = _load_config()

    expected_rows = config["synthetization_configuration"]["algorithm"]["sampling"]["num_samples"]
    assert len(df) == expected_rows


def test_obesity_levels_attribute_count_and_columns_match_config():
    df = pd.read_csv(DATA_PATH)
    config = _load_config()

    attributes = config["configurations"]
    assert len(df.columns) == len(attributes)

    sorted_attributes = sorted(attributes, key=lambda item: item["index"])
    configured_indices = [item["index"] for item in sorted_attributes]
    configured_columns = [item["name"] for item in sorted_attributes]

    assert configured_indices == list(range(len(df.columns)))
    assert configured_columns == list(df.columns)
