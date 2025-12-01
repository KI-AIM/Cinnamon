import { Injectable } from '@angular/core';
import { AttributeProtection } from 'src/app/shared/model/anonymization-attribute-config';
import { List } from "../../../core/utils/list";
import { areEnumValuesEqual } from "../../../shared/helper/enum-helper";
import { DataScale } from "../../../shared/model/data-scale";
import { DataType } from "../../../shared/model/data-type";

@Injectable({
    providedIn: 'root',
})
export class AnonymizationAttributeConfigurationService {

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

}

export interface DefaultIntervalSettings {
    intervalMin: number | null,
    intervalMax: number | null,
    intervalInitialValue: number | string | null,
    intervallsSelect: boolean,
    deactivateInterval: boolean
}
