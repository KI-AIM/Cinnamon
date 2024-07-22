import { Component, Input } from '@angular/core';
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { FormGroup } from "@angular/forms";

@Component({
  selector: 'app-configuration-input',
  templateUrl: './configuration-input.component.html',
  styleUrls: ['./configuration-input.component.less']
})
export class ConfigurationInputComponent {
    protected readonly ConfigurationInputType = ConfigurationInputType;

    @Input() configurationInputDefinition: ConfigurationInputDefinition;
    @Input() form!: FormGroup;

    get isValid() {
        return this.form.controls[this.configurationInputDefinition.name].valid;
    }

    setToDefault() {
        this.form.controls[this.configurationInputDefinition.name].setValue(this.configurationInputDefinition.defaultValue);
    }
}
