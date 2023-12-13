import { CsvFileConfiguration } from "./csv-file-configuration";

export class FileConfiguration {
    constructor(
        public fileType: string,
        public csvFileConfiguration: CsvFileConfiguration,
    ) {}
}
