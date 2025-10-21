import { Component, OnInit, QueryList, ViewChildren } from '@angular/core';
import { MatExpansionPanel } from "@angular/material/expansion";
import { Router } from "@angular/router";
import { ProcessStatus } from "@core/enums/process-status";
import { StepConfiguration, Steps } from "@core/enums/steps";
import { StateManagementService } from "@core/services/state-management.service";
import { TitleService } from "@core/services/title-service.service";
import { ExecutionStep } from "@shared/model/execution-step";
import { RiskEvaluation } from '@shared/model/risk-evaluation';
import { StatisticsResponse } from "@shared/model/statistics";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { StageDefinition } from "@shared/services/execution-step.service";
import { StatisticsService } from "@shared/services/statistics.service";
import { StatusService } from "@shared/services/status.service";
import { combineLatest, map, Observable, tap } from "rxjs";
import { EvaluationService } from "../../services/evaluation.service";

@Component({
    selector: 'app-evaluation',
    templateUrl: './evaluation.component.html',
    styleUrls: ['./evaluation.component.less'],
    standalone: false
})
export class EvaluationComponent implements OnInit {
    protected readonly ProcessStatus = ProcessStatus;

    protected statistics$: Observable<StatisticsResponse>;

    protected risks$: Observable<RiskEvaluation>;
    protected risks2$: Observable<any>;

    protected pageData$: Observable<{
        locked: boolean,
        stage: ExecutionStep | null,
        stageDefinition: StageDefinition,
    }>

    @ViewChildren('statusPanel') private statusPanels: QueryList<MatExpansionPanel>;

    private currentJob: number | null = null;
    private currentStatus: ProcessStatus | null = null;

    constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        protected readonly evaluationService: EvaluationService,
        private readonly router: Router,
        private readonly stateManagementService: StateManagementService,
        protected readonly statisticsService: StatisticsService,
        private readonly statusService: StatusService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Evaluation");
    }

    ngOnInit() {
        this.pageData$ = combineLatest({
            locked: this.stateManagementService.currentStepLocked$.pipe(
                map(value => value.isLocked),
            ),
            stage: this.evaluationService.status$.pipe(
                tap(value => {
                    // Create an error notification
                    if (this.statusPanels && value && value.status === ProcessStatus.ERROR &&
                        this.currentStatus !== ProcessStatus.ERROR && this.currentStatus !== null) {

                        let jobIndex = null;
                        for (let i = 0; i < value.processes.length; i++) {
                            if (value.processes[i].externalProcessStatus === ProcessStatus.ERROR) {
                                jobIndex = i;
                                break;
                            }
                        }

                        if (jobIndex !== null) {
                            this.statusPanels.get(jobIndex)?.open();
                            const name = this.getJobName(value.processes[jobIndex].step);
                            this.errorHandlingService.addError(`The ${name} process failed. See the status panel for more information.`);
                        }
                    }
                }),
                tap(value => {
                    // Open the status panel when the next job starts
                    if (value && this.statusPanels) {
                        if (value.currentProcessIndex === null && value.status === ProcessStatus.FINISHED) {
                            this.statusPanels.forEach(panel => panel.close());
                        }

                        if (value.currentProcessIndex !== null && value.currentProcessIndex !== this.currentJob) {
                            this.statusPanels.forEach(panel => panel.close());
                            this.statusPanels.get(value.currentProcessIndex)?.open();
                        }

                        // Update the current status
                        this.currentJob = value.currentProcessIndex;
                        this.currentStatus = value.status;
                    }
                }),
            ),
            stageDefinition: this.evaluationService.fetchStageDefinition$(),
        });

        this.risks$ = this.statisticsService.fetchRisks();
        this.risks2$ = this.statisticsService.fetchRisks2();
        this.statistics$ = this.statisticsService.fetchResult();
        this.evaluationService.fetchStatus();
    }

    protected getJobName(name: string): string {
        return this.evaluationService.getJobName(name);
    }

    protected continue() {
        this.statusService.updateNextStep(Steps.REPORT).subscribe({
            next: (v) => {
                this.router.navigateByUrl(StepConfiguration.REPORT.path);
            },
            error: (e) =>{
                console.error(e);
            }
        });
    }
}
