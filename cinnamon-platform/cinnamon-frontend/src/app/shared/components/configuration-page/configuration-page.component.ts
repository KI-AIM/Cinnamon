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
import { ConfigurationService } from "../../services/configuration.service";
import { ErrorHandlingService } from "../../services/error-handling.service";

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
    standalone: false
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
    protected disabled: boolean = true;

    @ViewChild('selection') private selection: ConfigurationSelectionComponent;
    @ViewChild('form') private forms: ConfigurationFormComponent;

    constructor(
        protected readonly algorithmService: AlgorithmService,
        private readonly changeDetectorRef: ChangeDetectorRef,
        private readonly configurationService: ConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
        private httpClient: HttpClient,
        private readonly router: Router,
        private readonly statusService: StatusService,
    ) {
    }

    ngOnInit() {
        // Set callback functions
        this.algorithmService.setDoGetConfig(() => this.getConfig());
        this.algorithmService.setDoSetConfig((error: string | null) => this.setConfig(error));

        this.statusService.fetchStatus().subscribe({
            next: value => {
                const registryData = this.configurationService.getRegisteredConfigurationByName(this.algorithmService.getConfigurationName());
                this.disabled = this.statusService.isStepCompleted(registryData?.lockedAfterStep);
            }
        });

        // Get available algorithms
        this.algorithmService.algorithms.subscribe({
            next: value => {
                this.errorHandlingService.clearError();
                this.algorithms = value;
                if (!this.hasAlgorithmSelection) {
                    this.algorithmService.selectCache = this.algorithms[0];
                    this.readFromCache();
                }
            }, error: error => {
                this.errorHandlingService.setError(error, "Failed to load available algorithms.");
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
            if (this.forms) {
                this.forms.readFromCache();
            }
        }
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
        this.errorHandlingService.setError(error);
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
                this.errorHandlingService.setError(err, "Failed to save configuration.");
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
                        this.errorHandlingService.setError(err, "Failed to save configuration.");
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
                    this.errorHandlingService.setError(err, "Failed to save configuration.");
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
        const configurationName = this.algorithmService.getConfigurationName();
        const configurationString = stringify(this.algorithmService.createConfiguration(configuration, this.selection.selectedOption));
        return this.configurationService.storeConfig(configurationName, configurationString, url);
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
                nextStep = Steps.EXECUTION;
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
        if (!this.statusService.isStepCompleted(nextStep)) {
            this.statusService.updateNextStep(nextStep).subscribe();
        }
    }
}
