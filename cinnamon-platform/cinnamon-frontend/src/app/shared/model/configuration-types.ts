import { List } from "src/app/core/utils/list";
import { DataType } from "./data-type";
import { DateFormatConfiguration } from "./date-format-configuration";
import { DateTimeFormatConfiguration } from "./date-time-format-configuration";
import { StringPatternConfiguration } from "./string-pattern-configuration";
import { RangeConfiguration } from "./range-configuration";

export enum ConfigurationType {
	DATEFORMAT = "DATEFORMAT",
	DATETIMEFORMAT = "DATETIMEFORMAT",
	RANGE = "RANGE",
	STRINGPATTERN = "STRINGPATTERN",
}

export function getConfigurationTypeForString(
	value: String
): ConfigurationType | undefined {
	switch (value) {
		case "DATEFORMAT":
			return ConfigurationType.DATEFORMAT;
		case "DATETIMEFORMAT":
			return ConfigurationType.DATETIMEFORMAT;
		case "RANGE":
			return ConfigurationType.RANGE;
		case "STRINGPATTERN":
			return ConfigurationType.STRINGPATTERN;
		default:
			return undefined;
	}
}

/**
 * Returns the configuration type for the given configuration name.
 * @param configurationName The name of the configuration.
 */
export function getConfigurationTypeForConfigurationName(configurationName: string): ConfigurationType | null {
    switch (configurationName) {
        case DateFormatConfiguration.name:
            return ConfigurationType.DATEFORMAT;
        case DateTimeFormatConfiguration.name:
            return ConfigurationType.DATETIMEFORMAT;
        case RangeConfiguration.name:
            return ConfigurationType.RANGE;
        case StringPatternConfiguration.name:
            return ConfigurationType.STRINGPATTERN;
        default:
                return null;
    }
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
	[DataType.DECIMAL]: [ConfigurationType.RANGE],
	[DataType.INTEGER]: [ConfigurationType.RANGE],
	[DataType.DATE]: [ConfigurationType.DATEFORMAT, ConfigurationType.RANGE],
	[DataType.DATE_TIME]: [ConfigurationType.DATETIMEFORMAT, ConfigurationType.RANGE],
	[DataType.STRING]: [ConfigurationType.STRINGPATTERN],
	[DataType.UNDEFINED]: [],
};

export function getConfigurationsForDatatype(
	type: DataType
): List<ConfigurationType> {
	return new List(DataTypeToConfigurationTypeMapping[type] || []);
}

export function getConfigurationForConfigurationType(
	type: ConfigurationType
): String {
	switch (type) {
		case ConfigurationType.DATEFORMAT:
			return DateFormatConfiguration.name;
		case ConfigurationType.DATETIMEFORMAT:
			return DateTimeFormatConfiguration.name;
		case ConfigurationType.RANGE:
			return RangeConfiguration.name;
		case ConfigurationType.STRINGPATTERN:
			return StringPatternConfiguration.name;
	}
}
