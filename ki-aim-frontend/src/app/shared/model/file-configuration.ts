import { CsvFileConfiguration } from "./csv-file-configuration";

export enum FileType {
	CSV = "CSV",
}

export class FileConfiguration {
    constructor(
        public fileType: FileType,
        public csvFileConfiguration: CsvFileConfiguration,
    ) {}
}
