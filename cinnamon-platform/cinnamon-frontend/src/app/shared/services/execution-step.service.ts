import { Injectable } from '@angular/core';
import { StateManagementService } from "@core/services/state-management.service";
import { ExecutionStep } from "../model/execution-step";
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../environments/environment";
import { ProcessStatus } from "../../core/enums/process-status";
import { catchError, map, Observable, of, tap } from "rxjs";
import { plainToInstance } from "class-transformer";
import { StatusService } from "./status.service";
import { getStepDefinition, Steps } from "../../core/enums/steps";
import { ErrorHandlingService } from "./error-handling.service";

@Injectable({
    providedIn: 'root'
})
export abstract class ExecutionStepService {
    private readonly baseUrl = environments.apiUrl + "/api/process";

    /**
     * Observer that periodically sends requests to fetch the status
     * @private
     */
    private readonly statusObserver$: Observable<any>;

    protected constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly http: HttpClient,
        private readonly stateManagementService: StateManagementService,
        private readonly statusService: StatusService,
    ) {
        // Create the status observer
        this.statusObserver$ = this.stateManagementService.pipelineInformation$.pipe(
            map(value => {
                for (const stage of value.stages) {
                    if (stage.stageName === this.getStageName()) {
                        return stage;
                    }
                }
                return null;
            }),
        );
    }

    public get status$(): Observable<ExecutionStep | null> {
        this.stateManagementService.initPipeline();
        return this.statusObserver$;
    }

    /**
     * Starts the anonymization and synthetization process.
     * @param job The job to start. If null, the first job will be started.
     * @protected
     */
    public start(job: string | null) {
        // Sets the step in case later steps have already been completed.
        this.statusService.updateNextStep(this.getStep()).subscribe();

        this.asyncStart(job).subscribe();
    }

    /**
     * Returns an observable for starting the process.
     * @param job The job to start. If null, the first job will be started.
     * @protected
     */
    public asyncStart(job: string | null): Observable<ExecutionStep | null> {
        let url = this.baseUrl + "/" + this.getStageName() + "/start";
        if (job != null) {
            url += "/" + job;
        }

        return this.http.post<ExecutionStep>(url, {}).pipe(
            map(value => plainToInstance(ExecutionStep, value)),
            tap(value => {
                    // For some reason value is a plain object here
                    this.update(value);
                    this.stateManagementService.startListenToPipeline();
            }),
            catchError(() => {
                return this.fetchStatus();
            }),
        );
    }

    /**
     * Cancels the synthetization process.
     * @protected
     */
    public cancel() {
        this.http.post<ExecutionStep>(this.baseUrl + '/' + this.getStageName() + '/cancel', {}).subscribe({
            next: (executionStep: ExecutionStep) => {
                this.update(executionStep);
            },
            error: err => {
                this.errorHandlingService.addError(err, "Failed to cancel the process.");
            }
        });
    }

    /**
     * Determines the CSS class for the line based on the process status of a given stage.
     *
     * @param {ExecutionStep | null} stage - The current execution step, or null if no stage is present.
     * @param {number} jobIndex - The index of the job in the process list.
     * @param {'top' | 'bottom'} part - Specifies whether the class should be determined for the 'top' or 'bottom' part of the line.
     * @return {string} A string representing the CSS class: "current", "past", "error", or "future".
     */
    public getLineClass(stage: ExecutionStep | null, jobIndex: number, part: 'top'| 'bottom'): string {
        const index = part === 'top' ? jobIndex : jobIndex + 1;

        let status: ProcessStatus;
        if (stage === null) {
            status = ProcessStatus.NOT_STARTED;
        } else {
            if (index < stage.processes.length) {
                status = stage.processes[index].externalProcessStatus;
            } else {
                if (stage.status === ProcessStatus.FINISHED) {
                    status = ProcessStatus.FINISHED;
                } else {
                    status = ProcessStatus.NOT_STARTED;
                }
            }
        }

        if (status === ProcessStatus.RUNNING || status === ProcessStatus.SCHEDULED) {
            return "current";
        } else if (status === ProcessStatus.FINISHED || status === ProcessStatus.SKIPPED) {
            return "past";
        } else if (status === ProcessStatus.CANCELED || status === ProcessStatus.ERROR) {
            return "error";
        } else {
            return "future";
        }
    }


    /**
     * Name of the step. Must be equal to the name of the step in the backend.
     */
    protected abstract getStageName2(): string;

    protected getStageName(): string {
        return getStepDefinition(this.getStep())!.stageName!;
    }
    /**
     * Corresponding step of the execution page.
     * @protected
     */
    protected abstract getStep(): Steps;

    /**
     * Starts or stops listening to the status based on the given status.
     * @param status
     * @private
     */
    private setState(status: ProcessStatus): void {
        if (status === ProcessStatus.SCHEDULED || status === ProcessStatus.RUNNING) {
        } else {
        }
    }

    /**
     * Fetches the stage definition.
     */
    public fetchStageDefinition$(): Observable<StageDefinition> {
        return this.http.get<StageDefinition>(environments.apiUrl + '/api/step/stage/' + this.getStageName());
    }

    /**
     * Fetches the status from the server and updates the UI.
     * @private
     */
    public fetchStatus(): Observable<ExecutionStep | null> {
        return this.getProcess().pipe(
            tap(value => {
                this.update(value);
            }),
            catchError((error) => {
                this.errorHandlingService.addError(error, "Failed to update status.");
                return of(null);
            }),
        );
    }

    /**
     *
     * Creates an observable that fetches the status.
     * @private
     */
    private getProcess(): Observable<ExecutionStep> {
        return this.http.get<ExecutionStep>(this.baseUrl + '/' + this.getStageName()).pipe(
            // For some reason value is a plain object here
            map(value => {
                return plainToInstance(ExecutionStep, value);
            }),
        );
    }

    private update(executionStep: ExecutionStep) {
        this.stateManagementService.updateStage(executionStep);
    }
}

export class StageDefinition {
    jobs: string[];
    stageName: string;
}
