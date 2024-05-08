import { Injectable } from '@angular/core';
import { DataConfiguration } from '../model/data-configuration';
import { ColumnConfiguration } from '../model/column-configuration';
import { List } from 'src/app/core/utils/list';
import { HttpClient } from '@angular/common/http';
import { map, Observable, PartialObserver } from 'rxjs';
import { instanceToPlain } from 'class-transformer';
import { ConfigurationService } from './configuration.service';
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { Steps } from 'src/app/core/enums/steps';
import { FileService } from "../../features/data-upload/services/file.service";

@Injectable({
    providedIn: 'root',
})
export class DataConfigurationService {
    public readonly CONFIGURATION_NAME = "configurations";

    private _dataConfiguration: DataConfiguration;

    constructor(
        private httpClient: HttpClient,
        private configurationService: ConfigurationService,
        private fileService: FileService,
    ) {
        this.setDataConfiguration(new DataConfiguration());
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.UPLOAD;
        configReg.lockedAfterStep = Steps.VALIDATION;
        configReg.displayName = "Data Configuration";
        configReg.name = this.CONFIGURATION_NAME;
        configReg.orderNumber = 0;
        configReg.syncWithBackend = false;
        configReg.storeConfig = (configName, yamlConfigString) => this.postDataConfigurationString(yamlConfigString);
        configReg.getConfigCallback = () => this.getConfigurationCallback();
        configReg.setConfigCallback = (config, onErrorCallback) => this.setConfigCallback(config, onErrorCallback);

        this.configurationService.registerConfiguration(configReg);
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

    public validateConfiguration(dataConfig: File, observer: PartialObserver<DataConfiguration>) {
        const callback = (result: object | null) => {
            this.setDataConfiguration(result as DataConfiguration);
            this.postDataConfiguration().pipe(map(() => this._dataConfiguration)).subscribe(observer);
        };

        this.configurationService.extractConfig(dataConfig, this.CONFIGURATION_NAME, callback);
    }

    public downloadDataConfigurationAsJson(): Observable<DataConfiguration> {
        return this.httpClient.get<DataConfiguration>("/api/data/configuration?format=json");
    }

    public postDataConfiguration(): Observable<Number> {
        const configString = JSON.stringify(instanceToPlain(this._dataConfiguration));
        return this.postDataConfigurationString(configString);
    }

    public postDataConfigurationString(configString: string): Observable<Number> {
        const formData = new FormData();

        formData.append("file", this.fileService.getFile());
        const fileConfigString = JSON.stringify(this.fileService.getFileConfiguration());
        formData.append("fileConfiguration", fileConfigString);

        formData.append("configuration", configString);

        return this.httpClient.post<Number>("/api/data/configuration", formData);
    }

    private getConfigurationCallback(): Object {
        this.postDataConfiguration().subscribe();
        return this.getDataConfiguration();
    }

    private setConfigCallback(configData: string, onErrorCallback: (errorMessage: string) => void): void {
        this.postDataConfigurationString(configData).subscribe({
            next: (data: Number) => {
                this.downloadDataConfigurationAsJson().subscribe({
                    next: (data: DataConfiguration) => {
                        this.setDataConfiguration(data);
                    },
                    error: (error) => {
                        console.log(error);
                        onErrorCallback(error);
                    },
                });
            },
            error: (error) => {
                console.log(error);
                onErrorCallback(error);
            },
        });
    }
}
