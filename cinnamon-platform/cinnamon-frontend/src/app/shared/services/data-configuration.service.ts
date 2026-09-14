import { Injectable } from '@angular/core';
import { ProjectService } from "@shared/services/project.service";
import { DataConfiguration } from '../model/data-configuration';
import { HttpClient } from '@angular/common/http';
import { filter, map, Observable, ReplaySubject, switchMap, take } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ConfigurationService } from './configuration.service';
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { Steps } from 'src/app/core/enums/steps';
import { environments } from "src/environments/environment";
import { ErrorHandlingService } from './error-handling.service';
import { AttributeImportOverview, MissingAttribute } from "@shared/model/attribute-import-overview";
import { ColumnConfiguration } from "@shared/model/column-configuration";

@Injectable({
    providedIn: 'root',
})
export class DataConfigurationService {
    public readonly CONFIGURATION_NAME = "configurations";
    public readonly IMPORT_CONFIGURATION_NAME = "import";

    private readonly _attributeMatching$: Observable<AttributeImportOverview>;
    private attributeMatchingSubject: ReplaySubject<AttributeImportOverview>;
    private fetched: boolean = false;

    public localDataConfiguration: DataConfiguration | null = null;
    public localDataSetConfiguration: DataSetConfiguration | null = null;
    /**
     * Local state of the attributes only present in the configuration, null if not modified.
     */
    public localConfigOnlyAttributes: ColumnConfiguration[] | null = null;
    /**
     * Local state of the attributes only present in the data, null if not modified.
     */
    public localDataOnlyAttributes: MissingAttribute[] | null = null;
    /**
     * Confidences of the estimation indexed by the attribute index.
     */
    public confidence: number[] | null = null;

    constructor(
        private httpClient: HttpClient,
        private configurationService: ConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly projectService: ProjectService,
    ) {
        this.attributeMatchingSubject = new ReplaySubject(1);
        this._attributeMatching$ = this.attributeMatchingSubject.asObservable();

        configurationService.configImport$.pipe(
            filter(value => value.configurationName === this.CONFIGURATION_NAME ||
                value.configurationName === this.IMPORT_CONFIGURATION_NAME),
            filter(value => value.configuration !== null),
        ).subscribe({
            next: () => this.reset(),
        });
    }

    /**
     * Overview of the attribute matching stored on the server.
     */
    public get attributeMatching$(): Observable<AttributeImportOverview> {
        if (!this.fetched) {
            this.fetch();
        }
        return this._attributeMatching$;
    }

    /**
     * Configuration of the attributes matched to the data stored on the server.
     */
    public get dataConfiguration$(): Observable<DataConfiguration> {
        return this.attributeMatching$.pipe(
            map(overview => {
                const dataConfiguration = new DataConfiguration();
                dataConfiguration.configurations = overview.importConfiguration.attributes;
                return dataConfiguration;
            }),
        );
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

    /**
     * Discards all local changes and fetches the attribute matching from the server.
     * Must be called after the configuration on the server has changed, e.g., after an estimation or an import.
     */
    public reset() {
        this.localDataConfiguration = null;
        this.localConfigOnlyAttributes = null;
        this.localDataOnlyAttributes = null;
        this.confidence = null;
        this.fetch();
    }

    /**
     * Replaces the attribute matching stored on the server.
     * @param attributes The configurations of the attributes matched to the data.
     * @param unmatchedAttributes The configurations of the attributes that are ignored.
     */
    public storeAttributeMatching(
        attributes: ColumnConfiguration[],
        unmatchedAttributes: ColumnConfiguration[],
    ): Observable<AttributeImportOverview> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.httpClient.put<AttributeImportOverview>(
                this.baseUrl(projectId) + "/matching",
                {attributes: attributes, unmatchedAttributes: unmatchedAttributes},
            )),
            map(value => plainToInstance(AttributeImportOverview, value)),
        );
    }

    public downloadDataConfigurationAsJson(): Observable<DataConfiguration> {
        return this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.getDataConfiguration(projectId)),
        );
    }

    private fetch() {
        this.fetched = true;
        this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.httpClient.get<AttributeImportOverview>(this.baseUrl(projectId) + "/matching")),
            map(value => plainToInstance(AttributeImportOverview, value)),
            take(1),
        ).subscribe({
            next: value => {
                this.attributeMatchingSubject.next(value);
            },
            error: err => {
                this.fetched = false;
                this.errorHandlingService.addError(err, "Failed to fetch the data configuration.");
            }
        });
    }

    private baseUrl(projectId: string): string {
        return `${environments.apiUrl}/api/project/${projectId}/data/configuration`;
    }

    private getDataConfiguration(projectId: string): Observable<DataConfiguration> {
        const params = {
            selector: "ORIGINAL",
        }
        return this.httpClient.get<DataConfiguration>(this.baseUrl(projectId) + "?format=json", {params: params}).pipe(
            map(value => plainToInstance(DataConfiguration, value)),
        );
    }
}

export interface DataSetConfiguration {
    holdOutSplitPercentage: number;
    createHoldOutSplit: boolean;
}
