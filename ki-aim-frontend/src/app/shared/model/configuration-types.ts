import { DataType } from "./data-type"
import { DateFormatConfiguration } from "./date-format-configuration"
import { DateTimeFormatConfiguration } from "./date-time-format-configuration"
import { StringPatternConfiguration } from "./string-pattern-configuration"

export enum ConfigurationTypes {
    DATEFORMAT,
    DATETIMEFORMAT,
    STRINGPATTERN
}

export const DataTypeToConfigurationTypeMapping = {
    [DataType.DATE] : [
        ConfigurationTypes.DATEFORMAT,
    ],
    [DataType.DATE_TIME]: [
        ConfigurationTypes.DATETIMEFORMAT
    ],
    [DataType.STRING]: [
        ConfigurationTypes.STRINGPATTERN
    ]
}

export const DataTypeToConfigurationClassMapping = {
    [DataType.DATE] : [
        DateFormatConfiguration
    ],
    [DataType.DATE_TIME]: [
        DateTimeFormatConfiguration
    ],
    [DataType.STRING]: [
        StringPatternConfiguration
    ]
}
