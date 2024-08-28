import { APP_INITIALIZER, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    AnonymizationConfigurationComponent
} from "./pages/anonymization-configuration/anonymization-configuration.component";
import { SharedModule } from "../../shared/shared.module";
import { AnonymizationService } from "./services/anonymization.service";


@NgModule({
    declarations: [AnonymizationConfigurationComponent],
    imports: [
        CommonModule,
        SharedModule
    ],
    providers: [
        {
            // Calls the useFactory function when starting the application
            provide: APP_INITIALIZER,
            useFactory: (service: AnonymizationService) => function () {
                return service.registerConfig();
            },
            deps: [AnonymizationService],
            multi: true,
        },
    ],
})
export class AnonymizationModule {
}
