import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { map, Observable, switchMap } from 'rxjs';
import { DataConfiguration, DataConfigurationEstimation } from '../model/data-configuration';
import { instanceToPlain, plainToInstance } from 'class-transformer';
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root',
})
export class DataService {

    constructor(
        private httpClient: HttpClient,
        private readonly projectService: ProjectService,
    ) {
    }

    public estimateData(): Observable<DataConfigurationEstimation> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postEstimateDataAction(projectId)),
        );
    }

    public storeData(config: DataConfiguration): Observable<number> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postData(projectId, config)),
        );
    }

    /**
     * Creates a hold-out split of the original data set.
     * Requires the data to be stored and not to be confirmed.
     * An existing hold-out split will be overwritten.
     *
     * @param holdOutPercentage Percentage of rows that should be assigned to the hold-out split.
     */
    public createHoldOutSplit(holdOutPercentage: number): Observable<void> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postCreateHoldOutSplitAction(projectId, holdOutPercentage)),
        );
    }

    public confirmData(): Observable<void> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.postConfirmDataAction(projectId)),
        );
    }

    private baseUrl(projectId: string): string {
        return `${environments.apiUrl}/api/project/${projectId}/data`;
    }

    private postEstimateDataAction(projectId: string) {
        return this.httpClient.get<DataConfigurationEstimation>(this.baseUrl(projectId) + "/estimation").pipe(
            map(estimation => {
                return plainToInstance(DataConfigurationEstimation, estimation);
            }),
        );
    }

    private postData(projectId: string, config: DataConfiguration) {
        const formData = new FormData();
        const configString = JSON.stringify(instanceToPlain(config));
        formData.append("configuration", configString);
        return this.httpClient.post<number>(this.baseUrl(projectId), formData);
    }

    private postCreateHoldOutSplitAction(projectId: string, holdOutPercentage: number) {
        const formData = new FormData();
        formData.append("holdOutPercentage", JSON.stringify(holdOutPercentage));
        return this.httpClient.post<void>(this.baseUrl(projectId) + "/hold-out", formData);
    }

    private postConfirmDataAction(projectId: string) {
        return this.httpClient.post<void>(this.baseUrl(projectId) + "/confirm", {});
    }
}
