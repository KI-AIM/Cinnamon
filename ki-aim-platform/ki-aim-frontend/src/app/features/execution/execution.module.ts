import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ExecutionComponent } from './pages/execution/execution.component';
import { MatButtonModule } from "@angular/material/button";
import {SharedModule} from "../../shared/shared.module";
import {MatExpansionModule} from "@angular/material/expansion";

@NgModule({
    declarations: [
        ExecutionComponent
    ],
    imports: [
        CommonModule,
        MatButtonModule,
        SharedModule,
        MatExpansionModule
    ],
})
export class ExecutionModule {
}
