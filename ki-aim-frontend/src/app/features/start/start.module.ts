import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { StartpageComponent } from "./pages/startpage/startpage.component";
import { RouterModule } from "@angular/router";
import { MatCardModule } from "@angular/material/card";
import { MatButtonModule } from "@angular/material/button";
import { MatDividerModule } from "@angular/material/divider";
import { MatIconModule } from "@angular/material/icon";
import { SharedModule } from "src/app/shared/shared.module";

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
	],
	exports: [StartpageComponent],
})
export class StartModule {}
