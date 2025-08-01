import { ColumnConfiguration } from "./column-configuration"
import { Type } from "class-transformer";

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
