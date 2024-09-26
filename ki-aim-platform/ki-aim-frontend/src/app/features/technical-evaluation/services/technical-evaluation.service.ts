import { Injectable } from '@angular/core';
import { Algorithm } from 'src/app/shared/model/algorithm';
import { AlgorithmService } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { Observable, of } from "rxjs";

@Injectable({
    providedIn: 'root'
})
export class TechnicalEvaluationService extends AlgorithmService {

    constructor(
        http: HttpClient,
        configurationService: ConfigurationService,
    ) {
        super(http, configurationService);
    }

    public override getStepName(): string {
        return "TECHNICAL_EVALUATION";
    }

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        console.log(arg);
        return {
            evaluation_configuration: {
                data_format: 'cross-sectional',
                ...arg
            },
        };
    }

    public override readConfiguration(arg: Object, configurationName: string): { config: Object; selectedAlgorithm: Algorithm; } {
        throw new Error('Method not implemented.');
    }

    protected override fetchAlgorithms(url: string): Observable<string> {
        return of("algorithms:\n" +
            "- URL: /get_evaluation_metrics/cross-sectional\n" +
            "  class: <class 'synthetic_tabular_data_generator.algorithms.ctgan.CtganSynthesizer'>\n" +
            "  description: Metrics used to evaluate the resemblance and utility of synthetic data compared to real data.\n" +
            "  display_name: Evaluation\n" +
            "  name: ctgan\n" +
            "  type: cross-sectional\n" +
            "  version: '0.1'");
    }

    protected override fetchAlgorithmDefinition(url: string): Observable<string> {
        return of("name: Evaluation\n" +
            "type: cross-sectional\n" +
            "display_name: Evaluation\n" +
            "description: Metrics used to evaluate the resemblance and utility of synthetic data compared to real data.\n" +
            "URL: /start_evaluation_process\n" +
            "configurations:\n" +
            "  resemblance:\n" +
            "    display_name: Resemblance Metrics\n" +
            "    description: Metrics that evaluate the resemblance between real and synthetic data.\n" +
            "    configurations:\n" +
            "      mean:\n" +
            "        display_name: Mean\n" +
            "        description: Average value of numerical variables\n" +
            "      standard_deviation:\n" +
            "        display_name: Standard Deviation\n" +
            "        description: Measure of the amount of variation or dispersion of a set of values\n" +
            "      skewness:\n" +
            "        display_name: Skewness\n" +
            "        description: Measure of the asymmetry of the probability distribution of a real-valued random variable about its mean\n" +
            "      mode:\n" +
            "        display_name: Mode\n" +
            "        description: Mode of categorical variables i.e., the most frequent value\n" +
            "      quantiles:\n" +
            "        display_name: Quantiles\n" +
            "        description: Divides the data into equal parts\n" +
            "        parameters:\n" +
            "        - name: quantile_array\n" +
            "          label: Quantiles\n" +
            "          description: List of quantiles to compute\n" +
            "          type: list\n" +
            "          default_value:\n" +
            "          - 0.2\n" +
            "          - 0.4\n" +
            "          - 0.6\n" +
            "          - 0.8\n" +
            "          min_value: 0.0\n" +
            "          max_value: 1.0\n" +
            "      kurtosis:\n" +
            "        display_name: Kurtosis\n" +
            "        description: Measure of the 'tailedness' of the probability distribution of a real-valued random variable\n" +
            "      ranges:\n" +
            "        display_name: Range\n" +
            "        description: Difference between the largest and smallest values in a dataset, can be used to check if the values ranges are still present after anonymization\n" +
            "      kolmogorov_smirnov:\n" +
            "        display_name: Kolmogorov-Smirnov\n" +
            "        description: Non-parametric test of the equality of continuous, one-dimensional probability distributions\n" +
            "      hellinger_distance:\n" +
            "        display_name: Hellinger Distance\n" +
            "        description: Distance metric for comparing distributions of categorical data\n" +
            "  utility:\n" +
            "    display_name: Utility Metrics\n" +
            "    description: Metrics that evaluate the utility of anonymized data in comparison with the real data.\n" +
            "    configurations:\n" +
            "      machine_learning:\n" +
            "        display_name: Machine Learning Utility\n" +
            "        description: Measures how well a machine learning model performs on synthetic data compared to real data.\n" +
            "        parameters:\n" +
            "        - name: train_size\n" +
            "          label: Train Size\n" +
            "          description: The proportion of the dataset to include in the train split.\n" +
            "          type: float\n" +
            "          default_value: 0.8\n" +
            "          min_value: 0.0\n" +
            "          max_value: 1.0\n" +
            "        - name: random_state\n" +
            "          label: Random State\n" +
            "          description: Random seed used by the random number generator to ensure reproducibility.\n" +
            "          type: integer\n" +
            "          default_value: 42\n" +
            "        - name: target_variable\n" +
            "          label: Target Variable\n" +
            "          description: The target variable (column) in the dataset to predict.\n" +
            "          type: attribute\n");
    }

    public registerConfig() {
        // TODO this is a racing condition with state-guard fetching the configurations
        this.stepConfig.subscribe({
            next: value => {

                const configReg = new ConfigurationRegisterData();
                configReg.availableAfterStep = Steps.TECHNICAL_EVALUATION;
                configReg.lockedAfterStep = null;
                configReg.displayName = "Technical Evaluation Configuration";
                configReg.fetchConfig = null;
                configReg.name = value.configurationName;
                configReg.orderNumber = 3;
                configReg.storeConfig = null;
                configReg.getConfigCallback = () => this.getConfig();
                configReg.setConfigCallback = (config) => this.setConfigWait(config);

                this.configurationService.registerConfiguration(configReg);

            }
        });
    }

}
