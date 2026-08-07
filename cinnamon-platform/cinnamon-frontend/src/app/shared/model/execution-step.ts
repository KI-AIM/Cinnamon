import { StageStatus } from "@core/enums/process-status";
import { Type } from "class-transformer";
import { ExternalProcess } from "./external-process";

export class ExecutionStep {
    currentProcessIndex: number;
    @Type(() => ExternalProcess)
    processes: ExternalProcess[];
    stageName: string;
    status: StageStatus;
}

/**
 * Status of a pipeline.
 */
export class PipelineInformation {
    /**
     * Index of the currently running stage.
     * Null if no stage is running.
     */
    currentStageIndex: number | null;

    /**
     * List containing status information for all stages.
     */
    @Type(() => ExecutionStep)
    stages: ExecutionStep[];
}
