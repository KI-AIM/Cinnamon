import { Component, Input, OnInit } from '@angular/core';
import {
    MetricTableType
} from "@shared/components/data-inspection-metric-table/data-inspection-metric-table.component";
import { AttributeStatistics, GraphType, } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { DataType } from "../../model/data-type";
import { MetricImportance, MetricSettings } from "../../model/project-settings";
import { ProjectConfigurationService } from "../../services/project-configuration.service";
import { map, Observable } from "rxjs";
import { MetricTableData } from "../../model/metric-table-data";

@Component({
    selector: 'app-data-inspection-attribute-details',
    templateUrl: './data-inspection-attribute-details.component.html',
    styleUrls: ['./data-inspection-attribute-details.component.less'],
    standalone: false
})
export class DataInspectionAttributeDetailsComponent implements OnInit {
    protected readonly DataType = DataType;
    protected readonly MetricTableType = MetricTableType;

    @Input() public attributeStatistics!: AttributeStatistics;
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';
    @Input() public processingSteps: string[] = [];

    protected graphType: GraphType = 'histogram';
    protected hasSynthetic: boolean = false;

    protected importantMetricsTableData = new MetricTableData();
    protected additionalMetricsTableData = new MetricTableData();

    protected originalDisplayName: string;
    protected datasetDisplayName: string;

    protected metricConfig$: Observable<MetricSettings>;

    constructor(
        protected readonly projectConfigService: ProjectConfigurationService,
        protected statisticsService: StatisticsService,
    ) {
        this.importantMetricsTableData.filter.importance = MetricImportance.IMPORTANT;
        this.additionalMetricsTableData.filter.importance = MetricImportance.ADDITIONAL;
    }

    ngOnInit(): void {
        this.graphType = this.isContinuous() ? 'histogram' : 'frequency';
        this.hasSynthetic = this.mainData == 'synthetic';

        if (this.hasSynthetic) {
            this.importantMetricsTableData.sort.direction = 'desc';
            this.importantMetricsTableData.sort.column = 'colorIndex';

            this.additionalMetricsTableData.sort.direction = 'desc';
            this.additionalMetricsTableData.sort.column = 'colorIndex';
        }

        this.originalDisplayName = this.statisticsService.getOriginalName(this.sourceDataset);
        this.datasetDisplayName = this.statisticsService.getSyntheticName(this.processingSteps)
        this.metricConfig$ = this.projectConfigService.projectSettings$.pipe(
            map(val => val.metricConfiguration)
        );
    }

    protected isContinuous(): boolean {
        return this.attributeStatistics.plot.density != null;
    }
}
