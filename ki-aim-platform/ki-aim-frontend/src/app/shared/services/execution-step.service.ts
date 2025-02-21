import {Injectable } from '@angular/core';
import { ExecutionStep } from "../model/execution-step";
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../environments/environment";
import { ProcessStatus } from "../../core/enums/process-status";
import {
    BehaviorSubject,
    catchError,
    interval, map,
    Observable,
    of,
    Subscription,
    switchMap,
    tap
} from "rxjs";
import { plainToInstance } from "class-transformer";

@Injectable({
    providedIn: 'root'
})
export abstract class ExecutionStepService {
    private readonly baseUrl = environments.apiUrl + "/api/process";

    private _disabled: boolean = false;
    private _error: string | null = null;
    private _status: ExecutionStep = new ExecutionStep();

    private _statusSubject: BehaviorSubject<ExecutionStep | null>;

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
        this._status.processes = [];

        // Create the status observer
        this.statusObserver$ = interval(2000).pipe(
            switchMap(() => {
                return this.getProcess();
            }),
            tap(value => {
                this.update(value)
            }),
        );
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

    public get status$(): Observable<ExecutionStep | null> {
        this.initializeProjectSettings();
        return this._statusSubject!.asObservable();
    }

    /**
     * Starts the anonymization and synthetization process.
     * @protected
     */
    public start() {
        this.http.post<ExecutionStep>(this.baseUrl + "/" + this.getStepName() + "/start", {}).subscribe({
            next: value => {
                // For some reason value is a plain object here
                this.update(plainToInstance(ExecutionStep, value));
            },
            error: () => {
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
                this.update(executionStep);
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

    protected abstract setCustomStatus(key: string, status: string | null, processSteps: string[]): void;

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
    public fetchStatus(): Observable<ExecutionStep | null> {
        return this.getProcess().pipe(
            tap(value => {
                this.update(value);
            }),
            catchError((error) => {
                this._error = `Failed to update status. Status: ${error.status} (${error.statusText})`;
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
        return this.http.get<ExecutionStep>(this.baseUrl + '/' + this.getStepName()).pipe(
            // For some reason value is a plain object here
            map(value => {
                return plainToInstance(ExecutionStep, value);
            }),
        );
    }

    private initializeProjectSettings(): void {
        if (!this._statusSubject) {
            this._statusSubject = new BehaviorSubject<ExecutionStep | null>(null)
            this.fetchStatus().subscribe({
                    next: value => {
                        this._statusSubject!.next(value);
                    }
                }
            );
        }
    }

    private update(executionStep: ExecutionStep) {
        this._error = null;
        this._status = executionStep;
        this.setState(executionStep.status);
        for (const status of executionStep.processes) {
            this.setCustomStatus(status.step, status.status, status.processSteps);
        }
        this._statusSubject.next(executionStep);
    }
}
