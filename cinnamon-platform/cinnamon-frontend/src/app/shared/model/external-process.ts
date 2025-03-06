import { ProcessStatus } from "../../core/enums/process-status";
import {Steps} from "../../core/enums/steps";

export class ExternalProcess {
    externalProcessStatus: ProcessStatus;
    status: string | null;
    step: string;
    processSteps: string[];
}
