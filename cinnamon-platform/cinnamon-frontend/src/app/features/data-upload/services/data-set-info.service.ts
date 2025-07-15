import {Injectable} from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { plainToInstance } from "class-transformer";
import { finalize, map, Observable, of, share, tap } from "rxjs";
import {DataSetInfo} from "../../../shared/model/data-set-info";
import {environments} from "../../../../environments/environment";

@Injectable({
    providedIn: 'root'
})
export class DataSetInfoService {
    private cache: Record<string, {
        dateSetInfo: DataSetInfo | null,
        dataSetInfo$: Observable<DataSetInfo> | null
    }> = {};

    constructor(private readonly http: HttpClient) {
    }

    /**
     * Returns the information to the original dataset
     */
    public getDataSetInfoOriginal$(): Observable<DataSetInfo> {
        return this.getDataSetInfo("validation");
    }

    /**
     * Returns the information to the dataset of the given step.
     * @param step The step of the data set or 'protected'.
     */
    public getDataSetInfo(step: string): Observable<DataSetInfo> {
        const dataSetInfo = this.cache[step]?.dateSetInfo;
        if (dataSetInfo) {
            return of(dataSetInfo);
        }

        const observable = this.cache[step]?.dataSetInfo$;
        if (observable) {
            return observable;
        }

        const params = {
            selector: step.toLowerCase() === "validation"
                ? "ORIGINAL"
                : step.toLowerCase() === "protected"
                    ? "protected"
                    : "JOB",
            jobName: step.toLowerCase(),
        }
        const dataSetInfo$ = this.http.get<DataSetInfo>(environments.apiUrl + "/api/data/info", {params: params}).pipe(
            map(value => {
                return plainToInstance(DataSetInfo, value);
            }),
            tap(value => {
                this.cache[step] = {dateSetInfo: value, dataSetInfo$: of(value)};
            }),
            share(),
            finalize(() => {
                if (this.cache[step]) {
                    this.cache[step].dataSetInfo$ = null;
                }
            }),
        );

        this.cache[step] = {dateSetInfo: null, dataSetInfo$};
        return dataSetInfo$;
    }

    public invalidateCache() {
        this.cache = {};
    }
}
