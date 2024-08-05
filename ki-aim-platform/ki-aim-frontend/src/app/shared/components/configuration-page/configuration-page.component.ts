import { Component, OnDestroy, OnInit, QueryList, ViewChild } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";
import { Algorithm } from "../../model/algorithm";
import { AlgorithmService } from "../../services/algorithm.service";
import { stringify } from "yaml";
import { ConfigurationFormComponent } from "../configuration-form/configuration-form.component";
import { environments } from "../../../../environments/environment";
import { StateManagementService } from "../../../core/services/state-management.service";
import { ProcessStatus } from "../../../core/enums/process-status";
import { interval, Observable, Subscription, tap } from "rxjs";

@Component({
    selector: 'app-configuration-page',
    templateUrl: './configuration-page.component.html',
    styleUrls: ['./configuration-page.component.less']
})
export class ConfigurationPageComponent implements OnInit, OnDestroy {
    private readonly baseUrl = environments.apiUrl + "/api/process";

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
        // anonService._setConfig = this.setConfig;
        // anonService._getConfig = () => {
        //     console.log(this.forms);
        //     return '';
        //     // return this.createConfiguration(this.form.getRawValue(), this.selection.selectedOption);
        // }

        this.statusObserver = interval(10000).pipe(tap(() => {
            this.getProcessStatus().subscribe({
                next: status => {
                    this.error = null;
                    if (status !== ProcessStatus.SCHEDULED && status !== ProcessStatus.RUNNING) {
                        this.disabled = false;
                        this.processStatus = status;
                        this.stopListenToStatus();
                    }
                },
                error: err => {
                    this.error = `Failed to update status. Status: ${err.status} (${err.statusText})`;
                }
            });
        }));
    }


    ngOnDestroy() {
        this.stopListenToStatus();
    }

    ngOnInit() {
        this.getProcessStatus().subscribe({
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
        if (this.statusSubscription) {
            this.statusSubscription.unsubscribe();
        }
    }

    private setState(status: ProcessStatus): void {
        this.processStatus = status;
        if (status === ProcessStatus.SCHEDULED || status === ProcessStatus.RUNNING) {
            this.startListenToStatus();
            this.disabled = true;
        }
    }

    private getProcessStatus(): Observable<ProcessStatus> {
        return this.http.get<ProcessStatus>(this.baseUrl + "/" + this.anonService.getStepName());
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

                this.http.post<ProcessStatus>(this.baseUrl + '/start', formData).subscribe({
                    next: (status: ProcessStatus) => {
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
        const formData = new FormData()
        formData.append("stepName", this.anonService.getConfigurationName());

        this.http.post<ProcessStatus>(this.baseUrl + '/cancel', formData).subscribe({
            next: (status: ProcessStatus) => {
                this.setState(status);
            },
            error: err => {
                this.error = `Failed to cancel the process. Status: ${err.status} (${err.statusText})`;
            }
        });
    }

    protected downloadResult() {
        this.http.get(this.baseUrl + '/zip', {responseType: 'arraybuffer'}).subscribe({
            next: data => {
                const blob = new Blob([data], {
                    type: 'application/zip'
                });
                const url = window.URL.createObjectURL(blob);
                window.open(url);
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
