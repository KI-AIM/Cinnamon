import { Injectable } from '@angular/core';
import { AlgorithmService, ReadConfigResult } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { Algorithm } from "../../../shared/model/algorithm";

@Injectable({
    providedIn: 'root',
})
export class SynthetizationService extends AlgorithmService {

    constructor(
        http: HttpClient,
        configurationService: ConfigurationService,
    ) {
        super(http, configurationService);
    }

    public override getConfigurationName(): string {
        return "synthetization_configuration";
    }

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        const formData = {...(arg as any)};
        const textSynthesisConfiguration = formData["text_synthesis_configuration"];
        delete formData["text_synthesis_configuration"];

        const config: any = {
            synthetization_configuration: {
                algorithm: {
                    id: selectedAlgorithm.name,
                    synthesizer: selectedAlgorithm.name,
                    type: selectedAlgorithm.type,
                    version: selectedAlgorithm.version,
                    ...formData
                },
            },
        };

        if (textSynthesisConfiguration) {
            config["text_synthesis_configuration"] = textSynthesisConfiguration;
        }

        return config;
    }

    public override readConfiguration(arg: any, configurationName: string): ReadConfigResult {
        const selectedAlgorithm = this.getAlgorithmByName(arg[configurationName]["algorithm"]["synthesizer"]);
        const config = {...arg[configurationName]["algorithm"]};
        delete config["id"];
        delete config["synthesizer"];
        delete config["type"];
        delete config["version"];

        if (arg["text_synthesis_configuration"] != null) {
            config["text_synthesis_configuration"] = arg["text_synthesis_configuration"];
        }

        return {config, selectedAlgorithm};
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.SYNTHETIZATION;
        configReg.lockedAfterStep = Steps.EXECUTION;
        configReg.displayName = "Synthetization Configuration";
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "synthetization_configuration";
        configReg.orderNumber = 2;

        this.configurationService.registerConfiguration(configReg);
    }
}
