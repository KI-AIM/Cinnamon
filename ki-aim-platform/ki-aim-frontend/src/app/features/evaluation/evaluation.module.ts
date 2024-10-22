import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EvaluationComponent } from './pages/evaluation/evaluation.component';
import { MatButtonModule } from "@angular/material/button";
import { MatExpansionModule } from "@angular/material/expansion";
import { SharedModule } from "../../shared/shared.module";

@NgModule({
    declarations: [
        EvaluationComponent
    ],
    imports: [
        CommonModule,
        MatButtonModule,
        MatExpansionModule,
        SharedModule,
    ]
})
export class EvaluationModule {
}
