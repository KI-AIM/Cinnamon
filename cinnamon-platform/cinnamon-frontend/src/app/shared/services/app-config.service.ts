import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { catchError, finalize, Observable, of, share, tap } from "rxjs";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root'
})
export class AppConfigService {
    private readonly baseURL = environments.apiUrl;

    private _appConfig: AppConfig| null = null;
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
            share(),
            catchError((e) => {
                this.errorHandlingService.addError(e, "Cinnamon is currently unavailable. Please try again later.");
                return of({isDemoInstance: false, maxFileSize: 0});
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
    maxFileSize: number;
}
