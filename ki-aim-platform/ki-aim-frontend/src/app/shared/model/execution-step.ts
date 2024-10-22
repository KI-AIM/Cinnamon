import { ProcessStatus } from "../../core/enums/process-status";
import { Steps } from "../../core/enums/steps";
import { ExternalProcess } from "./external-process";

export class ExecutionStep {
    currentStep: Steps;
    processes: { [stepName: string]: ExternalProcess };
    status: ProcessStatus;
}
