import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { ConfigurationObject } from "@shared/services/algorithm.service";
import { catchError, concatMap, from, map, mergeMap, Observable, of, switchMap, tap, throwError, toArray } from "rxjs";
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { parse, stringify } from 'yaml';
import { ImportPipeData, ImportPipeDataIntern } from "../model/import-pipe-data";
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
    }> = {};

    constructor(
        private httpClient: HttpClient,
    ) {
        this.registeredConfigurations = [];
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
     * Returns the registered configuration with the given name if present.
     * Otherwise, returns null.
     * @param name Name of the registered configuration to return.
     * @returns The registered configuration or null.
     */
    public getRegisteredConfigurationByName(name: string): ConfigurationRegisterData | null {
        for (const config of this.registeredConfigurations) {
            if (config.name === name) {
                return config;
            }
        }

        return null;
    }

    /**
     * Registers a new configuration.
     * @param data Metadata of the configuration.
     */
    public registerConfiguration(data: ConfigurationRegisterData) {
        if (data.fetchConfig === null) {
            data.fetchConfig = (configName) => this.loadConfig(configName);
        }
        if (data.storeConfig == null) {
            data.storeConfig = (configName, yamlConfigString) => this.storeConfig(configName, yamlConfigString, null);
        }

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
     * @param url Url for starting the process.
     * @returns Observable returning containing the ID of the dataset.
     */
    public storeConfig(configurationName: String, configuration: String, url: string | null): Observable<void> {
        const formData = new FormData();
        formData.append("configuration", configuration.toString());
        formData.append("configurationName", configurationName.toString());
        if (url) {
            formData.append("url", url);
        }
        return this.httpClient.post<void>(environments.apiUrl + "/api/config", formData);
    }

    /**
     * Uploads the configurations that are contained in the file and included in the given array.
     * Uses the setConfigCallback function to update the configuration in the application.
     * If configured, stores the configuration under the configured name into the database.
     * @param file file containing the config to be loaded.
     * @param includedConfigurations Names of the configurations to upload. If null, all configurations will be uploaded.
     * @return Observable of the import pipeline.
     */
    uploadAllConfigurations(file: Blob, includedConfigurations: Array<string> | null): Observable<ImportPipeData[] | null> {
        const readBlob = (blob: Blob) => new Observable<string | ArrayBuffer | null>((subscriber) => {
            const reader = new FileReader();
            reader.readAsText(blob);
            reader.onload = () => {
                subscriber.next(reader.result);
                subscriber.complete();
            };
            reader.onerror = () => subscriber.error(reader.error);
            return () => reader.abort();
        });

        return of(file).pipe(
            switchMap(currentFile => readBlob(currentFile)),
            mergeMap((result) => {
                const configData = result as string;
                const configurations = parse(configData) as object;

                return from(Object.entries(configurations)).pipe(
                    map(([name, config]) => {
                        // Get the data for the configuration

                        const configData = this.getRegisteredConfigurationByName(name);

                        let yamlConfigString = null;
                        if (configData != null && (includedConfigurations === null || includedConfigurations.includes(configData.name))) {
                            const yamlConfig: { [a: string]: any } = {};
                            yamlConfig[name] = config;
                            yamlConfigString = stringify(yamlConfig);
                        }

                        const result = new ImportPipeDataIntern();
                        result.name = name;
                        result.configData = configData;
                        result.yamlConfigString = yamlConfigString;
                        return result;
                    }),
                    concatMap((object) => {
                        // Store the configuration in the backend
                        const name = object.name;
                        const yamlConfigString = object.yamlConfigString;

                        let ob;
                        if (object.configData == null || yamlConfigString === null) {
                            // If the config is not in the config file, use 0 as a placeholder
                            ob = of(void 0);
                        } else {
                            // Cannot be null after registering
                            ob = object.configData.storeConfig!(name, yamlConfigString);
                        }

                        return ob.pipe(
                            map(() => {
                                object.success = true;
                                return object as ImportPipeData;
                            }),
                            catchError((error) => {
                                object.error = error;
                                object.success = false;
                                return of(object as ImportPipeData);
                            }),
                        );
                    }),
                    tap((object) => {
                        // Set the configuration in the UI
                        if (object.configData !== null && object.yamlConfigString !== null) {
                            object.configData.setConfigCallback(object);
                        }
                    }),
                    toArray(),
                    catchError((error) => {
                        console.log('Error during configuration import:', error);
                        return of(null);
                    }),
                );
            }),
            switchMap(result => {
                if (includedConfigurations !== null && result) {
                    // Look if all configurations are present
                    const missing: string[] = [];
                    for (const config of includedConfigurations) {
                        if (!result.some(value => value.name === config)) {
                            missing.push(config);
                        }
                    }

                    if (missing.length > 0) {
                        return throwError(() => "Invalid configuration file! The configuration must contain the following properties: " + missing.map(val => "'" + val + "'").join(", "));
                    }
                }
                return of(result);
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
            };
        }
    }

}
