import { Injectable } from '@angular/core';
import { Algorithm } from 'src/app/shared/model/algorithm';
import { AlgorithmService, ReadConfigResult } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";

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

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.TECHNICAL_EVALUATION;
        configReg.lockedAfterStep = null;
        configReg.displayName = "Technical Evaluation Configuration";
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "evaluation_configuration";
        configReg.orderNumber = 3;

        this.configurationService.registerConfiguration(configReg);
    }

}
