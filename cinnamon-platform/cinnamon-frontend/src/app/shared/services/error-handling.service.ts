import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from "rxjs";
import { HttpErrorResponse } from "@angular/common/http";
import { ErrorResponse } from "../model/error-response";
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

    private errorSubject: BehaviorSubject<string | null>;

    constructor(
        private readonly userService: UserService,
    ) {
        this.errorSubject = new BehaviorSubject<string | null>(null);
    }

    /**
     * Observable for listening to errors.
     * `null` value means there is currently no error.
     *
     * @return Error message or null.
     */
    public get error$(): Observable<string | null> {
        return this.errorSubject.asObservable();
    }

    /**
     * Sets the current error.
     * Triggers an update of the subject.
     * `null` value is valid and indicates that the error is resolved.
     *
     * @param error The new error.
     * @param message Additional message.
     */
    public setError(error: ErrorResponse | HttpErrorResponse | string | any, message?: string) {
        // TODO(DPM) Is this a good idea?
        if (error == null) {
            this.clearError();
            return;
        }

        if (typeof error === 'string') {
            this.errorSubject.next(error);
            return;
        }
        if (error instanceof HttpErrorResponse) {
            this.handleHttpErrorResponse(error, message);
            return;
        }

        this.errorSubject.next("An unexpected error occurred. Please contact the administrator.");
    }

    /**
     * Clears the current error.
     */
    public clearError(): void {
        this.errorSubject.next(null);
    }

    private handleHttpErrorResponse(response: HttpErrorResponse, message?: string): void {
        if (response.status != null) {
            if (response.status === 0) {
                if (message != null) {
                    this.errorSubject.next(message);
                } else {
                    this.errorSubject.next("Cinnamon is currently unavailable. Please try again later.");
                }

                return;
            } else if (response.status === 401) {
                this.userService.invalidate();
                return;
            } else if (response.status === 504) {
                this.errorSubject.next("The API at " + response.url + " could not be reached");
                return;
            }
        }

        if (typeof response.error === 'string') {
            this.errorSubject.next(response.error);
            return;
        } else {
            const errorResponse = plainToInstance(ErrorResponse, response.error);
            this.handleErrorResponse(errorResponse);
            return;
        }
    }

    private handleErrorResponse(error: ErrorResponse): void {
        let errorMessage = "";

        if (error.errorCode === 'PLATFORM_3_2_1') {
            for (const [field, errors] of Object.entries(error.errorDetails)) {
                const parts = field.split(".");
                if (parts.length === 3) {
                } else {
                    console.log(errorMessage);
                    errorMessage += (errors as string[]).join(", ") + "\n";
                    console.log(errors);
                }
            }

        } else {
            errorMessage = error.errorMessage;
        }

        this.errorSubject.next(errorMessage);
    }

    private wrapErrorMessage(errorMessage: string) {
        return "<div class='pre-wrapper'><pre>" + errorMessage + "</pre></div>";
    }

}
