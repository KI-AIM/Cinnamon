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
import { ConfigurationInputComponent } from './components/configuration-input/configuration-input.component';
import { ConfigurationInputInfoComponent } from './components/configuration-input-info/configuration-input-info.component';
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { ReactiveFormsModule } from "@angular/forms";
import { ConfigurationFormComponent } from './components/configuration-form/configuration-form.component';
import { MatIconModule } from "@angular/material/icon";
import { MatSelectModule } from "@angular/material/select";
import { ConfigurationInputArrayComponent } from './components/configuration-input-array/configuration-input-array.component';
import { ConfigurationSelectionComponent } from './components/configuration-selection/configuration-selection.component';
import { ConfigurationPageComponent } from './components/configuration-page/configuration-page.component';

@NgModule({
	declarations: [InformationDialogComponent, InfoCardComponent, LoadingSpinnerComponent, NoSpaceValidatorDirective, ConfigurationInputComponent, ConfigurationInputInfoComponent, ConfigurationFormComponent, ConfigurationInputArrayComponent, ConfigurationSelectionComponent, ConfigurationPageComponent],
    imports: [CommonModule, MatDialogModule, MatButtonModule, MatCardModule, MatProgressSpinnerModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule, MatIconModule, MatSelectModule],
    exports: [InfoCardComponent, LoadingSpinnerComponent, NoSpaceValidatorDirective, ConfigurationInputComponent, ConfigurationFormComponent],
})
export class SharedModule {}
