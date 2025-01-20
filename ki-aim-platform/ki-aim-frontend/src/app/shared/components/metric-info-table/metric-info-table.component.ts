import { Component, TemplateRef, ViewChild } from '@angular/core';
import { StatisticsMetaData } from "../../model/statistics";
import { MatDialog } from "@angular/material/dialog";
import { ProjectConfigurationService } from "../../services/project-configuration.service";
import { StatisticsService } from "../../services/statistics.service";

@Component({
    selector: 'app-metric-info-table',
    templateUrl: './metric-info-table.component.html',
    styleUrls: ['./metric-info-table.component.less']
})
export class MetricInfoTableComponent {

    protected metricInfo: StatisticsMetaData;

    @ViewChild('metricInfoDialog', { static: true }) templateRef: TemplateRef<any>;

    constructor(
        private dialog: MatDialog,
    ) {
    }

    public open(metricInfo: StatisticsMetaData) {
        this.metricInfo = metricInfo;

        this.dialog.open(this.templateRef, {
            width: '60%'
        });
    }
}
