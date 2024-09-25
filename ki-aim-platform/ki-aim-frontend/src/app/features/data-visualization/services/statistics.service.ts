import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environments } from 'src/environments/environment';

@Injectable({
    providedIn: 'root',
})
export class StatisticsService {
    private baseUrl: String = environments.apiUrl + '/api/statistics';

    constructor(private httpClient: HttpClient) {

    }

    fetchHistogramForColumns(columns: String[]): Observable<Object> {
        const columnConfigString = columns.join(",")

        return this.httpClient.get(this.baseUrl + "/histogram", {
            "params": {
                "columns": columnConfigString
            }
        });
    }

}
