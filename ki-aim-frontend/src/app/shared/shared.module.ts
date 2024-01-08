import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { InformationDialogComponent } from "./components/information-dialog/information-dialog.component";
import { MatDialogModule } from "@angular/material/dialog";
import { MatButtonModule } from "@angular/material/button";
import { InfoCardComponent } from './components/info-card/info-card.component';
import { MatCardModule } from "@angular/material/card";
import { LoadingSpinnerComponent } from './components/loading-spinner/loading-spinner.component';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner'

@NgModule({
	declarations: [InformationDialogComponent, InfoCardComponent, LoadingSpinnerComponent],
	imports: [CommonModule, MatDialogModule, MatButtonModule, MatCardModule, MatProgressSpinnerModule],
	exports: [InfoCardComponent, LoadingSpinnerComponent],
})
export class SharedModule {}
