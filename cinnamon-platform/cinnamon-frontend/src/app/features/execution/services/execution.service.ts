import {Injectable} from '@angular/core';
import {ExecutionStepService} from "../../../shared/services/execution-step.service";
import {HttpClient} from "@angular/common/http";
import { StatusService } from "../../../shared/services/status.service";
import { Steps } from "../../../core/enums/steps";

@Injectable({
  providedIn: 'root'
})
export class ExecutionService extends ExecutionStepService {

    constructor(
        http: HttpClient,
        statusService: StatusService,
    ) {
        super(http, statusService);
    }

    protected override getStageName(): string {
        return "execution";
    }

    protected override getStep(): Steps {
        return Steps.EXECUTION;
    }
}
