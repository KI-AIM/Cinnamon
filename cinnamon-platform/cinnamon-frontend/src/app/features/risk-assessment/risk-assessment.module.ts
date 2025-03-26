import { NgModule, inject, provideAppInitializer } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    RiskAssessmentConfigurationComponent
} from './pages/risk-assessment-configuration/risk-assessment-configuration.component';
import { RiskAssessmentService } from "./services/risk-assessment.service";
import { SharedModule } from "../../shared/shared.module";

@NgModule({
    declarations: [
        RiskAssessmentConfigurationComponent
    ],
    imports: [
        CommonModule,
        SharedModule
    ],
    providers: [
        provideAppInitializer(() => {
        const initializerFn = ((service: RiskAssessmentService) => function () {
                return service.registerConfig();
            })(inject(RiskAssessmentService));
        return initializerFn();
      }),
    ],
})
export class RiskAssessmentModule {
}
