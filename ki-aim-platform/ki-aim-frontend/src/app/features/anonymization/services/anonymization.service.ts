import { Injectable } from '@angular/core';
import { AlgorithmService } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { Algorithm } from "../../../shared/model/algorithm";
import { AnonymizationAttributeConfigurationService } from './anonymization-attribute-configuration.service';

@Injectable({
    providedIn: 'root',
})
export class AnonymizationService extends AlgorithmService {

    private attributeService: AnonymizationAttributeConfigurationService;
    constructor(
        http: HttpClient,
        configurationService: ConfigurationService,
        attributeService: AnonymizationAttributeConfigurationService,
    ) {
        super(http, configurationService);
        this.attributeService = attributeService;
    }

    public override getStepName() {
        return "ANONYMIZATION";
    }

    public override getExecStepName(): string {
        return "EXECUTION";
    }

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        return {
            privacyModels: [
                {
                    name: selectedAlgorithm.name,
                    type: selectedAlgorithm.type,
                    version: selectedAlgorithm.version,
                    ...arg
                },
            ],
            ...this.attributeService.createConfiguration()
         };
    }
    public override readConfiguration(arg: any, configurationName: string): {config: Object, selectedAlgorithm: Algorithm} {
        this.attributeService.setAttributeConfiguration(arg);
        const selectedAlgorithm = this.getAlgorithmByName(arg["privacyModels"][0]["name"])
        const config = arg["privacyModels"][0];
        delete config["name"];
        delete config["type"];
        return {config, selectedAlgorithm};
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
                configReg.getConfigCallback = () => this.getConfig();
                configReg.setConfigCallback = (config) => this.setConfigWait(config);

                this.configurationService.registerConfiguration(configReg);
            }
        });
    }
}
