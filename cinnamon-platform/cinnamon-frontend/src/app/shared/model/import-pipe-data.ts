export type ConfigurationImportStatus = "SUCCESS" | "PARTIAL_ERROR" | "ERROR";
export type ConfigurationImportPartStatus = "SUCCESS" | "IGNORED" | "ERROR";

export class ConfigurationImportParameters {
    allowPartialImport: boolean;
    configurationsToImport: string[] | null;
}

export class ConfigurationImportSummaryPart {
    configurationName: string;
    status: ConfigurationImportPartStatus;
    errorCode: string | null;

    configuration: Object | string | null;
}

export class ConfigurationImportSummary {
    parameters: ConfigurationImportParameters;
    status: ConfigurationImportStatus;
    configurationImportSummaries: ConfigurationImportSummaryPart[];
}
