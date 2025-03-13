import { Injectable } from '@angular/core';
import { environments } from "../../../environments/environment";
import { HttpClient } from "@angular/common/http";
import { catchError, finalize, Observable, of, share, tap } from "rxjs";

@Injectable({
    providedIn: 'root'
})
export class AppConfigService {
    private readonly baseURL = environments.apiUrl;

    private _appConfig: AppConfig| null = null;
    private _appConfig$: Observable<AppConfig> | null = null;

    constructor(
        private readonly http: HttpClient,
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
            share(),
            catchError(() => {
                return of({isCinnamonUnavailable: true, isDemoInstance: false});
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
    isCinnamonUnavailable?: boolean;
    isDemoInstance: boolean;
}
