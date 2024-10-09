import {
    Component,
    EventEmitter,
    Input,
    OnInit,
    Output,
    ViewChild,
} from '@angular/core';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { NgModel } from '@angular/forms';
import { List } from 'src/app/core/utils/list';
import {
    AnonymizationAttributeRowConfiguration,
    AttributeProtection,
} from 'src/app/shared/model/anonymization-attribute-config';
import { DataType } from 'src/app/shared/model/data-type';
import { DataScale } from 'src/app/shared/model/data-scale';
import { areEnumValuesEqual, getEnumKeysByValues, getEnumIndexForString, getEnumKeyByValue } from 'src/app/shared/helper/enum-helper';

@Component({
    selector: 'app-anonymization-attribute-row',
    templateUrl: './anonymization-attribute-row.component.html',
    styleUrls: ['./anonymization-attribute-row.component.less'],
})
export class AnonymizationAttributeRowComponent implements OnInit {
    @Input() configurationRow: ColumnConfiguration | null;
    @Input() anonymizationRowConfiguration: AnonymizationAttributeRowConfiguration;

    @Output() removeEvent = new EventEmitter<string>();

    @ViewChild('name') nameInput: NgModel;

    DataType = DataType;
    AttributeProtection = AttributeProtection;
    DataScale = DataScale;
    getEnumIndexForString = getEnumIndexForString;
    getEnumKeyForValue = getEnumKeyByValue

    constructor() {}

    ngOnInit() {
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



    /**
     * Determines the valid transformations that can be applied
     * to a given attribute configuration.
     *
     * @returns Array<AttributeProtection>
     */
    private getValidTransformationsForAttribute(): List<String> {
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

        return new List<String>(transformations);
    }

    intervalMin: number | null = null;
    intervalMax: number | null = null;
    intervalInitialValue: number | null = null;
    intervalIsSelect = false; // To determine whether to render an input or select element
    deactivateInterval = false;

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
        this.deactivateInterval = deactivateInterval;
    }

}
