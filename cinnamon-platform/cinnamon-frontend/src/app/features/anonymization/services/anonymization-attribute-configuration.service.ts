import { Injectable } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ValidationErrors, Validators } from "@angular/forms";
import { List } from "@core/utils/list";
import { areEnumValuesEqual } from "@shared/helper/enum-helper";
import { DataScale } from "@shared/model/data-scale";
import { DataType } from "@shared/model/data-type";
import {
    AnonymizationAttributeRowConfiguration,
    AttributeProtection
} from 'src/app/shared/model/anonymization-attribute-config';

@Injectable({
    providedIn: 'root',
})
export class AnonymizationAttributeConfigurationService {

    /**
     * Key for the form group of this configuration component.
     */
    public readonly formGroupName = 'attributeConfiguration';

    public constructor(
        private readonly formBuilder: FormBuilder,
    ) {
    }

    /**
     * Determines the valid transformations that can be applied
     * to a given attribute configuration.
     *
     * @returns Array<AttributeProtection>
     */
    public getValidTransformationsForAttribute(scale: DataScale, dataType: DataType): List<String> {
        const transformations = [];

        if (areEnumValuesEqual(DataScale, scale, DataScale.DATE) ||
            areEnumValuesEqual(DataScale, scale, DataScale.NOMINAL) ||
            areEnumValuesEqual(DataScale, scale, DataScale.ORDINAL) ||
            areEnumValuesEqual(DataScale, scale, DataScale.INTERVAL) ||
            areEnumValuesEqual(DataScale, scale, DataScale.RATIO)
        ) {
            transformations.push(AttributeProtection.MASKING);
        }

        if (areEnumValuesEqual(DataScale, scale, DataScale.DATE)) {
            transformations.push(AttributeProtection.DATE_GENERALIZATION);
        }

        if (areEnumValuesEqual(DataScale, scale, DataScale.ORDINAL)) {
            transformations.push(AttributeProtection.GENERALIZATION);
        }

        if (areEnumValuesEqual(DataScale, scale, DataScale.RATIO) ||
            areEnumValuesEqual(DataScale, scale, DataScale.INTERVAL)
        ) {
            if (areEnumValuesEqual(DataType, dataType, DataType.DECIMAL) ||
                areEnumValuesEqual(DataType, dataType, DataType.INTEGER)
            ) {
                transformations.push(AttributeProtection.GENERALIZATION);
                transformations.push(AttributeProtection.MICRO_AGGREGATION);
            }
        }

        transformations.push(AttributeProtection.ATTRIBUTE_DELETION);
        transformations.push(AttributeProtection.RECORD_DELETION);
        transformations.push(AttributeProtection.NO_PROTECTION);
        // transformations.push(AttributeProtection.VALUE_DELETION); // Not supported ye

        return new List<String>(transformations);
    }

    /**
     * Returns the default interval settings for the given parameters.
     *
     * @param transformationType The attribute protection type.
     * @param scale The attribute scale.
     * @param type The attribute type.
     */
    public getIntervalSettings(transformationType: AttributeProtection | null, scale: DataScale, type: DataType): DefaultIntervalSettings {
        const fallback = {
            intervalMin: null,
            intervalMax: null,
            intervalInitialValue: null,
            intervallsSelect: false,
            deactivateInterval: false
        };

        let result: DefaultIntervalSettings | null = null;

        if (transformationType == null) {
            console.log("No transformation type set");
            return fallback;
        }

        if (transformationType === AttributeProtection.MASKING &&
            (scale === DataScale.DATE || scale === DataScale.NOMINAL || scale === DataScale.ORDINAL ||
                scale === DataScale.INTERVAL || scale === DataScale.RATIO)) {
            // MASKING -> [ 'DATE', 'NOMINAL', 'ORDINAL', 'INTERVAL', 'RATIO' ]
            result = {
                intervalMin: 2,
                intervalMax: 1000,
                intervalInitialValue: 3,
                intervallsSelect: false,
                deactivateInterval: false
            };
        } else if (transformationType === AttributeProtection.DATE_GENERALIZATION && scale === DataScale.DATE) {
            // DATE_GENERALIZATION -> DATE
            result = {
                intervalMin: null,
                intervalMax: null,
                intervalInitialValue: 'year',
                intervallsSelect: true,
                deactivateInterval: false
            };
        } else if (transformationType === AttributeProtection.GENERALIZATION ||
            transformationType === AttributeProtection.MICRO_AGGREGATION) {
            //[ 'GENERALIZATION', 'MICRO_AGGREGATION' ]
            if (scale === DataScale.ORDINAL) {
                // ORDINAL
                result = {
                    intervalMin: 1,
                    intervalMax: 100,
                    intervalInitialValue: 5,
                    intervallsSelect: false,
                    deactivateInterval: false
                };
            } else if (type === DataType.INTEGER && scale === DataScale.INTERVAL) {
                // INTEGER -> INTERVAL
                result = {
                    intervalMin: 1,
                    intervalMax: 1000,
                    intervalInitialValue: 10,
                    intervallsSelect: false,
                    deactivateInterval: false
                };
            } else if (type === DataType.DECIMAL && scale === DataScale.RATIO) {
                // DECIMAL -> RATIO
                result = {
                    intervalMin: 0.001,
                    intervalMax: 1000.0,
                    intervalInitialValue: 1,
                    intervallsSelect: false,
                    deactivateInterval: false
                };
            }
        } else if (transformationType === AttributeProtection.NO_PROTECTION ||
            transformationType === AttributeProtection.ATTRIBUTE_DELETION ||
            transformationType === AttributeProtection.VALUE_DELETION ||
            transformationType === AttributeProtection.RECORD_DELETION) {
            // [ 'ATTRIBUTE_DELETION', 'VALUE_DELETION']
            result = {
                intervalMin: null,
                intervalMax: null,
                intervalInitialValue: null,
                intervallsSelect: false,
                deactivateInterval: true
            };
        }

        if (result == null) {
            console.error("No handling for transformation type: " + transformationType + " and scale: " + scale + " and type: " + type + "");
        }
        return result ?? fallback;
    }

    public getDefaultAttributeProtection(scale: DataScale, dataType: DataType): AttributeProtection {
        const transformations = this.getValidTransformationsForAttribute(scale, dataType);
        if (transformations.contains(AttributeProtection.DATE_GENERALIZATION)) {
            return AttributeProtection.DATE_GENERALIZATION;
        } else if (transformations.contains(AttributeProtection.MICRO_AGGREGATION)) {
            return AttributeProtection.MICRO_AGGREGATION;
        } else {
            return AttributeProtection.ATTRIBUTE_DELETION;
        }
    }

    /**
     * Initializes the given form to be used by this component.
     * @param form The form to be initialized.
     * @param configs The initial configuration.
     * @param disabled If the form is disabled initially.
     */
    public initForm(form: FormGroup, configs: AnonymizationAttributeRowConfiguration[] | null, disabled: boolean): void {
        const controls = this.doSetValue(configs, disabled);
        form.addControl(this.formGroupName, new FormArray(controls, [Validators.required, AnonymizationAttributeConfigurationService.hasGeneralization]));
    }

    /**
     * Creates form groups for all attribute configurations.
     *
     * @param configs The attribute to be protected.
     * @param disabled If the form is disabled.
     */
    public doSetValue(configs: AnonymizationAttributeRowConfiguration[] | null, disabled: boolean): FormGroup[] {
        if (configs == null) {
            return [];
        }

        const controls: FormGroup[] = [];

        configs.forEach(config => {
            const defaults = this.getIntervalSettings(config.attributeProtection, config.scale, config.dataType);

            const group = this.formBuilder.group({
                attributeProtection: [{value: config.attributeProtection, disabled: disabled}, [Validators.required]],
                dataType: [{value: config.dataType, disabled: true}, [Validators.required]],
                index: [config.index, [Validators.required]],
                intervalSize: [{
                    value: config.intervalSize,
                    disabled: disabled || defaults.deactivateInterval
                }, [Validators.required]],
                name: [{value: config.name, disabled: true}, [Validators.required]],
                scale: [{value: config.scale, disabled: true}, [Validators.required]],
            });

            controls.push(group);
        });

        return controls;
    }

    /**
     * Custom validator to check if at least one attribute is protected by a generalization method.
     * An empty array is considered valid.
     * @param control The form array to validate.
     * @return Validation errors if no generalization protection is available, null otherwise.
     * @private
     */
    private static hasGeneralization(control: AbstractControl): ValidationErrors | null {
        if (!(control instanceof FormArray)) {
            return null;
        }

        if (control.controls.length === 0) {
            return null;
        }

        for (const row of control.controls) {
            const protection = (row as FormGroup).controls['attributeProtection'].value;
            if (AnonymizationAttributeConfigurationService.isGeneralization(protection)) {
                return null;
            }
        }

        return {noGeneralization: {}};
    }

    /**
     * Defines if the given attribute protection is generalization.
     *
     * @param protection The attribute protection.
     * @private
     */
    private static isGeneralization(protection: string) {
        return protection === AttributeProtection.MASKING ||
            protection === AttributeProtection.GENERALIZATION ||
            protection === AttributeProtection.MICRO_AGGREGATION ||
            protection === AttributeProtection.RECORD_DELETION ||
            protection === AttributeProtection.DATE_GENERALIZATION;
    }

}

export interface DefaultIntervalSettings {
    intervalMin: number | null,
    intervalMax: number | null,
    intervalInitialValue: number | string | null,
    intervallsSelect: boolean,
    deactivateInterval: boolean
}
