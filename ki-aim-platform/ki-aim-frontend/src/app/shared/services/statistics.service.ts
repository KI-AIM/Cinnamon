import { Injectable } from '@angular/core';
import {environments} from "../../../environments/environment";
import {finalize, map, Observable, of, share, tap} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {Statistics, StatisticsData} from "../model/statistics";
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

    public get statistics$(): Observable<Statistics | null> {
        if (this._statistics) {
            return of(this._statistics);
        }
        if (this._statistics$) {
            return this._statistics$;
        }

        return this.fetchStatistics("VALIDATION").pipe(
            tap(value => this._statistics = value),
            share(),
            finalize(() => {
                this._statistics$ = null;
            })
        );
    }

    // TODO use statistics endpoint
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

    public fetchRisks(): Observable<any> {
        return this.httpClient.get<any>(environments.apiUrl + `/api/project/resultFile`,
            {
                params: {
                    executionStepName: 'EVALUATION',
                    processStepName: 'RISK_EVALUATION',
                    name: 'risks.json',
                }
            });
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
    public formatNumber(value: number | string | null | undefined, options?: FormatNumberOptions): string {
        options ??= {};
        const {dataType = null, min = null, max = null, maximumFractionDigits} = options;

        // If data type is date and value is string, pipe original data
        if (dataType && (dataType === 'DATE' || dataType === 'DATE_TIME') && typeof value === 'string') {
            return value;
        }

        if (value == null) {
            return 'N/A';
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

        let numberValue = '';
        if (value === 0) {
            numberValue = '0';
        } else {
            const abs = Math.abs(value);
            if (max && abs > Math.pow(10, max)) {
                numberValue = value.toExponential(max)
            } else if (min && abs < Math.pow(10, -min)) {
                numberValue = value.toExponential(min)
            }

            numberValue = value.toLocaleString(undefined, {maximumFractionDigits});
        }

        if (options.unit) {
            return numberValue + ' ' + options.unit;
        } else {
            return numberValue;
        }
    }

    public fetchStatistics(stepName: string): Observable<Statistics | null> {
        return this.httpClient.get<string>(this.baseUrl + "/" + stepName, {responseType: 'text' as 'json'})
            .pipe(
                map(value => {
                    if (value) {
                        return plainToInstance(Statistics, parse(parse(value)));
                    } else {
                        return null;
                    }
                }),
            );
    }

    public getValue(data: StatisticsData<any>, which : 'real' | 'synthetic'): number | string {
        const type = typeof data[which];

        if (type === "number" || type === "string") {
            return data[which];
        } else {
            return this.getComplexValue(data[which]);
        }
    }

    protected getComplexValue(complex: any): number | string {
        for (const [key, value] of Object.entries(complex)) {
            if (key !== 'color_index') {
                return value as number | string;
            }
        }

        return "N/A";
    }

}

export interface FormatNumberOptions {
    dataType?: DataType | null;
    min?: number | null;
    max?: number | null;
    maximumFractionDigits?: number;
    unit?: string;
}
