import { HttpClient } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { Steps } from "@core/enums/steps";
import {
    AnonymizationAttributeConfigurationService
} from "@features/anonymization/services/anonymization-attribute-configuration.service";
import { Algorithm } from "@shared/model/algorithm";
import {
    AnonymizationAttributeRowConfiguration,
    ConfigurationObject
} from "@shared/model/anonymization-attribute-config";
import { TextAnonymizationConfiguration } from "@features/anonymization/services/text-anonymization-configuration.service";
import { ConfigurationRegisterData } from "@shared/model/configuration-register-data";
import { AlgorithmData, AlgorithmService, ReadConfigResult } from "@shared/services/algorithm.service";
import { ConfigurationService } from "@shared/services/configuration.service";
import { ProjectService } from "@shared/services/project.service";
import { Observable } from "rxjs";

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
    textAnonymizationConfiguration: TextAnonymizationConfiguration | null = null;
}

@Injectable({
    providedIn: 'root',
})
export class AnonymizationService extends AlgorithmService {

    constructor(
        private readonly attributeConfigurationService: AnonymizationAttributeConfigurationService,
        http: HttpClient,
        configurationService: ConfigurationService,
        projectService: ProjectService,
    ) {
        super(http, configurationService, projectService);
    }

    public override getConfigurationName(): string {
        return "anonymization";
    }

    public override createConfiguration(arg: AnonymizationFormConfig, selectedAlgorithm: Algorithm): Object {
        return {
            anonymization: {
                algorithm: {
                    id: selectedAlgorithm.name,
                    version: selectedAlgorithm.version,
                },
                privacyModels: [
                    {
                        name: selectedAlgorithm.name,
                        type: selectedAlgorithm.type,
                        version: selectedAlgorithm.version,
                        modelConfiguration: arg.modelConfiguration,
                    },
                ],
                attributeConfiguration: arg.attributeConfiguration,
                textAnonymizationConfiguration: arg.textAnonymizationConfiguration,
            }
        };
    }

    public override readConfiguration(arg: any, _: string): ReadConfigResult {
        const selectedAlgorithm = this.getAlgorithmByName(arg["anonymization"]["privacyModels"][0]["name"])
        const config = arg["anonymization"]["privacyModels"][0];
        delete config["name"];
        delete config["type"];

        config[this.attributeConfigurationService.formGroupName] = arg["anonymization"][this.attributeConfigurationService.formGroupName];
        config.textAnonymizationConfiguration = arg["anonymization"].textAnonymizationConfiguration;

        return {config, selectedAlgorithm};
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.ANONYMIZATION;
        configReg.lockedAfterStep = Steps.EXECUTION;
        configReg.displayName = "Anonymization Configuration";
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "anonymization";
        configReg.orderNumber = 1;

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
