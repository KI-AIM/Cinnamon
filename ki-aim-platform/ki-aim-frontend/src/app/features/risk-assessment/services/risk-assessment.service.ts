import { Injectable } from '@angular/core';
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { AlgorithmService } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { Algorithm } from "../../../shared/model/algorithm";
import { Observable, of } from "rxjs";

@Injectable({
  providedIn: 'root'
})
export class RiskAssessmentService extends AlgorithmService {

    constructor(
        configurationService: ConfigurationService,
        http: HttpClient,
    ) {
        super(http, configurationService);
    }

    public override getStepName(): string {
        return "risk_evaluation";
    }

    public override getConfigurationName(): string {
        return "risk_assessment_configuration";
    }

    override getExecStepName(): string {
        return "evaluation";
    }

    override getJobs(): string[] {
        return ["risk_evaluation", "base_evaluation"];
    }

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        return {
            risk_assessment_configuration: {
                data_format: "cross-sectional",
                train_fraction: 0.8,
                targets: [],
                n_random_targets: 5,
                n_outlier_targets: 5,
                n_iterations: 1,
                columns_excluded: ["id"],
                linkage: {
                    n_attacks: 100,
                    available_columns: ["ChestPainType", "RestingBP", "Cholesterol", "FastingBS", "RestingECG", "MaxHR", "ExerciseAngina", "Oldpeak", "ST_Slope", "HeartDisease"],
                    unavailable_columns: ["birthdate", "death_date", "Age", "Sex",]  // unavailable and available sets of columns e.g. lab data and demographic data,
                },
                "singlingout-uni": {
                    n_attacks: 100,
                },
                "singlingout-multi": {
                    n_attacks: 100,
                },
                attribute_inference: {
                    n_attacks: 100,
                },
                metrics: {
                    uniqueness: true,
                },
            },
        };
    }

    public override readConfiguration(arg: Object, configurationName: string): { config: Object; selectedAlgorithm: Algorithm; } {
        const selectedAlgorithm = this.getAlgorithmByName("evaluation");
        // @ts-ignore
        const config = arg[configurationName];
        delete config["data_format"];
        return {config, selectedAlgorithm};
    }

    protected override fetchAlgorithms(url: string): Observable<string> {
        return of("algorithms:\n" +
            "- URL: /get_evaluation_metrics/cross-sectional\n" +
            "  class: <class 'synthetic_tabular_data_generator.algorithms.ctgan.CtganSynthesizer'>\n" +
            "  description:\n" +
            "  display_name: Evaluation\n" +
            "  name: evaluation\n" +
            "  type: cross-sectional\n" +
            "  version: '0.1'");
    }

    protected override fetchAlgorithmDefinition(url: string): Observable<string> {
        return of("name: Evaluation\n" +
            "type: cross-sectional\n" +
            "display_name: Evaluation\n" +
            "description: Metrics used to evaluate the resemblance and utility of synthetic data compared to real data.\n" +
            "URL: /start_evaluation\n" +
            "configurations:\n" +
            "  resemblance:\n" +
            "    display_name: Risk Metrics (Anonymeter)\n" +
            "    description: Metrics that evaluate associated risks of anonymized data in comparison with the real data.\n" +
            "    options:\n" +
            "      singling_out-uni:\n" +
            "        display_name: Singling records out\n" +
            "        description: Metrics that indicate if records from the anonymized dta can be used to separate record in th real data.\n" +
            "        parameters:\n" +
            "        - name: n_attacks\n" +
            "          label: Number of Attacks\n" +
            "          description: hi\n" +
            "          type: integer\n" +
            "          default_value: 100\n" +
            "      attribute_inference:\n" +
            "        display_name: Infer information from attributes (columns)\n" +
            "        description: Metrics that indicate if attributes fro the anonymized data can be used to derive values on the real data.\n" +
            "        parameters:\n" +
            "        - name: n_attacks\n" +
            "          label: Number of Attacks\n" +
            "          description: hi\n" +
            "          type: integer\n" +
            "          default_value: 100\n" +
            "      linkage:\n" +
            "        display_name: Infer risk of linking datasets\n" +
            "        description: Metrics that indicate if subsets of the synthetic dataset can be sued for linkage.\n" +
            "        parameters:\n" +
            "        - name: n_attacks\n" +
            "          label: Number of Attacks\n" +
            "          description: hi\n" +
            "          type: integer\n" +
            "          default_value: 100\n" +
            "        - name: available_columns\n" +
            "          label: Columns known\n" +
            "          invert: unavailable_columns\n" +
            "          description: Select attributes that might be known, e.g. due to a lab report being leaked. Do not select the information that might be of value to the attacker e.g. birthdate, or diagnosis\n" +
            "          type: attribute_list\n" +
            "");
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.RISK_EVALUATION;
        configReg.lockedAfterStep = null;
        configReg.displayName = "Technical Evaluation Configuration";
        configReg.fetchConfig = null;
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "risk_assessment_configuration";
        configReg.orderNumber = 3;
        configReg.storeConfig = null;
        configReg.getConfigCallback = () => "";
        configReg.setConfigCallback = (config) => {};

        this.configurationService.registerConfiguration(configReg);
    }
}
