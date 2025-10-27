import { DateFormatConfiguration } from "@shared/model/date-format-configuration";
import { DateTimeFormatConfiguration } from "@shared/model/date-time-format-configuration";
import { RangeConfiguration } from "@shared/model/range-configuration";
import { StringPatternConfiguration } from "@shared/model/string-pattern-configuration";
import { TransformFnParams } from "class-transformer/types/interfaces";
import { DataType } from "./data-type";
import { Configuration } from "./configuration";
import { Type, Transform, plainToInstance } from "class-transformer";
import { DataScale } from "./data-scale";

export class ColumnConfiguration {
    index: number;
    name: string;
    type: DataType;
    scale: DataScale;

    @Transform(transformConfigurations, { toClassOnly: true })
    @Type(() => Configuration)
    configurations: Configuration[] = [];

    addConfiguration(configuration: Configuration) {
        this.configurations.push(configuration);
    }

    removeConfiguration(configuration: Configuration) {
        var index = this.configurations.indexOf(configuration, 0);
        if (index > -1) {
            this.configurations.splice(index, 1);
        }
    }

}

/**
 * Transforms an array of {@link Configuration} objects into the concrete instance.
 *
 * @param params Transform function parameter.
 */
function transformConfigurations(params: TransformFnParams): Configuration[] {
    const result = [] as Configuration[];

    if (!params.value) {
        return result;
    }

    for (const obj of params.value) {
        const name = obj.getName() as string;
        switch (name) {
            case DateFormatConfiguration.name:
                result.push(plainToInstance(DateFormatConfiguration, obj));
                break;
            case DateTimeFormatConfiguration.name:
                result.push(plainToInstance(DateTimeFormatConfiguration, obj));
                break;
            case RangeConfiguration.name:
                result.push(plainToInstance(RangeConfiguration, obj));
                break;
            case StringPatternConfiguration.name:
                result.push(plainToInstance(StringPatternConfiguration, obj));
                break;
            default:
                console.error("Unhandled configuration type: " + name);
                result.push(obj);
        }
    }

    return result;
}
