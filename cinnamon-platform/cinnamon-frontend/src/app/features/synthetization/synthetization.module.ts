import { NgModule, inject, provideAppInitializer } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    SynthetizationConfigurationComponent
} from "./pages/synthetization-configuration/synthetization-configuration.component";
import { SharedModule } from "../../shared/shared.module";
import { SynthetizationService } from "./services/synthetization.service";
import { ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import {
    TextSynthesisConfigurationComponent
} from "./components/text-synthesis-configuration/text-synthesis-configuration.component";
import { MatExpansionModule } from "@angular/material/expansion";

@NgModule({
    declarations: [
        SynthetizationConfigurationComponent,
        TextSynthesisConfigurationComponent,
    ],
    imports: [
        CommonModule,
        SharedModule,
        MatButtonModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatExpansionModule,
        MatSelectModule,
        ReactiveFormsModule,
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
