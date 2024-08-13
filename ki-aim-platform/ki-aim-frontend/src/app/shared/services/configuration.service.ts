import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { catchError, concatMap, from, map, mergeMap, Observable, of, switchMap, tap, toArray } from "rxjs";
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { FileService } from 'src/app/features/data-upload/services/file.service';
import { FileUtilityService } from './file-utility.service';
import { parse, stringify } from 'yaml';
import { ImportPipeData, ImportPipeDataIntern } from "../model/import-pipe-data";
import { Steps } from "../../core/enums/steps";
import { environments } from "../../../environments/environment";

/**
 * Service for managing configurations.
 */
@Injectable({
    providedIn: 'root'
})
export class ConfigurationService {
    private baseUrl: string = environments.apiUrl + "/api/config";
    private registeredConfigurations: Array<ConfigurationRegisterData>;

    constructor(
        private fileService: FileService,
        private fileUtilityService: FileUtilityService,
        private httpClient: HttpClient,
    ) {
        this.registeredConfigurations = [];
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
            data.storeConfig = (configName, yamlConfigString) => this.storeConfig(configName, yamlConfigString);
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

            const result: {[a: string]: object | null} = {};
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
        return this.httpClient.get<string>(this.baseUrl + "?name=" + configurationName, {responseType: 'text' as 'json'});
    }

    /**
     * Stores the given configuration under the given name into the database.
     * @param configurationName Identifier of the configuration to load.
     * @param configuration Configuration to store in form of a string.
     * @returns Observable returning containing the ID of the dataset.
     */
    public storeConfig(configurationName: String, configuration: String): Observable<Number> {
        return this.httpClient.post<Number>(this.baseUrl + "?name=" + configurationName, configuration);
    }

    /**
     * Downloads the registered configurations which names are included in the given array.
     * Uses the getConfigCallback function to retrieve the configuration.
     * If configured, stores the configuration under the configured name into the database.
     * @param includedConfigurations Names of the configurations to download.
     */
    downloadAllConfigurations(includedConfigurations: Array<string>) {
        let configString = "";
        for (const config of this.getRegisteredConfigurations()) {
            if (!includedConfigurations.includes(config.name)) {
                continue;
            }

            const configData = config.getConfigCallback();
            if (typeof configData === "string") {
                configString += configData;
            } else {
                configString += stringify(configData)
            }
        }

        // TODO use project name
        const fileName = "configuration.yaml"
        this.fileUtilityService.saveYamlFile(configString, fileName);
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
                            ob = of(0 as Number);
                        } else {
                            // Cannot be null after registering
                            ob = object.configData.storeConfig!(name, yamlConfigString);
                        }

                        return ob.pipe(
                            map(number => {
                                object.success = number !== 0;
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
            catchError((error) => {
                console.log('Error during configuration read:', error);
                return of(null);
            }),
        );
    }

    /**
     * Fetches all configurations from the backend for steps that are available before the given step.
     * Calls the setConfigCallback function if a configuration is available.
     * @param step The step up to which the configurations should be fetched.
     */
    public fetchConfigurations(step: Steps) {
        const stepIndex = Number.parseInt(Steps[step]);
        for (const config of this.getRegisteredConfigurations()) {
            if (config.availableAfterStep <= stepIndex) {
                config.fetchConfig!(config.name).subscribe({
                    next: value => {
                        const data = new ImportPipeData();
                        data.name = config.name;
                        data.configData = config;
                        data.yamlConfigString = value;
                        config.setConfigCallback(data);
                    },
                    error: err => {
                        console.log(err);
                    }
                });
            }
        }
    }
}
