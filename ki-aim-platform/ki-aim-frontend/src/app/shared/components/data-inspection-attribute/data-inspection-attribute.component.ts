import {
    Component,
    Input,
    TemplateRef,
} from '@angular/core';
import {MatDialog} from "@angular/material/dialog";
import { AttributeStatistics } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent {
    @Input() public attributeStatistics!: AttributeStatistics;

    protected graphType = 'histogram';

    constructor(
        private matDialog: MatDialog,
        protected statisticsService: StatisticsService,
    ) {
    }

    protected openDetailsDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            width: '80%',
        });
    }

    protected changeGraph(event: Event) {
        this.graphType = (event.target as HTMLInputElement).value;
    }
}
