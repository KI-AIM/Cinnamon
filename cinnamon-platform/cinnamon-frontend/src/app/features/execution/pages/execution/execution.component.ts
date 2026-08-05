import { Component, OnInit, QueryList, ViewChildren } from '@angular/core';
import { MatExpansionPanel } from "@angular/material/expansion";
import { StateManagementService } from "@core/services/state-management.service";
import { plainToInstance } from "class-transformer";
import { combineLatest, map, Observable, tap } from "rxjs";
import { ProcessStatus, StageStatus } from "../../../../core/enums/process-status";
import { Steps } from "../../../../core/enums/steps";
import { ExecutionStep } from "../../../../shared/model/execution-step";
import { ProcessProgress } from "../../../../shared/model/process-progress";
import { SynthetizationProcess } from "../../../../shared/model/synthetization-process";
import { SynthetizationComponentProgress } from "../../../../shared/model/synthetization-component-progress";
import { ErrorHandlingService } from "../../../../shared/services/error-handling.service";
import { StageDefinition } from "../../../../shared/services/execution-step.service";
import { StatisticsService } from "../../../../shared/services/statistics.service";
import { ExecutionService } from "../../services/execution.service";

@Component({
    selector: 'app-execution',
    templateUrl: './execution.component.html',
    styleUrls: ['./execution.component.less'],
    standalone: false
})
export class ExecutionComponent implements OnInit {
    protected readonly ProcessStatus = ProcessStatus;
    protected readonly StageStatus = StageStatus;

    protected pageData$: Observable<{
        locked: boolean,
        stage: ExecutionStep | null,
        stageDefinition: StageDefinition,
    }>

    @ViewChildren('statusPanel') private statusPanels: QueryList<MatExpansionPanel>;

    private currentJob: number | null = null;
    private currentStatus: StageStatus | null = null;

    constructor(
        private errorHandlingService: ErrorHandlingService,
        protected readonly executionService: ExecutionService,
        private readonly stateManagementService: StateManagementService,
        private readonly statisticsService: StatisticsService,
    ) {
    }

    ngOnInit() {
        // Update the pipeline because the status could have been update due to configuration changes
        this.stateManagementService.updatePipeline();
        this.pageData$ = combineLatest({
            locked: this.stateManagementService.currentStepLocked$.pipe(
                map(value => value.isLocked),
            ),
            stage: this.executionService.status$.pipe(
                tap(value => {
                    // Create an error notification
                    if (this.statusPanels && value && value.status === StageStatus.ERROR &&
                        this.currentStatus !== null) {

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
                        if (value.currentProcessIndex === null && value.status === StageStatus.FINISHED) {
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
            stageDefinition: this.executionService.fetchStageDefinition$(),
        });
    }

    protected continue() {
        this.stateManagementService.setAndRouteToStep(Steps.TECHNICAL_EVALUATION).subscribe();
    }

    protected getSynthetizationStatus(value: string): SynthetizationProcess | null {
        if (!value.startsWith('{')) {
            return null;
        }

        return plainToInstance(SynthetizationProcess, JSON.parse(value));
    }

    protected getTextAnonymizationStatus(status: ProcessStatus): string {
        switch (status) {
            case ProcessStatus.RUNNING:
                return "The text anonymization is running";
            case ProcessStatus.FINISHED:
                return "The text anonymization is finished";
            default:
                return status.replace("_", " ");
        }
    }

    protected formatStepDuration(step: ProcessProgress): string {
        return this.formatDurationValue(step?.duration);
    }

    protected formatRemainingTime(step: ProcessProgress): string {
        if (step.step === "Initialization" && !this.hasUsableValue(step?.remaining_time)) {
            return this.formatDurationValue("0");
        }
        return this.formatDurationValue(step?.remaining_time);
    }

    private formatDurationValue(value: string | null | undefined): string {
        return this.statisticsService.formatNumber(value, {
            maximumFractionDigits: 0,
            unit: "s"
        });
    }

    protected hasSynthetizationComponents(process: SynthetizationProcess | null | undefined): boolean {
        return !!(
            process?.components?.structured_synthesis ||
            process?.components?.llm_synthesis
        );
    }

    protected getSynthetizationRows(process: SynthetizationProcess | null | undefined): ProcessProgress[] {
        return [...(process?.status ?? [])];
    }

    protected getSynthetizationComponents(process: SynthetizationProcess | null | undefined): Array<{
        key: string,
        label: string,
        value: SynthetizationComponentProgress,
        rows: ProcessProgress[],
    }> {
        if (!process?.components) {
            return [];
        }

        const components: Array<{
            key: string,
            label: string,
            value: SynthetizationComponentProgress,
            rows: ProcessProgress[],
        }> = [];

        if (process.components.structured_synthesis && !this.isUnusedComponent(process.components.structured_synthesis)) {
            components.push({
                key: "structured_synthesis",
                label: "Structured Synthesis",
                value: process.components.structured_synthesis,
                rows: this.getComponentRows(process.components.structured_synthesis),
            });
        }

        if (process.components.llm_synthesis && !this.isUnusedComponent(process.components.llm_synthesis)) {
            components.push({
                key: "llm_synthesis",
                label: "LLM Synthesis",
                value: process.components.llm_synthesis,
                rows: this.getComponentRows(process.components.llm_synthesis),
            });
        }

        return components;
    }

    protected getTotalSynthesisDuration(process: SynthetizationProcess | null | undefined): string {
        const total = process?.components?.total_synthesis;
        if (!total || total.completed !== "True" || !this.hasUsableValue(total.duration)) {
            return "Waiting";
        }
        return this.formatDurationValue(total.duration);
    }

    protected hasSynthetizationTotal(process: SynthetizationProcess | null | undefined): boolean {
        return !!process?.components?.total_synthesis;
    }

    protected getComponentTitle(component: SynthetizationComponentProgress, label: string): string {
        return label;
    }

    private getComponentRows(component: SynthetizationComponentProgress): ProcessProgress[] {
        if (this.isUnusedComponent(component)) {
            return [
                this.buildProgressRow("Initialization", component.completed, "N/A"),
                this.buildProgressRow("Fitting", component.completed, "N/A"),
                this.buildProgressRow("Sampling", component.completed, "N/A", "N/A"),
            ];
        }

        return [
            this.buildProgressRow(
                "Initialization",
                this.getRowCompleted(component.initialization_duration),
                component.initialization_duration,
                "0",
            ),
            this.buildProgressRow(
                "Fitting",
                this.getRowCompleted(component.fitting_duration),
                component.fitting_duration,
                this.getComponentStepRemainingTime(component, "fitting"),
            ),
            this.buildProgressRow(
                "Sampling",
                this.getRowCompleted(component.sampling_duration),
                component.sampling_duration,
                this.getComponentStepRemainingTime(component, "sampling"),
            ),
        ];
    }

    private hasUsableValue(value: string | null | undefined): boolean {
        return value != null && value !== "" && value !== "Waiting" && value !== "N/A";
    }

    private getComponentStepRemainingTime(
        component: SynthetizationComponentProgress,
        step: "fitting" | "sampling",
    ): string | null | undefined {
        if (step === "fitting") {
            if (this.hasUsableValue(component.fitting_remaining_time)) {
                return component.fitting_remaining_time;
            }
            return this.hasUsableValue(component.fitting_duration) ? "0" : "Waiting";
        }

        if (this.hasUsableValue(component.sampling_remaining_time)) {
            return component.sampling_remaining_time;
        }
        if (this.hasUsableValue(component.remaining_time)) {
            return component.remaining_time;
        }
        return this.hasUsableValue(component.sampling_duration) ? "0" : "Waiting";
    }

    private isUnusedComponent(component: SynthetizationComponentProgress): boolean {
        return !this.hasUsableValue(component.synthesizer_name)
            && component.completed !== "True"
            && !this.hasUsableValue(component.duration)
            && !this.hasUsableValue(component.initialization_duration)
            && !this.hasUsableValue(component.fitting_duration)
            && !this.hasUsableValue(component.sampling_duration)
            && !this.hasUsableValue(component.remaining_time);
    }

    private getRowCompleted(value: string | null | undefined): string {
        return this.hasUsableValue(value) ? "True" : "False";
    }

    private buildProgressRow(
        step: string,
        completed: string,
        duration: string | null | undefined,
        remainingTime?: string | null | undefined,
    ): ProcessProgress {
        return {
            step,
            completed,
            duration: duration ?? null,
            remaining_time: remainingTime ?? null,
        };
    }

    protected getJobName(job: string): string {
        return this.executionService.getJobName(job);
    }
}
