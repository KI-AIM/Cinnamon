import { ProcessProgress } from "./process-progress";

export class SynthetizationProgress {
    callback: ProcessProgress;
    fitting: ProcessProgress;
    initialization: ProcessProgress;
    sampling: ProcessProgress;
}
