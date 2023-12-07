import { Injectable } from '@angular/core';
import { DataConfiguration } from '../model/data-configuration';
import { ColumnConfiguration } from '../model/column-configuration';
import { List } from 'src/app/core/utils/list';

@Injectable({
    providedIn: 'root',
})
export class DataConfigurationService {
    private _dataConfiguration: DataConfiguration; 

    constructor() {
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
}
