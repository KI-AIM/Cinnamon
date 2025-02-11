import { AfterViewInit, ChangeDetectorRef, Component, Input, OnInit, ViewChild } from '@angular/core';
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";
import { Algorithm } from "../../model/algorithm";
import { AlgorithmService, ConfigData } from "../../services/algorithm.service";
import { stringify } from "yaml";
import { ConfigurationFormComponent } from "../configuration-form/configuration-form.component";
import { Steps } from "../../../core/enums/steps";
import { Router } from "@angular/router";
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../../environments/environment";
import { StatusService } from "../../services/status.service";
import { ConfigurationAdditionalConfigs } from '../../model/configuration-additional-configs';
import { from, mergeMap, Observable, switchMap } from "rxjs";

/**
 * Entire configuration page including the algorithm selection,
 * the configuration form and the confirmation and skip buttons.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-configuration-page',
    templateUrl: './configuration-page.component.html',
    styleUrls: ['./configuration-page.component.less'],
})
export class ConfigurationPageComponent implements OnInit, AfterViewInit {
    @Input() additionalConfigs: ConfigurationAdditionalConfigs | null = null
    @Input() public hasAlgorithmSelection: boolean = true;

    private baseUrl: string = environments.apiUrl + "/api/process";

    /**
     * Available algorithms fetched from the external API.
     * @protected
     */
    protected algorithms: Algorithm[] = [];

    /**
     * If this form is disabled.
     * @protected
     */
    protected disabled: boolean = false;

    /**
     * Error displayed on the top of the page.
     * Not visible if null.
     * @protected
     */
    protected error: string | null = null;

    @ViewChild('selection') private selection: ConfigurationSelectionComponent;
    @ViewChild('form') private forms: ConfigurationFormComponent;

    constructor(
        protected readonly algorithmService: AlgorithmService,
        private readonly changeDetectorRef: ChangeDetectorRef,
        private httpClient: HttpClient,
        private readonly router: Router,
        private readonly statusService: StatusService,
    ) {
    }

    ngOnInit() {
        // Set callback functions
        this.algorithmService.setDoGetConfig(() => this.getConfig());
        this.algorithmService.setDoSetConfig((error: string | null) => this.setConfig(error));

        // Get available algorithms
        this.algorithmService.algorithms.subscribe({
            next: value => {
                this.error = null;
                this.algorithms = value;
                if (!this.hasAlgorithmSelection) {
                    this.algorithmService.selectCache = this.algorithms[0];
                    this.readFromCache();
                }
            }, error: error => {
                console.log(error);
                this.error = `Failed to load available algorithms. Status: ${error.status} (${error.statusText})`;
            }
        });
    }

    ngAfterViewInit() {
        this.readFromCache();
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Updates the cached value of the current selected form.
     * @protected
     */
    protected updateConfigCache(): void
    {
        if (this.selection.selectedOption && this.forms)  {
            this.algorithmService.configCache[this.selection.selectedOption.name] = this.forms.formData;
        }
    }

    /**
     * Updates the cached value of the selected algorithm and sets the form to the last cached value.
     * @protected
     */
    protected updateSelectCache(): void {
        this.algorithmService.selectCache = this.selection.selectedOption;
        this.readFromCache();
    }

    /**
     * Reads the values of the configuration page from the cache.
     */
    public readFromCache(): void {
        if (this.algorithmService.selectCache && this.selection) {
            this.selection.selectedOption = this.algorithmService.selectCache;
            if (this.algorithmService.configCache[this.selection.selectedOption.name]) {
                //Timeout is 0, so function is called before data is overwritten
                setTimeout(() => {
                    this.forms.setConfiguration(this.algorithmService.configCache[this.algorithmService.selectCache!.name]);
                }, 0);
            }
        }
    }

    /**
     * Handles the given error by displaying the error on top of the page.
     * @param err The error message.
     * @protected
     */
    protected handleError(err: string) {
        this.error = err;
    }

    /**
     * Retrieves the configuration from the form.
     * @private
     */
    private getConfig(): ConfigData {
        if (!this.forms) {
            return {
                formData: {},
                selectedAlgorithm: this.selection.selectedOption
            };
        } else {
            return {
                formData: this.forms.formData,
                selectedAlgorithm: this.selection.selectedOption
            };
        }
    }

    /**
     * Sets the given configuration to the form.
     * @param error message if an error occurred, null if no error occurred.
     * @private
     */
    private setConfig(error: string | null) {
        this.error = error;
        if (error === null) {
            this.readFromCache();
        }
    }

    /**
     * Submits the given configuration form and proceeds to the next step.
     * @param configuration The raw data of the form.
     * @protected
     */
    protected onSubmit(configuration: Object) {
        this.updateSelectCache();
        this.updateConfigCache();

        this.algorithmService.getAlgorithmDefinition(this.selection.selectedOption).pipe(
            switchMap(value => {
                return this.postConfig(configuration, value.URL);
            }),
            switchMap(() => {
                return this.configureJobs(false);
            }),
        ).subscribe({
            next: () => this.finish(),
            error: err => {
                this.error = `Failed to save configuration. Status: ${err.status} (${err.statusText})`;
            }
        });
    }

    /**
     * Sets the step to be skipped and proceeds to the next step.
     * The step will be skipped during the execution.
     * @protected
     */
    protected skip() {
        this.updateSelectCache();
        this.updateConfigCache();

        if (!this.selection.selectedOption) {
            this.configureJobs(true).subscribe({
                    next: () => this.finish(),
                    error: err => {
                        this.error = `Failed to save configuration. Status: ${err.status} (${err.statusText})`;
                    }
                }
            );
        } else {

            const config = this.forms ? this.forms.formData : '';
            this.algorithmService.getAlgorithmDefinition(this.selection.selectedOption).pipe(
                switchMap(value => {
                    return this.postConfig(config, value.URL);
                }),
                switchMap(() => {
                    return this.configureJobs(true);
                }),
            ).subscribe({
                next: () => this.finish(),
                error: err => {
                    this.error = `Failed to save configuration. Status: ${err.status} (${err.statusText})`;
                }
            });
        }
    }

    /**
     * Sends the configuration to the backend.
     * @param configuration The configuration object.
     * @param url The URL to be used for the job.
     * @private
     */
    private postConfig(configuration: Object, url: string): Observable<void> {
        const formData = new FormData();
        formData.append("configuration", stringify(this.algorithmService.createConfiguration(configuration, this.selection.selectedOption)));
        formData.append("configurationName", this.algorithmService.getConfigurationName());
        formData.append("url", url);
        return this.httpClient.post<void>(environments.apiUrl + "/api/config", formData);
    }

    /**
     * Configures all jobs defined to be configured.
     * @param skip If the jobs should be skipped.
     * @private
     */
    private configureJobs(skip: boolean): Observable<void> {
        return from(this.algorithmService.getJobs()).pipe(
            mergeMap(job => {
                return this.postConfigure(skip, job);
            }),
        );
    }

    /**
     * Configures the job.
     * @param skip If the job should be skipped.
     * @param jobName The name of the job to configure.
     * @private
     */
    private postConfigure(skip: boolean, jobName: string): Observable<void> {
        const formData = new FormData();
        if (skip) {
            formData.append("skip", 'true');
        }
        formData.append("jobName", jobName);
        return this.httpClient.post<void>(this.baseUrl + "/" + this.algorithmService.getExecStepName() + "/configure", formData);
    }

    /**
     * Proceeds to the next step.
     * @private
     */
    private finish() {
        let nextUrl = "";
        let nextStep = Steps.EVALUATION;
        switch (this.algorithmService.getStepName()) {
            case "anonymization": {
                nextUrl = '/synthetizationConfiguration';
                nextStep = Steps.SYNTHETIZATION;
                break;
            }
            case "synthetization": {
                nextUrl = '/execution';
                nextStep = Steps.EVALUATION;
                break;
            }
            case "technical_evaluation": {
                nextUrl = "/riskEvaluationConfiguration";
                nextStep = Steps.RISK_EVALUATION;
                break;
            }
            case "risk_evaluation": {
                nextUrl = '/evaluation';
                nextStep = Steps.EVALUATION;
                break;
            }
            default: {
                console.error(`Unhandled step: ${this.algorithmService.getStepName()}`);
            }
        }

        this.router.navigateByUrl(nextUrl);
        this.statusService.setNextStep(nextStep);
    }
}
