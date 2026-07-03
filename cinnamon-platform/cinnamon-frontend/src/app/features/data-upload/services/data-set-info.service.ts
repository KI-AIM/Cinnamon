import { HttpClient } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { DataSetInfo } from "@shared/model/data-set-info";
import { ProjectService } from "@shared/services/project.service";
import { plainToInstance } from "class-transformer";
import { finalize, map, Observable, of, share, switchMap, tap } from "rxjs";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root'
})
export class DataSetInfoService {
    private cache: Record<string, {
        dateSetInfo: DataSetInfo | null,
        dataSetInfo$: Observable<DataSetInfo> | null
    }> = {};

    constructor(
        private readonly http: HttpClient,
        private readonly projectService: ProjectService,
    ) {
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

        const dataSetInfo$ = this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.fetchDataSetInfo(projectId, step)),
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

    /**
     * Creates the base URL for the dataset info API endpoint for the given project ID.
     * @param projectId The ID of the project.
     * @returns The base URL for the dataset info API endpoint.
     */
    private baseUrl(projectId: string): string {
        return environments.apiUrl + "/api/project/" + projectId + "/data/info";
    }

    /**
     * Fetches the dataset information for the given project ID and step from the backend.
     * The step can be "validation" for the original dataset, "protected" for the dataset,
     * or the concrete step that created the dataset, i.e. "anonymization" or "synthetization".
     *
     * @param projectId The ID of the project.
     * @param step The step as described above.
     * @returns An observable that emits the dataset information.
     */
    private fetchDataSetInfo(projectId: string, step: string): Observable<DataSetInfo> {
        const params = {
            selector: step.toLowerCase() === "validation"
                ? "ORIGINAL"
                : step.toLowerCase() === "protected"
                    ? "protected"
                    : "JOB",
            jobName: step.toLowerCase(),
        }

        return this.http.get<DataSetInfo>(this.baseUrl(projectId), {params: params}).pipe(
            map(datasetInfo => {
                return plainToInstance(DataSetInfo, datasetInfo);
            }),
        );
    }
}
