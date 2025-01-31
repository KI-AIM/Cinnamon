import {
    Component,
    EventEmitter,
    Input,
    OnInit,
    Output,
    ViewChild,
} from '@angular/core';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { FormControl, FormGroup, NgModel, Validators } from '@angular/forms';
import { List } from 'src/app/core/utils/list';
import {
    AnonymizationAttributeRowConfiguration,
    AttributeProtection,
} from 'src/app/shared/model/anonymization-attribute-config';
import { DataType } from 'src/app/shared/model/data-type';
import { DataScale } from 'src/app/shared/model/data-scale';
import { areEnumValuesEqual, getEnumKeysByValues, getEnumIndexForString, getEnumKeyByValue } from 'src/app/shared/helper/enum-helper';

type FormField = {
    name: string;
    disabled: boolean;
    value: any | null;
};

type FormElements = {
    name: FormField;
    scale: FormField;
    dataType: FormField;
    transformationType: FormField;
    interval: FormField;
};

@Component({
    selector: 'app-anonymization-attribute-row',
    templateUrl: './anonymization-attribute-row.component.html',
    styleUrls: ['./anonymization-attribute-row.component.less'],
})
export class AnonymizationAttributeRowComponent implements OnInit {
    @Input() configurationRow: ColumnConfiguration | null;
    @Input() anonymizationRowConfiguration: AnonymizationAttributeRowConfiguration;
    @Input() form: FormGroup;

    @Output() removeEvent = new EventEmitter<string>();

    DataType = DataType;
    AttributeProtection = AttributeProtection;
    DataScale = DataScale;
    getEnumIndexForString = getEnumIndexForString;
    getEnumKeyForValue = getEnumKeyByValue

    intervalMin: number | null = null;
    intervalMax: number | null = null;
    intervalInitialValue: number | null = null;
    intervalIsSelect = false; // To determine whether to render an input or select element

    validTransformations: List<String> | null = null;

    formElements: FormElements = {
        name: {
            name: "",
            disabled: true,
            value: null,
        },
        scale: {
            name: "",
            disabled: true,
            value: null,
        },
        dataType: {
            name: "",
            disabled: true,
            value: null,
        },
        transformationType: {
            name: "",
            disabled: false,
            value: null,
        },
        interval: {
            name: "",
            disabled: false,
            value: null,
        },
    }

    constructor() {
    }

    ngOnInit() {
        this.initParameterNames();
        this.initParameterValues();
        this.initFormControlElements();
        //If row is added and Generalization is available, set it
        if (this.anonymizationRowConfiguration.attributeProtection === null || this.anonymizationRowConfiguration.attributeProtection === undefined) {
            if (this.getValidTransformationsForAttribute().contains(AttributeProtection.DATE_GENERALIZATION)) {
                this.anonymizationRowConfiguration.attributeProtection = AttributeProtection.DATE_GENERALIZATION;
            } else if (this.getValidTransformationsForAttribute().contains(AttributeProtection.MICRO_AGGREGATION)) {
                this.anonymizationRowConfiguration.attributeProtection = AttributeProtection.MICRO_AGGREGATION;
            } else {
                this.anonymizationRowConfiguration.attributeProtection = AttributeProtection.ATTRIBUTE_DELETION;
            }
        }
        //Initialize the interval input field
        this.setIntervalConditions();
        console.log("initialProtection", this.anonymizationRowConfiguration.attributeProtection);
    }

    /**
     * Event that fires when clicking the remove icon.
     * Can be used to bubble up the event to a parent component
     */
    removeCurrentRow() {
        this.removeFormControlElements();
        this.removeEvent.emit('removeEvent');
    }

    /**
     * Manually triggers the update and
     * validity check of the form
     */
    updateForm() {
        this.form.updateValueAndValidity();
        console.log("on Update attribute protection", this.anonymizationRowConfiguration.attributeProtection);
    }

    /**
     * Returns all DataType instances
     * To be used in a select element
     * @returns
     */
    getAllTypes() {
        const types = Object.keys(DataType).filter((x) => !(parseInt(x) >= 0));

        return new List<String>(types);
    }

    /**
     * Returns all valid AttributeProtection instances
     * To be used in a select element
     * @returns
     */
    getTransformations(): List<String> {
        return this.getValidTransformationsForAttribute();
    }

    /**
     * Returns all DataScale instances
     * To be used in a select element
     * @returns
     */
    getAllDataScales() {
        const scales = Object.keys(DataScale).filter(
            (x) => !(parseInt(x) >= 0)
        );

        return new List<String>(scales);
    }


    getValidTransformationsForAttribute(): List<String> {
        if (!this.validTransformations) {
            this.setValidTransformationsForAttribute();
        }
        return this.validTransformations!!;
    }


    /**
     * Determines the valid transformations that can be applied
     * to a given attribute configuration.
     *
     * @returns Array<AttributeProtection>
     */
    private setValidTransformationsForAttribute() {
        var transformations = [];

        if (areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.DATE) ||
            areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.NOMINAL) ||
            areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.ORDINAL) ||
            areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.INTERVAL) ||
            areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.RATIO)
        ) {
            transformations.push(AttributeProtection.MASKING);
        }

        if (areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.DATE)) {
            transformations.push(AttributeProtection.DATE_GENERALIZATION);
        }

        if (areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.ORDINAL)) {
            transformations.push(AttributeProtection.GENERALIZATION);
        }

        if (areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.RATIO) ||
            areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.INTERVAL)
        ) {
            if (areEnumValuesEqual(DataType, this.anonymizationRowConfiguration.dataType, DataType.DECIMAL) ||
                areEnumValuesEqual(DataType, this.anonymizationRowConfiguration.dataType, DataType.INTEGER)
            ) {
                transformations.push(AttributeProtection.GENERALIZATION);
                transformations.push(AttributeProtection.MICRO_AGGREGATION);
            }
        }

        transformations.push(AttributeProtection.ATTRIBUTE_DELETION);
        transformations.push(AttributeProtection.RECORD_DELETION);
        transformations.push(AttributeProtection.NO_PROTECTION);
        // transformations.push(AttributeProtection.VALUE_DELETION); // Not supported ye

        this.validTransformations = new List<String>(transformations);
    }


    /**
     * Returns the valid options for a DATE_TRANSFORMATION
     * @returns List<String>
     */
    getIntervalEnumOptions(): List<String> {
        return new List<String>([
            "week/year",
            "month/year",
            "quarter/year",
            "year",
            "decade",
        ]);
    }

    /**
     * Function to adjust the interval input field
     * depending on the current settings in the frontend
     */
    setIntervalConditions() {
        if (this.anonymizationRowConfiguration.attributeProtection !== null) {
            // MASKING -> [ 'DATE', 'NOMINAL', 'ORDNIAL', 'INTERVAL', 'RATIO' ]
            if (areEnumValuesEqual(AttributeProtection, this.anonymizationRowConfiguration.attributeProtection, AttributeProtection.MASKING) &&
                (areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.DATE) ||
                areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.NOMINAL) ||
                areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.ORDINAL) ||
                areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.INTERVAL) ||
                areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.RATIO))
            ) {
                this.changeIntervalSettings(2, 1000, 3, false, false);
            }
            // DATE_GENERALIZATION -> DATE
            else if (areEnumValuesEqual(AttributeProtection, this.anonymizationRowConfiguration.attributeProtection, AttributeProtection.DATE_GENERALIZATION) &&
                    areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.DATE)
            ) {
                this.changeIntervalSettings(null, null, 'year', true, false);
            }
            //[ 'GENERALIZATION', 'MICRO_AGGREGATION' ]
            else if (areEnumValuesEqual(AttributeProtection, this.anonymizationRowConfiguration.attributeProtection, AttributeProtection.GENERALIZATION) ||
                    areEnumValuesEqual(AttributeProtection, this.anonymizationRowConfiguration.attributeProtection, AttributeProtection.MICRO_AGGREGATION)
            ) {
                // ORDINAL
                if (areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.attributeProtection, DataScale.ORDINAL)) {
                    this.changeIntervalSettings(1, 100, 5, false, false);
                }
                // INTEGER -> INTEVAL
                else if (areEnumValuesEqual(DataType, this.anonymizationRowConfiguration.dataType, DataType.INTEGER) &&
                        areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.INTERVAL)
                ) {
                    this.changeIntervalSettings(1, 1000, 10, false, false);
                }
                // DECIMAL -> RATIO
                else if (areEnumValuesEqual(DataType, this.anonymizationRowConfiguration.dataType, DataType.DECIMAL) &&
                        areEnumValuesEqual(DataScale, this.anonymizationRowConfiguration.scale, DataScale.RATIO)
                ) {
                    this.changeIntervalSettings(0.001, 1000.0, 1, false, false);
                }
            }
            // [ 'ATTRIBUTE_DELETION', 'VALUE_DELETION']
            else if (areEnumValuesEqual(AttributeProtection, this.anonymizationRowConfiguration.attributeProtection, AttributeProtection.ATTRIBUTE_DELETION) ||
                    areEnumValuesEqual(AttributeProtection, this.anonymizationRowConfiguration.attributeProtection, AttributeProtection.VALUE_DELETION) ||
                    areEnumValuesEqual(AttributeProtection, this.anonymizationRowConfiguration.attributeProtection, AttributeProtection.RECORD_DELETION) ||
                    areEnumValuesEqual(AttributeProtection, this.anonymizationRowConfiguration.attributeProtection, AttributeProtection.NO_PROTECTION)
            ) {
                this.changeIntervalSettings(null, null, null, false, true);
            }
            // SET FALLBACK VALUES
            else {
                this.changeIntervalSettings(null, null, null, false, false);
            }
        }
    }

    /**
     * Helper function to adjust the interval settings
     * that are used by the frontend
     * @param intervalMin min input for a number input
     * @param intervalMax max input for a number input
     * @param intervalInitialValue Initial value for the input field
     * @param intervalIsSelect If true, toggles the select input in the frontend. If false a number input is shown
     * @param deactivateInterval If true, the input field will be disabled
     */
    changeIntervalSettings(
        intervalMin: number | null ,
        intervalMax: number | null,
        intervalInitialValue: number | string | null,
        intervalIsSelect: boolean,
        deactivateInterval: boolean
    ) {
        this.intervalMin = intervalMin;
        this.intervalMax = intervalMax;
        this.intervalIsSelect = intervalIsSelect;
        this.anonymizationRowConfiguration.intervalSize = intervalInitialValue;
        this.toggleIntervalField(deactivateInterval);
    }

    /**
     * Helper function to init the names of the
     * form control elements by using the index of the
     * configuration row
     */
    initParameterNames() {
        this.formElements.name.name = this.configurationRow?.index + "_name";
        this.formElements.dataType.name = this.configurationRow?.index + "_dataType";
        this.formElements.scale.name = this.configurationRow?.index + "_scale";
        this.formElements.transformationType.name = this.configurationRow?.index + "_transformationType";
        this.formElements.interval.name = this.configurationRow?.index + "_interval";
    }

    /**
     * Helper function to init the values of the
     * form control elements by using the values of the
     * anonymization attribute row config
     */
    initParameterValues() {
        this.formElements.name.value = this.anonymizationRowConfiguration.name;
        this.formElements.dataType.value = this.anonymizationRowConfiguration.dataType;
        this.formElements.scale.value = this.anonymizationRowConfiguration.scale;
        this.formElements.transformationType.value = this.anonymizationRowConfiguration.attributeProtection;
        this.formElements.interval.value = this.anonymizationRowConfiguration.intervalSize;
    }

    /**
     * Helper function to init the form control elements
     * Automatically adds validators for required, min and max
     * and disables the element if configured in the formElements
     * object
     */
    initFormControlElements() {

        Object.values(this.formElements).forEach(element => {
            let validators = [];

            validators.push(Validators.required);

            if (this.intervalMin !== null) {
                validators.push(Validators.min(this.intervalMin));
            }
            if (this.intervalMax !== null) {
                validators.push(Validators.max(this.intervalMax));
            }

            this.form.controls[element.name] = new FormControl({value: element.value, disabled: element.disabled}, validators)
            this.form.controls[element.name].markAsTouched();
        });
    }

    /**
     * Helper function to remove all FormControl elements
     * for this row. To be used when removing the row
     */
    removeFormControlElements() {
        Object.values(this.formElements).forEach(element => {
            this.form.removeControl(element.name);
        });
    }

    /**
     * Helper function to trigger the interval fields
     * "enabled" attribute, by chaning the FormControl element
     * @param disable: boolean - if true disables the element; enables it otherwise
     */
    toggleIntervalField(disable: boolean) {
        if (disable) {
            this.disableIntervalField();
        } else {
            this.enableIntervalField();
        }
    }

    /**
     * Helper function to enable the interval field
     */
    enableIntervalField() {
        this.form.controls[this.formElements.interval.name].enable();
    }

    /**
     * Helper function to disable the interval field
     */
    disableIntervalField() {
        this.form.controls[this.formElements.interval.name].disable();
    }

    /**
     * Function to check if the input field is valid
     * @param name of the input field (must match to FormControl name)
     * @returns true if valid; false otherwise
     */
    isElementValid(name: string) {
        return !this.form.controls[name].invalid;
    }

}
