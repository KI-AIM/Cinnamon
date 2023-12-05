export enum DataType {
    BOOLEAN,
	DATE_TIME,
	DECIMAL,
	INTEGER,
	STRING,
	DATE,
	UNDEFINED
}

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