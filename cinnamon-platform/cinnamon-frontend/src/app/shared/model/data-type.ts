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
     * Name for displaying the type in the UI.
     */
    displayName: string;
}

/**
 * Additional metadata for each data type.
 */
export const DataTypeMetadata: Record<DataType, DataTypeAttributes> = {
    [DataType.BOOLEAN]: {
        displayName: "Boolean",
    },
    [DataType.DATE_TIME]: {
        displayName: "Date & Time",
    },
    [DataType.DECIMAL]: {
        displayName: "Decimal",
    },
    [DataType.INTEGER]: {
        displayName: "Integer",
    },
    [DataType.STRING]: {
        displayName: "String",
    },
    [DataType.DATE]: {
        displayName: "Date",
    },
    [DataType.UNDEFINED]: {
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
