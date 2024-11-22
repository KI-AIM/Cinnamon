import { Component, Input, TemplateRef, ViewChild } from '@angular/core';
import { AttributeStatistics, StatisticsValues } from "../../model/statistics";
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

    protected readonly colors = ['#000000', '#960d0d'];

    constructor(
        protected statisticsService: StatisticsService,
    ) {
    }

    protected getDataType(): DataType {
        return this.attributeStatistics.attribute_information.type;
    }

    protected readonly formatNumber = formatNumber;
}

interface TableContext {
    data: StatisticsValues;
    name: string;
}
