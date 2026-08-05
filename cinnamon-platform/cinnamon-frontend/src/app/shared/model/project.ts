import { StageStatus } from "@core/enums/process-status";
import { Steps } from "@core/enums/steps";

export class Project {
    id: string;
    name: string;
}

export class ProjectOverview {
    info: Project;
    currentStep: Steps;
    stageStatuses: StageStatus[];
}
