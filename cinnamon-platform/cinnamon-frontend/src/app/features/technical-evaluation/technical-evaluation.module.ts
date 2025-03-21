import { NgModule, inject, provideAppInitializer } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    TechnicalEvaluationConfigurationComponent
} from './pages/technical-evaluation-configuration/technical-evaluation-configuration.component';
import { SharedModule } from "../../shared/shared.module";
import { TechnicalEvaluationService } from "./services/technical-evaluation.service";

@NgModule({
    declarations: [
        TechnicalEvaluationConfigurationComponent
    ],
    imports: [
        CommonModule,
        SharedModule
    ],
    providers: [
        provideAppInitializer(() => {
        const initializerFn = ((service: TechnicalEvaluationService) => function () {
                return service.registerConfig();
            })(inject(TechnicalEvaluationService));
        return initializerFn();
      }),
    ],
})
export class TechnicalEvaluationModule {
}
