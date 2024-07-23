import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    SynthetizationConfigurationComponent
} from "./pages/synthetization-configuration/synthetization-configuration.component";
import { SharedModule } from "../../shared/shared.module";

@NgModule({
    declarations: [SynthetizationConfigurationComponent],
    imports: [
        CommonModule,
        SharedModule
    ]
})
export class SynthetizationModule {
}
