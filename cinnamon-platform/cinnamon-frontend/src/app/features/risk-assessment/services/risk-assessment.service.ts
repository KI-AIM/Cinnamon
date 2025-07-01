import { Injectable } from '@angular/core';
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { AlgorithmService, ReadConfigResult } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { Algorithm } from "../../../shared/model/algorithm";

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

    public override getConfigurationName(): string {
        return "risk_assessment_configuration";
    }

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        return {
            risk_assessment_configuration: {
                ...arg,
                data_format: "cross-sectional",
                train_fraction: 0.8,
                targets: [],
                n_random_targets: 5,
                n_outlier_targets: 5,
                n_iterations: 1,
                columns_excluded: [],
                singlingout_multi: {
                    n_attacks: 100,
                },
                metrics: {
                    uniqueness: true,
                },
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

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.RISK_EVALUATION;
        configReg.lockedAfterStep = null;
        configReg.displayName = "Risk Assessment Configuration";
        configReg.fetchConfig = null;
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "risk_assessment_configuration";
        configReg.orderNumber = 4;
        configReg.storeConfig = null;
        configReg.setConfigCallback = (config) => this.setConfigWait(config);

        this.configurationService.registerConfiguration(configReg);
    }
}
