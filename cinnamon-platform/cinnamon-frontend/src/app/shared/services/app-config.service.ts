import { HttpClient } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { catchError, finalize, Observable, of, shareReplay, tap } from "rxjs";
import { ErrorHandlingService } from "src/app/shared/services/error-handling.service";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root'
})
export class AppConfigService {
    private readonly baseURL = environments.apiUrl;

    private _appConfig: AppConfig | null = null;
    private _appConfig$: Observable<AppConfig> | null = null;

    constructor(
        private readonly http: HttpClient,
        private readonly errorHandlingService: ErrorHandlingService,
    ) {
    }

    public get appConfig$(): Observable<AppConfig> {
        if (this._appConfig) {
            return of(this._appConfig);
        }
        if (this._appConfig$) {
            return this._appConfig$;
        }

        const ac = this.http.get<AppConfig>(this.baseURL + "/config.json").pipe(
            tap(value => {
                this._appConfig = value;
            }),
            shareReplay(1),
            catchError((e) => {
                this.errorHandlingService.addError(e, "Cinnamon is currently unavailable. Please try again later.");
                return of({
                    isDemoInstance: false,
                    isInvitationRequired: false,
                    maxFileSize: 0,
                    passwordRequirements: {
                        minLength: 0,
                        constraints: [],
                    },
                    version: 'unknown',
                    statusRequestInterval: DEFAULT_STATUS_REQUEST_INTERVAL,
                });
            }),
            finalize(() => {
                this._appConfig$ = null;
            }),
        );

        this._appConfig$ = ac;

        return ac;
    }
}

/**
 * Default interval in milliseconds in which the status of running processes is requested.
 */
export const DEFAULT_STATUS_REQUEST_INTERVAL = 2000;

export interface AppConfig {
    isDemoInstance: boolean;
    isInvitationRequired: boolean;
    maxFileSize: number;
    passwordRequirements: PasswordRequirements;
    version: string;
    /**
     * Interval in milliseconds in which the status of running processes is requested.
     */
    statusRequestInterval: number;
}

export interface PasswordRequirements {
    minLength: number;
    constraints: PasswordConstraint[];
}

export type PasswordConstraint = 'LOWERCASE' | 'DIGIT' | 'SPECIAL_CHAR' | 'UPPERCASE';
