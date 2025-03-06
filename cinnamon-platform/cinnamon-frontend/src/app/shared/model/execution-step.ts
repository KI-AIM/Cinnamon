import { ProcessStatus } from "../../core/enums/process-status";
import { ExternalProcess } from "./external-process";
import { Type } from "class-transformer";

export class ExecutionStep {
    currentProcessIndex: number;
    @Type(() => ExternalProcess)
    processes: ExternalProcess[];
    status: ProcessStatus;
}
