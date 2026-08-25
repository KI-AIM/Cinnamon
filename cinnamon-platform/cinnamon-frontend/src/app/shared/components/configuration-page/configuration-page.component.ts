import { HttpClient } from "@angular/common/http";
import { ChangeDetectorRef, Component, Input, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { FormGroup } from "@angular/forms";
import { Router } from "@angular/router";
import { Mode } from "@core/enums/mode";
import { Steps } from "@core/enums/steps";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { StateManagementService } from "@core/services/state-management.service";
import { FileUploadComponent } from "@shared/components/file-upload/file-upload.component";
import { Status } from "@shared/model/status";
import { DataConfigurationService } from "@shared/services/data-configuration.service";
import { catchError, combineLatest, from, map, mergeMap, Observable, of, shareReplay, switchMap, tap } from "rxjs";
import { environments } from "src/environments/environment";
import { stringify } from "yaml";
import {
    Algorithm,
    isMixedDataSynthesizer,
    isStructuredOnlySynthesizer,
    isTextOnlySynthesizer,
} from "../../model/algorithm";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { ConfigurationAdditionalConfigs } from '../../model/configuration-additional-configs';
import { AlgorithmService, ConfigData, ConfigurationInfo } from "../../services/algorithm.service";

import { ConfigurationService } from "../../services/configuration.service";
import { ErrorHandlingService } from "../../services/error-handling.service";
import { StatusService } from "../../services/status.service";
import { ConfigurationFormComponent } from "../configuration-form/configuration-form.component";
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";
import {
    DataConfiguration,
    hasStructuredColumns,
    hasTextColumns,
    isMixedDataConfiguration,
    isStructuredOnlyDataConfiguration,
    isTextOnlyDataConfiguration,
} from "../../model/data-configuration";
import { TextSynthesisConfigurationService } from "../../../features/synthetization/services/text-synthesis-configuration.service";

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
        risk_evaluation: "Risk Evaluation of the synthesized dataset",
        risk_evaluation_o: "Privacy Score Calculation of the original dataset",
        text_anonymization: "Text Anonymization",
    };
    private readonly baseUrl: string = environments.apiUrl + "/api/process";

    @Input() public configurationInfo!: ConfigurationInfo;
    @Input() public step!: Steps;
    @Input() public additionalConfigs: ConfigurationAdditionalConfigs | null = null

    /**
     * Optional template rendered as an extra workstep between Algorithm Selection
     * and Algorithm Configuration. Used by the synthetization page to host the
     * hyperparameter-tuning controls; defaults to null on every other page.
     */
    @Input() public intermediateStep: TemplateRef<any> | null = null;

    /**
     * When true, the Algorithm Configuration box still renders but its body is
     * replaced by {@link hiddenAlgorithmConfigMessage}. Used when the user opts
     * into hyperparameter tuning and the form should be auto-optimised.
     */
    @Input() public hideAlgorithmConfigForm: boolean = false;

    /**
     * Message displayed inside the Algorithm Configuration box when
     * {@link hideAlgorithmConfigForm} is true.
     */
    @Input() public hiddenAlgorithmConfigMessage: string =
        'Algorithm parameters will be automatically optimised by Hyperparameter Tuning.';

    protected pageData$: Observable<{
        algorithms: Algorithm[],
        configurationData: ConfigData,
        dataConfiguration: DataConfiguration,
        locked: boolean,
        status: Status,
    }>

    /**
     * If more than one algorithm is available and a selection should be displayed.
     */
    protected hasAlgorithmSelection: boolean = false;

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
    protected currentAlgorithms: Algorithm[] = [];
    protected currentDataConfiguration: DataConfiguration | null = null;
    private freeTextDefinitionCache: Map<string, Observable<AlgorithmDefinition>> = new Map<string, Observable<AlgorithmDefinition>>();

    protected isProcessAvailable(job: string, dataConfiguration: DataConfiguration | null): boolean {
        if (dataConfiguration === null) {
            return false;
        }
        if (job === "text_anonymization") {
            return hasTextColumns(dataConfiguration);
        }
        if (job === "anonymization") {
            return hasStructuredColumns(dataConfiguration);
        }
        return true;
    }

    @ViewChild('selection') private selection: ConfigurationSelectionComponent;
    @ViewChild('form') protected forms: ConfigurationFormComponent;
    @ViewChild('expertFileUpload') protected expertFileUpload: FileUploadComponent;
    @ViewChild('standardFileUpload') protected standardFileUpload: FileUploadComponent;

    constructor(
        protected readonly algorithmService: AlgorithmService,
        protected readonly changeDetectorRef: ChangeDetectorRef,
        private readonly configurationService: ConfigurationService,
        private readonly dataConfigService: DataConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
        private httpClient: HttpClient,
        private readonly notificationService: NotificationService,
        private readonly router: Router,
        private readonly stateManagementService: StateManagementService,
        private readonly statusService: StatusService,
        private readonly textSynthesisConfigurationService: TextSynthesisConfigurationService,
    ) {
    }

    protected get isSynthetizationConfiguration(): boolean {
        return this.algorithmService.getConfigurationName() === "synthetization_configuration";
    }

    protected get numberSteps(): number {
        return this.isSynthetizationConfiguration ? 6 : 4;
    }

    protected get hasFreeTextConfiguration(): boolean {
        return this.forms?.form?.get("text_synthesis_configuration") != null;
    }

    protected get structuredConfigurationInvalid(): boolean {
        if (!this.isSynthetizationConfiguration) {
            return !this.formValid;
        }

        const form = this.forms?.form;
        if (!form) {
            return true;
        }

        return Object.entries(form.controls).some(([name, control]) => {
            if (name === "text_synthesis_configuration") {
                return false;
            }
            return control.invalid;
        });
    }

    protected get freeTextSelectionInvalid(): boolean {
        const control = this.forms?.form?.get("text_synthesis_configuration.synthetization_configuration.algorithm.synthesizer");
        return control == null || control.invalid;
    }

    protected get freeTextConfigurationInvalid(): boolean {
        const llmProfile = this.forms?.form?.get("text_synthesis_configuration.synthetization_configuration.algorithm.llm_profile");
        const modelParameter = this.forms?.form?.get("text_synthesis_configuration.synthetization_configuration.algorithm.model_parameter");
        const modelFitting = this.forms?.form?.get("text_synthesis_configuration.synthetization_configuration.algorithm.model_fitting");
        const sampling = this.forms?.form?.get("text_synthesis_configuration.synthetization_configuration.algorithm.sampling");

        if (llmProfile == null || sampling == null) {
            return true;
        }

        return [llmProfile, modelParameter, modelFitting, sampling]
            .filter(control => control != null)
            .some(control => control.invalid);
    }

    protected get submitInvalid(): boolean {
        if (!this.oneEnabled) {
            return false;
        }

        if (this.getEffectiveSelectedAlgorithm() == null) {
            return true;
        }

        if (!this.hideAlgorithmConfigForm && !this.formValid) {
            return true;
        }

        if (!this.hasFreeTextConfiguration) {
            return false;
        }

        return this.freeTextSelectionInvalid || this.freeTextConfigurationInvalid;
    }

    protected getFreeTextAlgorithmGroup(): FormGroup | null {
        return this.forms?.form?.get("text_synthesis_configuration.synthetization_configuration.algorithm") as FormGroup | null;
    }

    protected getStructuredAlgorithms(algorithms: Algorithm[]): Algorithm[] {
        if (!this.isSynthetizationConfiguration) {
            return algorithms;
        }

        return algorithms.filter(item => isStructuredOnlySynthesizer(item));
    }

    protected getTextOnlyAlgorithms(algorithms: Algorithm[]): Algorithm[] {
        return algorithms.filter(item => isTextOnlySynthesizer(item));
    }

    protected getFreeTextAlgorithms(algorithms: Algorithm[]): Algorithm[] {
        return algorithms.filter(item => isMixedDataSynthesizer(item));
    }

    protected getPrimaryAlgorithms(algorithms: Algorithm[], dataConfiguration: DataConfiguration): Algorithm[] {
        if (!this.isSynthetizationConfiguration) {
            return algorithms;
        }
        if (isTextOnlyDataConfiguration(dataConfiguration)) {
            return this.getTextOnlyAlgorithms(algorithms);
        }
        if (isMixedDataConfiguration(dataConfiguration)) {
            return this.getFreeTextAlgorithms(algorithms);
        }
        return this.getStructuredAlgorithms(algorithms);
    }

    protected getEffectiveSelectedAlgorithm(
        algorithms: Algorithm[] = this.currentAlgorithms,
        dataConfiguration: DataConfiguration | null = this.currentDataConfiguration,
    ): Algorithm | null {
        const currentSelection = this.selection?.selectedOption ?? this.selectedAlgorithm;
        if (dataConfiguration == null) {
            return currentSelection;
        }

        const primaryAlgorithms = this.getPrimaryAlgorithms(algorithms, dataConfiguration);
        if (currentSelection != null && primaryAlgorithms.some(item => item.name === currentSelection.name)) {
            return currentSelection;
        }

        return primaryAlgorithms.length === 1 ? primaryAlgorithms[0] : null;
    }

    protected getNumberSteps(dataConfiguration: DataConfiguration): number {
        return 4;
    }

    protected getTotalStepCount(dataConfiguration: DataConfiguration): number {
        const hasIntermediateStep = this.shouldShowIntermediateStep(dataConfiguration);
        return this.getNumberSteps(dataConfiguration) + (hasIntermediateStep ? 1 : 0);
    }

    protected getAlgorithmConfigurationStepIndex(dataConfiguration: DataConfiguration): number {
        const hasIntermediateStep = this.shouldShowIntermediateStep(dataConfiguration);
        return hasIntermediateStep ? 4 : 3;
    }

    protected getFreeTextSelectionStepIndex(dataConfiguration: DataConfiguration): number {
        const hasIntermediateStep = this.shouldShowIntermediateStep(dataConfiguration);
        return hasIntermediateStep ? 5 : 4;
    }

    protected getFreeTextConfigurationStepIndex(dataConfiguration: DataConfiguration): number {
        const hasIntermediateStep = this.shouldShowIntermediateStep(dataConfiguration);
        return hasIntermediateStep ? 6 : 5;
    }

    protected shouldShowIntermediateStep(dataConfiguration: DataConfiguration): boolean {
        return this.intermediateStep != null && isStructuredOnlyDataConfiguration(dataConfiguration);
    }

    protected shouldShowFreeTextSteps(dataConfiguration: DataConfiguration): boolean {
        return false;
    }

    protected getFreeTextAlgorithmDefinition(
        algorithms: Algorithm[],
        dataConfiguration: DataConfiguration,
    ): Observable<AlgorithmDefinition | null> {
        const selectedName = this.forms?.form?.get("text_synthesis_configuration.synthetization_configuration.algorithm.synthesizer")?.value;
        const selectedAlgorithm = this.getFreeTextAlgorithms(algorithms).find(item => item.name === selectedName);
        if (selectedAlgorithm == null) {
            return of(null);
        }

        let definition$ = this.freeTextDefinitionCache.get(selectedAlgorithm.name);
        if (!definition$) {
            definition$ = this.algorithmService.getAlgorithmDefinition(selectedAlgorithm).pipe(
                shareReplay(1),
            );
            this.freeTextDefinitionCache.set(selectedAlgorithm.name, definition$);
        }

        return definition$.pipe(
            tap(definition => {
                const disabled = this.forms?.form?.get(
                    "text_synthesis_configuration.synthetization_configuration.algorithm.synthesizer",
                )?.disabled ?? false;
                if (this.forms?.form) {
                    this.textSynthesisConfigurationService.syncFormWithDefinition(
                        this.forms.form,
                        definition,
                        dataConfiguration,
                        disabled,
                    );
                }
            }),
        );
    }

    protected getSelectionStepHeader(dataConfiguration: DataConfiguration): string {
        if (this.isSynthetizationConfiguration && isTextOnlyDataConfiguration(dataConfiguration)) {
            return "Select the free-text synthesizer";
        }
        if (this.isSynthetizationConfiguration && isMixedDataConfiguration(dataConfiguration)) {
            return "Select the mixed-data synthesizer";
        }
        if (this.isSynthetizationConfiguration) {
            return "Select the structured synthesizer";
        }
        return "Select the algorithm to be executed";
    }

    protected getConfigurationStepHeader(dataConfiguration: DataConfiguration): string {
        if (this.isSynthetizationConfiguration && isTextOnlyDataConfiguration(dataConfiguration)) {
            return "Configure the free-text synthesizer";
        }
        if (this.isSynthetizationConfiguration && isMixedDataConfiguration(dataConfiguration)) {
            return "Configure the mixed-data synthesizer";
        }
        if (this.isSynthetizationConfiguration) {
            return "Configure the structured synthesizer";
        }
        return "Configure the algorithm";
    }

    ngOnInit() {
        for (const process of this.configurationInfo.processes) {
            const cachedStatus = this.configurationService.getProcessStatus(this.algorithmService.getConfigurationName(), process.job);
            const active = cachedStatus != null ? cachedStatus : !process.skip;

            this.processEnabled[process.job] = active;
            this.oneEnabled ||= active;
        }

        this.pageData$ = combineLatest({
            algorithms: this.algorithmService.algorithms.pipe(
                tap(algorithms => {
                    this.hasAlgorithmSelection = this.getStructuredAlgorithms(algorithms).length > 1;
                }),
                catchError(err => {
                    // Disable all processes
                    this.oneEnabled = false;
                    for (const process of this.configurationInfo.processes) {
                        this.processEnabled[process.job] = false;
                    }

                    this.errorHandlingService.addError(err, "Failed to load the configuration page. You can skip this step for now or try again later.");
                    return of([] as Algorithm[]);
                }),
            ),
            dataConfiguration: this.dataConfigService.dataConfiguration$,
            locked: this.stateManagementService.currentStepLocked$.pipe(
                map(value => value.isLocked),
            ),
            status: this.statusService.statusNonNull$,
        }).pipe(
            switchMap(pageData => {
                this.currentAlgorithms = pageData.algorithms;
                this.currentDataConfiguration = pageData.dataConfiguration;
                this.applyProcessAvailability(pageData.dataConfiguration);
                const primaryAlgorithms = this.getPrimaryAlgorithms(pageData.algorithms, pageData.dataConfiguration);
                this.hasAlgorithmSelection = primaryAlgorithms.length > 1;

                if (pageData.algorithms.length === 0) {
                    return of({
                        ...pageData,
                        configurationData: {config: {}, selectedAlgorithm: null},
                    });
                }

                return this.algorithmService.fetchConfiguration().pipe(
                    tap(value => {
                        const hasCurrentSelection = this.selectedAlgorithm != null
                            && primaryAlgorithms.some(item => item.name === this.selectedAlgorithm!.name);

                        if (value.selectedAlgorithm != null && primaryAlgorithms.some(item => item.name === value.selectedAlgorithm!.name)) {
                            this.selectedAlgorithm = value.selectedAlgorithm
                            this.configurationService.setSelectedAlgorithm(this.algorithmService.getConfigurationName(), value.selectedAlgorithm);
                        } else if (hasCurrentSelection) {
                            value.selectedAlgorithm = this.selectedAlgorithm;
                        } else if (!this.hasAlgorithmSelection && primaryAlgorithms.length > 0) {
                            this.selectedAlgorithm = primaryAlgorithms[0];
                            this.configurationService.setSelectedAlgorithm(this.algorithmService.getConfigurationName(), primaryAlgorithms[0]);
                            value.selectedAlgorithm = this.selectedAlgorithm;
                        } else {
                            this.selectedAlgorithm = null;
                        }
                    }),
                    map(value => ({
                        ...pageData,
                        configurationData: value,
                    })),
                    catchError(error => {
                        this.errorHandlingService.addError(error);
                        const value: ConfigData = {config: {}, selectedAlgorithm: null};
                        const hasCurrentSelection = this.selectedAlgorithm != null
                            && primaryAlgorithms.some(item => item.name === this.selectedAlgorithm!.name);

                        if (hasCurrentSelection) {
                            value.selectedAlgorithm = this.selectedAlgorithm;
                        } else if (!this.hasAlgorithmSelection && primaryAlgorithms.length > 0) {
                            this.selectedAlgorithm = primaryAlgorithms[0];
                            this.configurationService.setSelectedAlgorithm(this.algorithmService.getConfigurationName(), primaryAlgorithms[0]);
                            value.selectedAlgorithm = this.selectedAlgorithm;
                        }

                        return of({
                            ...pageData,
                            configurationData: value,
                        });
                    }),
                );
            }),
        );

        // Set callback functions
        this.algorithmService.setDoSetConfig((error: string | null) => this.setConfig(error));
    }

    /**
     * Callback triggered when toggling a process.
     * Update if at least one job is enabled and updates the cache.
     *
     * @param job The name of the job that was toggled.
     * @protected
     */
    protected onProcessToggle(job: string) {
        this.updateOneEnabled();

        this.changeDetectorRef.detectChanges();

        // Cache the value change
        this.configurationService.setProcessStatus(this.algorithmService.getConfigurationName(), job, this.processEnabled[job]);
    }

    /**
     * Disables processes that do not apply to the selected data. This is also
     * needed when the user switches projects, because process toggle state is
     * cached independently from the data configuration.
     */
    private applyProcessAvailability(dataConfiguration: DataConfiguration): void {
        for (const process of this.configurationInfo.processes) {
            if (this.isProcessAvailable(process.job, dataConfiguration) || !this.processEnabled[process.job]) {
                continue;
            }

            this.processEnabled[process.job] = false;
            this.configurationService.setProcessStatus(
                this.algorithmService.getConfigurationName(),
                process.job,
                false,
            );
        }
        this.updateOneEnabled();
    }

    private updateOneEnabled(): void {
        this.oneEnabled = Object.values(this.processEnabled).some(enabled => enabled);
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
        const selectedAlgorithm = this.getEffectiveSelectedAlgorithm();
        if (selectedAlgorithm && this.forms)  {
            this.configurationService.setConfiguration(this.algorithmService.getConfigurationName(), selectedAlgorithm, this.forms.formData);
        }
    }

    /**
     * Updates the cached value of the selected algorithm and sets the form to the last cached value.
     * @protected
     */
    protected updateSelectCache(): void {
        const selectedAlgorithm = this.getEffectiveSelectedAlgorithm();
        if (selectedAlgorithm) {
            this.selectedAlgorithm = selectedAlgorithm;
            this.configurationService.setSelectedAlgorithm(this.algorithmService.getConfigurationName(), selectedAlgorithm);
        }
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
                this.standardFileUpload.clearFile();
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
            next: () => {
                this.notificationService.addNotification(new AppNotification("Successfully imported the configuration.", "success"));
                this.expertFileUpload.clearFile();
            },
            error: error => {
                this.errorHandlingService.addError(error, "Could not upload configuration.");
            },
        });
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
        if (this.submitInvalid) {
            return;
        }

        this.updateSelectCache();
        this.updateConfigCache();
        const selectedAlgorithm = this.getEffectiveSelectedAlgorithm();

        if (!selectedAlgorithm) {
            this.configureJobs().subscribe({
                    next: () => this.finish(),
                    error: err => {
                        this.errorHandlingService.addError(err, "Failed to save configuration.");
                    }
                }
            );
        } else {
            const config = this.hideAlgorithmConfigForm
                ? {}
                : (this.forms ? this.forms.formData : '');
            this.postConfig(config, selectedAlgorithm).pipe(
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
     * @private
     */
    private postConfig(configuration: Object, selectedAlgorithm: Algorithm): Observable<void> {
        const configurationString = stringify(this.algorithmService.createConfiguration(configuration, selectedAlgorithm));
        return this.configurationService.storeConfig(configurationString);
    }

    /**
     * Configures all jobs defined to be configured.
     * @private
     */
    private configureJobs(): Observable<void> {
        return from(Object.entries(this.processEnabled)).pipe(
            mergeMap(([job, enabled]) => {
                const available = this.isProcessAvailable(job, this.currentDataConfiguration);
                const skip = !enabled || !available;
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
