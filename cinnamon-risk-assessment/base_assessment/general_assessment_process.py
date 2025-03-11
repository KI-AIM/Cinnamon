import io
import json

import pandas as pd
import requests

from base_assessment.general_eval import categorical_attribute_eval as cat_eval
from base_assessment.general_eval import continuous_attribute_eval as cont_eval
from base_assessment.target_identification.OutlierDetectionCentroid import OutlierDetectionCentroid
from models.AttributeConfig import AttributeConfigList
from models.RiskAssessmentConfig import RiskAssessmentConfig
from util.data_utility import make_serializable

# import seaborn as sns
# import matplotlib.pyplot as plt


def prepare_callback_data(metrics):
    """
    Prepares the metrics data to be sent via the 'files' parameter in requests.post.
    Converts any DataFrame in the metrics dictionary to a JSON serializable format.
    """

    serializable_metrics = make_serializable(metrics)
    metrics_json = json.dumps(serializable_metrics)
    metrics_bytes = metrics_json.encode('utf-8')
    file_like_object = io.BytesIO(metrics_bytes)
    files = {'general_risks_file': ('general_risks.json', file_like_object, 'application/json')}

    return files


def assess_continuous_columns(dataset):
    results_continuous = {}
    results_continuous["number_columns"] = cont_eval.number_of_continuous_attributes(dataset)
    results_continuous["number_of_unique_combinations_raw"], results_continuous[
        "unique_records_raw"] = cont_eval.unique_combinations_of_continuous_attributes(dataset,
                                                                                       rounding=False)
    results_continuous["number_of_unique_combinations_rounded"], results_continuous[
        "unique_records_rounded"] = cont_eval.unique_combinations_of_continuous_attributes(
        dataset, rounding=True)

    results_continuous["unique_columns_raw"] = cont_eval.identify_columns_with_unique_entries(dataset, rounding=False)
    results_continuous["unique_columns_rounded"] = cont_eval.identify_columns_with_unique_entries(dataset,
                                                                                                  rounding=True)

    for column in dataset.select_dtypes("number").columns:
        column_result = {}
        column_result["has_outlier_raw"], column_result["outlier_raw"] = cont_eval.outlier_records(dataset[column],
                                                                                                   rounding=False)
        column_result["has_outlier_rounded"], column_result["outlier_rounded"] = cont_eval.outlier_records(
            dataset[column], rounding=True)

        results_continuous[column] = column_result
    return results_continuous


def assess_categorical_columns(dataset):
    results_categorical = {}
    results_categorical["number_columns"] = cat_eval.number_of_categorical_attributes(dataset)
    results_categorical["number_of_unique_combinations"], results_categorical[
        "unique_records"] = cat_eval.unique_combinations_of_categorical_attributes(dataset)

    results_categorical["unique_columns"] = cat_eval.identify_columns_with_unique_entries(dataset)

    for column in dataset.select_dtypes("number").columns:
        column_result = {}
        column_result["has_rare_categories"], column_result["rare_categories_list"] = cat_eval.rare_categories(
            dataset[column])
        results_categorical[column] = column_result
    return results_categorical


def general_assessment(process_id: int,
                       callback_url: str,
                       attribute_config: AttributeConfigList,
                       general_config: RiskAssessmentConfig,
                       dataset: pd.DataFrame):

    #TODO: use attribute config to set dtypes properly for dataframe

    detector = OutlierDetectionCentroid(dataset)
    detector.run()
    original_data_distanced = detector.dataset
    original_data_distanced.sort_values("NormalizedDistanceToCentroid", inplace=True, ascending=False)

    # original_data_distanced["_index"] = original_data_distanced.reset_index().index
    # sns.scatterplot(data=original_data_distanced[:100], x="_index", y="NormalizedDistanceToCentroid")
    # plt.show()

    outlier_row_IDs = original_data_distanced.index[:general_config.n_outlier_targets].tolist()

    results = {}
    results["distance_based_outlier_row_IDs"] = outlier_row_IDs
    results["results_continuous"] = assess_continuous_columns(dataset)
    results["results_categorical"] = assess_categorical_columns(dataset)

    if callback_url:
        try:
            files = prepare_callback_data(results)
            requests.post(callback_url, files=files, data={'session_key': process_id})
            print("Callback made successfully")

        except requests.exceptions.RequestException as e:
            print(f"Error while making callback: {e}")

    return results
