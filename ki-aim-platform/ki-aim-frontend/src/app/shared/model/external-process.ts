import { ProcessStatus } from "../../core/enums/process-status";
import {Steps} from "../../core/enums/steps";

export class ExternalProcess {
    externalProcessStatus: ProcessStatus;
    sessionKey: string;
    status: string | null;
    step: Steps;
}
