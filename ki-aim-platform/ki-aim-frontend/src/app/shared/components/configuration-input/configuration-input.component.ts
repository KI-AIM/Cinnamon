import { Component, Input } from '@angular/core';
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";

@Component({
  selector: 'app-configuration-input',
  templateUrl: './configuration-input.component.html',
  styleUrls: ['./configuration-input.component.less']
})
export class ConfigurationInputComponent {
    protected readonly ConfigurationInputType = ConfigurationInputType;

    @Input() configurationInputDefinition!: ConfigurationInputDefinition;
    @Input() form!: FormGroup;

    get isValid() {
        return this.form.controls[this.configurationInputDefinition.name].valid;
    }

    setToDefault() {
        if (this.configurationInputDefinition.type === ConfigurationInputType.ARRAY) {
            const formArray = this.form.controls[this.configurationInputDefinition.name] as FormArray
            formArray.clear();
            for (const defaultValue of this.configurationInputDefinition.defaultValue as number[]) {
                formArray.push(new FormControl(defaultValue, Validators.required));
            }
        } else {
            this.form.controls[this.configurationInputDefinition.name].setValue(this.configurationInputDefinition.defaultValue);
        }
    }
}
