import { ProcessProgress } from "./process-progress";
import { SynthetizationComponentProgress } from "./synthetization-component-progress";

export class SynthetizationProcess {
    components?: {
        structured_synthesis?: SynthetizationComponentProgress;
        llm_synthesis?: SynthetizationComponentProgress;
    };
    session_key: string;
    status: ProcessProgress[];
    synthesizer_name: string;
}
