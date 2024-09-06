import { Component, OnDestroy, OnInit } from '@angular/core';
import { ProcessStatus } from "../../../../core/enums/process-status";
import { environments } from "../../../../../environments/environment";
import { interval, Observable, Subscription, tap } from "rxjs";
import { SynthetizationProcess } from "../../../../shared/model/synthetization-process";
import { HttpClient } from "@angular/common/http";
import { ExecutionStep } from "../../../../shared/model/execution-step";
import { ExternalProcess } from "../../../../shared/model/external-process";
import { TitleService } from "../../../../core/services/title-service.service";

@Component({
  selector: 'app-execution',
  templateUrl: './execution.component.html',
  styleUrls: ['./execution.component.less']
})
export class ExecutionComponent implements OnInit, OnDestroy {
    private readonly baseUrl = environments.apiUrl + "/api/process";

    protected disabled: boolean = false;
    protected status: ExecutionStep = new ExecutionStep();

    // TODO implement for anonymization
    protected synthProcess: SynthetizationProcess | null = null;

    protected error: string | null = null;

    /**
     * Observer that periodically sends requests to fetch the status
     * @private
     */
    private statusObserver: Observable<any>;
    /**
     * Subscription of the {@link #statusObserver}.
     * @private
     */
    private statusSubscription: Subscription | null = null;

    constructor(
        private readonly http: HttpClient,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Execution");

        this.status.status = ProcessStatus.NOT_STARTED;
        this.status.processes = {};

        // Create the initial status object so something can be displayed
        const anonProcess = new ExternalProcess();
        anonProcess.externalProcessStatus = ProcessStatus.NOT_STARTED;
        this.status.processes['ANONYMIZATION'] = anonProcess;

        const synthProcess = new ExternalProcess();
        synthProcess.externalProcessStatus = ProcessStatus.NOT_STARTED;
        this.status.processes['SYNTHETIZATION'] = synthProcess;

        // Create the status observer
        this.statusObserver = interval(10000).pipe(tap(() => {
            this.fetchStatus();
        }));
    }

    ngOnDestroy() {
        this.stopListenToStatus();
    }

    ngOnInit() {
        this.fetchStatus();
    }

    /**
     * Fetches the status from the server and updates the UI.
     * @private
     */
    private fetchStatus(): void {
        this.getProcess().subscribe({
            next: process => {
                this.error = null;
                this.status = process;
                this.setState(process.status);
                const synth = process.processes["SYNTHETIZATION"].status;
                this.synthProcess = synth === null ? null : JSON.parse(synth);
            },
            error: err => {
                this.error = `Failed to update status. Status: ${err.status} (${err.statusText})`;
            }
        });
    }

    /**
     * Starts the observer by subscribing.
     * Does nothing if a subscriber already exists.
     * @private
     */
    private startListenToStatus(): void {
        if (this.statusSubscription === null) {
            this.statusSubscription = this.statusObserver.subscribe();
        }
    }

    /**
     * Stops listening to the status by unsubscribing.
     * @private
     */
    private stopListenToStatus(): void {
        if (this.statusSubscription !== null) {
            this.statusSubscription.unsubscribe();
            this.statusSubscription = null;
        }
    }

    /**
     * Starts or stops listening to the status based on the given status.
     * @param status
     * @private
     */
    private setState(status: ProcessStatus): void {
        if (status === ProcessStatus.SCHEDULED || status === ProcessStatus.RUNNING) {
            this.startListenToStatus();
            this.disabled = true;
        } else {
            this.stopListenToStatus();
            this.disabled = false;
        }
    }

    /**
     * Creates an observable that fetches the status.
     * @private
     */
    private getProcess(): Observable<ExecutionStep> {
        return this.http.get<ExecutionStep>(this.baseUrl);
    }

    /**
     * Starts the anonymization and synthetization process.
     * @protected
     */
    protected onSubmit() {
        this.http.post<ExecutionStep>(this.baseUrl + "/start", {}).subscribe({
            next: value => {
                this.error = null;
                this.status = value;
                this.setState(value.status);
            },
            error: err => {
                this.error = `Failed to start the process. Status: ${err.status} (${err.statusText})`;
            }
        });
    }

    /**
     * Cancels the synthetization process.
     * @protected
     */
    protected cancel() {
        this.http.post<ProcessStatus>(this.baseUrl + '/cancel', {}).subscribe({
            next: (status: ProcessStatus) => {
                this.setState(status);
            },
            error: err => {
                this.error = `Failed to cancel the process. Status: ${err.status} (${err.statusText})`;
            }
        });
    }

    /**
     * Downloads all files related to the project in a ZIP file.
     * @protected
     */
    protected downloadResult() {
        this.http.get(environments.apiUrl + "/api/project/zip", {responseType: 'arraybuffer'}).subscribe({
            next: data => {
                const blob = new Blob([data], {
                    type: 'application/zip'
                });
                const url = window.URL.createObjectURL(blob);
                window.open(url);
            }
        });
    }
}
