import { Configuration } from "./configuration";

export class RangeConfiguration extends Configuration {
    /** Stable identifier; do not use the class name, which is mangled by minification. */
    static readonly NAME = "RangeConfiguration";

    name: String = RangeConfiguration.NAME;
    minValue: number | string;
    maxValue: number | string;

    override getName(): any {
        return this.name;
    }
}
