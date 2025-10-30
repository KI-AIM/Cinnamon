import { NgModule } from "@angular/core";
import { CommonModule, NgOptimizedImage } from "@angular/common";
import { ExpansionPanelOverflowDirective } from "@shared/directives/expansion-panel-overflow.directive";
import { NumberToTextPipe } from "@shared/pipes/number-to-text.pipe";
import { ChartCorrelationComponent } from "src/app/shared/components/chart-correlation/chart-correlation.component";
import { StatisticsSorterPipe } from "src/app/shared/pipes/statistics-sorter.pipe";
import { ProjectSettingsComponent } from "src/app/shared/components/project-settings/project-settings.component";
import { MatTooltip } from "@angular/material/tooltip";
import { WorkstepBoxComponent } from "@shared/components/workstep-box/workstep-box.component";
import { WorkstepListComponent } from "@shared/components/workstep-list/workstep-list.component";
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
import {DataTableComponent} from "./components/data-table/data-table.component";
import {MatTableModule} from "@angular/material/table";
import {MatPaginatorModule} from "@angular/material/paginator";
import {MatProgressBarModule} from "@angular/material/progress-bar";
import { DataInspectionComponent } from './components/data-inspection/data-inspection.component';
import { DataInspectionAttributeComponent } from './components/data-inspection-attribute/data-inspection-attribute.component';
import {ColumnConfigurationNameFilterPipe} from "./pipes/column-configuration-name-filter.pipe";
import { DataInspectionAttributeDetailsComponent } from './components/data-inspection-attribute-details/data-inspection-attribute-details.component';
import { NgxEchartsModule } from "ngx-echarts";
import { ChartDensityComponent } from './components/chart-density/chart-density.component';
import { StatisticsFilterPipe } from './pipes/statistics-filter.pipe';
import { ChartFrequencyComponent } from './components/chart-frequency/chart-frequency.component';
import { ChartComponent } from './components/chart/chart.component';
import { ChartSelectComponent } from './components/chart-select/chart-select.component';
import { ChartCalendarComponent } from './components/chart-calendar/chart-calendar.component';
import { MetricFilterPipe } from './pipes/metric-filter.pipe';
import { MetricSorterPipe } from './pipes/metric-sorter.pipe';
import {MatMenuModule} from "@angular/material/menu";
import { ColorLegendComponent } from './components/color-legend/color-legend.component';
import {MatRadioModule} from "@angular/material/radio";
import {InstanceOfPipe} from "./pipes/instance-of.pipe";
import { MetricInfoTableComponent } from './components/metric-info-table/metric-info-table.component';
import { MetricConfigurationComponent } from './components/metric-configuration/metric-configuration.component';
import { ConfigurationInputAttributeListComponent } from './components/configuration-input-attribute-list/configuration-input-attribute-list.component';
import { WorkstepComponent } from "./components/workstep/workstep.component";
import { WorkstepTitleComponent } from "./components/workstep-title/workstep-title.component";
import { FileUploadComponent } from "./components/file-upload/file-upload.component";
import { WorkstepItemComponent } from './components/workstep-item/workstep-item.component';
import { ProjectExportComponent } from './components/project-export/project-export.component';
import { TooltipComponent } from './components/tooltip/tooltip.component';
import { DataInspectionMetricTableComponent } from './components/data-inspection-metric-table/data-inspection-metric-table.component';
import { InjectMetricImportancePipe } from './pipes/inject-metric-importance.pipe';
import { DataInspectionUtilityComponent } from './components/data-inspection-utility/data-inspection-utility.component';
import { DataInspectionUtilityTableComponent } from './components/data-inspection-utility-table/data-inspection-utility-table.component';
import { FormatNumberPipe } from './pipes/format-number.pipe';
import { DataInspectionRisksComponent } from './components/data-inspection-risks/data-inspection-risks.component';
import { RiskColorIndexPipe } from './pipes/risk-color-index.pipe';
import { RiskConfidenceIntervalPipe } from './pipes/risk-confidence-interval.pipe';
import { IsObjectPipe } from './pipes/is-object.pipe';

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
        DataInspectionMetricTableComponent,
        DataInspectionRisksComponent,
        DataInspectionUtilityComponent,
        DataInspectionUtilityTableComponent,
        DataTableComponent,
        FileUploadComponent,
        InfoCardComponent,
        InformationDialogComponent,
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
        FormatNumberPipe,
        InjectMetricImportancePipe,
        InstanceOfPipe,
        IsObjectPipe,
        MetricFilterPipe,
        MetricSorterPipe,
        NumberToTextPipe,
        RiskColorIndexPipe,
        RiskConfidenceIntervalPipe,
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
        ChartCorrelationComponent,
        ChartDensityComponent,
        ChartFrequencyComponent,
        ColorLegendComponent,
        ConfigurationFormComponent,
        ConfigurationInputComponent,
        ConfigurationPageComponent,
        DataInspectionComponent,
        DataInspectionAttributeComponent,
        DataInspectionMetricTableComponent,
        DataInspectionRisksComponent,
        DataInspectionUtilityComponent,
        DataInspectionUtilityTableComponent,
        DataTableComponent,
        FileUploadComponent,
        InfoCardComponent,
        LoadingSpinnerComponent,
        MetricConfigurationComponent,
        MetricInfoTableComponent,
        ProjectExportComponent,
        ProjectSettingsComponent,
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
        IsObjectPipe,
        TooltipComponent,
        NumberToTextPipe,
    ],
})
export class SharedModule {}
