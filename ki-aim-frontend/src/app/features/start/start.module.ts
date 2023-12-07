import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StartpageComponent } from './pages/startpage/startpage.component';
import { RouterModule } from '@angular/router';

@NgModule({
    declarations: [
        StartpageComponent,
    ],
    imports: [
        CommonModule,
        RouterModule,
    ],
    exports: [
        StartpageComponent,
    ]
})
export class StartModule {}
