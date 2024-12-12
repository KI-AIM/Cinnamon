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

@Component({
    selector: 'app-data-inspection-attribute-details',
    templateUrl: './data-inspection-attribute-details.component.html',
    styleUrls: ['./data-inspection-attribute-details.component.less']
})
export class DataInspectionAttributeDetailsComponent implements OnInit {
    protected readonly Object = Object;

    @Input() public attributeStatistics!: AttributeStatistics;
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';

    protected graphType: string = 'histogram';
    protected hasSynthetic: boolean = false;

    protected metricFilterText: string;
    protected metricSortDirection: SortDirection | null = null;
    protected metricSortColumn: SortType | null = null;

    protected metricInfo: StatisticsValues | StatisticsValuesNominal<any>;

    constructor(
        private dialog: MatDialog,
        protected statisticsService: StatisticsService,
    ) {
    }

    ngOnInit(): void {

        this.hasSynthetic = this.attributeStatistics.important_metrics.mean?.values.synthetic != null
            || this.attributeStatistics.important_metrics.mode?.values.synthetic != null;
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

    protected get dataType(): DataType {
        return this.attributeStatistics.attribute_information.type;
    }

    protected getImportantMetrics(): StatisticsValueTypes[] {
        return Object.values(this.attributeStatistics.important_metrics) as StatisticsValueTypes[];
    }

    protected getDetailMetrics() {
        return Object.values(this.attributeStatistics.details);
    }

    protected getAllMetrics() {
        return Object.values(this.attributeStatistics.important_metrics).concat(Object.values(this.attributeStatistics.details));
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
        const type = typeof data[which];

        if (type === "number" || type === "string") {
            return data[which];
        } else {
            return this.getComplexValue(data[which]);
        }
    }

    protected getComplexValue(complex: any): number | string {
        for (const [key, value] of Object.entries(complex)) {
            if (key !== 'color_index') {
                return value as number | string;
            }
        }

        return "N/A";
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

    protected readonly DataType = DataType;
}
