import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";

@Injectable({
    providedIn: 'root'
})
export class ConfigurationService {
    private baseUrl: String = "api/config";

    constructor(private httpClient: HttpClient) {
    }

    public loadConfig(configurationName: String): Observable<String> {
        return this.httpClient.get<String>(this.baseUrl + "?name=" + configurationName, {responseType: 'text' as 'json'});
    }

    public storeConfig(configurationName: String, configuration: String) {
        this.httpClient.post<Number>(this.baseUrl + "?name=" + configurationName, configuration);
    }

}
