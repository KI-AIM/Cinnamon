import { Configuration } from "./configuration";

export class TextLanguageConfiguration extends Configuration {
    /** Stable identifier; do not use the class name, which is mangled by minification. */
    static readonly NAME = "TextLanguageConfiguration";

    name: String = TextLanguageConfiguration.NAME;
    language: String;
}
