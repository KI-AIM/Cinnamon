import { Configuration } from "./configuration";

export class StringPatternConfiguration extends Configuration {
    /** Stable identifier; do not use the class name, which is mangled by minification. */
    static readonly NAME = "StringPatternConfiguration";

    name: String = StringPatternConfiguration.NAME;
    pattern: String
}
