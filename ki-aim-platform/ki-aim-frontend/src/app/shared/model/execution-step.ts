import { ProcessStatus } from "../../core/enums/process-status";
import { Steps } from "../../core/enums/steps";
import { ExternalProcess } from "./external-process";

export class ExecutionStep {
    currentProcessIndex: number;
    currentStep: Steps;
    processes: ExternalProcess[];
    status: ProcessStatus;
}
