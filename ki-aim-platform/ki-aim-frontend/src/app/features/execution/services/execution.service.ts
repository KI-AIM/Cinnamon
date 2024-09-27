import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";
import { SynthetizationProcess } from "../../../shared/model/synthetization-process";

@Injectable({
  providedIn: 'root'
})
export class ExecutionService extends ExecutionStepService {
    // TODO implement for anonymization
    public _synthProcess: SynthetizationProcess | null = null;

    constructor(
        http: HttpClient,
    ) {
        super(http);
    }

    public get synthProcess(): SynthetizationProcess | null {
        return this._synthProcess;
    };

    protected override getStepName(): string {
        return "EXECUTION";
    }

    protected override setCustomStatus(key: string, status: string | null): void {
        if (key === "SYNTHETIZATION") {
            this._synthProcess = status === null ? null : JSON.parse(status);
        }
    }

    protected override getSteps(): string[] {
        return ['ANONYMIZATION', 'SYNTHETIZATION'];
    }
}
