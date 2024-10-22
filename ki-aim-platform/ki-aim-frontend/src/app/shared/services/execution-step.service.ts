import { Injectable } from '@angular/core';
import { ExecutionStep } from "../model/execution-step";
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../environments/environment";
import { ProcessStatus } from "../../core/enums/process-status";
import { interval, Observable, Subscription, tap } from "rxjs";
import { ExternalProcess } from "../model/external-process";

@Injectable({
    providedIn: 'root'
})
export abstract class ExecutionStepService {
    private readonly baseUrl = environments.apiUrl + "/api/process";

    private _disabled: boolean = false;
    private _error: string | null = null;
    private _status: ExecutionStep = new ExecutionStep();

    /**
     * Observer that periodically sends requests to fetch the status
     * @private
     */
    private statusObserver$: Observable<any>;
    /**
     * Subscription of the {@link #statusObserver$}.
     * @private
     */
    private statusSubscription: Subscription | null = null;

    protected constructor(
        private readonly http: HttpClient,
    ) {
        // Create the initial status object so something can be displayed
        this._status.status = ProcessStatus.NOT_STARTED;
        this._status.processes = {};
        for (const step of this.getSteps()) {
            const externalProcess = new ExternalProcess();
            externalProcess.externalProcessStatus = ProcessStatus.NOT_STARTED;
            this._status.processes[step] = externalProcess;
        }

        // Create the status observer
        this.statusObserver$ = interval(2000).pipe(tap(() => {
            this.fetchStatus();
        }));
    }

    public get disabled(): boolean {
        return this._disabled;
    }

    public get error(): string | null {
        return this._error;
    }

    public get status(): ExecutionStep {
        return this._status;
    }

    /**
     * Starts the anonymization and synthetization process.
     * @protected
     */
    public start() {
        this.http.post<ExecutionStep>(this.baseUrl + "/" + this.getStepName() + "/start", {}).subscribe({
            next: value => {
                this._error = null;
                this._status = value;
                this.setState(value.status);
                for (const [key, status] of Object.entries(value.processes)) {
                    this.setCustomStatus(key, status.status);
                }
            },
            error: err => {
                this.fetchStatus();
            }
        });
    }

    /**
     * Cancels the synthetization process.
     * @protected
     */
    public cancel() {
        this.http.post<ExecutionStep>(this.baseUrl + '/' + this.getStepName() + '/cancel', {}).subscribe({
            next: (executionStep: ExecutionStep) => {
                this._status = executionStep;
                this.setState(executionStep.status);
            },
            error: err => {
                this._error = `Failed to cancel the process. Status: ${err.status} (${err.statusText})`;
            }
        });
    }

    /**
     * Starts the observer by subscribing.
     * Does nothing if a subscriber already exists.
     * @private
     */
    public startListenToStatus(): void {
        if (this.statusSubscription === null) {
            this.statusSubscription = this.statusObserver$.subscribe();
        }
    }

    /**
     * Stops listening to the status by unsubscribing.
     * @private
     */
    public stopListenToStatus(): void {
        if (this.statusSubscription !== null) {
            this.statusSubscription.unsubscribe();
            this.statusSubscription = null;
        }
    }

    /**
     * Name of the step. Must be equal to the name of the step in the backend.
     */
    protected abstract getStepName(): string;

    protected abstract setCustomStatus(key: string, status: string | null): void;

    /**
     * TODO We could read the steps of this execution step from dynamically from the backend
     * @protected
     */
    protected abstract getSteps(): string[];

    /**
     * Starts or stops listening to the status based on the given status.
     * @param status
     * @private
     */
    private setState(status: ProcessStatus): void {
        if (status === ProcessStatus.SCHEDULED || status === ProcessStatus.RUNNING) {
            this.startListenToStatus();
            this._disabled = true;
        } else {
            this.stopListenToStatus();
            this._disabled = false;
        }
    }

    /**
     * Fetches the status from the server and updates the UI.
     * @private
     */
   public fetchStatus(): void {
        this.getProcess().subscribe({
            next: process => {
                this._error = null;
                this._status = process;
                this.setState(process.status);
                for (const [key, status] of Object.entries(process.processes)) {
                    this.setCustomStatus(key, status.status);
                }
            },
            error: err => {
                this._error = `Failed to update status. Status: ${err.status} (${err.statusText})`;
            }
        });
    }
    /**
     *
     * Creates an observable that fetches the status.
     * @private
     */
    private getProcess(): Observable<ExecutionStep> {
        return this.http.get<ExecutionStep>(this.baseUrl + '/' + this.getStepName());
    }
}
