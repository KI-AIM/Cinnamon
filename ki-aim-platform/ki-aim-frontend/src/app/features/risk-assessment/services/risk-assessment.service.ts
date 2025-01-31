import { Injectable } from '@angular/core';
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";

@Injectable({
  providedIn: 'root'
})
export class RiskAssessmentService {

    constructor(
        private readonly configurationService: ConfigurationService,
    ) {
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.RISK_EVALUATION;
        configReg.lockedAfterStep = null;
        configReg.displayName = "Technical Evaluation Configuration";
        configReg.fetchConfig = null;
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "evaluation_configuration";
        configReg.orderNumber = 3;
        configReg.storeConfig = null;
        configReg.getConfigCallback = () => "";
        configReg.setConfigCallback = (config) => {};

        this.configurationService.registerConfiguration(configReg);
    }
}
