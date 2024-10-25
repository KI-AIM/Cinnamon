import { Injectable } from '@angular/core';
import { DataConfiguration } from '../model/data-configuration';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, map, Observable } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ConfigurationService } from './configuration.service';
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { Steps } from 'src/app/core/enums/steps';
import { parse, stringify } from "yaml";
import { ImportPipeData } from "../model/import-pipe-data";
import { environments } from "../../../environments/environment";

@Injectable({
    providedIn: 'root',
})
export class DataConfigurationService {
    private readonly baseUrl: string = environments.apiUrl + "/api/data/VALIDATION/configuration";
    public readonly CONFIGURATION_NAME = "configurations";

    private readonly _dataConfiguration$: Observable<DataConfiguration>;
    private dataConfigurationSubject: BehaviorSubject<DataConfiguration>;

    public localDataConfiguration: DataConfiguration | null = null;

    constructor(
        private httpClient: HttpClient,
        private configurationService: ConfigurationService,
    ) {
        this.dataConfigurationSubject = new BehaviorSubject(new DataConfiguration());
        this._dataConfiguration$ = this.dataConfigurationSubject.asObservable();
    }

    public get dataConfiguration$() {
        return this._dataConfiguration$;
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.UPLOAD;
        configReg.lockedAfterStep = Steps.VALIDATION;
        configReg.displayName = "Data Configuration";
        configReg.fetchConfig = (configName) => this.downloadDataConfigurationAsJson().pipe(map(value => stringify(value)));
        configReg.name = this.CONFIGURATION_NAME;
        configReg.orderNumber = 0;
        configReg.storeConfig = (configName, yamlConfigString) => this.postDataConfigurationString(yamlConfigString);
        configReg.getConfigCallback = () => this.getConfigurationCallback();
        configReg.setConfigCallback = (config) => this.setConfigCallback(config);

        this.configurationService.registerConfiguration(configReg);
    }

    public setDataConfiguration(value: DataConfiguration) {
        this.localDataConfiguration = null;
        this.dataConfigurationSubject.next(value);
    }

    public downloadDataConfigurationAsJson(): Observable<DataConfiguration> {
        return this.httpClient.get<DataConfiguration>(this.baseUrl + "?format=json");
    }

    public downloadDataConfiguration(step: string): Observable<DataConfiguration> {
        return this.httpClient.get<DataConfiguration>(environments.apiUrl + "/api/data/" + step + "/configuration?format=json");
    }

    public postDataConfigurationString(configString: string): Observable<void> {
        const formData = new FormData();
        formData.append("configuration", configString);
        return this.httpClient.post<void>(this.baseUrl, formData);
    }

    private getConfigurationCallback(): Object {
        let config;
        if (this.localDataConfiguration !== null) {
            config = this.localDataConfiguration;
            this.setDataConfiguration(config);
        } else {
            config = this.dataConfigurationSubject.getValue();
        }
        return config;
    }

    private setConfigCallback(importData: ImportPipeData): void {
        const config = parse(importData.yamlConfigString);
        const dataConfig = plainToInstance(DataConfiguration, config);
        this.setDataConfiguration(dataConfig);
    }
}
