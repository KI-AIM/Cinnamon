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
        const defaults = this.anonConfigService.getIntervalSettings(this.getTransformationType(), this.getScale(), this.getDataType());
        this.intervalMin = defaults.intervalMin;
        this.intervalMax = defaults.intervalMax;
        this.intervalIsSelect = defaults.intervallsSelect;
    }

    public ngOnChanges(changes: SimpleChanges): void {
        if (changes['disabled'] != null && !changes['disabled'].firstChange) {
            const disabled = changes['disabled'].currentValue;
            if (disabled)  {
                this.form.controls["attributeProtection"].disable();
                this.form.controls["intervalSize"].disable();
            } else {
                this.form.controls["attributeProtection"].enable();

                const intervalSettings = this.anonConfigService.getIntervalSettings(this.getTransformationType(), this.getScale(), this.getDataType());
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
        const intervalSettings = this.anonConfigService.getIntervalSettings(this.getTransformationType(), this.getScale(), this.getDataType());
        this.changeIntervalSettings(intervalSettings.intervalMin, intervalSettings.intervalMax, intervalSettings.intervalInitialValue, intervalSettings.intervallsSelect, intervalSettings.deactivateInterval);
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
