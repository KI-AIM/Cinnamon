import { Component, Input } from '@angular/core';
import { ColumnConfiguration } from "../../model/column-configuration";

@Component({
    selector: 'app-data-inspection-attribute-details',
    templateUrl: './data-inspection-attribute-details.component.html',
    styleUrls: ['./data-inspection-attribute-details.component.less']
})
export class DataInspectionAttributeDetailsComponent {

    @Input() public configuration!: ColumnConfiguration;

    protected metrics: number[] = [1, 2, 3, 4, 5 , 6];
    protected graphs: number[] = [1, 2, 3, 4, 5, 6];
}
