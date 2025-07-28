import io
import json

import numpy as np
import pandas as pd
import requests

from base_assessment.general_eval.datatype_consistancy_eval import check_dtype_consistency, df_consistency_eval
from models.AttributeConfig import AttributeConfigList
from models.RiskAssessmentConfig import RiskAssessmentConfig
from risk_assessment.anonymeter_eval.anonymeter_attack import linkage_attack, inference_attack, singling_out_attack
from util.data_utility import prepare_dataset, make_serializable, __to_series


def prepare_callback_data(metrics):
    """
    Prepares the metrics data to be sent via the 'files' parameter in requests.post.
    Converts any DataFrame in the metrics dictionary to a JSON serializable format.
    """
    serializable_metrics = make_serializable(metrics)
    metrics_json = json.dumps(serializable_metrics)
    metrics_bytes = metrics_json.encode('utf-8')
    file_like_object = io.BytesIO(metrics_bytes)
    files = {'risks_file': ('risks.json', file_like_object, 'application/json')}

    return files


def average_attribute_inf(risks, results):

    results_df = pd.DataFrame({k: __to_series(v, "inference") for k, v in dict(results).items()}).add_prefix(
        "inference_")
    results_df = results_df.transpose()
    results_df[["attack_rate_success_rate", "attack_rate_error"]] = pd.DataFrame(results_df['attack_rate'].tolist(),
                                                                                 index=results_df.index)
    results_df[["baseline_rate_success_rate", "baseline_rate_error"]] = pd.DataFrame(
        results_df['baseline_rate'].tolist(), index=results_df.index)
    results_df[["control_rate_success_rate", "control_rate_error"]] = pd.DataFrame(results_df['control_rate'].tolist(),
                                                                                   index=results_df.index)
    results_df.drop(["attack_rate", "baseline_rate", "control_rate"], inplace=True, axis=1)

    risks_df = pd.DataFrame({k: v for k, v in dict(risks).items()}).add_prefix(
        "inference_")
    risks_df.index = ["priv_risk", "priv_risk_ci"]
    risks_df = risks_df.transpose()
    results_df[["priv_risk", "priv_risk_ci"]] = risks_df[["priv_risk", "priv_risk_ci"]]

    results_df["can_be_used"] = results_df["baseline_rate_success_rate"] < results_df["attack_rate_success_rate"]
    results_df = results_df.convert_dtypes()

    # TODO: consider to use sum of attribute inference times instead of average for summary?
    return results_df.mean(numeric_only=True).to_dict()


def risk_assessment(process_id: int,
                    callback_url: str,
                    attribute_config: AttributeConfigList,
                    risk_assessment_config: RiskAssessmentConfig,
                    data_origin: pd.DataFrame,
                    synthetic_data: pd.DataFrame,
                    holdout_data: pd.DataFrame = None):
    data_origin = prepare_dataset(data_origin, attribute_config)
    synthetic_data = prepare_dataset(synthetic_data, attribute_config)
    if holdout_data is not None:
        holdout_data = prepare_dataset(holdout_data, attribute_config)

    if risk_assessment_config.columns_excluded:
        data_origin.drop(risk_assessment_config.columns_excluded, inplace=True, axis=1, errors="ignore")
        synthetic_data.drop(risk_assessment_config.columns_excluded, inplace=True, axis=1, errors="ignore")
        holdout_data.drop(risk_assessment_config.columns_excluded, inplace=True, axis=1, errors="ignore")

    data_frames = [data_origin, synthetic_data, holdout_data]
    df_consistency_eval(data_frames, try_correction=True)
    data_origin = data_frames[0]
    synthetic_data = data_frames[1]
    holdout_data = data_frames[2]

    results = {}

    if risk_assessment_config.linkage is not None:
        risk_link = linkage_attack(data_origin, synthetic_data, risk_assessment_config.linkage, holdout_data)
        results["linkage_health_risk"] = risk_link

    if risk_assessment_config.attribute_inference is not None:
        risk_inf, result_inf = inference_attack(data_origin, synthetic_data, attribute_config,
                                                risk_assessment_config.attribute_inference, holdout_data)
        risk_inf_avg = average_attribute_inf(risk_inf, result_inf)
        results["inference_risk"] = risk_inf
        results["inference_results"] = result_inf
        results["inference_average_risk"] = risk_inf_avg

    if risk_assessment_config.singlingout_uni is not None:
        risk_sout_uni = singling_out_attack(data_origin, synthetic_data, risk_assessment_config.singlingout_uni,
                                            holdout_data, mode="univariate")
        results["univariate_singling_out_risk"] = risk_sout_uni

    if risk_assessment_config.singlingout_multi is not None:
        risk_sout_multi = singling_out_attack(data_origin, synthetic_data, risk_assessment_config.singlingout_multi,
                                              holdout_data, mode="multivariate")
        results["multivariate_singling_out_risk"] = risk_sout_multi

    if callback_url:
        try:
            files = prepare_callback_data(results)
            requests.post(callback_url, files=files, data={'session_key': process_id})
            print("Callback made successfully")

        except Exception as e:
            print(f"Error while making callback: {e}")
    return results
