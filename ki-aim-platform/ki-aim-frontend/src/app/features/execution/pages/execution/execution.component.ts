import { Component, OnDestroy, OnInit } from '@angular/core';
import { ProcessStatus } from "../../../../core/enums/process-status";
import { environments } from "../../../../../environments/environment";
import { HttpClient } from "@angular/common/http";
import { ExecutionStep } from "../../../../shared/model/execution-step";
import { TitleService } from "../../../../core/services/title-service.service";
import { Steps } from "../../../../core/enums/steps";
import { Router } from "@angular/router";
import { ExecutionService } from "../../services/execution.service";
import { StatusService } from "../../../../shared/services/status.service";
import {Observable} from "rxjs";
import { StatisticsService } from "../../../../shared/services/statistics.service";
import { StageDefinition } from "../../../../shared/services/execution-step.service";

@Component({
  selector: 'app-execution',
  templateUrl: './execution.component.html',
  styleUrls: ['./execution.component.less'],
})
export class ExecutionComponent implements OnInit, OnDestroy {
    protected readonly ProcessStatus = ProcessStatus;
    protected stage$: Observable<ExecutionStep | null>;
    protected stageDefinition$: Observable<StageDefinition>;


    protected expanded: Array<Array<boolean>>;

    constructor(
        protected readonly executionService: ExecutionService,
        private readonly http: HttpClient,
        private readonly router: Router,
        private readonly statisticsService: StatisticsService,
        private readonly statusService: StatusService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Execution");
        this.expanded = [
            [
                false, false, false
            ],
            [
                false, false, false
            ],
        ]
    }

    ngOnDestroy() {
        this.executionService.stopListenToStatus();
    }

    ngOnInit() {
        this.stage$ = this.executionService.status$;
        this.stageDefinition$ = this.executionService.fetchStageDefinition$();
    }

    protected get status(): ExecutionStep {
        return this.executionService.status;
    }

    protected continue() {
        this.http.post(environments.apiUrl + "/api/process/confirm", {}).subscribe({
            next: () => {
                this.router.navigateByUrl("/technicalEvaluationConfiguration");
                this.statusService.setNextStep(Steps.TECHNICAL_EVALUATION);
            },
            error: (e) =>{
                console.error(e);
            }
        });
    }

    protected isObject(value: string | object) {
        return typeof value === 'object';
    }

    protected castObject(value: any): any {
        return value as any;
    }

    protected formatTime(step: any): string {
        return this.statisticsService.formatNumber((step?.completed === "True") ? step?.duration : step?.remaining_time, {
            maximumFractionDigits: 0,
            unit: "s"
        });
    }

    protected getJobName(job: string): string {
        const jobNames: Record<string, string> = {
            'anonymization': 'Anonymization',
            'synthetization': 'Synthetization',
        };

        return jobNames[job];
    }
}
