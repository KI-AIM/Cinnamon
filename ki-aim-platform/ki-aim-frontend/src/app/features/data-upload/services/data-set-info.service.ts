import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {finalize, Observable, of, share, tap} from "rxjs";
import {DataSetInfo} from "../../../shared/model/data-set-info";
import {environments} from "../../../../environments/environment";

@Injectable({
    providedIn: 'root'
})
export class DataSetInfoService {
    private dataSetInfo: DataSetInfo | null = null;
    private dataSetInfo$: Observable<DataSetInfo> | null = null;

    constructor(private readonly http: HttpClient) {
    }

    /**
     * Returns the information to the dataset of the validation step.
     */
    public getDataSetInfo(): Observable<DataSetInfo> {
        if (this.dataSetInfo) {
            return of(this.dataSetInfo);
        }
        if (this.dataSetInfo$) {
            return this.dataSetInfo$;
        }

        this.dataSetInfo$ = this.http.get<DataSetInfo>(environments.apiUrl + "/api/data/validation/info").pipe(
            tap(value => {
                this.dataSetInfo = value;
            }),
            share(),
            finalize(() => {
                this.dataSetInfo$ = null;
            }),
        );
        return this.dataSetInfo$;
    }
}
