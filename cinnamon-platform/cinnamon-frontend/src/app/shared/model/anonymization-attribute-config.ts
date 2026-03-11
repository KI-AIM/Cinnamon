import { DataScale } from './data-scale';
import { DataType } from './data-type';

/**
 * Defines types that fields inside the configuration object can have.
 */
export type ConfigurationObjectType =
    string
    | String
    | number
    | boolean
    | ConfigurationObject
    | ConfigurationObject[]
    | null;

/**
 * Generic structure definition of the configuration objects.
 * Other classes can extend this class for specifying concrete fields.
 */
export class ConfigurationObject {
    /**
     * Configuration object can contain any key names as they are defined in the algorithm definition.
     */
    [parameterName: string]: ConfigurationObjectType;
}

export enum AttributeProtection {
    ATTRIBUTE_DELETION = "ATTRIBUTE_DELETION",
    GENERALIZATION = "GENERALIZATION",
    MICRO_AGGREGATION = "MICRO_AGGREGATION",
    DATE_GENERALIZATION = "DATE_GENERALIZATION",
    VALUE_DELETION = "VALUE_DELETION",
    RECORD_DELETION = "RECORD_DELETION",
    MASKING = "MASKING",
    NO_PROTECTION = "NO_PROTECTION",
}

/**
 * Metadata for protection types.
 */
interface AttributeProtectionAttributes {
    /**
     * Name for displaying the protection type in the UI.
     */
    displayName: string;
}

/**
 *
 */
export const AttributeProtectionMetadata: Record<AttributeProtection, AttributeProtectionAttributes> = {
    [AttributeProtection.ATTRIBUTE_DELETION]: {displayName: "Attribute Deletion"},
    [AttributeProtection.DATE_GENERALIZATION]: {displayName: "Date Generalization"},
    [AttributeProtection.GENERALIZATION]: {displayName: "Generalization"},
    [AttributeProtection.MICRO_AGGREGATION]: {displayName: "Micro Aggregation"},
    [AttributeProtection.MASKING]: {displayName: "Masking"},
    [AttributeProtection.NO_PROTECTION]: {displayName: "No Protection"},
    [AttributeProtection.RECORD_DELETION]: {displayName: "Record Deletion"},
    [AttributeProtection.VALUE_DELETION]: {displayName: "Value Deletion"},
}

export class AnonymizationAttributeRowConfiguration extends ConfigurationObject {
    index: number;
    name: String;
    dataType: DataType;
    scale: DataScale;
    attributeProtection: AttributeProtection | null = null;
    intervalSize: string | number | null;
}
