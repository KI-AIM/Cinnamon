import { Component, Input, OnInit } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { stringify } from "yaml";
import { ConfigurationInputType } from "../../model/configuration-input-type";

@Component({
  selector: 'app-configuration-form',
  templateUrl: './configuration-form.component.html',
  styleUrls: ['./configuration-form.component.less']
})
export class ConfigurationFormComponent implements OnInit {

    @Input() formDefinition!: ConfigurationInputDefinition[];
    form!: FormGroup;

    ngOnInit() {
        const group: any = {};

        this.formDefinition.forEach(inputDefinition => {
            if (inputDefinition.type === ConfigurationInputType.ARRAY) {
                const controls = [];
                for (const defaultValue of inputDefinition.defaultValue as number[]) {
                    controls.push(new FormControl(defaultValue, Validators.required));
                }

                group[inputDefinition.name] = new FormArray(controls, Validators.required)
            } else {
                group[inputDefinition.name] = new FormControl(inputDefinition.defaultValue, Validators.required)
            }
        });

        this.form = new FormGroup(group);
    }

    onSubmit() {
        console.log(stringify(this.form.getRawValue()));
    }
}
