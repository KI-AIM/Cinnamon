import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { ConfigurationObject } from "@shared/model/anonymization-attribute-config";
import {
    concatMap,
    endWith,
    filter,
    from,
    ignoreElements,
    map,
    Observable,
    ReplaySubject,
    switchMap,
    tap,
    throwError,
} from "rxjs";
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { parse } from 'yaml';
import { ConfigurationImportSummary, ConfigurationImportSummaryPart } from "../model/import-pipe-data";
import { environments } from "src/environments/environment";
import { Algorithm } from "../model/algorithm";

/**
 * Service for managing configurations.
 */
@Injectable({
    providedIn: 'root'
})
export class ConfigurationService {
    private baseUrl: string = environments.apiUrl + "/api/config";
    private registeredConfigurations: Array<ConfigurationRegisterData>;

    private configurationCache: Record<string, {
        selectedAlgorithm: Algorithm | null,
        configuration: {[algorithmName: string]: ConfigurationObject},
        processStatus: {[processName: string]: boolean},
    }> = {};

    /**
     * Subject storing all configuration import parts.
     */
    private configImportSubject = new ReplaySubject<ConfigurationImportSummaryPart>();

    constructor(
        private httpClient: HttpClient,
    ) {
        this.registeredConfigurations = [];
    }

    /**
     * Returns an observable for the configuration import summary.
     * The observable emits the summary part whenever a new part is added to the summary.
     */
    public get configImport$(): Observable<ConfigurationImportSummaryPart> {
        return this.configImportSubject.asObservable();
    }

    /**
     * Caches the selected algorithm for the given configuration name.
     * @param configurationName The configuration name.
     * @param algorithm The algorithm to cache.
     */
    public setSelectedAlgorithm(configurationName: string, algorithm: Algorithm): void {
        this.initCache(configurationName);
        this.configurationCache[configurationName].selectedAlgorithm = algorithm;
    }

    /**
     * Gets the cached selected algorithm for the given configuration name or null if no algorithm is cached.
     * @param configurationName The configuration name.
     */
    public getSelectedAlgorithm(configurationName: string): Algorithm | null {
        return this.configurationCache[configurationName]?.selectedAlgorithm || null;
    }

    /**
     * Gets the cached configuration for the given configuration name and algorithm or null if no configuration is cached.
     * @param configurationName The configuration name.
     * @param algorithm The algorithm to get the configuration for.
     */
    public getConfiguration(configurationName: string, algorithm: Algorithm): ConfigurationObject | null {
        return this.configurationCache[configurationName]?.configuration[algorithm.name] || null;
    }

    /**
     * Caches the configuration for the given configuration name and algorithm.
     * @param configurationName The configuration name.
     * @param algorithm The algorithm to cache the configuration for.
     * @param configuration The configuration to cache.
     */
    public setConfiguration(configurationName: string, algorithm: Algorithm, configuration: ConfigurationObject): void {
        this.initCache(configurationName);
        this.configurationCache[configurationName].configuration[algorithm.name] = configuration;
    }

    /**
     * Returns the cached configuration for the cached algorithm of the given configuration name.
     * @param configurationName The configuration name.
     */
    public getSelectedConfiguration(configurationName: string): ConfigurationObject | null {
        const selectedAlgorithm = this.getSelectedAlgorithm(configurationName);
        if (selectedAlgorithm === null) {
            return null;
        }
        return this.getConfiguration(configurationName, selectedAlgorithm);
    }

    /**
     * Caches the activation status for the given process.
     * @param configurationName The configuration name.
     * @param processName The process name.
     * @param status If the process is activated or not.
     */
    public setProcessStatus(configurationName: string, processName: string, status: boolean): void {
        this.initCache(configurationName);
        this.configurationCache[configurationName].processStatus[processName] = status;
    }

    /**
     * Returns the cached activation status for the given process.
     * If no status is cached returns null.
     *
     * @param configurationName The configuration name.
     * @param processName The process name.
     */
    public getProcessStatus(configurationName: string, processName: string): boolean | null {
        if (this.configurationCache[configurationName] == null ||
            this.configurationCache[configurationName].processStatus[processName] == null) {
            return null;
        }
        return this.configurationCache[configurationName].processStatus[processName];
    }

    /**
     * Invalidates the cached configurations.
     */
    public invalidateCache(): void {
        this.configurationCache = {};
    }

    /**
     * Returns a list of all registered configurations.
     * @returns List of all registered configurations.
     */
    public getRegisteredConfigurations() {
        return this.registeredConfigurations;
    }

    /**
     * Registers a new configuration.
     * @param data Metadata of the configuration.
     */
    public registerConfiguration(data: ConfigurationRegisterData) {
        this.registeredConfigurations.push(data);
        this.registeredConfigurations = this.registeredConfigurations.sort((a, b) => a.orderNumber - b.orderNumber);
    }

    /**
     * Loads the configuration with the given name from the database.
     * @param configurationName Identifier of the configuration to load.
     * @returns Observable containing the configuration as a string.
     */
    public loadConfig(configurationName: String): Observable<string> {
        return this.httpClient.get<string>(this.baseUrl + "?name=" + configurationName, {responseType: 'text' as 'json'})
            .pipe(
                map(value => {
                    // Plain text has to be parsed to YAML string
                    return parse(value)
                }),
            );
    }

    /**
     * Stores the given configuration under the given name into the database.
     * @param configurationName Identifier of the configuration to load.
     * @param configuration Configuration to store in form of a string.
     * @returns Observable returning containing the ID of the dataset.
     */
    public storeConfig(configurationName: String, configuration: String): Observable<void> {
        const formData = new FormData();
        formData.append("configuration", configuration.toString());
        formData.append("configurationName", configurationName.toString());
        return this.httpClient.post<void>(environments.apiUrl + "/api/config", formData);
    }


    /**
     * Uploads the configurations that are contained in the file and included in the given array.
     * The configurations are set to the UI using the configImport$ observable.
     * Returns a summary containing the status for all configurations contained in the file.
     *
     * @param file file containing the config to be loaded.
     * @param includedConfigurations Names of the configurations to upload. If null, all configurations will be uploaded.
     * @return Observable of the import result.
     */
    public uploadAllConfigurations(file: Blob, includedConfigurations: Array<string> | null): Observable<ConfigurationImportSummary> {
        const formData = new FormData();
        formData.append("configuration", file);
        formData.append("importParameters", JSON.stringify({configurationsToImport: includedConfigurations}));

        // TODO currently there is always just one configuration imported.
        //  If multiple configurations are uploaded, handling for PARTIAL_ERROR must be implemented.
        return this.httpClient
            .post<ConfigurationImportSummary>(`${environments.apiUrl}/api/config/import`, formData)
            .pipe(
                switchMap((summary) => {
                    if (summary.status === 'ERROR') {
                        return throwError(() => new Error('Configuration import failed.'));
                    }

                    return from(summary.configurationImportSummaries).pipe(
                        filter((part) => part.status === 'SUCCESS'),
                        concatMap((part) => {
                            return this.loadConfig(part.configurationName).pipe(
                                tap((config) => {
                                    part.configuration = config;
                                    this.configImportSubject.next(part);
                                }),
                            );
                        }),
                        ignoreElements(),
                        endWith(summary),
                    );
                }),
            );
    }

    /**
     * Initializes the cache for the given configuration name if it does not exist.
     * @param configurationName The name of the configuration to initialize.
     * @private
     */
    private initCache(configurationName: string): void {
        if (!this.configurationCache[configurationName]) {
            this.configurationCache[configurationName] = {
                selectedAlgorithm: null,
                configuration: {},
                processStatus: {},
            };
        }
    }

}
