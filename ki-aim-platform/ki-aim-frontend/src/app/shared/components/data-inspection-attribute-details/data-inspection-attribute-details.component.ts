import {Component, Input, OnInit, TemplateRef} from '@angular/core';
import {
    AttributeStatistics,
    StatisticsData,
    StatisticsValues,
    StatisticsValuesNominal, StatisticsValueTypes
} from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { DataType } from "../../model/data-type";
import { SortDirection, SortType } from "../../pipes/metric-sorter.pipe";
import {MatDialog} from "@angular/material/dialog";
import {Steps} from "../../../core/enums/steps";
import {processEnumValue} from "../../helper/enum-helper";
import {MetricImportance, MetricImportanceData, MetricSettings} from "../../model/project-settings";
import {ProjectConfigurationService} from "../../services/project-configuration.service";
import {map, Observable} from "rxjs";

@Component({
    selector: 'app-data-inspection-attribute-details',
    templateUrl: './data-inspection-attribute-details.component.html',
    styleUrls: ['./data-inspection-attribute-details.component.less']
})
export class DataInspectionAttributeDetailsComponent implements OnInit {
    protected readonly DataType = DataType;
    protected readonly Object = Object;

    @Input() public attributeStatistics!: AttributeStatistics;
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';
    @Input() public processingSteps: Steps[] = [];

    protected graphType: string = 'histogram';
    protected hasSynthetic: boolean = false;

    protected metricFilterText: string;
    protected metricSortDirection: SortDirection | null = null;
    protected metricSortColumn: SortType | null = null;

    protected metricInfo: StatisticsValues | StatisticsValuesNominal<any>;

    protected datasetDisplayName: string;

    protected metricConfig$: Observable<MetricSettings>;

    constructor(
        private dialog: MatDialog,
        protected readonly projectConfigService: ProjectConfigurationService,
        protected statisticsService: StatisticsService,
    ) {
    }

    ngOnInit(): void {
        this.hasSynthetic = this.mainData == 'synthetic';
        this.datasetDisplayName = this.getSyntheticName();
        this.metricConfig$ = this.projectConfigService.projectSettings$.pipe(
            map(val => val.metricConfiguration)
        );
    }

    protected sort(type: SortType): void {
        if (this.metricSortColumn === type) {
            if (this.metricSortDirection === 'asc') {
                this.metricSortDirection = 'desc';
            } else if (this.metricSortDirection === 'desc') {
                this.metricSortColumn = null;
                this.metricSortDirection = null;
            }
        } else {
            this.metricSortColumn = type;
            this.metricSortDirection = 'asc';
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

    /**
     * Creates the name of the dataset based on the steps applied to this dataset.
     * @protected
     */
    private getSyntheticName(): string {
        const names = ['','','','','','','','',''];
        names[Steps.ANONYMIZATION] = "Anonymized";
        names[Steps.SYNTHETIZATION] = "Synthesized";

        let syntheticName = this.processingSteps
            .map(value => processEnumValue(Steps, value))
            .map(value => names[value])
            .join(" and ");
        syntheticName = syntheticName + " Values"
        return syntheticName;
    }

    /**
     * Opens the info popup.
     * @param templateRef The reference to the popup.
     * @param statistics The statistics data.
     */
    openDialog(templateRef: TemplateRef<any>, statistics: StatisticsValueTypes) {
        this.metricInfo = statistics;

        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

    protected readonly MetricImportance = MetricImportance;
}
