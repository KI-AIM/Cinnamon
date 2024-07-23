import { Component, Input, ViewChild } from '@angular/core';
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { HttpClient } from "@angular/common/http";
import { ConfigurationSelectionComponent } from "../configuration-selection/configuration-selection.component";

@Component({
  selector: 'app-configuration-page',
  templateUrl: './configuration-page.component.html',
  styleUrls: ['./configuration-page.component.less']
})
export class ConfigurationPageComponent {
    @Input() public stepName!: string;
    @Input() public configurationName!: string;
    @Input() public defs!: {[name :string]: ConfigurationInputDefinition[]};

    protected readonly Object = Object;
    @ViewChild('selection') private selection: ConfigurationSelectionComponent;

    constructor(
        private readonly http: HttpClient,
    ) {
    }

    onSubmit(configuration: string) {
        const formData = new FormData();

        formData.append("stepName", this.stepName);
        formData.append("algorithm", this.selection.selectedOption);
        formData.append("configurationName", this.configurationName);
        formData.append("configuration", configuration);

        console.log('post');
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
