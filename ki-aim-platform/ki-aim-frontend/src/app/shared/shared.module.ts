import { NgModule } from "@angular/core";
import { CommonModule, NgOptimizedImage } from "@angular/common";
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
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import { ConfigurationFormComponent } from './components/configuration-form/configuration-form.component';
import { MatIconModule } from "@angular/material/icon";
import { MatSelectModule } from "@angular/material/select";
import { ConfigurationInputArrayComponent } from './components/configuration-input-array/configuration-input-array.component';
import { ConfigurationSelectionComponent } from './components/configuration-selection/configuration-selection.component';
import { ConfigurationPageComponent } from "./components/configuration-page/configuration-page.component";
import { ConfigurationGroupComponent } from './components/configuration-group/configuration-group.component';
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatExpansionModule } from "@angular/material/expansion";
import { ConfigurationUploadComponent } from "./components/configuration-upload/configuration-upload.component";
import { ConfigurationManagementComponent } from "./components/configuration-management/configuration-management.component";
import {DataTableComponent} from "./components/data-table/data-table.component";
import {MatTableModule} from "@angular/material/table";
import {MatPaginatorModule} from "@angular/material/paginator";
import {MatProgressBarModule} from "@angular/material/progress-bar";
import { DataInspectionComponent } from './components/data-inspection/data-inspection.component';
import { DataInspectionAttributeComponent } from './components/data-inspection-attribute/data-inspection-attribute.component';
import {ColumnConfigurationNameFilterPipe} from "./pipes/column-configuration-name-filter.pipe";
import { DataInspectionAttributeDetailsComponent } from './components/data-inspection-attribute-details/data-inspection-attribute-details.component';

@NgModule({
    declarations: [
        // Components
        ConfigurationFormComponent,
        ConfigurationGroupComponent,
        ConfigurationInputArrayComponent,
        ConfigurationInputComponent,
        ConfigurationInputInfoComponent,
        ConfigurationManagementComponent,
        ConfigurationPageComponent,
        ConfigurationSelectionComponent,
        ConfigurationUploadComponent,
        DataInspectionComponent,
        DataInspectionAttributeComponent,
        DataInspectionAttributeDetailsComponent,
        DataTableComponent,
        InfoCardComponent,
        InformationDialogComponent,
        LoadingSpinnerComponent,
        // Directives
        NoSpaceValidatorDirective,
        // Pipes
        ColumnConfigurationNameFilterPipe,
    ],
    imports: [
        CommonModule,
        MatButtonModule,
        MatCardModule,
        MatCheckboxModule,
        MatDialogModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatPaginatorModule,
        MatProgressSpinnerModule,
        MatSelectModule,
        MatTableModule,
        ReactiveFormsModule,
        MatProgressBarModule,
        NgOptimizedImage,
        FormsModule,
    ],
    exports: [
        ConfigurationFormComponent,
        ConfigurationInputComponent,
        ConfigurationManagementComponent,
        ConfigurationPageComponent,
        ConfigurationUploadComponent,
        DataTableComponent,
        InfoCardComponent,
        LoadingSpinnerComponent,
        NoSpaceValidatorDirective,
        DataInspectionComponent,
    ],
})
export class SharedModule {}
