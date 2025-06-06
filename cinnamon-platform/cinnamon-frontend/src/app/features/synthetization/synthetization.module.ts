import { NgModule, inject, provideAppInitializer } from '@angular/core';
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
        provideAppInitializer(() => {
        const initializerFn = ((service: SynthetizationService) => function () {
                return service.registerConfig();
            })(inject(SynthetizationService));
        return initializerFn();
      }),
    ]
})
export class SynthetizationModule {
}
