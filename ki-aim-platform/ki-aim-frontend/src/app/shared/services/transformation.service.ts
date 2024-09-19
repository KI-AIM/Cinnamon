import { TransformationResult } from "src/app/shared/model/transformation-result";
import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { concatMap, Observable, of, tap } from "rxjs";
import {StateManagementService} from "../../core/services/state-management.service";
import {Steps} from "../../core/enums/steps";
import {environments} from "../../../environments/environment";

@Injectable({
    providedIn: "root"
})
export class TransformationService {
    private transformationResult: TransformationResult;
    private fetched: boolean = false;
    private step: string = 'validation';

	constructor(
        private readonly http: HttpClient,
        private stateManagement: StateManagementService,
    ) {
        this.transformationResult = new TransformationResult();
    }

    public setStep(step: string): void {
        this.step = step;
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

        return this.stateManagement.fetchStatus().pipe(
            concatMap(status => {
                if (this.stateManagement.isStepCompleted(Steps.VALIDATION)) {
                    return this.http.get<TransformationResult>(environments.apiUrl + "/api/data/" + this.step + "/transformationResult")
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
