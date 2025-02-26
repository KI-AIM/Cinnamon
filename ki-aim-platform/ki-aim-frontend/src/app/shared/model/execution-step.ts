import { ProcessStatus } from "../../core/enums/process-status";
import { ExternalProcess } from "./external-process";

export class ExecutionStep {
    currentProcessIndex: number;
    processes: ExternalProcess[];
    status: ProcessStatus;
}
