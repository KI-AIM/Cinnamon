import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";
import {Steps} from "../../../core/enums/steps";

@Injectable({
    providedIn: 'root'
})
export class EvaluationService extends ExecutionStepService {

    constructor(
        http: HttpClient,
    ) {
        super(http);
    }

    protected override getStageName(): string {
        return "evaluation";
    }
}
