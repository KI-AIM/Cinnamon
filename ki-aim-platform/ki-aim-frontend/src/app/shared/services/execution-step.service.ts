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

    private _error: string | null = null;

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

    public get error(): string | null {
        return this._error;
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
        this.asyncStart().subscribe();
    }

    /**
     * Returns an observable for starting the process.
     * @protected
     */
    public asyncStart(): Observable<ExecutionStep | null> {
        return this.http.post<ExecutionStep>(this.baseUrl + "/" + this.getStageName() + "/start", {}).pipe(
            tap(value => {
                    // For some reason value is a plain object here
                    this.update(plainToInstance(ExecutionStep, value));
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
    protected abstract getStageName(): string;

    /**
     * Starts or stops listening to the status based on the given status.
     * @param status
     * @private
     */
    private setState(status: ProcessStatus): void {
        if (status === ProcessStatus.SCHEDULED || status === ProcessStatus.RUNNING) {
            this.startListenToStatus();
        } else {
            this.stopListenToStatus();
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
        return this.http.get<ExecutionStep>(this.baseUrl + '/' + this.getStageName()).pipe(
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
        this.setState(executionStep.status);
        this._statusSubject.next(executionStep);
    }
}

export class StageDefinition {
    jobs: string[];
    stageName: string;
}
