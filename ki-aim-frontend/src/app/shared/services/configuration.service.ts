import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { ConfigurationRegisterData } from '../model/configuration-register-data';
import { FileService } from 'src/app/features/data-upload/services/file.service';
import { FileUtilityService } from './file-utility.service';
import { parse, stringify } from 'yaml';

/**
 * Service for managing configurations.
 */
@Injectable({
    providedIn: 'root'
})
export class ConfigurationService {
    private baseUrl: String = "api/config";
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
     * Otherwise returns null.
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
        this.registeredConfigurations.push(data);
        this.registeredConfigurations = this.registeredConfigurations.sort((a, b) => a.orderNumber - b.orderNumber);
    }

    /**
     * Loads the configuration with the given name from the database.
     * @param configurationName Identifier of the configuration to load.
     * @returns Observable containing the configuration as a string.
     */
    public loadConfig(configurationName: String): Observable<String> {
        return this.httpClient.get<String>(this.baseUrl + "?name=" + configurationName, {responseType: 'text' as 'json'});
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
     * Uploads the configuration from the target of the given event as the configuration of the given configuration name.
     * Uses the setConfigCallback function to update the configuration in the application.
     * If configured, stores the configuration under the configured name into the database.
     * 
     * @param configName Name of the register data of the configuration to upload.
     * @param file file containing the config to be loaded.
     */
    uploadConfigurationByName(configName: string, file: Blob) {
        const config = this.getRegisteredConfigurationByName(configName);
        if (config != null) {
            this.uploadAllConfigurations(file, [configName]);
        }
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

        const fileName = this.fileService.getFile().name + "-configuration.yaml"
        this.fileUtilityService.saveYamlFile(configString, fileName);
    }

    /**
     * Uploads the configurations that are contained in the file and included in the given array.
     * Uses the setConfigCallback function to update the configuration in the application.
     * If configured, stores the configuration under the configured name into the database.
     * @param file file containing the config to be loaded.
     * @param includedConfigurations Names of the configurations to upload.
     */
    uploadAllConfigurations(file: Blob, includedConfigurations: Array<string>) {
        const reader = new FileReader();
        reader.addEventListener("load", () => {
            const configData = reader.result as string;
            const configurations = parse(configData);

            for (const [name, config] of Object.entries(configurations)) {
                const configData = this.getRegisteredConfigurationByName(name);

                if (configData == null || !includedConfigurations.includes(configData?.name)) {
                    continue;
                }

                const yamlConfig: { [a: string]: any } = {};
                yamlConfig[name] = config;
                const yamlConfigString = stringify(yamlConfig);

                if (configData.syncWithBackend) {
                    this.storeConfig(configData.name, yamlConfigString).subscribe({
                        error: (error) => {
                            // TODO Error handling
                            // this.error = error;
                        },
                    });
                }
                configData?.setConfigCallback(yamlConfigString);
            }

        }, false);

        reader.readAsText(file);
    }

}
