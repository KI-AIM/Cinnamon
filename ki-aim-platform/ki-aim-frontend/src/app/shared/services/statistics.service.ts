import { Injectable } from '@angular/core';
import {environments} from "../../../environments/environment";
import {finalize, map, Observable, of, share, tap} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {Statistics} from "../model/statistics";
import {parse} from "yaml";
import {plainToInstance} from "class-transformer";

@Injectable({
  providedIn: 'root'
})
export class StatisticsService {
    private readonly baseUrl: string = environments.apiUrl + "/api/statistics";

    private _statistics: Statistics | null = null;
    private _statistics$: Observable<Statistics> | null = null;

    constructor(
        private readonly httpClient: HttpClient,
    ) {
    }

    public get statistics$(): Observable<Statistics> {
        if (this._statistics) {
            return of(this._statistics);
        }
        if (this._statistics$) {
            return this._statistics$;
        }

        return this.fetchStatistics().pipe(
            tap(value => this._statistics = value),
            share(),
            finalize(() => {
                this._statistics$ = null;
            })
        );
    }

    public invalidateCache() {
        this._statistics = null;
        this._statistics$ = null;
    }

    private fetchStatistics(): Observable<Statistics> {
        return this.httpClient.get<string>(this.baseUrl, {responseType: 'text' as 'json'})
            .pipe(
                map(value => {
                    return plainToInstance(Statistics, parse( parse(value)));
                }),
            );
    }
}
