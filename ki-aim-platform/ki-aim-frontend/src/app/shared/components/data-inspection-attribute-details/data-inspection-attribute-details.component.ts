import {Component, Input, OnInit, TemplateRef, ViewChild} from '@angular/core';
import {
    AttributeStatistics, HellingerDistanceData,
    KolmogorovSmirnovData,
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

    protected ksIndex(data: StatisticsData<KolmogorovSmirnovData>): number {
        return Math.max(data.real.color_index, data.synthetic.color_index);
    }

    protected hdIndex(data: StatisticsData<HellingerDistanceData>): number {
        return Math.max(data.real.color_index, data.synthetic.color_index);
    }

    protected getImportantMetrics() {
        return Object.entries(this.attributeStatistics.important_metrics);
    }

    protected getDetailsEntries(): [string, StatisticsValues | StatisticsValuesNominal<KolmogorovSmirnovData> | StatisticsValuesNominal<HellingerDistanceData>][] {
        console.log(Object.entries(this.attributeStatistics.details));
        return Object.entries(this.attributeStatistics.details);
    }

    protected isSimple(value: any): boolean {
        return typeof value === "number" || typeof value === "string";
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
    data: StatisticsValues | StatisticsValuesNominal<KolmogorovSmirnovData> | StatisticsValuesNominal<HellingerDistanceData>;
    name: string;

}
