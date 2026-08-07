import { ColumnConfiguration } from "./column-configuration"
import { Type } from "class-transformer";
import { DataType } from "./data-type";

export class DataConfiguration {
    @Type(() => ColumnConfiguration)
    configurations: ColumnConfiguration[] = [];

    addColumnConfiguration(columnConfiguration: ColumnConfiguration) {
        this.configurations.push(columnConfiguration);
    }

}

export class DataConfigurationEstimation {
    @Type(() => DataConfiguration)
    dataConfiguration: DataConfiguration;

    confidences: number[];
}

export function hasTextColumns(dataConfiguration: DataConfiguration): boolean {
    return dataConfiguration.configurations.some(configuration => configuration.type === DataType.TEXT);
}

export function hasStructuredColumns(dataConfiguration: DataConfiguration): boolean {
    return dataConfiguration.configurations.some(configuration => configuration.type !== DataType.TEXT);
}

export function isTextOnlyDataConfiguration(dataConfiguration: DataConfiguration): boolean {
    return hasTextColumns(dataConfiguration) && !hasStructuredColumns(dataConfiguration);
}

export function isStructuredOnlyDataConfiguration(dataConfiguration: DataConfiguration): boolean {
    return hasStructuredColumns(dataConfiguration) && !hasTextColumns(dataConfiguration);
}

export function isMixedDataConfiguration(dataConfiguration: DataConfiguration): boolean {
    return hasStructuredColumns(dataConfiguration) && hasTextColumns(dataConfiguration);
}
