import { Component, OnInit, QueryList, ViewChildren } from '@angular/core';
import { MatExpansionPanel } from "@angular/material/expansion";
import { Router } from "@angular/router";
import { plainToInstance } from "class-transformer";
import { Observable, tap } from "rxjs";
import { ProcessStatus } from "../../../../core/enums/process-status";
import { Steps } from "../../../../core/enums/steps";
import { TitleService } from "../../../../core/services/title-service.service";
import { ExecutionStep } from "../../../../shared/model/execution-step";
import { SynthetizationProcess } from "../../../../shared/model/synthetization-process";
import { ErrorHandlingService } from "../../../../shared/services/error-handling.service";
import { StageDefinition } from "../../../../shared/services/execution-step.service";
import { StatisticsService } from "../../../../shared/services/statistics.service";
import { StatusService } from "../../../../shared/services/status.service";
import { ExecutionService } from "../../services/execution.service";

@Component({
    selector: 'app-execution',
    templateUrl: './execution.component.html',
    styleUrls: ['./execution.component.less'],
    standalone: false
})
export class ExecutionComponent implements OnInit {
    protected readonly ProcessStatus = ProcessStatus;
    protected stage$: Observable<ExecutionStep | null>;
    protected stageDefinition$: Observable<StageDefinition>;

    @ViewChildren('statusPanel') private statusPanels: QueryList<MatExpansionPanel>;

    private currentJob: number | null = null;
    private currentStatus: ProcessStatus | null = null;

    constructor(
        private errorHandlingService: ErrorHandlingService,
        protected readonly executionService: ExecutionService,
        private readonly router: Router,
        private readonly statisticsService: StatisticsService,
        private readonly statusService: StatusService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Execution");
    }

    ngOnInit() {
        this.stage$ = this.executionService.status$.pipe(
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
        );

        this.stageDefinition$ = this.executionService.fetchStageDefinition$();
    }

    protected continue() {
        this.statusService.updateNextStep(Steps.TECHNICAL_EVALUATION).subscribe({
            next: () => {
                this.router.navigateByUrl("/technicalEvaluationConfiguration");
            },
            error: (e) =>{
                console.error(e);
            }
        });
    }

    protected getSynthetizationStatus(value: string): SynthetizationProcess | null {
        if (!value.startsWith('{')) {
            return null;
        }

        return plainToInstance(SynthetizationProcess, JSON.parse(value));
    }

    protected formatTime(step: any): string {
        return this.statisticsService.formatNumber((step?.completed === "True") ? step?.duration : step?.remaining_time, {
            maximumFractionDigits: 0,
            unit: "s"
        });
    }

    protected getJobName(job: string): string {
        return this.executionService.getJobName(job);
    }
}
