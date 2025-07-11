import {Injectable} from '@angular/core';
import { StateManagementService } from "@core/services/state-management.service";
import {ExecutionStepService} from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";
import { StatusService } from "../../../shared/services/status.service";
import { Steps } from "../../../core/enums/steps";
import { ErrorHandlingService } from "../../../shared/services/error-handling.service";

@Injectable({
  providedIn: 'root'
})
export class ExecutionService extends ExecutionStepService {

    constructor(
        errorHandlingService: ErrorHandlingService,
        http: HttpClient,
        stateManagementService: StateManagementService,
        statusService: StatusService,
    ) {
        super(errorHandlingService, http, stateManagementService, statusService);
    }

    protected override getStageName(): string {
        return "execution";
    }

    protected override getStep(): Steps {
        return Steps.EXECUTION;
    }

    /**
     * Returns the display name for the given job.
     * @param job Key of the job.
     */
    public getJobName(job: string): string {
        const jobNames: Record<string, string> = {
            'anonymization': 'Anonymization',
            'synthetization': 'Synthetization',
        };

        return jobNames[job];
    }
}
