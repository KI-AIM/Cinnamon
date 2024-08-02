import { Component, OnDestroy, OnInit, QueryList, ViewChild } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";
import { Algorithm } from "../../model/algorithm";
import { AlgorithmService } from "../../services/algorithm.service";
import { stringify } from "yaml";
import { ConfigurationFormComponent } from "../configuration-form/configuration-form.component";
import { environments } from "../../../../environments/environment";
import { Status } from "../../model/status";
import { StateManagementService } from "../../../core/services/state-management.service";
import { ProcessStatus } from "../../../core/enums/process-status";
import { interval, Observable, Subscription, tap } from "rxjs";

@Component({
    selector: 'app-configuration-page',
    templateUrl: './configuration-page.component.html',
    styleUrls: ['./configuration-page.component.less']
})
export class ConfigurationPageComponent implements OnInit, OnDestroy {

    protected algorithms: Algorithm[] = [];
    protected disabled: boolean = false;
    protected processStatus: ProcessStatus = ProcessStatus.NOT_STARTED;

    @ViewChild('selection') private selection: ConfigurationSelectionComponent;
    @ViewChild('form') private forms: QueryList<ConfigurationFormComponent>;

    protected error: string | null = null;

    private statusObserver: Observable<any>;
    private statusSubscription: Subscription;

    constructor(
        private readonly http: HttpClient,
        private readonly anonService: AlgorithmService,
        private readonly stateService: StateManagementService,
    ) {

        anonService._setConfig = this.setConfig;
        anonService._getConfig = () => {
            console.log(this.forms);
            return '';
            // return this.createConfiguration(this.form.getRawValue(), this.selection.selectedOption);
        }

        this.statusObserver = interval(10000).pipe(tap(() => {
            this.stateService.getStatus(true, (error) => {
                this.error = `Failed to update status. Status: ${error.status} (${error.statusText})`;
            }).subscribe({
                next: value => {
                    this.error = null;
                    if (value.externalProcessStatus !== ProcessStatus.SCHEDULED &&
                        value.externalProcessStatus !== ProcessStatus.RUNNING) {
                        this.disabled = false;
                        this.processStatus = value.externalProcessStatus;
                        this.stopListenToStatus();
                    }
                },
                error: err => {
                    this.error = `Failed to update status. Status: ${err.status} (${err.statusText})`;
                },
            });
        }));
    }

    ngOnDestroy() {
        this.stopListenToStatus();
    }

    ngOnInit() {
        this.stateService.fetchStatus().subscribe({
            next: value => {
                this.setState(value);
            },
        });

        this.anonService.algorithms.subscribe({
            next: value => {
                this.error = null;
                this.algorithms = value;
            }, error: error => {
                console.log(error);
                this.error = `Failed to load available algorithms. Status: ${error.status} (${error.statusText})`;
            }
        });
    }

    private startListenToStatus(): void {
       this.statusSubscription = this.statusObserver.subscribe();
    }

    private stopListenToStatus(): void {
        this.statusSubscription.unsubscribe();
    }

    private setState(status: Status): void {
        this.processStatus = status.externalProcessStatus;
        if (status.externalProcessStatus === ProcessStatus.SCHEDULED || status.externalProcessStatus === ProcessStatus.RUNNING) {
            this.startListenToStatus();
            this.disabled = true;
        }
    }

    onSubmit(configuration: Object) {
        const formData = new FormData();

        this.anonService.getAlgorithmDefinition(this.selection.selectedOption).subscribe({
            next: value => {

                formData.append("stepName", this.anonService.getStepName());
                formData.append("algorithm", this.selection.selectedOption.synthesizer);
                formData.append("configurationName", this.anonService.getConfigurationName());
                formData.append("configuration", stringify(this.createConfiguration(configuration, this.selection.selectedOption)));
                formData.append("url", value.URL);

                this.http.post<Status>(environments.apiUrl + '/api/process/start', formData).subscribe({
                    next: (status: Status) => {
                        this.setState(status);
                    },
                    error: err => {
                        this.error = `Failed to start the process. Status: ${err.status} (${err.statusText})`;
                    }
                });
            }
        });
    }

    protected cancel() {
        this.http.post<Status>(environments.apiUrl + '/api/process/cancel', {}).subscribe({
            next: (status: Status) => {
                this.setState(status);
            },
            error: err => {
                this.error = `Failed to cancel the process. Status: ${err.status} (${err.statusText})`;
            }
        });
    }

    private createConfiguration(arg: Object, selectedAlgorithm: Algorithm) {
        // TODO remove hardcoded value for missing argument
        //@ts-ignore
        (arg["model_parameter"] as Object)["generator_decay"] = 1e-6;

        return {
            synthetization_process: [
                {
                    algorithm: null,
                    synthesizer: selectedAlgorithm.name,
                    type: selectedAlgorithm.type,
                    version: selectedAlgorithm.version,
                    ...arg
                },
            ],
        };
    }
}
