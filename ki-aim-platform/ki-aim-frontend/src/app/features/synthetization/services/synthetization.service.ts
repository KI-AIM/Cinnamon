import { Injectable } from '@angular/core';
import { AlgorithmService } from "../../../shared/services/algorithm.service";
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

    public override getStepName = (): string => "SYNTHETIZATION";

    public override getConfigurationName = (): string => "synthetization_configuration";

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        return {
            synthetization_configuration: {
                algorithm: {
                    synthesizer: selectedAlgorithm.name,
                    type: selectedAlgorithm.type,
                    version: selectedAlgorithm.version,
                    ...arg
                },
            },
        };
    }

    public override readConfiguration(arg: any): {config: Object, selectedAlgorithm: Algorithm} {
        const selectedAlgorithm = this.getAlgorithmByName(arg["synthetization_configuration"]["algorithm"]["synthesizer"])
        const config = arg["synthetization_configuration"]["algorithm"];
        delete config["synthesizer"];
        delete config["type"];
        delete config["version"];
        return {config, selectedAlgorithm};
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.SYNTHETIZATION;
        configReg.lockedAfterStep = null;
        configReg.displayName = "Synthetization Configuration";
        configReg.fetchConfig = null;
        configReg.name = this.getConfigurationName();
        configReg.orderNumber = 2;
        configReg.storeConfig = null;
        configReg.getConfigCallback = () => this.doGetConfig();
        configReg.setConfigCallback = (config) => this.doSetConfig(config);

        this.configurationService.registerConfiguration(configReg);
    }
}
