import { Configuration } from "./configuration";

export class DateTimeFormatConfiguration extends Configuration {
    /** Stable identifier; do not use the class name, which is mangled by minification. */
    static readonly NAME = "DateTimeFormatConfiguration";

    name: String = DateTimeFormatConfiguration.NAME;
    dateTimeFormatter: String
}
