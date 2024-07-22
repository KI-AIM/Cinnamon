import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { stringify } from "yaml";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { HttpClient } from "@angular/common/http";

@Component({
  selector: 'app-configuration-form',
  templateUrl: './configuration-form.component.html',
  styleUrls: ['./configuration-form.component.less']
})
export class ConfigurationFormComponent implements OnInit {

    @Input() public formDefinition!: ConfigurationInputDefinition[];
    @Output() public submitConfiguration = new EventEmitter<string>();
    protected form!: FormGroup;

    constructor(
        private readonly http: HttpClient,
    ) {
    }

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
                const validators = [Validators.required];
                if (inputDefinition.minValue !== null) {
                    validators.push(Validators.min(inputDefinition.minValue));
                }
                if (inputDefinition.maxValue !== null) {
                    validators.push(Validators.max(inputDefinition.maxValue));
                }

                group[inputDefinition.name] = new FormControl(inputDefinition.defaultValue, validators)
            }
        });

        this.form = new FormGroup(group);
    }

    onSubmit() {
        console.log("emit");
        this.submitConfiguration.emit(stringify(this.form.getRawValue()));
    }
}
