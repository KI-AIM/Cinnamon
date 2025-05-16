import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import {
    catchError,
    concatMap, debounceTime,
    filter,
    from,
    map,
    mergeMap,
    Observable,
    of, scan,
    switchMap,
    tap,
    throwError,
    toArray
} from "rxjs";
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { FileUtilityService } from './file-utility.service';
import { parse, stringify } from 'yaml';
import { ImportPipeData, ImportPipeDataIntern } from "../model/import-pipe-data";
import { Steps } from "../../core/enums/steps";
import { environments } from "../../../environments/environment";
import { StatusService } from "./status.service";
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
        configuration: {[algorithmName: string]: Object},
    }> = {};

    constructor(
        private fileUtilityService: FileUtilityService,
        private httpClient: HttpClient,
        private readonly statusService: StatusService,
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
    public getConfiguration(configurationName: string, algorithm: Algorithm): Object | null {
        return this.configurationCache[configurationName]?.configuration[algorithm.name] || null;
    }

    /**
     * Caches the configuration for the given configuration name and algorithm.
     * @param configurationName The configuration name.
     * @param algorithm The algorithm to cache the configuration for.
     * @param configuration The configuration to cache.
     */
    public setConfiguration(configurationName: string, algorithm: Algorithm, configuration: Object): void {
        this.initCache(configurationName);
        this.configurationCache[configurationName].configuration[algorithm.name] = configuration;
    }

    /**
     * Returns the cached configuration for the cached algorithm of the given configuration name.
     * @param configurationName The configuration name.
     */
    public getSelectedConfiguration(configurationName: string): Object | null {
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
     * Returns the registered configuration with given name if present.
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
     * Extracts the configuration object with the given name form the given YAML configuration file.
     * Calls the given callback with the parameter in the form of {<configuration name>: <extracted config>}.
     * If the config is not present, <extracted config> is null.
     *
     * @param file YAML file containing configurations.
     * @param configurationName Name of the configuration to extract.
     * @param callback Callback that gets called with the configuration.
     */
    public extractConfig(file: Blob, configurationName: string, callback: (result: object | null) => void) {
        const reader = new FileReader();
        reader.addEventListener("load", () => {
            const configData = reader.result as string;
            const configurations = parse(configData);

            const result: { [a: string]: object | null } = {};
            result[configurationName] = null;

            for (const [name, config] of Object.entries(configurations)) {
                if (name === configurationName) {
                    result[configurationName] = config as object;
                    break;
                }
            }

            callback(result);
        }, false);

        reader.readAsText(file);
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
     * Downloads the registered configurations which names are included in the given array.
     * Uses the getConfigCallback function to retrieve the configuration.
     * If configured, stores the configuration under the configured name into the database.
     * @param includedConfigurations Names of the configurations to download.
     */
    public downloadAllConfigurations(includedConfigurations: Array<string>): Observable<string> {
        return from(includedConfigurations).pipe(
            map(configName => {
                // Get the registered data about the configuration
               return this.getRegisteredConfigurationByName(configName);
            }),
            filter(value => {
                // Filter unknown configurations
                return value !== null;
            }),
            switchMap(value => {
                // Get the configuration string
                return value.getConfigCallback().pipe(
                    map(config => {
                        const configString = typeof config === 'string' ? config : stringify(config);
                        return {config: configString, metadata: value};
                    }),
                );
            }),
            // TODO Should we upload the configuration implicitly?
            // switchMap(value => {
            //     // Upload the configuration
            //     if (!this.statusService.isStepCompleted(value.metadata.lockedAfterStep)) {
            //         return value.metadata.storeConfig!(value.metadata.name, value.config).pipe(
            //             map(() => value),
            //         );
            //     } else {
            //         return of(value);
            //     }
            // }),
            scan((acc, value) => {
                return acc + value.config;
            }, ""),
            debounceTime(0),
            tap(value => {
                // TODO use project name
                const fileName = "configuration.yaml"
                this.fileUtilityService.saveYamlFile(value, fileName);
            }),
        )
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
            // catchError((error) => {
            //     console.log('Error during configuration read:', error);
            //     return of(null);
            // }),
        );
    }

    /**
     * Fetches all configurations from the backend for steps that are available before the given step.
     * Calls the setConfigCallback function if a configuration is available.
     * @param step The step up to which the configurations should be fetched.
     */
    // public fetchConfigurations(step: Steps) {
    //     const stepIndex = Number.parseInt(Steps[step]);
    //     for (const config of this.getRegisteredConfigurations()) {
    //         if (config.availableAfterStep < stepIndex) {
    //             config.fetchConfig!(config.name).subscribe({
    //                 next: value => {
    //                     const data = new ImportPipeData();
    //                     data.success = true;
    //                     data.name = config.name;
    //                     data.configData = config;
    //                     data.yamlConfigString = value;
    //
    //                     config.setConfigCallback(data);
    //                 },
    //                 error: err => {
    //                     console.log(err);
    //                 }
    //             });
    //         }
    //     }
    // }

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
