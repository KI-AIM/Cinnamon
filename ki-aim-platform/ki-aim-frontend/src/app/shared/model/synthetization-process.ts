import { ProcessProgress } from "./process-progress";

export class SynthetizationProcess {
    session_key: string;
    status: ProcessProgress[];
    synthesizer_name: string;
}
