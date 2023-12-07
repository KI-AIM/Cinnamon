import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { InformationDialogComponent } from "./components/information-dialog/information-dialog.component";
import { MatDialogModule } from "@angular/material/dialog";
import { MatButtonModule } from "@angular/material/button";

@NgModule({
	declarations: [InformationDialogComponent],
	imports: [CommonModule, MatDialogModule, MatButtonModule],
	exports: [],
})
export class SharedModule {}
