import time

from anonymeter.evaluators import SinglingOutEvaluator, InferenceEvaluator, LinkabilityEvaluator

from models.AttributeConfig import AttributeConfigList
from models.RiskAssessmentConfig import LinkageConfig, AttributeInferenceConfig, SinglingOutConfig
from risk_assessment.anonymeter_eval.anonymeter_util import risk_to_dict, results_to_dict


def linkage_attack(original, synthetic, config: LinkageConfig, data_control=None):
    start = time.time()

    aux_columns = [config.available_columns, config.unavailable_columns]
    evaluator = LinkabilityEvaluator(ori=original,
                                     syn=synthetic,
                                     control=data_control,
                                     n_attacks=config.n_attacks,
                                     aux_cols=aux_columns,
                                     n_neighbors=1)

    evaluator.evaluate(n_jobs=-2)
    end = time.time()

    risk = risk_to_dict(evaluator.risk(confidence_level=0.95))
    results = results_to_dict(evaluator.results())
    retVal = dict(risk, **results)
    retVal["execution_time"] = end - start
    return retVal


def inference_attack(data_origin, data_processed, attribute_config: AttributeConfigList, config: AttributeInferenceConfig,
                     data_control=None):
    columns = list(set(data_origin.columns).intersection(data_processed.columns))
    results = []
    risks = []

    for secret in columns:
        regression = True
        for attribute in attribute_config.configurations:
            if attribute.name == secret:
                if attribute.scale == "NOMINAL" or attribute.scale == "DATE":
                    regression = False
                break

        aux_cols = [col for col in columns if col != secret]
        start = time.time()
        evaluator = InferenceEvaluator(ori=data_origin,
                                       syn=data_processed,
                                       control=data_control,
                                       aux_cols=aux_cols,
                                       secret=secret,
                                       regression=regression,
                                       n_attacks=config.n_attacks)
        evaluator.evaluate(n_jobs=-2)
        end = time.time()

        _results = results_to_dict(evaluator.results())
        _results["execution_time"] = end-start

        results.append((secret, _results))
        risks.append((secret, risk_to_dict(evaluator.risk())))

    return risks, results


def singling_out_attack(data_origin, data_processed, config: SinglingOutConfig, data_control=None,
                        mode="univariate"):
    start = time.time()

    max_attempts =  len(data_control.index) * 10 if data_control is not None else 0
    max_attempts = max(10_000, min(max_attempts, 1_000_000))

    evaluator = SinglingOutEvaluator(ori=data_origin,
                                     syn=data_processed,
                                     control=data_control,
                                     n_attacks=config.n_attacks,
                                     max_attempts=max_attempts)
    evaluator.evaluate(mode=mode)
    end = time.time()
    risk = risk_to_dict(evaluator.risk(confidence_level=0.95))
    results = results_to_dict(evaluator.results())
    queries = evaluator.queries()
    retVal = dict(risk, **results)
    retVal["queries"] = queries
    retVal["execution_time"] = end - start
    return retVal
