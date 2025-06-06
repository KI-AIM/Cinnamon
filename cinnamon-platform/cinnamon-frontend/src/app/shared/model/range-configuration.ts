import { Configuration } from "./configuration";

export class RangeConfiguration extends Configuration {
    name: String = "RangeConfiguration";
    minValue: number | string;
    maxValue: number | string;

    override getName(): any {
        return this.name;
    }
}
