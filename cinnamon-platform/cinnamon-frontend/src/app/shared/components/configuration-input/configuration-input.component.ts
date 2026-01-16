import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import {AbstractControl, FormArray, FormControl, FormGroup, Validators} from "@angular/forms";
import { DataConfigurationService } from "../../services/data-configuration.service";
import { Subscription } from 'rxjs';
import { ColumnConfiguration } from '../../model/column-configuration';

/**
 * Component for an input including the reset button and the information popup.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-configuration-input',
    templateUrl: './configuration-input.component.html',
    styleUrls: ['./configuration-input.component.less'],
    standalone: false
})
export class ConfigurationInputComponent implements OnInit, OnDestroy {
    protected readonly ConfigurationInputType = ConfigurationInputType;
    protected readonly Math = Math;

    public dataConfiguration: ColumnConfiguration[];

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

    private dataConfigurationSubscription: Subscription;

    constructor(protected dataConfigurationService: DataConfigurationService) {
    }

    /**
     * If the input is valid.
     */
     protected get isValid() {
        return !this.form.controls[this.configurationInputDefinition.name].invalid &&
            (!this.configurationInputDefinition.invert || !this.form.controls[this.configurationInputDefinition.invert].invalid);
    }

    /**
     * Disables or enables this input component.
     * Toggles the input element, set to default button and the popup button for list elements.
     * @param disabled
     */
    public setDisabled(disabled: boolean) {
         this.disabled = disabled;
         if (disabled) {
             this.inputControl.disable();
         } else {
             this.inputControl.enable();
         }
    }

    /**
     * Returns the input control of the input element.
     * @private
     */
    private get inputControl(): AbstractControl {
         return this.form.controls[this.configurationInputDefinition.name];
    }

    ngOnInit() {
        // Check if there is a `switch` for this field

        this.dataConfigurationSubscription = this.dataConfigurationService.dataConfiguration$.subscribe(value => {
            this.dataConfiguration = value.configurations;
        });

        if (this.configurationInputDefinition.switch && this.configurationInputDefinition.switch.length > 0) {

            const switchDefinition = this.configurationInputDefinition.switch[0]; // Take the first switch condition
            // Check if the field on which this `switch` depends is already present in the form
            const dependentField = switchDefinition.depends_on;
            if (this.form.controls[dependentField]) {

                // Watch for changes on the dependent field
                this.form.controls[dependentField].valueChanges.subscribe((value) => {
                    this.applySwitchLogic(value, switchDefinition);

                    // Update the default value and re-evaluate validators
                    const control = this.form.controls[this.configurationInputDefinition.name];
                    control.setValue(this.configurationInputDefinition.default_value, {emitEvent: false});
                    control.updateValueAndValidity({emitEvent: false, onlySelf: true});
                });

                // Immediately apply the switch logic for initialization
                this.applySwitchLogic(this.form.controls[dependentField].value, switchDefinition);

            }
        }
    }

    ngOnDestroy() {
        this.dataConfigurationSubscription.unsubscribe();
    }

    // Method to apply the `switch` logic in a generic way
    private applySwitchLogic(dependentValue: any, switchDefinition: any) {
        const conditions = switchDefinition.conditions;
        const matchedCondition = conditions.find((condition: any) => condition.if === dependentValue);

        if (matchedCondition) {

            // If a match is found, apply the allowed values
            if (matchedCondition.values) {
                this.configurationInputDefinition.values = matchedCondition.values;
            }

            // You can also apply other properties like min/max if defined in the switch
            if (matchedCondition.min_value !== undefined) {
                this.configurationInputDefinition.min_value = matchedCondition.min_value;
            }

            if (matchedCondition.max_value !== undefined) {
                this.configurationInputDefinition.max_value = matchedCondition.max_value;
            }
        }
    }


    /**
     * Sets the value of the input to its default specified in the definition.
     */
    protected setToDefault() {
        if (this.configurationInputDefinition.type === ConfigurationInputType.LIST) {
            const formArray = this.form.controls[this.configurationInputDefinition.name] as FormArray
            formArray.clear();
            for (const defaultValue of this.configurationInputDefinition.default_value as number[]) {
                formArray.push(new FormControl(defaultValue, Validators.required));
            }
        } else if (this.configurationInputDefinition.type === ConfigurationInputType.ATTRIBUTE_LIST) {
            const formArray = this.form.controls[this.configurationInputDefinition.name] as FormArray
            formArray.clear();

            if (this.configurationInputDefinition.invert) {
                const formArrayInverted = this.form.controls[this.configurationInputDefinition.invert] as FormArray
                formArrayInverted.clear();

                this.dataConfiguration.forEach(attribute => {
                    formArrayInverted.push(new FormControl(attribute.name));
                });
            }
        } else {
            this.form.controls[this.configurationInputDefinition.name].setValue(this.configurationInputDefinition.default_value);
        }
    }
}
