import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { DataConfiguration, DataConfigurationEstimation } from '../model/data-configuration';
import { instanceToPlain, plainToInstance } from 'class-transformer';
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root',
})
export class DataService {
    private baseUrl: string = environments.apiUrl + "/api/data"

    constructor(private httpClient: HttpClient) {
    }

    public estimateData(): Observable<DataConfigurationEstimation> {
        return this.httpClient.get<DataConfigurationEstimation>(this.baseUrl + "/estimation").pipe(
            map(value => {
                return plainToInstance(DataConfigurationEstimation, value);
            })
        );
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
}
