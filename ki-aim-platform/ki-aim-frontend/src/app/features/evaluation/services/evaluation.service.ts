import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";

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

    protected override setCustomStatus(key: string, status: string | null): void {
        this._technicalEvaluationStatus = status;
    }

    protected override getSteps(): string[] {
        return ['TECHNICAL_EVALUATION'];
    }
}
