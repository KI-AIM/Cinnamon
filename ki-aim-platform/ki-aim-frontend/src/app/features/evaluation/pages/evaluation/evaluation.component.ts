import { Component, OnInit } from '@angular/core';
import { EvaluationService } from "../../services/evaluation.service";
import { TitleService } from "../../../../core/services/title-service.service";
import { ProcessStatus } from "../../../../core/enums/process-status";
import { Observable } from "rxjs";
import { StatisticsService } from "../../../../shared/services/statistics.service";
import {
    Statistics, UtilityData,
    UtilityMetricData2,
    UtilityMetricData3,
    UtilityStatisticsData
} from "../../../../shared/model/statistics";
import { ExecutionStep } from "../../../../shared/model/execution-step";

@Component({
    selector: 'app-evaluation',
    templateUrl: './evaluation.component.html',
    styleUrls: ['./evaluation.component.less'],
})
export class EvaluationComponent implements OnInit {
    protected readonly ProcessStatus = ProcessStatus;
    protected readonly UtilityData = UtilityData;
    protected readonly UtilityMetricData2 = UtilityMetricData2;
    protected readonly UtilityMetricData3 = UtilityMetricData3;

    protected stage$: Observable<ExecutionStep | null>;

    protected statistics$: Observable<Statistics | null>;
    protected risks$: Observable<any>;

    constructor(
        protected readonly evaluationService: EvaluationService,
        protected readonly statisticsService: StatisticsService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Evaluation");
    }

    ngOnInit() {
        this.risks$ = this.statisticsService.fetchRisks();
        this.stage$ = this.evaluationService.status$;
        this.statistics$ = this.statisticsService.fetchResult();
        this.evaluationService.fetchStatus();
    }

    protected getFirstElement(obj: UtilityData): Array<UtilityStatisticsData> {
        const keys = Object.keys(obj);
        if (keys.length > 0) {
            return obj[keys[0]];
        }
        return [];
    }
}
