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
    /**
     * If the user can select this type.
     */
    selectable: boolean;
}

/**
 * Additional metadata for each data type.
 */
export const DataTypeMetadata: Record<DataType, DataTypeAttributes> = {
    [DataType.BOOLEAN]: {
        displayName: "Boolean",
        selectable: true,
    },
    [DataType.DATE_TIME]: {
        displayName: "Date & Time",
        selectable: true,
    },
    [DataType.DECIMAL]: {
        displayName: "Decimal",
        selectable: true,
    },
    [DataType.INTEGER]: {
        displayName: "Integer",
        selectable: true,
    },
    [DataType.STRING]: {
        displayName: "String",
        selectable: true,
    },
    [DataType.DATE]: {
        displayName: "Date",
        selectable: true,
    },
    [DataType.UNDEFINED]: {
        displayName: "Undefined",
        selectable: false,
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
