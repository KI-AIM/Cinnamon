import { Component, Input } from '@angular/core';
import { AttributeStatistics } from "../../model/statistics";

@Component({
    selector: 'app-data-inspection-attribute-details',
    templateUrl: './data-inspection-attribute-details.component.html',
    styleUrls: ['./data-inspection-attribute-details.component.less']
})
export class DataInspectionAttributeDetailsComponent {
    @Input() public attributeStatistics!: AttributeStatistics;
}
