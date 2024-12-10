import {Component, Input, OnInit, TemplateRef, ViewChild} from '@angular/core';
import {
    AttributeStatistics,
    StatisticsData,
    StatisticsValues,
    StatisticsValuesNominal
} from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { DataType } from "../../model/data-type";

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
    @ViewChild('table', {static: true}) tableTemplate!: TemplateRef<TableContext>;

    protected graphType: string = 'histogram';
    protected hasSynthetic: boolean = false;

    constructor(
        protected statisticsService: StatisticsService,
    ) {
    }

    ngOnInit(): void {

        this.hasSynthetic = this.attributeStatistics.important_metrics.mean?.values.synthetic != null
            || this.attributeStatistics.important_metrics.mode?.values.synthetic != null;
    }

    protected get dataType(): DataType {
        return this.attributeStatistics.attribute_information.type;
    }

    protected getImportantMetrics() {
        return Object.entries(this.attributeStatistics.important_metrics);
    }

    protected isSimple(value: any): boolean {
        return typeof value === "number" || typeof value === "string";
    }

    protected getColorIndex(data: StatisticsValues | StatisticsValuesNominal<any>): number {
        if (data instanceof StatisticsValues) {
            return data.difference.color_index;
        } else {
            return Math.max(data.values.real.color_index, data.values.synthetic.color_index);
        }
    }

    protected getDifference(data: StatisticsValues | StatisticsValuesNominal<any>, which: 'absolute' | 'percentage'): number | string  {
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

    protected isComplex(value: any): boolean {
        return Object.keys(value).length > 2;
    }

    protected castValue(value: any): number | string {
        return value as number | string;
    }

    protected readonly DataType = DataType;
}

interface TableContext {
    data: StatisticsValues | StatisticsValuesNominal<any>;
}
