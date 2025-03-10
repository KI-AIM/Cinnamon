import numpy as np
import pandas as pd

from models.AttributeConfig import AttributeConfigList


def prepare_dataset(data: pd.DataFrame,
                    attribute_config: AttributeConfigList) -> pd.DataFrame:
    return data


def check_consistency(dict_data: dict[pd.DataFrame]):
    pass


def __to_series(class_object, name=0):
    series = pd.Series(class_object, name=name)
    return series


def make_serializable(item):
    """
    Helper function to convert DataFrames or other non-serializable objects
    into a JSON serializable format.
    """
    if isinstance(item, pd.DataFrame):
        for col in item.select_dtypes(include=[np.int64]).columns:
            item[col] = item[col].astype(int)
        return make_serializable(item.to_dict(orient='records'))
    elif isinstance(item, dict):
        return {make_serializable(k): make_serializable(v) for k, v in item.items()}
    elif isinstance(item, tuple):
        return [make_serializable(i) for i in item]
    elif isinstance(item, list):
        return [make_serializable(i) for i in item]
    elif isinstance(item, np.integer):
        return int(item)
    return item
