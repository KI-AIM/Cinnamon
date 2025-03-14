import { APP_INITIALIZER, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    SynthetizationConfigurationComponent
} from "./pages/synthetization-configuration/synthetization-configuration.component";
import { SharedModule } from "../../shared/shared.module";
import { SynthetizationService } from "./services/synthetization.service";

@NgModule({
    declarations: [SynthetizationConfigurationComponent],
    imports: [
        CommonModule,
        SharedModule
    ],
    providers: [
        {
            // Calls the useFactory function when starting the application
            provide: APP_INITIALIZER,
            useFactory: (service: SynthetizationService) => function () {
                return service.registerConfig();
            },
            deps: [SynthetizationService],
            multi: true,
        },
    ]
})
export class SynthetizationModule {
}
