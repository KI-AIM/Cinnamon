import { Injectable } from '@angular/core';
import { DataConfiguration } from '../model/data-configuration';
import { HttpClient } from '@angular/common/http';
import { map, Observable, of, ReplaySubject, take } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ConfigurationService } from './configuration.service';
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { Steps } from 'src/app/core/enums/steps';
import { parse, stringify } from "yaml";
import { ImportPipeData } from "../model/import-pipe-data";
import { environments } from "src/environments/environment";
import { ErrorHandlingService } from './error-handling.service';

@Injectable({
    providedIn: 'root',
})
export class DataConfigurationService {
    private readonly baseUrl: string = environments.apiUrl + "/api/data/configuration";
    public readonly CONFIGURATION_NAME = "configurations";

    private readonly _dataConfiguration$: Observable<DataConfiguration>;
    private dataConfigurationSubject: ReplaySubject<DataConfiguration>;
    private fetched: boolean = false;

    public localDataConfiguration: DataConfiguration | null = null;
    public localDataSetConfiguration: DataSetConfiguration | null = null;

    constructor(
        private httpClient: HttpClient,
        private configurationService: ConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
    ) {
        this.dataConfigurationSubject = new ReplaySubject(1);
        this._dataConfiguration$ = this.dataConfigurationSubject.asObservable();
    }

    public get dataConfiguration$() {
        if (!this.fetched) {
            this.fetched = true;
            this.downloadDataConfigurationAsJson().pipe(
                take(1),
            ).subscribe({
                next: value => {
                    this.dataConfigurationSubject.next(value);
                },
                error: err => {
                    this.fetched = false;
                    this.errorHandlingService.addError(err, "Failed to fetch the data configuration.");
                }
            });
        }
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
        const params = {
            selector: "ORIGINAL",
        }
        return this.httpClient.get<DataConfiguration>(this.baseUrl + "?format=json", {params: params}).pipe(
            map(value => plainToInstance(DataConfiguration, value)),
        );
    }

    public postDataConfigurationString(configString: string): Observable<void> {
        const formData = new FormData();
        formData.append("configuration", configString);
        return this.httpClient.post<void>(this.baseUrl, formData);
    }

    private getConfigurationCallback(): Observable<Object> {
        if (this.localDataConfiguration !== null) {
            return of(this.localDataConfiguration);
        } else {
            return this.dataConfiguration$;
        }
    }

    private setConfigCallback(importData: ImportPipeData): void {
        const config = parse(importData.yamlConfigString);
        const dataConfig = plainToInstance(DataConfiguration, config);
        this.setDataConfiguration(dataConfig);
    }
}

export interface DataSetConfiguration {
    holdOutSplitPercentage: number;
    createHoldOutSplit: boolean;
}
