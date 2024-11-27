import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";
import {Steps} from "../../../core/enums/steps";

@Injectable({
    providedIn: 'root'
})
export class EvaluationService extends ExecutionStepService {

    private _technicalEvaluationStatus: string | null = null;

    constructor(
        http: HttpClient,
    ) {
        super(http);
    }

    public get technicalEvaluationStatus(): string | null {
        return this._technicalEvaluationStatus;
    }

    protected override getStepName(): string {
        return "EVALUATION";
    }

    protected override setCustomStatus(key: Steps, status: string | null): void {
        this._technicalEvaluationStatus = status;
    }
}
