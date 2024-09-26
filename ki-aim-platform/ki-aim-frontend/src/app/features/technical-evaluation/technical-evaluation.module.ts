import { APP_INITIALIZER, NgModule } from '@angular/core';
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
        {
            // Calls the useFactory function when starting the application
            provide: APP_INITIALIZER,
            useFactory: (service: TechnicalEvaluationService) => function () {
                return service.registerConfig();
            },
            deps: [TechnicalEvaluationService],
            multi: true,
        },
    ],
})
export class TechnicalEvaluationModule {
}
