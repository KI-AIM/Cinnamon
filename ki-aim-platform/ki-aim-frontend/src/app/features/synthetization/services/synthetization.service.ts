import { Injectable } from '@angular/core';
import { AlgorithmService } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";

@Injectable({
    providedIn: 'root',
})
export class SynthetizationService extends AlgorithmService {

    _configuration: Object;

    constructor(
        http: HttpClient,
        configurationService: ConfigurationService,
    ) {
        super(http, configurationService);
    }

    public override getStepName = (): string => "SYNTHETIZATION";

    public override getConfigurationName = (): string => "synthetization";

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.SYNTHETIZATION;
        configReg.lockedAfterStep = null;
        configReg.displayName = "Synthetization Configuration";
        configReg.fetchConfig = null;
        configReg.name = this.getConfigurationName();
        configReg.orderNumber = 3;
        configReg.storeConfig = null;
        configReg.getConfigCallback = () => this._configuration;
        configReg.setConfigCallback = (config) => this._configuration = config;

        this.configurationService.registerConfiguration(configReg);
    }
}
