/**
 * Class representing an error response from the API.
 */
export class ErrorResponse {
    timestamp: Date;
    status: number;
    path: string;
    errorCode: string;
    errorMessage: string;
    errorDetails: object;
}
