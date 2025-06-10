import { Component, Input, OnInit } from '@angular/core';
import {
    AttributeStatistics,
    GraphType,
    StatisticsData,
    StatisticsValues,
    StatisticsValueTypes
} from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { DataType } from "../../model/data-type";
import { MetricImportance, MetricImportanceData, MetricSettings } from "../../model/project-settings";
import { ProjectConfigurationService } from "../../services/project-configuration.service";
import { map, Observable } from "rxjs";
import { MetricTableData, MetricTableFilterData, MetricTableSortData, SortType } from "../../model/metric-table-data";

@Component({
    selector: 'app-data-inspection-attribute-details',
    templateUrl: './data-inspection-attribute-details.component.html',
    styleUrls: ['./data-inspection-attribute-details.component.less'],
    standalone: false
})
export class DataInspectionAttributeDetailsComponent implements OnInit {
    protected readonly DataType = DataType;
    protected readonly MetricTableData = MetricTableData
    protected readonly MetricTableFilterData = MetricTableFilterData;
    protected readonly MetricTableSortData = MetricTableSortData;

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

    protected sort(sortData: MetricTableSortData, type: SortType): void {
        if (sortData.column === type) {
            if (sortData.direction === 'asc') {
                sortData.direction = 'desc';
            } else if (sortData.direction === 'desc') {
                sortData.column = null;
                sortData.direction = null;
            }
        } else {
            sortData.column = type;
            sortData.direction = 'asc';
        }
    }

    protected getColorIndex(data: StatisticsValueTypes): number {
        if (data instanceof StatisticsValues) {
            return data.difference.color_index;
        } else {
            return Math.max(data.values.real.color_index, data.values.synthetic.color_index);
        }
    }

    protected getDifference(data: StatisticsValueTypes, which: 'absolute' | 'percentage'): number | string  {
        if (data instanceof StatisticsValues) {
            return data.difference[which];
        } else {
            return "N/A";
        }
    }

    protected getValue(data: StatisticsData<any>, which : 'real' | 'synthetic'): number | string {
        return this.statisticsService.getValue(data, which);
    }

    protected injectImportance(input: Array<[string, StatisticsValueTypes]>, config: MetricSettings): Array<[string, StatisticsValueTypes, number]> {
        return input.map(value => {
            return [value[0], value[1], MetricImportanceData[config.userDefinedImportance[value[0]]].value];
        });
    }
}
