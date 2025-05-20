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
