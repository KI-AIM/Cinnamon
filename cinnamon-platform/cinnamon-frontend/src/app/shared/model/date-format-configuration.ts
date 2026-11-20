import { Configuration } from "./configuration";

export class DateFormatConfiguration extends Configuration {
    /** Stable identifier; do not use the class name, which is mangled by minification. */
    static readonly NAME = "DateFormatConfiguration";

    name: String = DateFormatConfiguration.NAME;
    dateFormatter: String;


    override getName(): any {
        return this.name;
    }
}
