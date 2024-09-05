import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ExecutionComponent } from './pages/execution/execution.component';
import { MatButtonModule } from "@angular/material/button";

@NgModule({
    declarations: [
        ExecutionComponent
    ],
    imports: [
        CommonModule,
        MatButtonModule
    ]
})
export class ExecutionModule {
}
