import { Injectable } from '@angular/core';
import { ExecutionStepService } from "../../../shared/services/execution-step.service";
import { HttpClient } from "@angular/common/http";
import { SynthetizationProcess } from "../../../shared/model/synthetization-process";

@Injectable({
  providedIn: 'root'
})
export class ExecutionService extends ExecutionStepService {
    // TODO implement for anonymization
    public _synthProcess: SynthetizationProcess | string | null = null;
    public _anonProcess: string | null = null;

    constructor(
        http: HttpClient,
    ) {
        super(http);
    }

    public get anonProcess(): string | null {
        return this._anonProcess;
    };

    public get synthProcess(): SynthetizationProcess | string | null {
        return this._synthProcess;
    };

    protected override getStepName(): string {
        return "EXECUTION";
    }

    protected override setCustomStatus(key: string, status: string | null): void {
        if (key === "SYNTHETIZATION") {
            if (status === null) {
                this._synthProcess = null;
            } else {
                try {
                    this._synthProcess = JSON.parse(status)
                } catch (e) {
                    this._synthProcess = status
                }
            }
        } else if (key === "ANONYMIZATION") {
            this._anonProcess = status;
        }
    }

    protected override getSteps(): string[] {
        return ['ANONYMIZATION', 'SYNTHETIZATION'];
    }
}
