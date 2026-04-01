import { ConfigurationImportSummary } from "@shared/model/import-pipe-data";
import { Type } from "class-transformer";

/**
 * Class representing an error response from the API.
 */
export class ErrorResponse {
    type: string;
    title: string;
    timestamp: Date;
    status: number;
    path: string;
    errorCode: string;
    errorMessage: string;

    @Type(() => ErrorDetails)
    errorDetails: ErrorDetails | null;
}

export class ErrorDetails {
    configurationName: string | null;
    configurationImportSummary?: ConfigurationImportSummary;
    validationErrors: Record<string, string[]> | null;
}
