import { Component, Input, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { stringify } from "yaml";

@Component({
  selector: 'app-configuration-form',
  templateUrl: './configuration-form.component.html',
  styleUrls: ['./configuration-form.component.less']
})
export class ConfigurationFormComponent implements OnInit {

    @Input() formDefinition: ConfigurationInputDefinition[];
    form!: FormGroup;

    ngOnInit() {
        const group: any = {};

        this.formDefinition.forEach(inputDefinition => {
            group[inputDefinition.name] = new FormControl(inputDefinition.defaultValue, Validators.required)
        });

        this.form = new FormGroup(group);
    }

    onSubmit() {
        console.log(stringify(this.form.getRawValue()));
    }
}
