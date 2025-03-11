import pandas as pd
import yaml

from base_assessment.general_assessment_process import general_assessment
from models.AttributeConfig import AttributeConfigList
from models.RiskAssessmentConfig import RiskAssessmentConfig
from risk_assessment.RiskAssessmentProcess import risk_assessment

############## DEFAULT VALUES ###################

ATTRIBUTE_CONFIG = "inputs/attribute_config_with_DateFormat.yaml"
RISK_CONFIG = "inputs/input_user_anonymeter.yaml"
ORIGINAL_DATA = "inputs/heart.csv"
SYNTH_DATA = "inputs/TVAE/tvae_synthetic_data.csv"
HOLDOUT_DATA = "inputs/TVAE/tvae_test.csv"

##################### END SECTION ###################


if __name__ == "__main__":
    with open(ATTRIBUTE_CONFIG) as f:
        attribute_config_data = yaml.safe_load(f)
    attribute_config_model = AttributeConfigList(**attribute_config_data)

    with open(RISK_CONFIG) as f:
        risk_assessment_config_data = yaml.safe_load(f)
    risk_assessment_config_model = RiskAssessmentConfig(**risk_assessment_config_data["risk_assessment_configuration"])

    original_data_df = pd.read_csv(ORIGINAL_DATA)
    synthetic_data_df = pd.read_csv(SYNTH_DATA)
    holdout_data_df = pd.read_csv(HOLDOUT_DATA)

    general_results = general_assessment(1, None,
                                         attribute_config_model,
                                         risk_assessment_config_model,
                                         original_data_df)

    results = risk_assessment(1, None,
                              attribute_config_model,
                              risk_assessment_config_model,
                              original_data_df,
                              synthetic_data_df,
                              holdout_data_df)

    print()
    # TODO: check what anonymeter needs e.g. query aggregation
    # think about further metrics
    # aggregate results and export
    # write info yamls for frontend