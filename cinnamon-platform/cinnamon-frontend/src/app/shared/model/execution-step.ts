import { ProcessStatus } from "../../core/enums/process-status";
import { ExternalProcess } from "./external-process";
import { Type } from "class-transformer";

export class ExecutionStep {
    currentProcessIndex: number;
    @Type(() => ExternalProcess)
    processes: ExternalProcess[];
    stageName: string;
    status: ProcessStatus;
}

export class PipelineInformation {
    currentStageIndex: number | null;

    @Type(() => ExecutionStep)
    stages: ExecutionStep[];
}
