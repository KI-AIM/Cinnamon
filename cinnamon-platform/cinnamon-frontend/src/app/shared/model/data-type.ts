import { ConfigurationType } from "@shared/model/configuration-types";

export enum DataType {
    BOOLEAN = "BOOLEAN",
	DATE_TIME = "DATE_TIME",
	DECIMAL = "DECIMAL",
	INTEGER = "INTEGER",
	STRING = "STRING",
	DATE = "DATE",
	UNDEFINED = "UNDEFINED",
}

/**
 * Metadata for data types.
 */
interface DataTypeAttributes {
    /**
     * List of additional configurations available for this data type.
     */
    availableConfigurationTypes: ConfigurationType[];
    /**
     * Name for displaying the type in the UI.
     */
    displayName: string;
}

/**
 * Additional metadata for each data type.
 */
export const DataTypeMetadata: Record<DataType, DataTypeAttributes> = {
    [DataType.BOOLEAN]: {
        availableConfigurationTypes: [],
        displayName: "Boolean",
    },
    [DataType.DATE_TIME]: {
        availableConfigurationTypes: [ConfigurationType.DATEFORMAT, ConfigurationType.RANGE],
        displayName: "Date & Time",
    },
    [DataType.DECIMAL]: {
        availableConfigurationTypes: [ConfigurationType.RANGE],
        displayName: "Decimal",
    },
    [DataType.INTEGER]: {
        availableConfigurationTypes: [ConfigurationType.RANGE],
        displayName: "Integer",
    },
    [DataType.STRING]: {
        availableConfigurationTypes: [ConfigurationType.STRINGPATTERN],
        displayName: "String",
    },
    [DataType.DATE]: {
        availableConfigurationTypes: [ConfigurationType.DATEFORMAT, ConfigurationType.RANGE],
        displayName: "Date",
    },
    [DataType.UNDEFINED]: {
        availableConfigurationTypes: [],
        displayName: "Undefined",
    }
};

export function dataTypeFromString(value: String): DataType {
    switch (value) {
        case "BOOLEAN": return DataType.BOOLEAN
        case "DATE_TIME": return DataType.DATE_TIME
        case "DECIMAL": return DataType.DECIMAL
        case "INTEGER": return DataType.INTEGER
        case "STRING": return DataType.STRING
        case "DATE": return DataType.DATE
        default: return DataType.UNDEFINED
    }
}
