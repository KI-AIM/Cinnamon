import { Injectable } from '@angular/core';
import { DataConfiguration } from '../model/data-configuration';
import { ColumnConfiguration } from '../model/column-configuration';
import { List } from 'src/app/core/utils/list';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, switchMap } from 'rxjs';
import { instanceToPlain } from 'class-transformer';

@Injectable({
    providedIn: 'root',
})
export class DataConfigurationService {
    private _dataConfiguration: DataConfiguration; 

    constructor(private httpClient: HttpClient) {
        this.setDataConfiguration(new DataConfiguration());
    }

    public getDataConfiguration(): DataConfiguration {
        return this._dataConfiguration;
    }
    public setDataConfiguration(value: DataConfiguration) {
        this._dataConfiguration = value;
    }

    public getColumnConfigurations(): List<ColumnConfiguration> {
        return new List<ColumnConfiguration>(this.getDataConfiguration().configurations); 
    }

    public postDataConfiguration(): Observable<Number> {
        const formData = new FormData();

        var configString = JSON.stringify(instanceToPlain(this._dataConfiguration));
        formData.append("configuration", configString);

        return this.httpClient.post<Number>("/api/data/configuration", formData);
    }

    public downloadDataConfigurationAsYaml(): Observable<Blob> {
        return this.postDataConfiguration().pipe(switchMap((response) => {
            return this.httpClient.get<Blob>("/api/data/configuration?format=json", {responseType: 'text' as 'json'});
        }));
    }
}
