import {Injectable} from '@angular/core';
import {ExecutionStepService} from "../../../shared/services/execution-step.service";
import {HttpClient} from "@angular/common/http";

@Injectable({
  providedIn: 'root'
})
export class ExecutionService extends ExecutionStepService {

    constructor(
        http: HttpClient,
    ) {
        super(http);
    }

    protected override getStageName(): string {
        return "execution";
    }
}
