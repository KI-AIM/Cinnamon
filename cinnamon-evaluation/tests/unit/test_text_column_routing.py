from pathlib import Path
import sys
import types

import pandas as pd


PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


sys.modules.setdefault("phik", types.ModuleType("phik"))

wordcloud_module = types.ModuleType("wordcloud")


class DummyWordCloud:
    def __init__(self, *args, **kwargs):
        pass

    def process_text(self, text):
        return {}


wordcloud_module.WordCloud = DummyWordCloud
sys.modules.setdefault("wordcloud", wordcloud_module)


from resemblance.tabular.metrics import get_text_columns
from visualization.vis_converter import is_metric_applicable_to_attribute


def test_get_text_columns_only_returns_declared_text_columns():
    real = pd.DataFrame({"name": ["Alice"], "notes": ["Detailed clinical note"]})
    synthetic = pd.DataFrame({"name": ["Bob"], "notes": ["Synthetic note"]})

    real.attrs["column_types"] = {"name": "STRING", "notes": "TEXT"}
    synthetic.attrs["column_types"] = {"name": "STRING", "notes": "TEXT"}

    assert get_text_columns(real, synthetic) == ["notes"]


def test_get_text_columns_does_not_fallback_to_string_columns_without_metadata():
    real = pd.DataFrame({"name": ["Alice"], "city": ["Trier"]})
    synthetic = pd.DataFrame({"name": ["Bob"], "city": ["Mainz"]})

    assert get_text_columns(real, synthetic) == []


def test_text_metrics_are_not_applicable_to_string_attributes():
    assert is_metric_applicable_to_attribute("average_text_length", "STRING") is False
    assert is_metric_applicable_to_attribute("wordcloud", "STRING") is False


def test_shared_text_safe_metrics_remain_applicable():
    assert is_metric_applicable_to_attribute("distinct_values", "STRING") is True
    assert is_metric_applicable_to_attribute("missing_values_count", "STRING") is True
    assert is_metric_applicable_to_attribute("average_text_length", "TEXT") is True
