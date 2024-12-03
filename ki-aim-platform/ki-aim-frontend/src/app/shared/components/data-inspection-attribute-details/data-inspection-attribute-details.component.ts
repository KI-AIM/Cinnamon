import { Component, Input, TemplateRef, ViewChild } from '@angular/core';
import {
    AttributeStatistics, HellingerDistanceData,
    KolmogorovSmirnovData,
    StatisticsData,
    StatisticsValues,
    StatisticsValuesNominal
} from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { DataType } from "../../model/data-type";
import { formatNumber } from "@angular/common";

@Component({
    selector: 'app-data-inspection-attribute-details',
    templateUrl: './data-inspection-attribute-details.component.html',
    styleUrls: ['./data-inspection-attribute-details.component.less']
})
export class DataInspectionAttributeDetailsComponent {
    protected readonly Object = Object;

    @Input() public attributeStatistics!: AttributeStatistics;
    @ViewChild('table', {static: true}) tableTemplate!: TemplateRef<TableContext>;

    protected graphType: string = 'histogram';

    constructor(
        protected statisticsService: StatisticsService,
    ) {
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

    protected isNumber(value: any): boolean {
        return typeof value === "number";
    }

    protected isComplex(value: any): boolean {
        return Object.keys(value).length > 2;
    }

    protected toNumber(value: any): number {
        return parseFloat(value);
    }

    protected readonly DataType = DataType;
}

interface TableContext {
    data: StatisticsValues | StatisticsValuesNominal<KolmogorovSmirnovData> | StatisticsValuesNominal<HellingerDistanceData>;
    name: string;

}
