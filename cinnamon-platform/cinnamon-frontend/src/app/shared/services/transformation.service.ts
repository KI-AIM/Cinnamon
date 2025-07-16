import { TransformationResult } from "src/app/shared/model/transformation-result";
import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { concatMap, Observable, of, tap } from "rxjs";
import {Steps} from "../../core/enums/steps";
import {environments} from "../../../environments/environment";
import { StatusService } from "./status.service";

@Injectable({
    providedIn: "root"
})
export class TransformationService {
    private transformationResult: TransformationResult;
    private fetched: boolean = false;

	constructor(
        private readonly http: HttpClient,
        private readonly statusService: StatusService,
    ) {
        this.transformationResult = new TransformationResult();
    }

    getTransformationResult(): TransformationResult {
        return this.transformationResult;
    }

    setTransformationResult(value: TransformationResult) {
        this.transformationResult = value;
    }

    /**
     * Returns an observable of the transformation result.
     * If the data has already been stored, fetch the transformation result from the backend.
     * Otherwise, returns the local transformation result.
     */
    public fetchTransformationResult(): Observable<TransformationResult> {
        // Return local transformation result if it has been fetched before
        if (this.fetched) {
            return of(this.transformationResult);
        }

        return this.statusService.status$.pipe(
            concatMap(status => {
                if (this.statusService.isStepCompleted(Steps.VALIDATION)) {
                    return this.http.get<TransformationResult>(environments.apiUrl + "/api/data/validation/transformationResult")
                        .pipe(
                            tap(value => {
                                this.transformationResult = value;
                                this.fetched = true;
                            }),
                        );
                } else {
                    this.fetched = true;
                    return of(this.transformationResult);
                }
            }),
        );
    }
}
