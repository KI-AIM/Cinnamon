import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';

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

    readAndValidateData(file: File) {
        const formData = new FormData();

        formData.append("file", file);

        const upload$ = this.httpClient.post(this.baseUrl + "/validation", formData);
        upload$.subscribe();
    }
}
