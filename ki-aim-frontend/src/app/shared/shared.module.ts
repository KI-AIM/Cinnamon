import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { InformationDialogComponent } from "./components/information-dialog/information-dialog.component";
import { MatDialogModule } from "@angular/material/dialog";
import { MatButtonModule } from "@angular/material/button";
import { InfoCardComponent } from './components/info-card/info-card.component';
import { MatCardModule } from "@angular/material/card";
import { LoadingSpinnerComponent } from './components/loading-spinner/loading-spinner.component';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NoSpaceValidatorDirective } from './directives/no-space-validator.directive';

@NgModule({
	declarations: [InformationDialogComponent, InfoCardComponent, LoadingSpinnerComponent, NoSpaceValidatorDirective],
	imports: [CommonModule, MatDialogModule, MatButtonModule, MatCardModule, MatProgressSpinnerModule],
	exports: [InfoCardComponent, LoadingSpinnerComponent, NoSpaceValidatorDirective],
})
export class SharedModule {}
