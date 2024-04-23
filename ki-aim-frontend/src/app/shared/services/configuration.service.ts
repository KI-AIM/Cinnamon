import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { ConfigurationRegisterData } from '../model/configuration-register-data';

/**
 * Service for managing configurations.
 */
@Injectable({
    providedIn: 'root'
})
export class ConfigurationService {
    private baseUrl: String = "api/config";
    private registeredConfigurations: Array<ConfigurationRegisterData>;

    constructor(private httpClient: HttpClient) {
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
}
