import { CsvFileConfiguration } from "./csv-file-configuration";
import { XlsxFileConfiguration } from "./xlsx-file-configuration";

export enum FileType {
	CSV = "CSV",
    XLSX = "XLSX",
}

export class FileConfiguration {
    constructor(
        public fileType: FileType,
        public csvFileConfiguration: CsvFileConfiguration,
        public xlsxFileConfiguration: XlsxFileConfiguration,
    ) {}
}
