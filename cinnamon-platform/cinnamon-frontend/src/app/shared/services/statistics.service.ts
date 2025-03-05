import { Injectable } from '@angular/core';
import {environments} from "../../../environments/environment";
import {finalize, map, Observable, of, share, tap} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {Statistics, StatisticsData} from "../model/statistics";
import {parse} from "yaml";
import {plainToInstance} from "class-transformer";
import { DataType } from "../model/data-type";
import { RiskEvaluation } from '../model/risk-evaluation';

@Injectable({
  providedIn: 'root'
})
export class StatisticsService {
    private readonly baseUrl: string = environments.apiUrl + "/api/statistics";

    private _statistics: Statistics | null = null;
    private _statistics$: Observable<Statistics> | null = null;

    private readonly labels: Record<string, string> = {
        "anonymization": "Anonymized",
        "synthetization": "Synthesized",
    }

    public readonly colorDefinitions = [
        {
            name: 'Default',
            colors: [
                '#00aaff', '#007700', '#009900', '#00bb00',
                '#00dd00', '#00ff00', '#ff0000', '#dd0000',
                '#bb0000', '#990000', '#770000',
            ],
        },
        {
            name: 'Green Red',
            colors: [
                '#00aaff', '#00ff00', '#00dd00', '#00bb00',
                '#009900', '#007700', '#770000', '#990000',
                '#bb0000', '#dd0000', '#ff0000',
            ],
        },
        {
            name: 'Blue Green',
            colors: [
                '#00aaff', '#292f56', '#1e4572', '#005c8b',
                '#007498', '#008ba0', '#00a3a4', '#00bca1',
                '#00d493', '#69e882', '#acfa70',
            ]
        },
        {
            name: 'Green Blue',
            colors: [
                '#00aaff', '#acfa70', '#69e882', '#00d493',
                '#00bca1', '#00a3a4', '#008ba0', '#007498',
                '#005c8b', '#1e4572', '#292f56',
            ],
        },
        {
            name: 'Blue Red',
            colors: [
                '#00aaff', '#0c3d67', '#1f74b9', '#43b3e4',
                '#a7cee2', '#e8f4d9', '#fcf1ad', '#fbaa64',
                '#f48146', '#dc4230', '#a8172a',
            ]
        },
        {
            name: 'Red Blue',
            colors: [
                '#00aaff', '#a8172a', '#dc4230', '#f48146',
                '#fbaa64', '#fcf1ad', '#e8f4d9', '#a7cee2',
                '#43b3e4', '#1f74b9', '#0c3d67',
            ],
        },
        {
            name: 'Blue Yellow',
            colors: [
                '#00aaff', '#002f61', '#00507b', '#006e8e',
                '#008b98', '#00a79c', '#00c395', '#18dc82',
                '#71ee65', '#bbf942', '#ffff00',
            ]
        },
        {
            name: 'Yellow Blue',
            colors: [
                '#00aaff', '#ffff00', '#bbf942', '#71ee65',
                '#18dc82', '#00c395', '#00a79c', '#008b98',
                '#006e8e', '#00507b', '#002f61',
            ]
        },
        {
            name: 'Blue Lila Red',
            colors: [
                '#00aaff', '#3b48f7', '#6a38fb', '#882cf6',
                '#a023e9', '#b41fd6', '#c522bd', '#d329a2',
                '#de3284', '#e73d67', '#ed4a4a',
            ]
        },
        {
            name: 'Red Lila Blue',
            colors: [
                '#00aaff', '#ed4a4a', '#e73d67', '#de3284',
                '#d329a2', '#c522bd', '#b41fd6', '#a023e9',
                '#882cf6', '#6a38fb', '#3b48f7',
            ]
        },
        {
            name: 'BlueOrange1',
            colors: [
                '#00aaff', '#0008ff', '#284af7', '#4d68f8',
                '#6d7ffb', '#8a95ff', '#ff8800', '#db7409',
                '#b55f0b', '#8f4806', '#653001',
            ]
        },
        {
            name: 'BlueOrange2',
            colors: [
                '#00aaff', '#8a95ff', '#6d7ffb', '#4d68f8',
                '#284af7', '#0008ff', '#653001', '#8f4806',
                '#b55f0b', '#db7409', '#ff8800'
            ]
        },
        {
            name: 'GreenOrange1',
            colors: [
                '#00aaff', '#165f53', '#197665', '#1a8c79',
                '#14a289', '#00b899', '#ff8800', '#db7409',
                '#b55f0b', '#8f4806', '#653001',
            ]
        },
        {
            name: 'GreenOrange2',
            colors: [
                '#00aaff', '#00b899', '#14a289', '#1a8c79',
                '#197665', '#165f53', '#653001', '#8f4806',
                '#b55f0b', '#db7409', '#ff8800',
            ]
        },
        {
            name: 'Green1',
            colors: [
                '#00aaff', '#165f53', '#18695b', '#197363',
                '#1a7e6c', '#1a8874', '#19917c', '#179b84',
                '#13a58b', '#0cae92', '#00b899'
            ]
        },
        {
            name: 'Green2',
            colors: [
                '#00aaff', '#00b899', '#0cae92', '#13a58b',
                '#179b84', '#19917c', '#1a8874', '#1a7e6c',
                '#197363', '#18695b', '#165f53'
            ]
        },
        {
            name: 'Orange1',
            colors: [
                '#00aaff', '#653001', '#773b03', '#8a4606',
                '#9c5008', '#ad5a0b', '#bd640b', '#ce6d0a',
                '#df7608', '#ef7f05', '#ff8800'
            ]
        },
        {
            name: 'Orange2',
            colors: [
                '#00aaff', '#ff8800', '#ef7f05', '#df7608',
                '#ce6d0a', '#bd640b', '#ad5a0b', '#9c5008',
                '#8a4606', '#773b03', '#653001'
            ]
        },
    ];

    constructor(
        private readonly httpClient: HttpClient,
    ) {
    }

    public getColorScheme(name: string) {
        return this.colorDefinitions.find(value => value.name === name)?.colors ?? [];
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

    public fetchRisksString(): Observable<any> {
        return this.httpClient.get<any>(environments.apiUrl + `/api/project/resultFile`,
            {
                params: {
                    executionStepName: 'EVALUATION',
                    processStepName: 'RISK_EVALUATION',
                    name: 'risks.json',
                }
            });
    }

    public fetchRisks(): Observable<RiskEvaluation> {
        return this.httpClient.get<any>(environments.apiUrl + `/api/project/resultFile`,
            {
                params: {
                    executionStepName: 'EVALUATION',
                    processStepName: 'RISK_EVALUATION',
                    name: 'risks.json',
                }
            }).pipe(
                map(value =>plainToInstance(RiskEvaluation, JSON.parse(value)))
            );
    }

    public fetchRisks2(): Observable<any> {
        return this.httpClient.get<any>(environments.apiUrl + `/api/project/resultFile`,
            {
                params: {
                    executionStepName: 'EVALUATION',
                    processStepName: 'BASE_EVALUATION',
                    name: 'general_risks.json',
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
        const params = {
            selector: stepName.toLowerCase() === "validation" ? "ORIGINAL" : "JOB",
            jobName: stepName.toLowerCase(),
        }

        return this.httpClient.get<string>(this.baseUrl, {params: params, responseType: 'text' as 'json'})
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

    /**
     * Creates the name of the dataset based on the source of the dataset.
     * @param sourceDataset
     */
    public getOriginalName(sourceDataset: string | null): string {
        if (sourceDataset && Object.hasOwn(this.labels, sourceDataset)) {
            return this.labels[sourceDataset];
        }
        return "Original";
    }

    /**
     * Creates the name of the dataset based on the steps applied to the dataset.
     * @protected
     */
    public getSyntheticName(processingSteps: string[]): string {
        return processingSteps.map(value => this.labels[value]).join(" and ");
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
