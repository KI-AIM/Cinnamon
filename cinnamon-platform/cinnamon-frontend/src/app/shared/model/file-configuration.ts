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
        public fhirFileConfiguration: FhirFileConfiguration,
    ) {}
}

/**
 * File configuration for FHIR bundles.
 */
export class FhirFileConfiguration {
    constructor(
        /**
         * The resource type to export from the bundle.
         */
        public resourceType: string,
    ) {
    }
}

/**
 * Result of the file configuration estimation.
 */
export class FileConfigurationEstimation {
    /**
     * The estimated file configuration.
     */
    estimation: FileConfiguration;

    /**
     * If the estimated file type is FHIR, contains all resource types in the given FHIR bundle.
     * Otherwise, the value is null.
     */
    fhirResourceTypes: string[] | null;
}
