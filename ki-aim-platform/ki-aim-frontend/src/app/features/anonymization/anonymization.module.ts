import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    AnonymizationConfigurationComponent
} from "./pages/anonymization-configuration/anonymization-configuration.component";
import { SharedModule } from "../../shared/shared.module";


@NgModule({
    declarations: [AnonymizationConfigurationComponent],
    imports: [
        CommonModule,
        SharedModule
    ]
})
export class AnonymizationModule {
}
