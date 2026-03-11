import { Component, Input } from '@angular/core';
import { FormGroup } from "@angular/forms";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { MetricImportance, MetricImportanceData } from "../../model/project-settings";
import { StatisticsService } from "../../services/statistics.service";

@Component({
    selector: 'app-metric-configuration',
    templateUrl: './metric-configuration.component.html',
    styleUrls: ['./metric-configuration.component.less'],
    standalone: false
})
export class MetricConfigurationComponent {

    @Input() public algorithmDefinition!: AlgorithmDefinition
    @Input() public projectSettingsForm!: FormGroup;

    protected readonly MetricImportance = MetricImportance;
    protected readonly MetricImportanceData = MetricImportanceData;
    protected readonly Object = Object;

    constructor(
        protected readonly statisticsService: StatisticsService,
    ) {
    }

    protected get metricSettingsForm(): FormGroup {
        return this.projectSettingsForm.get('metricConfiguration') as FormGroup;
    }
}
