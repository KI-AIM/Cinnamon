import { List } from "src/app/core/utils/list";
import { DataType } from "./data-type";
import { DateFormatConfiguration } from "./date-format-configuration";
import { DateTimeFormatConfiguration } from "./date-time-format-configuration";
import { StringPatternConfiguration } from "./string-pattern-configuration";
import { Configuration } from "./configuration";
import { dataTypeFromString } from "./data-type";
import { Type } from "@angular/core";

export enum ConfigurationType {
	DATEFORMAT,
	DATETIMEFORMAT,
	STRINGPATTERN,
}

export function getConfigurationTypeForString(value: String): ConfigurationType | undefined {
    switch (value){
        case "DATEFORMAT": return ConfigurationType.DATEFORMAT
        case "DATETIMEFORMAT": return ConfigurationType.DATETIMEFORMAT
        case "STRINGPATTERN": return ConfigurationType.STRINGPATTERN
        default: return undefined; 
    }
}

export function getConfigurationTypeStringForIndex(index: number) {
    return ConfigurationType[index];
}


/**
 * Obejcts need to be complete
 * in order to perform indexing by type
 */
export const DataTypeToConfigurationTypeMapping: Record<
	DataType,
	ConfigurationType[]
> = {
	[DataType.BOOLEAN]: [],
	[DataType.DECIMAL]: [],
	[DataType.INTEGER]: [],
	[DataType.DATE]: [ConfigurationType.DATEFORMAT],
	[DataType.DATE_TIME]: [ConfigurationType.DATETIMEFORMAT],
	[DataType.STRING]: [ConfigurationType.STRINGPATTERN],
	[DataType.UNDEFINED]: [],
};

export function getConfigurationsForDatatype(
	type: String
): List<ConfigurationType> {
	var datatype = dataTypeFromString(type);
	return new List(DataTypeToConfigurationTypeMapping[datatype] || []);
}

export function getConfigurationForConfigurationType(type: ConfigurationType): String{
    switch (type) {
        case ConfigurationType.DATEFORMAT: return DateFormatConfiguration.name; 
        case ConfigurationType.DATETIMEFORMAT: return DateTimeFormatConfiguration.name; 
        case ConfigurationType.STRINGPATTERN: return StringPatternConfiguration.name; 
    }
}
