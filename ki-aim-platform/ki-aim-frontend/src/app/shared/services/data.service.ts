import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DataConfiguration } from '../model/data-configuration';
import { instanceToPlain } from 'class-transformer';
import { environments } from "../../../environments/environment";

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

    storeData(config: DataConfiguration): Observable<Object> {
        const formData = new FormData();

        const configString = JSON.stringify(instanceToPlain(config));
        formData.append("configuration", configString);

        return this.httpClient.post(this.baseUrl, formData);
    }

    public confirmData(): Observable<void> {
        return this.httpClient.post<void>(this.baseUrl + "/confirm", {});
    }

    public deleteData(): Observable<void> {
        return this.httpClient.delete<void>(this.baseUrl);
    }
}
