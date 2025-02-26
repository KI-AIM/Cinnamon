import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EvaluationComponent } from './pages/evaluation/evaluation.component';
import { MatButtonModule } from "@angular/material/button";
import { MatExpansionModule } from "@angular/material/expansion";
import { SharedModule } from "../../shared/shared.module";
import { MatIconModule } from "@angular/material/icon";
import { MatMenuModule } from "@angular/material/menu";

@NgModule({
    declarations: [
        EvaluationComponent
    ],
    imports: [
        CommonModule,
        MatButtonModule,
        MatExpansionModule,
        SharedModule,
        MatIconModule,
        MatMenuModule,
    ]
})
export class EvaluationModule {
}
