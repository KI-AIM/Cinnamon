import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Steps } from 'src/app/core/enums/steps';
import { Algorithm } from 'src/app/shared/model/algorithm';
import { ConfigurationRegisterData } from 'src/app/shared/model/configuration-register-data';
import { AlgorithmService } from 'src/app/shared/services/algorithm.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

@Injectable({
  providedIn: 'root'
})
export class RiskAssessmentService extends AlgorithmService {

  constructor(
      http: HttpClient,
      configurationService: ConfigurationService,
  ) {
      super(http, configurationService);
  }

  override getStepName(): string {
    return "RISK_EVALUATION";
  }
  override getExecStepName(): string {
    return "EVALUATION";
  }
  public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
    return {
        risk_assessment_configuration: {
            data_format: 'cross-sectional',
            ...arg
        },
    };
}
  override readConfiguration(arg: Object, configurationName: string): { config: Object; selectedAlgorithm: Algorithm; } {
    // TODO: implement config reading
    throw new Error('Method not implemented.');
  }

  // TODO: implement fetchAlgorithm method? Do we want to distinguish between different algorithms?

  public registerConfig() {
    // const configReg = new ConfigurationRegisterData();
    // configReg.availableAfterStep = Steps.TECHNICAL_EVALUATION;
    // configReg.lockedAfterStep = null;
    // configReg.displayName = "Technical Evaluation Configuration";
    // configReg.fetchConfig = null;
    // // TODO fetch from server, user must be logged in for authentication
    // configReg.name = "evaluation_configuration";
    // configReg.orderNumber = 3;
    // configReg.storeConfig = null;
    // configReg.getConfigCallback = () => this.getConfig();
    // configReg.setConfigCallback = (config) => this.setConfigWait(config);

    // this.configurationService.registerConfiguration(configReg);

    
    // TODO: Think about when to perform and configure base evaluation and when risk evaluation
    throw new Error('Method not implemented.');
}

}
