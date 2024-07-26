import { Injectable } from '@angular/core';
import { AlgorithmService } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { Algorithm } from "../../../shared/model/algorithm";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";

@Injectable({
    providedIn: 'root',
})
export class AnonymizationService extends AlgorithmService {

    _configuration: Object;

    constructor(
        private readonly http2: HttpClient,
        private readonly configurationService2: ConfigurationService,
    ) {
        super(http2, configurationService2);
    }

    public override getStepName = (): string => "anonymization";

    public override getConfigurationName = (): string => "anonymization";

    // TODO set correct URL
    public override getDefinitionUrl = (algorithm: Algorithm) => `/synthetic_${algorithm.type}_data_generator/synthesizer_config/${algorithm.name}.yaml`;

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.ANONYMIZATION_CONFIG;
        configReg.lockedAfterStep = null;
        configReg.displayName = "Anonymization Configuration";
        configReg.fetchConfig = null;
        configReg.name = this.getConfigurationName();
        configReg.orderNumber = 3;
        configReg.storeConfig = null;
        configReg.getConfigCallback = () => this._configuration;
        configReg.setConfigCallback = (config) => this._configuration = config;

        this.configurationService.registerConfiguration(configReg);
    }
}
