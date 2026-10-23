import { HttpClient } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { BehaviorSubject, catchError, filter, finalize, map, Observable, of, shareReplay, switchMap, tap } from "rxjs";
import { ErrorHandlingService } from "src/app/shared/services/error-handling.service";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root'
})
export class AppConfigService {
    private readonly baseURL = environments.apiUrl;

    private readonly appConfigSubject = new BehaviorSubject<AppConfig | null>(null);
    /**
     * If the current value of {@link appConfigSubject} has been successfully fetched from the backend.
     */
    private isLoaded = false;
    /**
     * Currently running request, used to avoid parallel requests.
     */
    private fetchRequest$: Observable<AppConfig> | null = null;

    constructor(
        private readonly http: HttpClient,
        private readonly errorHandlingService: ErrorHandlingService,
    ) {
    }

    /**
     * Returns an observable for the app config that emits updates on changes, e.g. after {@link fetchAppConfig} has been called.
     * Fetches the app config from the backend on the first subscription.
     */
    public get appConfig$(): Observable<AppConfig> {
        if (!this.isLoaded) {
            this.fetchAppConfig().subscribe();
        }

        return this.appConfigSubject.pipe(
            filter((value): value is AppConfig => value !== null),
        );
    }

    /**
     * Returns the current cached app config.
     * Null if the app config has not been fetched before.
     *
     * @return Cached app config or null.
     */
    public get appConfig(): AppConfig | null {
        return this.appConfigSubject.value;
    }

    /**
     * Fetches the app config from the backend and emits the new value to all subscribers of {@link appConfig$}.
     * If the request fails and no app config has been loaded yet, a fallback config is emitted.
     *
     * @return Observable emitting the current app config.
     */
    public fetchAppConfig(): Observable<AppConfig> {
        if (this.fetchRequest$) {
            return this.fetchRequest$;
        }

        this.fetchRequest$ = this.http.get<AppConfig>(this.baseURL + "/config.json").pipe(
            tap(() => {
                this.isLoaded = true;
            }),
            catchError((e) => {
                this.errorHandlingService.addError(e, "Cinnamon is currently unavailable. Please try again later.");
                return of(this.appConfigSubject.value ?? this.fallbackAppConfig());
            }),
            tap(value => {
                this.appConfigSubject.next(value);
            }),
            finalize(() => {
                this.fetchRequest$ = null;
            }),
            shareReplay(1),
        );

        return this.fetchRequest$;
    }

    /**
     * Updates the imprint and refreshes the app config afterward.
     *
     * @param newImprint The new imprint.
     */
    public updateImprint(newImprint: string | null): Observable<void> {
        return this.http.put<void>(this.baseURL + "/api/admin/settings/imprint", newImprint).pipe(
            switchMap(() => this.fetchAppConfig()),
            map(() => undefined),
        );
    }

    private fallbackAppConfig(): AppConfig {
        return {
            imprint: null,
            isDemoInstance: false,
            isInvitationRequired: false,
            maxFileSize: 0,
            passwordRequirements: {
                minLength: 0,
                constraints: [],
            },
            version: 'unknown',
        };
    }
}

export interface AppConfig {
    imprint: string | null;
    isDemoInstance: boolean;
    isInvitationRequired: boolean;
    maxFileSize: number;
    passwordRequirements: PasswordRequirements;
    version: string;
}

export interface PasswordRequirements {
    minLength: number;
    constraints: PasswordConstraint[];
}

export type PasswordConstraint = 'LOWERCASE' | 'DIGIT' | 'SPECIAL_CHAR' | 'UPPERCASE';
