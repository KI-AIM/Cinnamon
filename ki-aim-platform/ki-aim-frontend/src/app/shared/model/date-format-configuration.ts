import { Configuration } from "./configuration";

export class DateFormatConfiguration extends Configuration {
    name: String = "DateFormatConfiguration";
    dateFormatter: String;


    override getName(): any {
        return this.name;
    }
}
