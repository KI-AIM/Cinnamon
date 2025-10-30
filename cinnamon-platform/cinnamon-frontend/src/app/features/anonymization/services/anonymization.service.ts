import { Injectable } from '@angular/core';
import {
    AnonymizationAttributeRowConfiguration,
    ConfigurationObject
} from "@shared/model/anonymization-attribute-config";
import { Observable } from "rxjs";
import {
    AlgorithmData,
    AlgorithmService,
    ReadConfigResult
} from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { Algorithm } from "../../../shared/model/algorithm";
import {
    AnonymizationAttributeConfigurationComponent
} from "../components/anonymization-attribute-configuration/anonymization-attribute-configuration.component";

/**
 * Specialized algorithm data containing the specialized anonymization configuration object.
 */
export interface AnonymizationAlgorithmData extends AlgorithmData {
    config:  AnonymizationFormConfig;
}

/**
 * Defines the configuration structure of the anonymization.
 */
export class AnonymizationFormConfig extends ConfigurationObject {
    modelConfiguration: any; // Specify the type of `modelConfiguration` as needed.
    attributeConfiguration: AnonymizationAttributeRowConfiguration[];
}

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

    public override getConfigurationName(): string {
        return "anonymization";
    }

    public override createConfiguration(arg: AnonymizationFormConfig, selectedAlgorithm: Algorithm): Object {
        return {
            anonymization: {
                privacyModels: [
                    {
                        name: selectedAlgorithm.name,
                        type: selectedAlgorithm.type,
                        version: selectedAlgorithm.version,
                        modelConfiguration: arg.modelConfiguration,
                    },
                ],
                attributeConfiguration: arg.attributeConfiguration,
            }
         };
    }

    public override readConfiguration(arg: any, configurationName: string): ReadConfigResult {
        const selectedAlgorithm = this.getAlgorithmByName(arg["anonymization"]["privacyModels"][0]["name"])
        const config = arg["anonymization"]["privacyModels"][0];
        delete config["name"];
        delete config["type"];

        config[AnonymizationAttributeConfigurationComponent.formGroupName] = arg["anonymization"][AnonymizationAttributeConfigurationComponent.formGroupName];

        return {config, selectedAlgorithm};
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.ANONYMIZATION;
        configReg.lockedAfterStep = Steps.EXECUTION;
        configReg.displayName = "Anonymization Configuration";
        configReg.fetchConfig = null;
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "anonymization";
        configReg.orderNumber = 1;
        configReg.storeConfig = null;
        configReg.setConfigCallback = (config) => this.setConfigWait(config);

        this.configurationService.registerConfiguration(configReg);
    }

    /**
     * Returns the selected algorithm, its definition and the configuration object for the process.
     * @returns Observable for the selected algorithm and configuration.
     */
    public override getAlgorithmData$(): Observable<AnonymizationAlgorithmData> {
        return super.getAlgorithmData$() as Observable<AnonymizationAlgorithmData>;
    }
}
