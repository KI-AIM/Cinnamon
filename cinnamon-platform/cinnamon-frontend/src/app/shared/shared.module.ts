import { CommonModule, NgOptimizedImage } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatMenuModule } from "@angular/material/menu";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatTableModule } from "@angular/material/table";
import { MatTooltip } from "@angular/material/tooltip";
import { WorkstepBoxComponent } from "@shared/components/workstep-box/workstep-box.component";
import { WorkstepListComponent } from "@shared/components/workstep-list/workstep-list.component";
import { ExpansionPanelOverflowDirective } from "@shared/directives/expansion-panel-overflow.directive";
import { NgxEchartsModule } from "ngx-echarts";
import { ChartCorrelationComponent } from "src/app/shared/components/chart-correlation/chart-correlation.component";
import {
    InformationDialogExplanationComponent
} from 'src/app/shared/components/information-dialog-explanation/information-dialog-explanation.component';
import {
    InformationDialogOptionsComponent
} from 'src/app/shared/components/information-dialog-options/information-dialog-options.component';
import {
    InformationDialogPartComponent
} from 'src/app/shared/components/information-dialog-part/information-dialog-part.component';
import { ProjectSettingsComponent } from "src/app/shared/components/project-settings/project-settings.component";
import { StatisticsSorterPipe } from "src/app/shared/pipes/statistics-sorter.pipe";
import { ChartCalendarComponent } from './components/chart-calendar/chart-calendar.component';
import { ChartDensityComponent } from './components/chart-density/chart-density.component';
import { ChartFrequencyComponent } from './components/chart-frequency/chart-frequency.component';
import { ChartSelectComponent } from './components/chart-select/chart-select.component';
import { ChartComponent } from './components/chart/chart.component';
import { ColorLegendComponent } from './components/color-legend/color-legend.component';
import { ConfigurationFormComponent } from './components/configuration-form/configuration-form.component';
import { ConfigurationGroupComponent } from './components/configuration-group/configuration-group.component';
import {
    ConfigurationInputArrayComponent
} from './components/configuration-input-array/configuration-input-array.component';
import {
    ConfigurationInputAttributeListComponent
} from './components/configuration-input-attribute-list/configuration-input-attribute-list.component';
import {
    ConfigurationInputInfoComponent
} from './components/configuration-input-info/configuration-input-info.component';
import { ConfigurationInputComponent } from './components/configuration-input/configuration-input.component';
import { ConfigurationPageComponent } from "./components/configuration-page/configuration-page.component";
import {
    ConfigurationSelectionComponent
} from './components/configuration-selection/configuration-selection.component';
import {
    DataInspectionAttributeDetailsComponent
} from './components/data-inspection-attribute-details/data-inspection-attribute-details.component';
import {
    DataInspectionAttributeComponent
} from './components/data-inspection-attribute/data-inspection-attribute.component';
import { DataInspectionComponent } from './components/data-inspection/data-inspection.component';
import { DataTableComponent } from "./components/data-table/data-table.component";
import { FileUploadComponent } from "./components/file-upload/file-upload.component";
import { InfoCardComponent } from './components/info-card/info-card.component';
import {
    InformationDialogExamplesComponent
} from './components/information-dialog-examples/information-dialog-examples.component';
import { InformationDialogComponent, } from "./components/information-dialog/information-dialog.component";
import { LoadingSpinnerComponent } from './components/loading-spinner/loading-spinner.component';
import { MetricConfigurationComponent } from './components/metric-configuration/metric-configuration.component';
import { MetricInfoTableComponent } from './components/metric-info-table/metric-info-table.component';
import { ProjectExportComponent } from './components/project-export/project-export.component';
import { TooltipComponent } from './components/tooltip/tooltip.component';
import { WorkstepItemComponent } from './components/workstep-item/workstep-item.component';
import { WorkstepTitleComponent } from "./components/workstep-title/workstep-title.component";
import { WorkstepComponent } from "./components/workstep/workstep.component";
import { NoSpaceValidatorDirective } from './directives/no-space-validator.directive';
import { ColumnConfigurationNameFilterPipe } from "./pipes/column-configuration-name-filter.pipe";
import { InstanceOfPipe } from "./pipes/instance-of.pipe";
import { MetricFilterPipe } from './pipes/metric-filter.pipe';
import { MetricSorterPipe } from './pipes/metric-sorter.pipe';
import { StatisticsFilterPipe } from './pipes/statistics-filter.pipe';

@NgModule({
    declarations: [
        // Components
        ChartDensityComponent,
        ChartFrequencyComponent,
        ChartComponent,
        ChartSelectComponent,
        ChartCalendarComponent,
        ChartCorrelationComponent,
        ColorLegendComponent,
        ConfigurationFormComponent,
        ConfigurationGroupComponent,
        ConfigurationInputArrayComponent,
        ConfigurationInputAttributeListComponent,
        ConfigurationInputComponent,
        ConfigurationInputInfoComponent,
        ConfigurationPageComponent,
        ConfigurationSelectionComponent,
        DataInspectionComponent,
        DataInspectionAttributeComponent,
        DataInspectionAttributeDetailsComponent,
        DataTableComponent,
        FileUploadComponent,
        InfoCardComponent,
        InformationDialogComponent,
        InformationDialogExamplesComponent,
        InformationDialogExplanationComponent,
        InformationDialogOptionsComponent,
        InformationDialogPartComponent,
        LoadingSpinnerComponent,
        MetricConfigurationComponent,
        MetricInfoTableComponent,
        ProjectExportComponent,
        ProjectSettingsComponent,
        TooltipComponent,
        WorkstepComponent,
        WorkstepBoxComponent,
        WorkstepItemComponent,
        WorkstepListComponent,
        WorkstepTitleComponent,
        // Directives
        ExpansionPanelOverflowDirective,
        NoSpaceValidatorDirective,
        // Pipes
        ColumnConfigurationNameFilterPipe,
        InstanceOfPipe,
        MetricFilterPipe,
        MetricSorterPipe,
        StatisticsFilterPipe,
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
        NgxEchartsModule.forRoot(({
            echarts: () => import('echarts')
        })),
        MatMenuModule,
        MatRadioModule,
        MatTooltip,
        StatisticsSorterPipe,
    ],
    exports: [
        // Components
        ColorLegendComponent,
        ConfigurationFormComponent,
        ConfigurationInputComponent,
        ConfigurationInputInfoComponent,
        ConfigurationPageComponent,
        DataInspectionComponent,
        DataInspectionAttributeComponent,
        DataTableComponent,
        FileUploadComponent,
        InfoCardComponent,
        InformationDialogComponent,
        InformationDialogExamplesComponent,
        InformationDialogExplanationComponent,
        InformationDialogOptionsComponent,
        InformationDialogPartComponent,
        LoadingSpinnerComponent,
        MetricConfigurationComponent,
        MetricInfoTableComponent,
        ProjectExportComponent,
        ProjectSettingsComponent,
        TooltipComponent,
        WorkstepComponent,
        WorkstepBoxComponent,
        WorkstepItemComponent,
        WorkstepListComponent,
        WorkstepTitleComponent,
        // Directives
        ExpansionPanelOverflowDirective,
        NoSpaceValidatorDirective,
        // Pipes
        InstanceOfPipe,
    ],
})
export class SharedModule {}
