import { Component, Input, OnInit } from '@angular/core';
import { MetricTableData, MetricTableFilterData, MetricTableSortData, SortType } from "@shared/model/metric-table-data";
import { MetricImportanceData, MetricSettings, ProjectSettings } from "@shared/model/project-settings";
import { AttributeStatistics, StatisticsData, StatisticsValues, StatisticsValueTypes } from "@shared/model/statistics";
import { ProjectConfigurationService } from "@shared/services/project-configuration.service";
import { StatisticsService } from "@shared/services/statistics.service";
import { combineLatest, Observable } from "rxjs";

/**
 * Table showing metrics for a specific attribute.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
  selector: 'app-data-inspection-metric-table',
  standalone: false,
  templateUrl: './data-inspection-metric-table.component.html',
  styleUrl: './data-inspection-metric-table.component.less'
})
export class DataInspectionMetricTableComponent implements OnInit {
    /**
     * Resemblance statistics for the attribute.
     */
    @Input() public attributeStatistics!: AttributeStatistics;

    /**
     * Name of the protected dataset.
     */
    @Input() public datasetDisplayName!: string;

    /**
     * Which data should be shown if the {@link #tableType} is {@link MetricTableType#SINGLE}
     */
    @Input() public mainData: 'real' | 'synthetic' = 'real';

    /**
     * The context data of the search and filter.
     */
    @Input() public tableData!: MetricTableData;

    /**
     * Type of the table to be displayed.
     * See {@link MetricTableType}.
     */
    @Input() public tableType!: MetricTableType;

    protected pageData$: Observable<{
        projectSettings: ProjectSettings,
    }>;

    protected readonly MetricTableFilterData = MetricTableFilterData;
    protected readonly MetricTableSortData = MetricTableSortData;
    protected readonly MetricTableType = MetricTableType;

    public constructor(
        protected readonly projectConfigService: ProjectConfigurationService,
        protected readonly statisticsService: StatisticsService,
    ) {
    }

    public ngOnInit(): void {
        this.pageData$ = combineLatest({
            projectSettings: this.projectConfigService.projectSettings$,
        });
    }

    /**
     * Updates the sort context of the table based on the given type, i.e., the column that was clicked.
     *
     * @param sortData The sort context of the table.
     * @param type The type of the columns which has been clicked.
     * @protected
     */
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

    /**
     * Injects the metric importance as configured in the project settings to the given statistics.
     *
     * @param input The statistic for the table.
     * @param config The metric settings.
     * @protected
     */
    protected injectImportance(input: Array<[string, StatisticsValueTypes]>, config: MetricSettings): Array<[string, StatisticsValueTypes, number]> {
        return input.map(value => {
            return [value[0], value[1], MetricImportanceData[config.userDefinedImportance[value[0]]].value];
        });
    }

    /**
     * Gets the color index for the given metric.
     *
     * @param data The metric data.
     * @protected
     */
    protected getColorIndex(data: StatisticsValueTypes): number {
        if (data instanceof StatisticsValues) {
            return data.difference.color_index;
        } else {
            return Math.max(data.values.real.color_index, data.values.synthetic.color_index);
        }
    }

    /**
     * Gets the absolute or proportional difference of the give metric.
     *
     * @param data The metric data.
     * @param which The difference to be returned.
     * @protected
     */
    protected getDifference(data: StatisticsValueTypes, which: 'absolute' | 'percentage'): number | string  {
        if (data instanceof StatisticsValues) {
            return data.difference[which];
        } else {
            return "N/A";
        }
    }

    /**
     * Gets the value of the real or protected datasets of the given metric
     *
     * @param data The metric data.
     * @param which The value to be returned.
     * @protected
     */
    protected getValue(data: StatisticsData<any>, which : 'real' | 'synthetic'): number | string {
        return this.statisticsService.getValue(data, which);
    }
}

/**
 * Type of the table to be displayed.
 */
export enum MetricTableType {
    /**
     * Shows metrics for the original and protected data as well as the difference.
     */
    COMPARISON = "COMPARISON",
    /**
     * Shows only metrics for a specific dataset.
     */
    SINGLE = "SINGLE",
}
