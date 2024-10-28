import {Component, Input} from '@angular/core';
import {ColumnConfiguration} from "../../model/column-configuration";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent {
    @Input() public configuration!: ColumnConfiguration;
}
