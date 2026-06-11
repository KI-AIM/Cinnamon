import { NgModule, inject, provideAppInitializer } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import {
    HtConfigurationComponent
} from "./components/ht-configuration/ht-configuration.component";
import {
    SynthetizationConfigurationComponent
} from "./pages/synthetization-configuration/synthetization-configuration.component";
import { SharedModule } from "../../shared/shared.module";
import { SynthetizationService } from "./services/synthetization.service";

@NgModule({
    declarations: [
        SynthetizationConfigurationComponent,
        HtConfigurationComponent,
    ],
    imports: [
        CommonModule,
        SharedModule,
        ReactiveFormsModule,
        MatCheckboxModule,
        MatFormFieldModule,
        MatIconModule,
        MatSelectModule,
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
