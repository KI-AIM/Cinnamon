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

@Component({
    selector: 'app-configuration-page',
    templateUrl: './configuration-page.component.html',
    styleUrls: ['./configuration-page.component.less'],
})
export class ConfigurationPageComponent implements OnInit {
    private baseUrl: string = environments.apiUrl + "/api/process";

    protected algorithms: Algorithm[] = [];
    protected disabled: boolean = false;

    @ViewChild('selection') private selection: ConfigurationSelectionComponent;
    @ViewChild('form') private forms: ConfigurationFormComponent;

    protected error: string | null = null;

    constructor(
        protected readonly anonService: AlgorithmService,
        private httpClient: HttpClient,
        private readonly router: Router,
        private readonly stateManagement: StateManagementService,
    ) {
    }

    ngOnInit() {
        this.anonService.setDoGetConfig(() => this.getConfig());
        this.anonService.setDoSetConfig((data: ImportPipeData) => this.setConfig(data));

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

    private getConfig(): string {
        if (!this.forms) {
            return '';
        } else {
            return stringify(this.anonService.createConfiguration(this.forms.formData, this.selection.selectedOption))
        }
    }

    private setConfig(config: ImportPipeData) {
        if (config.success) {
            this.error = null;
            const result = this.anonService.readConfiguration(parse(config.yamlConfigString), config.configData.name);
            this.selection.selectedOption = result.selectedAlgorithm;
            setTimeout(() => {
                this.forms.setConfiguration(result.config);
            }, 100);
        } else {
            this.error = "Failed to load configuration";
        }
    }

    onSubmit(configuration: Object) {
        this.anonService.getAlgorithmDefinition(this.selection.selectedOption).subscribe({
            next: value => {
                const formData = new FormData();
                formData.append("configuration", stringify(this.anonService.createConfiguration(configuration, this.selection.selectedOption)));
                formData.append("stepName", this.anonService.getStepName());
                formData.append("url", value.URL);

                this.httpClient.post<void>(this.baseUrl + "/configure", formData).subscribe({
                    next: () => {
                        this.router.navigateByUrl(this.anonService.getStepName() === "ANONYMIZATION" ? '/synthetizationConfiguration' : "/execution");
                        this.stateManagement.setNextStep(this.anonService.getStepName() === "ANONYMIZATION" ? Steps.SYNTHETIZATION : Steps.EXECUTION);
                    },
                    error: err => {
                        this.error = `Failed to save configuration. Status: ${err.status} (${err.statusText})`;
                    }
                });
            }
        });
    }

    skip() {
        const formData = new FormData();
        formData.append("configuration", "skip");
        formData.append("stepName", this.anonService.getStepName());
        formData.append("url", "skip");

        this.httpClient.post<void>(this.baseUrl + "/configure", formData).subscribe({
            next: () => {
                this.router.navigateByUrl(this.anonService.getStepName() === "ANONYMIZATION" ? '/synthetizationConfiguration' : "/execution");
                this.stateManagement.setNextStep(this.anonService.getStepName() === "ANONYMIZATION" ? Steps.SYNTHETIZATION : Steps.EXECUTION);
            },
            error: err => {
                this.error = `Failed to save configuration. Status: ${err.status} (${err.statusText})`;
            }
        });
    }
}
