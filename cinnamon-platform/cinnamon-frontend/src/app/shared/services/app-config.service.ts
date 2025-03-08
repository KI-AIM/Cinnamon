import { Injectable } from '@angular/core';
import { environments } from "../../../environments/environment";
import { HttpClient } from "@angular/common/http";

@Injectable({
    providedIn: 'root'
})
export class AppConfigService {
    private readonly baseURL = environments.apiUrl;

    public config: AppConfig | null = null;

    constructor(
        http: HttpClient,
    ) {
        http.get<AppConfig>(this.baseURL + "/config.json").subscribe(config => {
            this.config = config;
        });
    }

}

export interface AppConfig {
    isDemoInstance: boolean;
}
