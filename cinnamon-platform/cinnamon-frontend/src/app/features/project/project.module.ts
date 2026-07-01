import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from "@angular/router";
import { ProjectShellComponent } from './components/project-shell/project-shell.component';



@NgModule({
  declarations: [
    ProjectShellComponent
  ],
    imports: [
        CommonModule,
        RouterOutlet
    ]
})
export class ProjectModule { }
