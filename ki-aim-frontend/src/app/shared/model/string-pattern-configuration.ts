import { Configuration } from "./configuration";

export class StringPatternConfiguration extends Configuration {
    name: String = this.getName();
    pattern: String
}
