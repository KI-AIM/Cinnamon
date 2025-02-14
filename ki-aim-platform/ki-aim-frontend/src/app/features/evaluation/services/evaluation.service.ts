import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";
import {Steps} from "../../../core/enums/steps";

@Injectable({
    providedIn: 'root'
})
export class EvaluationService extends ExecutionStepService {

    private _technicalEvaluationStatus: string | null = null;
    private _processSteps: string[] = [];

    constructor(
        http: HttpClient,
    ) {
        super(http);
    }

    public get technicalEvaluationStatus(): string | null {
        return this._technicalEvaluationStatus;
    }

    public get processSteps(): string[] {
        return this._processSteps;
    }

    protected override getStepName(): string {
        return "evaluation";
    }

    protected override setCustomStatus(key: string, status: string | null, processSteps: string[]): void {
        if (key === "technical_evaluation") {
            this._technicalEvaluationStatus = status;
            this._processSteps = processSteps;
        }
    }
}
