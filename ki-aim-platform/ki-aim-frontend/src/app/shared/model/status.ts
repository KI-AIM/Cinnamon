import { Mode } from "../../core/enums/mode";
import { Steps } from "../../core/enums/steps";
import { ProcessStatus } from "../../core/enums/process-status";

export class Status {
    mode: Mode;
    currentStep: Steps;
    externalProcessStatus: ProcessStatus;
}
