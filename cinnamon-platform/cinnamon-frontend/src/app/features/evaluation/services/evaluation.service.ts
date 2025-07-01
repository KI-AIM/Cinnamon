import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";
import {Steps} from "../../../core/enums/steps";
import { StatusService } from "../../../shared/services/status.service";
import { ErrorHandlingService } from "../../../shared/services/error-handling.service";

@Injectable({
    providedIn: 'root'
})
export class EvaluationService extends ExecutionStepService {

    constructor(
        errorHandlingService: ErrorHandlingService,
        http: HttpClient,
        statusService: StatusService,
    ) {
        super(errorHandlingService, http, statusService);
    }

    protected override getStageName(): string {
        return "evaluation";
    }

    protected override getStep(): Steps {
        return Steps.EVALUATION;
    }

    /**
     * Returns the display name for the given job.
     * @param name Key of the job.
     */
    public getJobName(name: string): string {
        const jobNames: Record<string, string> = {
            'technical_evaluation': 'Technical Evaluation',
            'risk_evaluation': 'Risk Evaluation',
            'base_evaluation': 'Base Evaluation',
        };

        return jobNames[name];
    }
}
