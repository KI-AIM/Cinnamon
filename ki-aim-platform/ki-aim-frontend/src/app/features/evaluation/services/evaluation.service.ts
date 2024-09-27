import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";

@Injectable({
    providedIn: 'root'
})
export class EvaluationService extends ExecutionStepService {

    constructor(
        http: HttpClient,
    ) {
        super(http);
    }

    protected override getStepName(): string {
        return "EVALUATION";
    }

    protected override setCustomStatus(key: string, status: string | null): void {
    }

    protected override getSteps(): string[] {
        return ['TECHNICAL_EVALUATION'];
    }
}
