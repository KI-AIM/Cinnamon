import { Component, ViewChild } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";
import { Algorithm } from "../../model/algorithm";
import { AlgorithmService } from "../../services/algorithm.service";
import { stringify } from "yaml";
import { environments } from "../../../../environments/environment";

@Component({
  selector: 'app-configuration-page',
  templateUrl: './configuration-page.component.html',
  styleUrls: ['./configuration-page.component.less']
})
export class ConfigurationPageComponent {

    protected algorithms: Algorithm[] = [];

    @ViewChild('selection') private selection: ConfigurationSelectionComponent;

    protected error: string | null = null;

    constructor(
        private readonly http: HttpClient,
        private readonly anonService: AlgorithmService
    ) {
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

    onSubmit(configuration: Object) {
        const formData = new FormData();

        this.anonService.getAlgorithmDefinition(this.selection.selectedOption).subscribe({
            next: value => {

                formData.append("stepName", this.anonService.getStepName());
                formData.append("algorithm", this.selection.selectedOption.synthesizer);
                formData.append("configurationName", this.anonService.getConfigurationName());
                formData.append("configuration", stringify(this.createConfiguration(configuration, this.selection.selectedOption)));
                formData.append("url", value.URL);

                this.http.post<any>(environments.apiUrl + '/api/process/start', formData).subscribe({
                    next: (a) => {
                        // TODO set status
                    },
                    error: err => {
                        console.log(err);
                    }
                });
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
