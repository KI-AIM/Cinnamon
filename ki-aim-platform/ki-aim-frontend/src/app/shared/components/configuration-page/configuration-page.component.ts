import { Component, OnInit, ViewChild } from '@angular/core';
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";
import { Algorithm } from "../../model/algorithm";
import { AlgorithmService } from "../../services/algorithm.service";
import { parse, stringify } from "yaml";
import { ConfigurationFormComponent } from "../configuration-form/configuration-form.component";
import { ImportPipeData } from "../../model/import-pipe-data";
import { Steps } from "../../../core/enums/steps";
import { Router } from "@angular/router";
import { StateManagementService } from "../../../core/services/state-management.service";
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../../environments/environment";

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
export class ConfigurationPageComponent implements OnInit {
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
        private httpClient: HttpClient,
        private readonly router: Router,
        private readonly stateManagement: StateManagementService,
    ) {
    }

    ngOnInit() {
        // Set callback functions
        this.algorithmService.setDoGetConfig(() => this.getConfig());
        this.algorithmService.setDoSetConfig((data: ImportPipeData) => this.setConfig(data));

        // Get available algorithms
        this.algorithmService.algorithms.subscribe({
            next: value => {
                this.error = null;
                this.algorithms = value;
            }, error: error => {
                console.log(error);
                this.error = `Failed to load available algorithms. Status: ${error.status} (${error.statusText})`;
            }
        });
    }

    /**
     * Retrieves the configuration from the form and transforms it into the specified structure.
     * @private
     */
    private getConfig(): string {
        if (!this.forms) {
            return '';
        } else {
            return stringify(this.algorithmService.createConfiguration(this.forms.formData, this.selection.selectedOption))
        }
    }

    /**
     * Sets the given configuration to the form.
     * @param config Result of the configuration import.
     * @private
     */
    private setConfig(config: ImportPipeData) {
        if (config.success) {
            this.error = null;
            if (config.yamlConfigString !== "skip") {
                const result = this.algorithmService.readConfiguration(parse(config.yamlConfigString), config.configData.name);
                this.selection.selectedOption = result.selectedAlgorithm;
                setTimeout(() => {
                    this.forms.setConfiguration(result.config);
                }, 100);
            }
        } else {
            this.error = "Failed to load configuration";
        }
    }

    /**
     * Submits the given configuration form and proceeds to the next step.
     * @param configuration The raw data of the form.
     * @protected
     */
    protected onSubmit(configuration: Object) {
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
        const formData = new FormData();
        formData.append("configuration", "skip");
        formData.append("stepName", this.algorithmService.getStepName());
        formData.append("url", "skip");
        this.configure(formData);
    }

    /**
     * Sends the configuration to the backend and proceeds to the next step.
     * @param formData The form data for the request.
     * @private
     */
    private configure(formData: FormData) {
        this.httpClient.post<void>(this.baseUrl + "/configure", formData).subscribe({
            next: () => {
                this.router.navigateByUrl(this.algorithmService.getStepName() === "ANONYMIZATION" ? '/synthetizationConfiguration' : "/execution");
                this.stateManagement.setNextStep(this.algorithmService.getStepName() === "ANONYMIZATION" ? Steps.SYNTHETIZATION : Steps.EXECUTION);
            },
            error: err => {
                this.error = `Failed to save configuration. Status: ${err.status} (${err.statusText})`;
            }
        });
    }
}
