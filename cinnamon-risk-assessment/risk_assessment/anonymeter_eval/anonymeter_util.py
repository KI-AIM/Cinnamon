import numpy as np
import pandas as pd
import anonymeter.stats.confidence as aym
from sklearn.model_selection import train_test_split
from typing import List, Dict, Tuple, Any


class EvaluationResults(aym.EvaluationResults):

    def __init__(self, original_obj):
        # Copy attributes from the original object
        self.__dict__ = original_obj.__dict__.copy()

    def as_dict(self):
        result = {
            "n_attacks": format_numbers_to_two_decimals(self.n_attacks),
            "n_success": format_numbers_to_two_decimals(self.n_success),
            "n_baseline": format_numbers_to_two_decimals(self.n_baseline),
            "n_control": format_numbers_to_two_decimals(self.n_control),
            "attack_rate": format_numbers_to_two_decimals(self.attack_rate._asdict()),
            "baseline_rate": format_numbers_to_two_decimals(self.baseline_rate._asdict()),
            "control_rate": format_numbers_to_two_decimals(
                self.control_rate._asdict()) if self.control_rate else None,
        }
        return result

    def as_dataframe(self):
        pass


class PrivacyRisk(aym.PrivacyRisk):

    def __new__(cls, anonymeter_risks: aym.PrivacyRisk):
        # Unpack the original named tuple into the new instance
        return super(PrivacyRisk, cls).__new__(cls, *anonymeter_risks)

    def as_dict(self):
        _result = self._asdict()
        _result = {f"risk_{key}": value for key, value in _result.items()}
        return _result

    def as_dataframe(self):
        pass


def split_dataset(
    dataframe: pd.DataFrame, fraction: float = 0.8, seed: int = 387
) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    Splits the given DataFrame into training and testing datasets.

    Args:
        dataframe (pd.DataFrame): The input DataFrame to be split.
        fraction (float): The fraction of the data to use for training. Default is 0.8.
        seed (int): The random seed for reproducibility. Default is 387.

    Returns:
        Tuple[pd.DataFrame, pd.DataFrame]: A tuple of (train_data, test_data).
    """
    return train_test_split(dataframe, test_size=1 - fraction, random_state=seed)


def format_numbers_to_two_decimals(data: Any) -> Any:
    """
    Recursively formats numerical values in dictionaries, lists, or scalar inputs to two decimals.

    Args:
        data (Any): A dictionary, list, or scalar data to format.

    Returns:
        Any: The input data with numbers rounded to two decimal places.
    """
    if isinstance(data, dict):
        return {key: format_numbers_to_two_decimals(value) for key, value in data.items()}
    elif isinstance(data, list):
        return [format_numbers_to_two_decimals(item) for item in data]
    elif isinstance(data, (int, float, np.integer, np.floating)):
        return round(data, 2)
    else:
        return data


def risk_to_dict(result: aym.PrivacyRisk) -> Dict:
    """
    Converts a PrivacyRisk object into a dictionary.
    """
    return PrivacyRisk(result).as_dict()


def results_to_dict(result: aym.EvaluationResults) -> Dict:
    """
    Converts an EvaluationResults object into a dictionary.
    """
    return EvaluationResults(result).as_dict()


def export_results_as_text(anonymeter_results: List[Dict], output_file: str) -> None:
    """
    Exports anonymeter results to a text file.
    """
    with open(output_file + ".txt", 'a') as result_file:
        for run in anonymeter_results:
            result_file.write(str(run))
            result_file.write("\n")


def import_results_from_text(input_file: str) -> List[Dict]:
    """
    Imports anonymeter results from a text file.
    """
    try:
        with open(input_file, 'r') as result_file:
            # Read each line, stripping newline characters and converting back to appropriate data types
            results = [eval(line.strip()) for line in result_file]
        return results
    except FileNotFoundError:
        print(f"Error: File {input_file} not found.")
        return []
    except Exception as e:
        print(f"An error occurred while importing results: {e}")
        return []


def attribute_inference_results_to_dataframe(
    risk_df: List[Dict], result_df: List[Dict], iteration: int, split_type: str
) -> pd.DataFrame:
    """
    Combines risk and result data into a structured DataFrame.

    Args:
        risk_df (List[Dict]): List of risk dictionaries with keys "attribute" and "risk_result".
        result_df (List[Dict]): List of result dictionaries with attack, baseline, and control metrics.
        iteration (int): The iteration number of the analysis.
        split_type (str): A label indicating the type of split.

    Returns:
        pd.DataFrame: A DataFrame combining risk and result data with additional fields.
    """
    _inf_df = pd.DataFrame(risk_df)
    _inf_df.columns = ["attribute", "risk_result"]
    _inf_df["iteration"] = iteration + 1
    _inf_df["split"] = split_type
    _inf_df['risk_value'] = _inf_df["risk_result"].apply(lambda x: x['risk_value'])
    _inf_df['risk_ci_low'] = _inf_df["risk_result"].apply(lambda x: x['risk_ci'][0])
    _inf_df['risk_ci_high'] = _inf_df["risk_result"].apply(lambda x: x['risk_ci'][1])
    _inf_df = _inf_df.drop("risk_result", axis=1)
    _inf_df = _inf_df.sort_values("attribute", key=lambda x: x.str.lower())

    _inf_res_df = pd.DataFrame(result_df)
    _inf_res_df.columns = ["attribute", "risk_result"]
    _inf_res_df['n_attacks'] = _inf_res_df["risk_result"].apply(lambda x: x['n_attacks'])
    _inf_res_df['n_success'] = _inf_res_df["risk_result"].apply(lambda x: x['n_success'])
    _inf_res_df['n_baseline'] = _inf_res_df["risk_result"].apply(lambda x: x['n_baseline'])
    _inf_res_df['n_control'] = _inf_res_df["risk_result"].apply(lambda x: x['n_control'])
    _inf_res_df['attack_rate_value'] = _inf_res_df["risk_result"].apply(lambda x: x['attack_rate']["value"])
    _inf_res_df['attack_rate_error'] = _inf_res_df["risk_result"].apply(lambda x: x['attack_rate']["error"])
    _inf_res_df['baseline_rate_value'] = _inf_res_df["risk_result"].apply(lambda x: x['baseline_rate']["value"])
    _inf_res_df['baseline_rate_error'] = _inf_res_df["risk_result"].apply(lambda x: x['baseline_rate']["error"])
    _inf_res_df['control_rate_value'] = _inf_res_df["risk_result"].apply(lambda x: x['control_rate']["value"])
    _inf_res_df['control_rate_error'] = _inf_res_df["risk_result"].apply(lambda x: x['control_rate']["error"])
    _inf_res_df = _inf_res_df.drop("risk_result", axis=1)
    _inf_res_df = _inf_res_df.sort_values("attribute", key=lambda x: x.str.lower())
    attribute_inference_df = pd.merge(_inf_df, _inf_res_df, on="attribute")
    return attribute_inference_df


def singling_out_results_to_dataframe(
        risk_list: List[Dict], result_list: List[Dict], split_type: str
) -> pd.DataFrame:
    """
    Combines risk and result data into a structured DataFrame with additional calculated fields.

    Args:
        risk_list (List[Dict]): A list of dictionaries containing risk data, where each dictionary
                                includes a "risk_ci" key with a tuple of (low, high) confidence intervals.
        result_list (List[Dict]): A list of dictionaries containing result data, where each dictionary
                                  includes keys such as "attack_rate", "baseline_rate", and "control_rate",
                                  each containing a "value" and "error".
        split_type (str): A label to categorize the type of split for the results.

    Returns:
        pd.DataFrame: A combined DataFrame containing data from both risk and result lists with additional fields.
    """
    sout_uni_risks = pd.DataFrame(risk_list)
    sout_uni_risks['risk_ci_low'] = sout_uni_risks["risk_ci"].apply(lambda x: x[0])
    sout_uni_risks['risk_ci_high'] = sout_uni_risks["risk_ci"].apply(lambda x: x[1])
    sout_uni_risks['iteration'] = sout_uni_risks.index
    sout_uni_risks["split"] = split_type

    sout_uni_results = pd.DataFrame(result_list)
    sout_uni_results['attack_rate_value'] = sout_uni_results["attack_rate"].apply(lambda x: x["value"])
    sout_uni_results['attack_rate_error'] = sout_uni_results["attack_rate"].apply(lambda x: x["error"])
    sout_uni_results['baseline_rate_value'] = sout_uni_results["baseline_rate"].apply(lambda x: x["value"])
    sout_uni_results['baseline_rate_error'] = sout_uni_results["baseline_rate"].apply(lambda x: x["error"])
    sout_uni_results['control_rate_value'] = sout_uni_results["control_rate"].apply(lambda x: x["value"])
    sout_uni_results['control_rate_error'] = sout_uni_results["control_rate"].apply(lambda x: x["error"])
    _temp = pd.concat([sout_uni_risks, sout_uni_results], axis=1)
    return _temp


def singling_out_univariate_query_results_to_dataframe(
        queries_list: List[str], split_type: str
) -> pd.DataFrame:
    """
    Processes a list of univariate query results into a structured DataFrame.

    Args:
        queries_list (List[str]): A list of query strings, where each query consists of
                                  a single condition in the form "Attribute == Value".
        split_type (str): A label indicating the type of split used for categorizing the queries.

    Returns:
        pd.DataFrame: A DataFrame with the following columns:
            - "Attribute": The name of the attribute in the condition.
            - "Value": The value associated with the attribute in the condition.
            - "Direction": The comparison operator (e.g., '==') used in the condition.
            - "split_type": The type of split passed to the function.
    """
    df_new = pd.Series(queries_list).str.replace(" ", "").str.split("==", expand=True)

    df_new.columns = ["Attribute", "Value"]
    df_new["Direction"] = pd.Series(queries_list).str.extract(r"(==|>=|<=)")

    df_new = df_new.sort_values("Attribute").reset_index(drop=True)
    df_new["split_type"] = split_type
    return df_new


def get_singling_out_attributes(singling_out_results: pd.DataFrame) -> List[str]:
    """
    Extracts the unique attributes from the 'Attribute' column of a DataFrame.

    Args:
        singling_out_results (pd.DataFrame): A DataFrame containing a column named 'Attribute',
                                             which lists attributes used in singling-out queries.

    Returns:
        List[str]: A list of unique attribute names.
    """
    return singling_out_results["Attribute"].unique().tolist()


def singling_out_multivariate_query_results_to_dataframe(
        queries_list: List[str], split_type: str
) -> pd.DataFrame:
    """
    Processes a list of multivariate query results into a structured DataFrame.

    Args:
        queries_list (List[str]): A list of query strings, where each query consists of
                                  conditions separated by `&`. Each condition includes an
                                  attribute, a comparison operator (`==`, `>=`, `<=`), and a value.
                                  Example: ["A==1 & B>=2", "C<=3 & D==4"].
        split_type (str): A label indicating the type of split used for categorizing the queries.

    Returns:
        pd.DataFrame: A DataFrame with the following columns:
            - "Attribute": The name of the attribute in each condition.
            - "Direction": The comparison operator (`==`, `>=`, `<=`) used in each condition.
            - "Value": The value associated with the attribute in each condition.
            - "Combination": A unique identifier for each query in the original list.
            - "split_type": The type of split passed to the function.
    """
    df = pd.Series(queries_list).str.replace(" ", "").str.split("&", expand=True)
    df['Combination'] = range(1, len(df) + 1)
    melted = pd.melt(df, id_vars='Combination', var_name='Variable', value_name='Value')
    df = melted[['Combination', 'Value']]
    df_new = df["Value"].str.split(f"[==]|[>=]|[<=]", expand=True)
    df_new[1] = df["Value"].str.extract(r"(==|>=|<=)")
    df_new.columns = ["Attribute", "Direction", "Value"]
    df_new["Combination"] = df["Combination"]
    df_new = df_new.sort_values("Combination")
    df_new["split_type"] = split_type
    return df_new
