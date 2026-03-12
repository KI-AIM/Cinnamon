import io
import json
import traceback
from datetime import datetime

import pandas as pd
import requests

from base_assessment.general_eval.datatype_consistancy_eval import df_consistency_eval
from models.AttributeConfig import AttributeConfigList
from models.RiskAssessmentConfig import RiskAssessmentConfig
from risk_assessment.anonymeter_eval.anonymeter_attack import inference_attack, linkage_attack, singling_out_attack
from util.data_utility import __to_series, make_serializable, prepare_dataset


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


def send_callback_error(callback_url, error_message, error_code, error_details=None):
    # Prepare the error_details if not provided
    if error_details is None:
        error_details = {}

    # Prepare the JSON payload according to the ErrorRequest structure
    payload = {
        "type": "about:blank",  # Default value as per ErrorRequest
        "timestamp": datetime.now().isoformat(timespec='milliseconds') + 'Z',
        "errorCode": error_code,
        "errorMessage": error_message,
        "errorDetails": error_details
    }

    headers = {
        'Content-Type': 'application/json'
    }

    try:
        print(f"Sending error callback to {callback_url} with JSON payload: {json.dumps(payload, indent=2)}")
        response = requests.post(callback_url, headers=headers, json=payload, timeout=5)
        response.raise_for_status()
    except requests.exceptions.RequestException as e:
        print(f"Failed to send error to callback URL: {e}")


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
    results = {}

    try:
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
    except Exception as e:
        error_message = f"Failed to do prepare datasets: {e}"
        print(error_message)
        print(traceback.format_exc())
        send_callback_error(callback_url, error_message, "RISK-ASSESSMENT_1_0_1")
        return results

    if risk_assessment_config.linkage:
        try:
            print("got here! linkage")
            risk_link = linkage_attack(data_origin, synthetic_data, risk_assessment_config.linkage, holdout_data)
            results["linkage_health_risk"] = risk_link
        except Exception as e:
            error_message = f"Failed to do linkage attack: {e}"
            print(error_message)
            print(traceback.format_exc())
            send_callback_error(callback_url, error_message, "RISK-ASSESSMENT_1_0_2")
            return results

    if risk_assessment_config.attribute_inference:
        try:
            print("got here! inf")
            risk_inf, result_inf = inference_attack(data_origin, synthetic_data, attribute_config,
                                                    risk_assessment_config.attribute_inference, holdout_data)
            risk_inf_avg = average_attribute_inf(risk_inf, result_inf)
            results["inference_risk"] = risk_inf
            results["inference_results"] = result_inf
            results["inference_average_risk"] = risk_inf_avg
        except Exception as e:
            error_message = f"Failed to do inference attack: {e}"
            print(error_message)
            print(traceback.format_exc())
            send_callback_error(callback_url, error_message, "RISK-ASSESSMENT_1_0_3")
            return results

    if risk_assessment_config.singlingout_uni:
        try:
            print("got here! sout uni")
            risk_sout_uni = singling_out_attack(data_origin, synthetic_data, risk_assessment_config.singlingout_uni,
                                                holdout_data, mode="univariate")
            results["univariate_singling_out_risk"] = risk_sout_uni
        except Exception as e:
            error_message = f"Failed to do univariate singling out attack: {e}"
            print(error_message)
            print(traceback.format_exc())
            send_callback_error(callback_url, error_message, "RISK-ASSESSMENT_1_0_4")
            return results

    if risk_assessment_config.singlingout_multi:
        try:
            print("got here! sout multi")
            risk_sout_multi = singling_out_attack(data_origin, synthetic_data, risk_assessment_config.singlingout_multi,
                                                  holdout_data, mode="multivariate")
            results["multivariate_singling_out_risk"] = risk_sout_multi
        except Exception as e:
            error_message = f"Failed to do multivariate singling out attack: {e}"
            print(error_message)
            print(traceback.format_exc())
            send_callback_error(callback_url, error_message, "RISK-ASSESSMENT_1_0_5")
            return results

    if risk_assessment_config.linkage and risk_assessment_config.singlingout_uni and risk_assessment_config.singlingout_multi and risk_assessment_config.attribute_inference:
        try:
            results["total_risk_score"] = round(
                sum([risk_inf_avg["priv_risk"],
                     risk_link["risk_value"],
                     risk_sout_uni["risk_value"],
                     risk_sout_multi["risk_value"]]) / 4
                , 3)
        except Exception as e:
            error_message = f"Failed to calculate total risk score: {e}"
            print(error_message)
            print(traceback.format_exc())
            send_callback_error(callback_url, error_message, "RISK-ASSESSMENT_1_0_6")
            return results
    else:
        """NOTE! The method of calculating the total risk score should not be changed to compensate for fewer available subscores
        dynamically. This would undermine the comparability of the resulting scores and thresholds etc. would not universally apply. """
        print("Not all required attacks were provided, therefore no total score could be calculated.")

    if callback_url:
        try:
            files = prepare_callback_data(results)
            requests.post(callback_url, files=files, data={'session_key': process_id})
            print("Callback made successfully")

        except Exception as e:
            print(f"Error while making callback: {e}")
    return results
