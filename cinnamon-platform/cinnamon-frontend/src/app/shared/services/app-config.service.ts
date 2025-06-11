import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { ErrorHandlingService } from "src/app/shared/services/error-handling.service";
import { environments } from "src/environments/environment";
import { catchError, finalize, Observable, of, shareReplay, tap } from "rxjs";

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
                    passwordRequirements: {
                        minLength: 0,
                        constraints: [],
                    },
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

export interface AppConfig {
    isDemoInstance: boolean;
    passwordRequirements: PasswordRequirements;
}

export interface PasswordRequirements {
    minLength: number;
    constraints: PasswordConstraint[];
}

export type PasswordConstraint = 'LOWERCASE' | 'DIGIT' | 'SPECIAL_CHAR' | 'UPPERCASE';
