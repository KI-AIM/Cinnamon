import { DataSourceType, FileType } from "./file-configuration";

export class FileInformation {
    name: string | null;
    type: FileType | null;

    dataSourceType: DataSourceType | null;

    /**
     * If the file contains a FHIR bundle, contains all resource types in the bundle.
     * Otherwise, the value is null.
     */
    fhirResourceTypes: string[] | null;
}
