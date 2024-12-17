import {
    Component,
    Input,
    TemplateRef,
} from '@angular/core';
import {MatDialog} from "@angular/material/dialog";
import { AttributeStatistics } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { DataType } from "../../model/data-type";
import {Steps} from "../../../core/enums/steps";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent {
    @Input() public attributeStatistics!: AttributeStatistics;
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';
    @Input() public processingSteps: Steps[] = [];

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

    protected get dataType(): DataType {
        return this.attributeStatistics.attribute_information.type;
    }

    protected readonly DataType = DataType;
}
