import { Injectable } from '@angular/core';
import {environments} from "../../../environments/environment";
import {finalize, map, Observable, of, share, tap} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {Statistics} from "../model/statistics";
import {parse} from "yaml";
import {plainToInstance} from "class-transformer";
import { DataType } from "../model/data-type";

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

    /**
     * Formats a number of milliseconds to a date.
     * @param value Number of milliseconds
     * @param type The date as a string.
     */
    public formatDate(value: number, type: DataType) {
        if (type === 'DATE') {
            return new Date(value).toLocaleDateString();
        } else {
            return new Date(value).toLocaleString();
        }
    }

    /**
     * Formats a number.
     * @param value The number to be formatted.
     * @param dataType Number as string.
     */
    public formatNumber(value: number | string, dataType?: DataType | null): string {
        if (typeof value === "string") {
            value = parseFloat(value);
        }

        if (dataType) {
            if (dataType === 'DATE') {
                return new Date(value).toLocaleDateString();
            } else if (dataType === 'DATE_TIME') {
                return new Date(value).toLocaleString();
            }
        }

        if (value === 0) {
            return '0';
        }

        const abs = Math.abs(value);
        if (abs > 1000 || abs < 0.001) {
            return value.toExponential(3)
        }
        return value.toFixed(3);
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
