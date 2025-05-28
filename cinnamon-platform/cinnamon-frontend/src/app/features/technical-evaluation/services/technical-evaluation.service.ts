import { Injectable } from '@angular/core';
import { Algorithm } from 'src/app/shared/model/algorithm';
import { AlgorithmService, ReadConfigResult } from "../../../shared/services/algorithm.service";
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

    public override getConfigurationName(): string {
        return "evaluation_configuration";
    }

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        return {
            evaluation_configuration: {
                data_format: 'cross-sectional',
                ...arg
            },
        };
    }

    public override readConfiguration(arg: Object, configurationName: string): ReadConfigResult {
        const selectedAlgorithm = this.getAlgorithmByName("evaluation");
        // @ts-ignore
        const config = arg[configurationName];
        delete config["data_format"];
        return {config, selectedAlgorithm};
    }

    protected override fetchAlgorithms(): Observable<string> {
        return of("algorithms:\n" +
            "- URL: /get_evaluation_metrics/cross-sectional\n" +
            "  class: <class 'synthetic_tabular_data_generator.algorithms.ctgan.CtganSynthesizer'>\n" +
            "  description: Metrics used to evaluate the resemblance and utility of synthetic data compared to real data.\n" +
            "  display_name: Evaluation\n" +
            "  name: evaluation\n" +
            "  type: cross-sectional\n" +
            "  version: '0.1'");
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.TECHNICAL_EVALUATION;
        configReg.lockedAfterStep = null;
        configReg.displayName = "Technical Evaluation Configuration";
        configReg.fetchConfig = null;
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "evaluation_configuration";
        configReg.orderNumber = 3;
        configReg.storeConfig = null;
        configReg.getConfigCallback = () => this.getConfig();
        configReg.setConfigCallback = (config) => this.setConfigWait(config);

        this.configurationService.registerConfiguration(configReg);
    }

}
