import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from "rxjs";
import { HttpErrorResponse } from "@angular/common/http";
import { ErrorResponse } from "../model/error-response";

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

    constructor() {
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
    public setError(error: ErrorResponse | HttpErrorResponse | any, message?: string) {
        if (error == null) {
            this.clearError();
        }

        if (error.status != null) {
            if (error.status === 0) {
                this.errorSubject.next("Cinnamon is currently unavailable. Please try again later.");
            }
        }
    }

    /**
     * Clears the current error.
     */
    public clearError(): void {
        this.errorSubject.next(null);
    }

}
