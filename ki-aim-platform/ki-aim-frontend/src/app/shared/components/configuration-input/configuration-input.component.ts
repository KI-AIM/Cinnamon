import { Component, Input } from '@angular/core';
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { DataConfigurationService } from "../../services/data-configuration.service";

/**
 * Component for an input including the reset button and the information popup.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
  selector: 'app-configuration-input',
  templateUrl: './configuration-input.component.html',
  styleUrls: ['./configuration-input.component.less']
})
export class ConfigurationInputComponent {
    protected readonly ConfigurationInputType = ConfigurationInputType;
    protected readonly Math = Math;

    /**
     * The definition for this input.
     */
    @Input() public configurationInputDefinition!: ConfigurationInputDefinition;

    /**
     * The parent form group of the input.
     */
    @Input() public form!: FormGroup;

    /**
     * If the input is disabled.
     */
    @Input() public disabled!: boolean;

    constructor(protected dataConfigurationService: DataConfigurationService) {
    }

    /**
     * If the input is valid.
     */
    get isValid() {
        return !this.form.controls[this.configurationInputDefinition.name].invalid;
    }

    /**
     * Sets the value of the input to its default specified in the definition.
     */
    setToDefault() {
        if (this.configurationInputDefinition.type === ConfigurationInputType.LIST) {
            const formArray = this.form.controls[this.configurationInputDefinition.name] as FormArray
            formArray.clear();
            for (const defaultValue of this.configurationInputDefinition.default_value as number[]) {
                formArray.push(new FormControl(defaultValue, Validators.required));
            }
        } else {
            this.form.controls[this.configurationInputDefinition.name].setValue(this.configurationInputDefinition.default_value);
        }
    }
}
