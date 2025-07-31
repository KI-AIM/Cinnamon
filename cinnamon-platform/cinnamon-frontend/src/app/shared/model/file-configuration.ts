import { CsvFileConfiguration } from "./csv-file-configuration";
import { XlsxFileConfiguration } from "./xlsx-file-configuration";

export enum FileType {
	CSV = "CSV",
    FHIR = "FHIR",
    XLSX = "XLSX",
}

export class FileConfiguration {
    constructor(
        public fileType: FileType | null,
        public csvFileConfiguration: CsvFileConfiguration,
        public xlsxFileConfiguration: XlsxFileConfiguration,
    ) {}
}
