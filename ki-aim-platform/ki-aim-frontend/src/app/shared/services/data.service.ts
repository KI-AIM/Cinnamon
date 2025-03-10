import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { DataConfiguration } from '../model/data-configuration';
import { instanceToPlain } from 'class-transformer';
import { environments } from "../../../environments/environment";
import { FileInformation } from "../model/file-information";

@Injectable({
    providedIn: 'root',
})
export class DataService {
    private baseUrl: string = environments.apiUrl + "/api/data"

    constructor(private httpClient: HttpClient) {
    }

    estimateData(): Observable<Object> {
        return this.httpClient.get(this.baseUrl + "/estimation");
    }

    storeData(config: DataConfiguration): Observable<number> {
        const formData = new FormData();

        const configString = JSON.stringify(instanceToPlain(config));
        formData.append("configuration", configString);

        return this.httpClient.post<number>(this.baseUrl, formData);
    }

    /**
     * Creates a hold-out split of the original data set.
     * Requires the data to be stored and not to be confirmed.
     * An existing hold-out split will be overwritten.
     *
     * @param holdOutPercentage Percentage of rows that should be assigned to the hold-out split.
     */
    public createHoldOutSplit(holdOutPercentage: number): Observable<void> {
        const formData = new FormData();
        formData.append("holdOutPercentage", JSON.stringify(holdOutPercentage));
        return this.httpClient.post<void>(this.baseUrl + "/hold-out", formData);
    }

    public confirmData(): Observable<void> {
        return this.httpClient.post<void>(this.baseUrl + "/confirm", {});
    }

    public deleteData(): Observable<void> {
        return this.httpClient.delete<void>(this.baseUrl);
    }
}
