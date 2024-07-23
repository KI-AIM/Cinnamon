import { Component, ViewChild } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { AlgorithmService } from "../../services/algorithm.service";

@Component({
  selector: 'app-configuration-page',
  templateUrl: './configuration-page.component.html',
  styleUrls: ['./configuration-page.component.less']
})
export class ConfigurationPageComponent {
    protected defs: {[name :string]: AlgorithmDefinition} = {};

    protected readonly Object = Object;
    @ViewChild('selection') private selection: ConfigurationSelectionComponent;

    constructor(
        private readonly http: HttpClient,
        private readonly anonService: AlgorithmService
    ) {
        this.anonService.algorithms.subscribe(value =>
            value.forEach(algorithm => {
                    this.anonService.getAlgorithmDefinition(algorithm).subscribe(value1 => this.defs[algorithm.name] = value1);
                }
            ));
    }

    onSubmit(configuration: string) {
        const formData = new FormData();

        formData.append("stepName", this.anonService.getStepName());
        formData.append("algorithm", this.selection.selectedOption);
        formData.append("configurationName", this.anonService.getConfigurationName());
        formData.append("configuration", configuration);
        console.log("stepName", this.anonService.getStepName());
        console.log("algorithm", this.selection.selectedOption);
        console.log("configurationName", this.anonService.getConfigurationName());
        console.log("configuration", configuration);

        this.http.post<any>('api/process/start', formData).subscribe({
            next: (a) => {
                console.log(a);
            },
            error: err => {
                console.log(err);
            }
        });
    }
}
