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
        if (this.selection.selectedOption) {
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

        this.algorithmService.getAlgorithmDefinition(this.selection.selectedOption).subscribe({
            next: value => {
                const formData = new FormData();
                formData.append("configuration", stringify(this.algorithmService.createConfiguration(configuration, this.selection.selectedOption)));
                formData.append("stepName", this.algorithmService.getStepName());
                formData.append("url", value.URL);
                this.configure(formData);
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

        const formData = new FormData();
        formData.append("skip", 'true');
        formData.append("stepName", this.algorithmService.getStepName());
        const config = this.forms ? this.forms.formData : '';

        if (!this.selection.selectedOption) {
            this.configure(formData);
        } else {
            formData.append("configuration", stringify(this.algorithmService.createConfiguration(config, this.selection.selectedOption)));
            this.algorithmService.getAlgorithmDefinition(this.selection.selectedOption).subscribe({
                next: value => {
                    formData.append("url", value.URL);
                    this.configure(formData);
                }
            });
        }
    }

    /**
     * Sends the configuration to the backend and proceeds to the next step.
     * @param formData The form data for the request.
     * @private
     */
    private configure(formData: FormData) {
        this.httpClient.post<void>(this.baseUrl + "/" + this.algorithmService.getExecStepName() + "/configure", formData).subscribe({
            next: () => {
                this.router.navigateByUrl(this.algorithmService.getStepName() === "ANONYMIZATION"
                    ? '/synthetizationConfiguration'
                    : this.algorithmService.getStepName() === "SYNTHETIZATION"
                        ? "/execution"
                        : "/evaluation");
                this.statusService.setNextStep(this.algorithmService.getStepName() === "ANONYMIZATION"
                    ? Steps.SYNTHETIZATION
                    : this.algorithmService.getStepName() === "SYNTHETIZATION"
                        ? Steps.EXECUTION
                        : Steps.EVALUATION
            );
            },
            error: err => {
                this.error = `Failed to save configuration. Status: ${err.status} (${err.statusText})`;
            }
        });
    }
}
