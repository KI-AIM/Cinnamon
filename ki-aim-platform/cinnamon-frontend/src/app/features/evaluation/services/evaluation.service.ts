import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";
import {Steps} from "../../../core/enums/steps";
import { StatusService } from "../../../shared/services/status.service";

@Injectable({
    providedIn: 'root'
})
export class EvaluationService extends ExecutionStepService {

    constructor(
        http: HttpClient,
        statusService: StatusService,
    ) {
        super(http, statusService);
    }

    protected override getStageName(): string {
        return "evaluation";
    }

    protected override getStep(): Steps {
        return Steps.EVALUATION;
    }
}
