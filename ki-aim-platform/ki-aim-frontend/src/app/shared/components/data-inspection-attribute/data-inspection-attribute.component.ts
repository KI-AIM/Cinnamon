import {
    Component,
    Input,
    TemplateRef,
} from '@angular/core';
import {MatDialog} from "@angular/material/dialog";
import { AttributeStatistics } from "../../model/statistics";
import {DataType} from "../../model/data-type";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent {
    @Input() public attributeStatistics!: AttributeStatistics;

    constructor(
        private matDialog: MatDialog,
    ) {
    }

    protected openDetailsDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            width: '60%'
        });
    }

    protected formatDate(value: number, type: DataType) {
        if (type === 'DATE') {
            return new Date(value).toLocaleDateString();
        } else {
            return new Date(value).toLocaleString();
        }
    }
}
