import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { List } from 'src/app/core/utils/list';
import { AttributeProtection, } from 'src/app/shared/model/anonymization-attribute-config';
import { DataType } from 'src/app/shared/model/data-type';
import { DataScale } from 'src/app/shared/model/data-scale';
import { areEnumValuesEqual } from 'src/app/shared/helper/enum-helper';
import {
    AnonymizationAttributeConfigurationService
} from "../../services/anonymization-attribute-configuration.service";

@Component({
    selector: 'app-anonymization-attribute-row',
    templateUrl: './anonymization-attribute-row.component.html',
    styleUrls: ['./anonymization-attribute-row.component.less'],
    standalone: false
})
export class AnonymizationAttributeRowComponent implements OnInit, OnChanges {
    @Input() disabled!: boolean;
    @Input() form: FormGroup;
    @Input() parentForm: FormGroup;

    @Output() removeEvent = new EventEmitter<string>();

    DataType = DataType;
    DataScale = DataScale;

    intervalMin: number | null = null;
    intervalMax: number | null = null;
    intervalIsSelect = false; // To determine whether to render an input or select element

    validTransformations: List<String> | null = null;

    constructor(
        private anonConfigService: AnonymizationAttributeConfigurationService,
    ) {
    }

    ngOnInit() {
        // Initialize the interval input field
        this.intervalIsSelect = this.getTransformationType() === AttributeProtection.DATE_GENERALIZATION;
    }

    public ngOnChanges(changes: SimpleChanges): void {
        if (changes['disabled'] != null && !changes['disabled'].firstChange) {
            const disabled = changes['disabled'].currentValue;
            if (disabled)  {
                this.form.controls["attributeProtection"].disable();
                this.form.controls["intervalSize"].disable();
            } else {
                this.form.controls["attributeProtection"].enable();

                const intervalSettings = this.getIntervalSettings();
                if (!intervalSettings.deactivateInterval) {
                    this.form.controls["intervalSize"].enable();
                }
            }

        }
    }

    /**
     * Event that fires when clicking the remove icon.
     * Can be used to bubble up the event to a parent component
     */
    removeCurrentRow() {
        this.removeEvent.emit('removeEvent');
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
            this.validTransformations = this.anonConfigService.getValidTransformationsForAttribute(this.getScale(), this.getDataType());
        }
        return this.validTransformations;
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
     * Returns the current data type.
     * @protected
     */
    protected getDataType(): DataType {
        return this.form.controls["dataType"].value;
    }

    /**
     * Returns the current data scale.
     * @protected
     */
    protected getScale(): DataScale {
        return this.form.controls["scale"].value;
    }

    /**
     * Returns the current transformation type/ attribute protection.
     * @protected
     */
    protected getTransformationType(): AttributeProtection {
        return this.form.controls["attributeProtection"].value;
    }

    /**
     * Sets the value of the interval size.
     * @param value The new value.
     * @protected
     */
    protected setIntervalSize(value: string | number | null): void {
        this.form.controls["intervalSize"].setValue(value);
    }

    /**
     * Function to adjust the interval input field
     * depending on the current settings in the frontend
     */
    setIntervalConditions() {
        if (this.getTransformationType() !== null) {
            // MASKING -> [ 'DATE', 'NOMINAL', 'ORDNIAL', 'INTERVAL', 'RATIO' ]
            if (areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.MASKING) &&
                (areEnumValuesEqual(DataScale, this.getScale(), DataScale.DATE) ||
                areEnumValuesEqual(DataScale, this.getScale(), DataScale.NOMINAL) ||
                areEnumValuesEqual(DataScale, this.getScale(), DataScale.ORDINAL) ||
                areEnumValuesEqual(DataScale, this.getScale(), DataScale.INTERVAL) ||
                areEnumValuesEqual(DataScale, this.getScale(), DataScale.RATIO))
            ) {
                this.changeIntervalSettings(2, 1000, 3, false, false);
            }
            // DATE_GENERALIZATION -> DATE
            else if (areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.DATE_GENERALIZATION) &&
                    areEnumValuesEqual(DataScale, this.getScale(), DataScale.DATE)
            ) {
                this.changeIntervalSettings(null, null, 'year', true, false);
            }
            //[ 'GENERALIZATION', 'MICRO_AGGREGATION' ]
            else if (areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.GENERALIZATION) ||
                    areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.MICRO_AGGREGATION)
            ) {
                // ORDINAL
                if (areEnumValuesEqual(DataScale, this.getTransformationType(), DataScale.ORDINAL)) {
                    this.changeIntervalSettings(1, 100, 5, false, false);
                }
                // INTEGER -> INTEVAL
                else if (areEnumValuesEqual(DataType, this.getDataType(), DataType.INTEGER) &&
                        areEnumValuesEqual(DataScale, this.getScale(), DataScale.INTERVAL)
                ) {
                    this.changeIntervalSettings(1, 1000, 10, false, false);
                }
                // DECIMAL -> RATIO
                else if (areEnumValuesEqual(DataType, this.getDataType(), DataType.DECIMAL) &&
                        areEnumValuesEqual(DataScale, this.getScale(), DataScale.RATIO)
                ) {
                    this.changeIntervalSettings(0.001, 1000.0, 1, false, false);
                }
            }
            // [ 'ATTRIBUTE_DELETION', 'VALUE_DELETION']
            else if (areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.ATTRIBUTE_DELETION) ||
                    areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.VALUE_DELETION) ||
                    areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.RECORD_DELETION) ||
                    areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.NO_PROTECTION)
            ) {
                this.changeIntervalSettings(null, null, null, false, true);
            }
            // SET FALLBACK VALUES
            else {
                this.changeIntervalSettings(null, null, null, false, false);
            }
        }
    }

    private getIntervalSettings(): {
        intervalMin: number | null,
        intervalMax: number | null,
        intervalInitialValue: number | string | null,
        intervallsSelect: boolean,
        deactivateInterval: boolean
    } {
        if (this.getTransformationType() !== null) {
            // MASKING -> [ 'DATE', 'NOMINAL', 'ORDNIAL', 'INTERVAL', 'RATIO' ]
            if (areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.MASKING) &&
                (areEnumValuesEqual(DataScale, this.getScale(), DataScale.DATE) ||
                    areEnumValuesEqual(DataScale, this.getScale(), DataScale.NOMINAL) ||
                    areEnumValuesEqual(DataScale, this.getScale(), DataScale.ORDINAL) ||
                    areEnumValuesEqual(DataScale, this.getScale(), DataScale.INTERVAL) ||
                    areEnumValuesEqual(DataScale, this.getScale(), DataScale.RATIO))
            ) {
                return {
                    intervalMin: 2,
                    intervalMax: 1000,
                    intervalInitialValue: 3,
                    intervallsSelect: false,
                    deactivateInterval: false
                };
            }
            // DATE_GENERALIZATION -> DATE
            else if (areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.DATE_GENERALIZATION) &&
                areEnumValuesEqual(DataScale, this.getScale(), DataScale.DATE)
            ) {
                return {
                    intervalMin: null,
                    intervalMax: null,
                    intervalInitialValue: 'year',
                    intervallsSelect: true,
                    deactivateInterval: false
                };
            }
            //[ 'GENERALIZATION', 'MICRO_AGGREGATION' ]
            else if (areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.GENERALIZATION) ||
                areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.MICRO_AGGREGATION)
            ) {
                // ORDINAL
                if (areEnumValuesEqual(DataScale, this.getTransformationType(), DataScale.ORDINAL)) {
                    return {
                        intervalMin: 1,
                        intervalMax: 100,
                        intervalInitialValue: 5,
                        intervallsSelect: false,
                        deactivateInterval: false
                    };
                }
                // INTEGER -> INTEVAL
                else if (areEnumValuesEqual(DataType, this.getDataType(), DataType.INTEGER) &&
                    areEnumValuesEqual(DataScale, this.getScale(), DataScale.INTERVAL)
                ) {
                    return {
                        intervalMin: 1,
                        intervalMax: 1000,
                        intervalInitialValue: 10,
                        intervallsSelect: false,
                        deactivateInterval: false
                    };
                }
                // DECIMAL -> RATIO
                else if (areEnumValuesEqual(DataType, this.getDataType(), DataType.DECIMAL) &&
                    areEnumValuesEqual(DataScale, this.getScale(), DataScale.RATIO)
                ) {
                    return {
                        intervalMin: 0.001,
                        intervalMax: 1000.0,
                        intervalInitialValue: 1,
                        intervallsSelect: false,
                        deactivateInterval: false
                    };
                }
            }
            // [ 'ATTRIBUTE_DELETION', 'VALUE_DELETION']
            else if (areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.ATTRIBUTE_DELETION) ||
                areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.VALUE_DELETION) ||
                areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.RECORD_DELETION) ||
                areEnumValuesEqual(AttributeProtection, this.getTransformationType(), AttributeProtection.NO_PROTECTION)
            ) {
                return {
                    intervalMin: null,
                    intervalMax: null,
                    intervalInitialValue: null,
                    intervallsSelect: false,
                    deactivateInterval: true
                };
            }
            // SET FALLBACK VALUES
            else {
                return {
                    intervalMin: null,
                    intervalMax: null,
                    intervalInitialValue: null,
                    intervallsSelect: false,
                    deactivateInterval: false
                };
            }
        }

        console.log("No transformation type set");
        return {
            intervalMin: null,
            intervalMax: null,
            intervalInitialValue: null,
            intervallsSelect: false,
            deactivateInterval: false
        };
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
        this.setIntervalSize(intervalInitialValue);
        this.toggleIntervalField(deactivateInterval);
    }

    /**
     * Helper function to trigger the interval fields
     * "enabled" attribute, by changing the FormControl element
     * @param disable if true disables the element; enables it otherwise
     */
    toggleIntervalField(disable: boolean) {
        if (this.disabled) {
            return;
        }

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
        this.form.controls["intervalSize"].enable();
    }

    /**
     * Helper function to disable the interval field
     */
    disableIntervalField() {
        this.form.controls["intervalSize"].disable();
    }

}
