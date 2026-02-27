import { DateFormatConfiguration } from "./date-format-configuration";
import { DateTimeFormatConfiguration } from "./date-time-format-configuration";
import { StringPatternConfiguration } from "./string-pattern-configuration";
import { RangeConfiguration } from "./range-configuration";
import { TextLanguageConfiguration } from "./text-language-configuration";
import { TextEncodingConfiguration } from "./text-encoding-configuration";

export enum ConfigurationType {
	DATEFORMAT = "DATEFORMAT",
	DATETIMEFORMAT = "DATETIMEFORMAT",
	RANGE = "RANGE",
	STRINGPATTERN = "STRINGPATTERN",
	TEXTLANGUAGE = "TEXTLANGUAGE",
	TEXTENCODING = "TEXTENCODING",
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
        case TextLanguageConfiguration.name:
            return ConfigurationType.TEXTLANGUAGE;
        case TextEncodingConfiguration.name:
            return ConfigurationType.TEXTENCODING;
        default:
                return null;
    }
}

/**
 * Metadata for data scales.
 */
interface ConfigurationTypeAttributes {
    displayName: string;
}

/**
 * Additional metadata for each data type.
 */
export const ConfigurationTypeMetadata: Record<ConfigurationType, ConfigurationTypeAttributes> = {
    [ConfigurationType.DATEFORMAT]: {
        displayName: "Date Format",
    },
    [ConfigurationType.DATETIMEFORMAT]: {
        displayName: "Date & Time Format",
    },
    [ConfigurationType.RANGE]: {
        displayName: "Range",
    },
    [ConfigurationType.STRINGPATTERN]: {
        displayName: "String Pattern",
    },
    [ConfigurationType.TEXTLANGUAGE]: {
        displayName: "Text Language",
    },
    [ConfigurationType.TEXTENCODING]: {
        displayName: "Text Encoding",
    },
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
		case ConfigurationType.TEXTLANGUAGE:
			return TextLanguageConfiguration.name;
		case ConfigurationType.TEXTENCODING:
			return TextEncodingConfiguration.name;
	}
}
