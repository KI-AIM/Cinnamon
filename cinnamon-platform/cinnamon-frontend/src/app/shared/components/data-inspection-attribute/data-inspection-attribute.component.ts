import {
    Component,
    Input, OnInit,
    TemplateRef,
} from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { AttributeStatistics } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { DataType } from "../../model/data-type";
import { Observable } from "rxjs";
import { MetricImportance, ProjectSettings } from "../../model/project-settings";
import { ProjectConfigurationService } from "../../services/project-configuration.service";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent implements OnInit {
    @Input() public attributeStatistics!: AttributeStatistics;
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';
    @Input() public processingSteps: string[] = [];

    protected originalDisplayName: string;
    protected syntheticDisplayName: string;
    protected hasSynthetic: boolean = false;

    protected graphType = 'histogram';

    protected metricConfig$: Observable<ProjectSettings>;

    constructor(
        private matDialog: MatDialog,
        protected readonly projectConfigService: ProjectConfigurationService,
        protected statisticsService: StatisticsService,
    ) {
    }

    ngOnInit() {
        this.hasSynthetic = this.mainData == 'synthetic';
        this.originalDisplayName = this.statisticsService.getOriginalName(this.sourceDataset);
        this.syntheticDisplayName = this.statisticsService.getSyntheticName(this.processingSteps);

        this.metricConfig$ = this.projectConfigService.projectSettings$;
    }

    protected openDetailsDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            width: '80%',
        });
    }

    protected readonly DataType = DataType;
    protected readonly MetricImportance = MetricImportance;
}
