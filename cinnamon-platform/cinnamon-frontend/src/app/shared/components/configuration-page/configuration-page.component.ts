import { HttpClient } from "@angular/common/http";
import { ChangeDetectorRef, Component, Input, OnInit, ViewChild } from '@angular/core';
import { Router } from "@angular/router";
import { Mode } from "@core/enums/mode";
import { Steps } from "@core/enums/steps";
import { Status } from "@shared/model/status";
import { catchError, from, mergeMap, Observable, of, switchMap, tap } from "rxjs";
import { environments } from "src/environments/environment";
import { stringify } from "yaml";
import { Algorithm } from "../../model/algorithm";
import { ConfigurationAdditionalConfigs } from '../../model/configuration-additional-configs';
import {
    AlgorithmService,
    ConfigData,
    ConfigurationInfo,
    ReadConfigResult2
} from "../../services/algorithm.service";
import { ConfigurationService } from "../../services/configuration.service";
import { ErrorHandlingService } from "../../services/error-handling.service";
import { StatusService } from "../../services/status.service";
import { ConfigurationFormComponent } from "../configuration-form/configuration-form.component";
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";

/**
 * Component for the entire configuration page including the algorithm selection,
 * the configuration form, and the confirmation and skip buttons.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-configuration-page',
    templateUrl: './configuration-page.component.html',
    styleUrls: ['./configuration-page.component.less'],
    standalone: false
})
export class ConfigurationPageComponent implements OnInit {
    protected readonly Mode = Mode;
    protected readonly jobLabels: Record<string, string> = {
        anonymization: "Anonymization",
        synthetization: "Synthetization",
        technical_evaluation: "Technical Evaluation",
        risk_evaluation: "Risk Evaluation",
    };
    private readonly baseUrl: string = environments.apiUrl + "/api/process";

    @Input() public configurationInfo!: ConfigurationInfo;
    @Input() public step!: Steps;
    @Input() public additionalConfigs: ConfigurationAdditionalConfigs | null = null
    @Input() public hasAlgorithmSelection: boolean = true;

    protected configurationData$: Observable<ReadConfigResult2 | null>;
    protected status$: Observable<Status>;

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

    /**
     * If the corresponding process should be executed.
     * @protected
     */
    protected processEnabled: Record<string, boolean> = {};

    /**
     * If an algorithm is selected and the corresponding form is valid.
     * @protected
     */
    protected formValid: boolean = true;

    /**
     * Cache for the configuration file in the guided mode.
     * @protected
     */
    protected configFileCache: File | null = null;

    /**
     * If at least one process is enabled.
     * @protected
     */
    protected oneEnabled = false;

    protected selectedAlgorithm: Algorithm | null = null;

    @ViewChild('selection') private selection: ConfigurationSelectionComponent;
    @ViewChild('form') protected forms: ConfigurationFormComponent;

    constructor(
        protected readonly algorithmService: AlgorithmService,
        protected readonly changeDetectorRef: ChangeDetectorRef,
        private readonly configurationService: ConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
        private httpClient: HttpClient,
        private readonly router: Router,
        private readonly statusService: StatusService,
    ) {
    }

    ngOnInit() {
        for (const process of this.configurationInfo.processes) {
            this.processEnabled[process.job] = !process.skip;
            if (!process.skip) {
                this.oneEnabled = true;
            }
        }

        this.configurationData$ = this.algorithmService.algorithms.pipe(
            tap(value => {
                this.algorithms = value;
                if (!this.hasAlgorithmSelection) {
                    this.configurationService.setSelectedAlgorithm(this.algorithmService.getConfigurationName(), this.algorithms[0]);
                }
            }),
            switchMap(_ => {
                return this.algorithmService.fetchConfiguration();
            }),
            tap(value => {
                this.selectedAlgorithm = value.selectedAlgorithm
                if (value.selectedAlgorithm != null) {
                    this.configurationService.setSelectedAlgorithm(this.algorithmService.getConfigurationName(), value.selectedAlgorithm);
                }
            }),
            catchError(err => {
                this.errorHandlingService.addError(err, "Failed to load the configuration page. You can skip this step for now or try again later.");
                return of(null);
            }),
        );

        // Set callback functions
        this.algorithmService.setDoGetConfig(() => this.getConfig());
        this.algorithmService.setDoSetConfig((error: string | null) => this.setConfig(error));

        this.status$ = this.statusService.status$.pipe(
            tap(() => {
                const registryData = this.configurationService.getRegisteredConfigurationByName(this.algorithmService.getConfigurationName());
                this.disabled = this.statusService.isStepCompleted(registryData?.lockedAfterStep);
            }),
        );
    }

    /**
     * Checks if at least one process is enabled.
     * @protected
     */
    protected updateOneEnabled() {
        let _oneEnabled = false;
        for (const enabled of Object.values(this.processEnabled)) {
            if (enabled) {
                _oneEnabled = true;
                break;
            }
        }

        if (_oneEnabled !== this.oneEnabled) {
            this.oneEnabled = _oneEnabled;
            this.changeDetectorRef.detectChanges();
        }
    }

    /**
     * Handles changes on the selected form.
     * Updates the cached configuration and valid flag.
     * @protected
     */
    protected onFormChange(valid: boolean): void {
        this.updateConfigCache();
        this.formValid = valid;
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Handles changes on the selected algorithm.
     * Updates the cache and the validity of the form.
     * @param a The selected algorithm.
     * @protected
     */
    protected onSelectionChange(a: Algorithm): void {
        this.selectedAlgorithm = a;
        this.updateSelectCache();
    }

    /**
     * Updates the cached value of the current selected form.
     * @protected
     */
    protected updateConfigCache(): void {
        if (this.selectedAlgorithm && this.forms)  {
            this.configurationService.setConfiguration(this.algorithmService.getConfigurationName(), this.selectedAlgorithm, this.forms.formData);
        }
    }

    /**
     * Updates the cached value of the selected algorithm and sets the form to the last cached value.
     * @protected
     */
    protected updateSelectCache(): void {
        this.configurationService.setSelectedAlgorithm(this.algorithmService.getConfigurationName(), this.selection.selectedOption);
    }

    /**
     * Reads the values of the configuration page from the cache.
     */
    public readFromCache(): void {
        const selectedAlgorithm = this.configurationService.getSelectedAlgorithm(this.algorithmService.getConfigurationName());
        this.selectedAlgorithm = selectedAlgorithm;
        if (selectedAlgorithm && this.selection) {
            this.selection.selectedOption = selectedAlgorithm;
            if (this.forms) {
                this.forms.readFromCache();
            }
        }
    }

    /**
     * Caches the configuration file.
     * @param fileList
     * @protected
     */
    protected cacheConfiguration(fileList: FileList | null): void {
        if (fileList === null || fileList.length === 0) {
            return;
        }

        this.configFileCache = fileList[0];
    }

    /**
     * Uploads the cached configuration file.
     * Uses the setConfigCallback function to update the configuration in the application.
     * @protected
     */
    protected uploadCachedConfiguration(): void {
        if (this.configFileCache === null) {
            return;
        }

        const included = [this.algorithmService.getConfigurationName()];
        this.configurationService.uploadAllConfigurations(this.configFileCache, included).subscribe({
            next: () => {
                this.configFileCache = null;
            },
            error: error => {
                this.errorHandlingService.addError(error, "Could not upload configuration.");
            },
        });
    }

    /**
     * Handles the file upload event and uploads the selected configuration file.
     * Uses the setConfigCallback function to update the configuration in the application.
     */
    protected uploadConfiguration(fileList: FileList | null): void {
        if (fileList === null || fileList.length === 0) {
            return;
        }

        const file = fileList[0];
        const included = [this.algorithmService.getConfigurationName()];

        this.configurationService.uploadAllConfigurations(file, included).subscribe({
            error: error => {
                this.errorHandlingService.addError(error, "Could not upload configuration.");
            },
        });
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
        if (error === null) {
            this.readFromCache();
        } else {
            this.errorHandlingService.addError(error);
        }
    }

    /**
     * Submits the current form and proceeds to the next step.
     * @protected
     */
    protected submit(): void {
        this.updateSelectCache();
        this.updateConfigCache();

        if (!this.selection.selectedOption) {
            this.configureJobs().subscribe({
                    next: () => this.finish(),
                    error: err => {
                        this.errorHandlingService.addError(err, "Failed to save configuration.");
                    }
                }
            );
        } else {
            this.algorithmService.getAlgorithmDefinition(this.selection.selectedOption).pipe(
                switchMap(value => {
                    const config = this.forms ? this.forms.formData : '';
                    return this.postConfig(config, value.URL);
                }),
                switchMap(() => {
                    return this.configureJobs();
                }),
            ).subscribe({
                next: () => this.finish(),
                error: err => {
                    this.errorHandlingService.addError(err, "Failed to save configuration.");
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
     * @private
     */
    private configureJobs(): Observable<void> {
        return from(Object.entries(this.processEnabled)).pipe(
            mergeMap(process => {
                return this.postConfigure(!process[1], process[0]);
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
        return this.httpClient.post<void>(this.baseUrl + "/configure", formData);
    }

    /**
     * Proceeds to the next step.
     * @private
     */
    private finish() {
        let nextUrl = "";
        let nextStep = Steps.EVALUATION;
        switch (this.algorithmService.getConfigurationName()) {
            case "anonymization": {
                nextUrl = '/synthetizationConfiguration';
                nextStep = Steps.SYNTHETIZATION;
                break;
            }
            case "synthetization_configuration": {
                nextUrl = '/execution';
                nextStep = Steps.EXECUTION;
                break;
            }
            case "evaluation_configuration": {
                nextUrl = "/riskEvaluationConfiguration";
                nextStep = Steps.RISK_EVALUATION;
                break;
            }
            case "risk_assessment_configuration": {
                nextUrl = '/evaluation';
                nextStep = Steps.EVALUATION;
                break;
            }
            default: {
                console.error(`Unhandled step: ${this.algorithmService.getConfigurationName()}`);
            }
        }

        this.router.navigateByUrl(nextUrl);
        if (!this.statusService.isStepCompleted(nextStep)) {
            this.statusService.updateNextStep(nextStep).subscribe();
        }
    }
}
