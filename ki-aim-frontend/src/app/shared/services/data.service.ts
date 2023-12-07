import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { DataConfiguration } from '../model/data-configuration';
import { instanceToPlain } from 'class-transformer';

@Injectable({
    providedIn: 'root',
})
export class DataService {
    private baseUrl: String = "api/data"

    constructor(private httpClient: HttpClient) {
    }

    estimateData(file: File): Observable<Object> {
        const formData = new FormData();

        formData.append("file", file);

        return this.httpClient.post(this.baseUrl + "/datatypes", formData);
    }    

    readAndValidateData(file: File, config: DataConfiguration): Observable<Object>  {
        const formData = new FormData();

        formData.append("file", file);
        
        var configString = JSON.stringify(instanceToPlain(config)); 
        formData.append("configuration", configString); 

        return this.httpClient.post(this.baseUrl + "/validation", formData);
    }
}
