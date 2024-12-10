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


    public readonly colors = [
        '#00aaff',
        '#007700',
        '#009900',
        '#00bb00',
        '#00dd00',
        '#00ff00',
        '#ff0000',
        '#dd0000',
        '#bb0000',
        '#990000',
        '#770000',
    ]

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

    public fetchResult(): Observable<Statistics> {
        return this.httpClient.get<string>(environments.apiUrl + `/api/project/resultFile`,
            {
                params: {
                    executionStepName: 'EVALUATION',
                    processStepName: 'TECHNICAL_EVALUATION',
                    name: 'metrics.json',
                }
            }).pipe(
            map(value => plainToInstance(Statistics, JSON.parse(value)))
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
     * @param options Format options
     */
    public formatNumber(value: number | string, options?: FormatNumberOptions): string {
        options ??= {};
        const {dataType = null, min = null, max = null, maximumFractionDigits} = options;

        // If data type is date and value is string, pipe original data
        if (dataType && (dataType === 'DATE' || dataType === 'DATE_TIME') && typeof value === 'string') {
            return value;
        }

        if (typeof value === "string") {
            const floatValue = parseFloat(value);
            if (isNaN(floatValue)) {
                return value;
            }
            value = floatValue;
        }

        // if (dataType) {
        //     if (dataType === 'DATE') {
        //         return new Date(value).toLocaleDateString();
        //     } else if (dataType === 'DATE_TIME') {
        //         return new Date(value).toLocaleString();
        //     }
        // }

        if (value === 0) {
            return '0';
        }

        const abs = Math.abs(value);
        if (max && abs > Math.pow(10, max)) {
            return value.toExponential(max)
        }

        if (min && abs < Math.pow(10, -min)) {
            return value.toExponential(min)
        }

        return value.toLocaleString(undefined, {maximumFractionDigits});
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

export interface FormatNumberOptions {
    dataType?: DataType | null;
    min?: number | null;
    max?: number | null;
    maximumFractionDigits?: number;
}
