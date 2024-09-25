import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DataConfiguration } from '../model/data-configuration';
import { instanceToPlain } from 'class-transformer';
import { FileConfiguration } from "../model/file-configuration";
import { environments } from "../../../environments/environment";

@Injectable({
    providedIn: 'root',
})
export class DataService {
    private baseUrl: string = environments.apiUrl + "/api/data"

    constructor(private httpClient: HttpClient) {
    }

    estimateData(file: File, fileConfig: FileConfiguration): Observable<Object> {
        const formData = new FormData();

        formData.append("file", file);

        const fileConfigString = JSON.stringify(fileConfig);
        formData.append("fileConfiguration", fileConfigString);

        return this.httpClient.post(this.baseUrl + "/datatypes", formData);
    }

    readAndValidateData(file: File, fileConfig: FileConfiguration, config: DataConfiguration): Observable<Object> {
        const formData = new FormData();

        formData.append("file", file);

        const fileConfigString = JSON.stringify(fileConfig);
        formData.append("fileConfiguration", fileConfigString);

        var configString = JSON.stringify(instanceToPlain(config));
        formData.append("configuration", configString);

        return this.httpClient.post(this.baseUrl + "/validation", formData);
    }

    storeData(file: File, config: DataConfiguration, fileConfig: FileConfiguration): Observable<Object> {
        const formData = new FormData();

        formData.append("file", file);

        const fileConfigString = JSON.stringify(fileConfig);
        formData.append("fileConfiguration", fileConfigString);

        var configString = JSON.stringify(instanceToPlain(config));
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
