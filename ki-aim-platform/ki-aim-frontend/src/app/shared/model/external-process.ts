import { ProcessStatus } from "../../core/enums/process-status";

export class ExternalProcess {
    externalProcessStatus: ProcessStatus;
    sessionKey: string;
    status: string | null;
}
