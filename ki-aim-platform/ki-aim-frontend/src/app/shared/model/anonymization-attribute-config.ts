import { DataScale } from './data-scale';
import { DataType } from './data-type';

export enum AttributeProtection {
    ATTRIBUTE_DELETION = "ATTRIBUTE_DELETION",
    GENERALIZATION = "GENERALIZATION",
    MICRO_AGGREGATION = "MICRO_AGGREGATION",
    DATE_GENERALIZATION = "DATE_GENERALIZATION",
    VALUE_DELETION = "VALUE_DELETION",
    MASKING = "MASKING",
}

export class AnonymizationAttributeConfiguration {
    attributeConfiguration: AnonymizationAttributeRowConfiguration[];
}

export class AnonymizationAttributeRowConfiguration {
    index: number;
    name: String;
    dataType: DataType;
    scale: DataScale;
    attributeProtection: AttributeProtection | null = null;
    intervalSize: string | number | null;
}
