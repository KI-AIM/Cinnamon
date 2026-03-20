import { Injectable } from '@angular/core';
import { DataConfiguration } from '../model/data-configuration';
import { HttpClient } from '@angular/common/http';
import { filter, map, Observable, ReplaySubject, take } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ConfigurationService } from './configuration.service';
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { Steps } from 'src/app/core/enums/steps';
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
    public confidence: number[] | null = null;

    constructor(
        private httpClient: HttpClient,
        private configurationService: ConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
    ) {
        this.dataConfigurationSubject = new ReplaySubject(1);
        this._dataConfiguration$ = this.dataConfigurationSubject.asObservable();

        configurationService.configImport$.pipe(
            filter(value => value.configurationName === this.CONFIGURATION_NAME),
            filter(value => value.configuration !== null),
        ).subscribe({
            next: value => {
                const dataConfig = plainToInstance(DataConfiguration, value.configuration);
                this.setDataConfiguration(dataConfig);
            }
        });
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
        configReg.name = this.CONFIGURATION_NAME;
        configReg.orderNumber = 0;

        this.configurationService.registerConfiguration(configReg);
    }

    public setDataConfiguration(value: DataConfiguration) {
        this.localDataConfiguration = null;
        this.confidence = null
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
}

export interface DataSetConfiguration {
    holdOutSplitPercentage: number;
    createHoldOutSplit: boolean;
}
