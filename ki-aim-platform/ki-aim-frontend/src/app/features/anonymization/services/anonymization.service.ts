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
export class AnonymizationService extends AlgorithmService {

    constructor(
        http: HttpClient,
        configurationService: ConfigurationService,
    ) {
        super(http, configurationService);
    }

    public override getStepName = (): string => "ANONYMIZATION";

    public override getConfigurationName = (): string => "anonymization";

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        // TODO
        return { };
    }
    public override readConfiguration(arg: Object, configurationName: string): {config: Object, selectedAlgorithm: Algorithm} {
        // TODO
        return {config: {}, selectedAlgorithm: new Algorithm()};
    }

    public registerConfig() {
        this.stepConfig.subscribe({
            next: value => {
                const configReg = new ConfigurationRegisterData();
                configReg.availableAfterStep = Steps.ANONYMIZATION;
                configReg.lockedAfterStep = null;
                configReg.displayName = "Anonymization Configuration";
                configReg.fetchConfig = null;
                configReg.name = value.configurationName;
                configReg.orderNumber = 1;
                configReg.storeConfig = null;
                configReg.getConfigCallback = () => this.doGetConfig();
                configReg.setConfigCallback = (config) => this.setConfigWait(config);

                this.configurationService.registerConfiguration(configReg);
            }
        });
    }
}
