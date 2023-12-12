import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { InformationDialogComponent } from "./components/information-dialog/information-dialog.component";
import { MatDialogModule } from "@angular/material/dialog";
import { MatButtonModule } from "@angular/material/button";
import { InfoCardComponent } from './components/info-card/info-card.component';
import { MatCardModule } from "@angular/material/card";

@NgModule({
	declarations: [InformationDialogComponent, InfoCardComponent],
	imports: [CommonModule, MatDialogModule, MatButtonModule, MatCardModule],
	exports: [InfoCardComponent],
})
export class SharedModule {}
