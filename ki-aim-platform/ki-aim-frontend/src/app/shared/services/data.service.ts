import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of } from 'rxjs';
import { DataConfiguration } from '../model/data-configuration';
import { instanceToPlain } from 'class-transformer';
import { FileConfiguration } from "../model/file-configuration";
import { environments } from "../../../environments/environment";
import { DataSet } from '../model/data-set';

@Injectable({
    providedIn: 'root',
})
export class DataService {
    private baseUrl: String = environments.apiUrl + "/api/data"

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

        return this.httpClient.post(this.baseUrl.toString(), formData);
    }

    fetchDataColumns(columns: String[]): Observable<Object> {
        const columnConfigString = columns.join(",");

        return this.httpClient.get(this.baseUrl.toString(), {
            "params": {
                "columns": columnConfigString
            }
        });
    }

    fetchColumnAsArray(column: string): Observable<Array<any> | null> {
        return this.fetchDataColumns(new Array<string>(column)).pipe(
            map(data => this.flattenSingleDimensionDataSet(data as DataSet)),
            catchError(error => {
                console.error(error);
                return of(null); // Return null or an Observable of null if there is an error
            })
        );
    }

    private flattenSingleDimensionDataSet(data: DataSet): Array<any> {
        const flattened: Array<any> = [];

        data.data.forEach(innerArray => {
            if (innerArray.length > 0) {
                flattened.push(innerArray[0]); // Push the first (and supposedly only) element
            }
        });

        return flattened;
    }

}
