import { Injectable } from '@angular/core';
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { HttpErrorResponse } from "@angular/common/http";
import { ErrorDetails, ErrorResponse } from "../model/error-response";
import { plainToInstance } from "class-transformer";
import { UserService } from "./user.service";

/**
 * Service for central error handling.
 * Provides a subject for listening to errors.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class ErrorHandlingService {

    constructor(
        private readonly notificationService: NotificationService,
        private readonly userService: UserService,
    ) {
    }

    /**
     * Adds a notification for the given error.
     *
     * @param error The new error.
     * @param message Additional message.
     */
    public addError(error: ErrorResponse | HttpErrorResponse | string | any, message?: string) {
        if (error == null) {
            console.error("Error is null!");
            return;
        }

        let errorMessage = null;

        if (typeof error === 'string') {
            errorMessage = error;
        } else if (error instanceof HttpErrorResponse) {
            errorMessage = this.handleHttpErrorResponse(error, message);
        }

        if (!errorMessage) {
            console.error(error);
            errorMessage = "An unexpected error occurred. Please contact the administrator.";
        }

        this.notificationService.addNotification(new AppNotification(errorMessage, "failure"));
    }

    private handleHttpErrorResponse(response: HttpErrorResponse, message?: string): string {
        if (response.status != null) {
            if (response.status === 0) {
                if (message != null) {
                    return message;
                } else {
                    return "Cinnamon is currently unavailable. Please try again later.";
                }

            } else if (response.status === 401) {
                this.userService.logout('expired');
                return "Session expired. Please log in again.";
            } else if (response.status === 504) {
                return "The API at " + response.url + " could not be reached";
            }
        }

        if (this.isJsonString(response.error)) {
            const errorResponse = plainToInstance(ErrorResponse, JSON.parse(response.error));
            return this.handleErrorResponse(errorResponse);
        } else if(typeof response.error === 'object') {
            const errorResponse = plainToInstance(ErrorResponse, response.error);
            return this.handleErrorResponse(errorResponse);
        } else {
            return response.error;
        }
    }

    private handleErrorResponse(error: ErrorResponse): string {
        let errorMessage = "";

        if (error.errorCode === 'PLATFORM_3_2_1') {
            if (error.errorDetails?.validationErrors == null) {
                console.error("Validation error details are null!");
                return "Request validation failed.";
            }

            for (const [field, errors] of Object.entries(error.errorDetails?.validationErrors)) {
                const parts = field.split(".");
                if (parts.length === 3) {

                    // Data configuration
                    if (parts[0] === "configuration" && parts[1].startsWith("configurations")) {
                        const index = parseInt(parts[1].split("[")[1].split("]")[0]);
                        errorMessage += "The " + parts[2] + " of the " + (index + 1) + ". attribute has the following errors:\n";
                        for (const error of errors) {
                            errorMessage += "- " + error + "\n";
                        }
                    }
                } else {
                    errorMessage += (errors as string[]).join(", ") + "\n";
                }
            }

        } else if (error.errorCode === 'PLATFORM_2_4_4') {
            errorMessage = `Failed to fetch available algorithms for the ${this.getConfigurationDisplayName(error.errorDetails)}. You can skip this step or try again later.`;
        } else if (error.errorCode === 'PLATFORM_2_4_5') {
            errorMessage = `Failed to fetch the configuration definition for the ${this.getConfigurationDisplayName(error.errorDetails)}. You can skip this step or try again later.`;
        } else {
            errorMessage = error.errorMessage;
        }

        return errorMessage;
    }

    private wrapErrorMessage(errorMessage: string) {
        return "<div class='pre-wrapper'><pre>" + errorMessage + "</pre></div>";
    }

    private isJsonString(str: string) {
        try {
            JSON.parse(str);
        } catch (e) {
            return false;
        }
        return true;
    }

    private readonly configurationNames: Record<string, string> = {
        "anonymization": "anonymization configuration",
        "synthetization_configuration": "synthetization configuration",
        "evaluation_configuration": "technical evaluation configuration",
    }

    private getConfigurationDisplayName(errorDetails: ErrorDetails | null): string {
        if (errorDetails == null || errorDetails.configurationName == null) {
            console.error("No configuration name available!");
            return "configuration";
        }

        const configurationName = errorDetails.configurationName;

        if (this.configurationNames[configurationName]) {
            return this.configurationNames[configurationName];
        } else {
            console.error("No display name available for configuration: " + configurationName);
            return configurationName;
        }
    }

}
