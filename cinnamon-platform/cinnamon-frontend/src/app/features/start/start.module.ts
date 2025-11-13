import { CommonModule, NgOptimizedImage } from "@angular/common";
import { NgModule } from "@angular/core";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatDividerModule } from "@angular/material/divider";
import { MatIconModule } from "@angular/material/icon";
import { RouterModule } from "@angular/router";
import { SharedModule } from "src/app/shared/shared.module";
import { StartpageComponent } from "./pages/startpage/startpage.component";

@NgModule({
    declarations: [StartpageComponent],
    imports: [
        CommonModule,
        RouterModule,
        SharedModule,
        MatCardModule,
        MatButtonModule,
        MatDividerModule,
        MatIconModule,
        NgOptimizedImage,
    ],
    exports: [StartpageComponent],
})
export class StartModule {
}
